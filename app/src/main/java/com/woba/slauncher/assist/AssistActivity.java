package com.woba.slauncher.assist;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.woba.slauncher.data.AppEntry;
import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.data.Prefs;
import com.woba.slauncher.data.Sys;
import com.woba.slauncher.ui.CortanaView;
import com.woba.slauncher.ui.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * El asistente: Cortana como panel que sube desde abajo sobre la app que estés usando, como el
 * asistente de Google al mantener pulsado Home. También responde a ACTION_ASSIST / VOICE_COMMAND.
 */
public class AssistActivity extends Activity {
    private static final int REQ_MIC = 71, REQ_OTHER = 72;

    private FrameLayout root;
    private CortanaView cortana;
    private HostBridge.MicCallback onMic;
    private Runnable permDone;
    private volatile List<AppEntry> apps;
    private boolean closing;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        Theme.apply(Prefs.loadAccent(this), Prefs.loadDark(this));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        final int screenH = getResources().getDisplayMetrics().heightPixels;
        root = new FrameLayout(this) {
            @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                super.onSizeChanged(w, h, ow, oh);
                resizePanel(h, screenH);
            }
        };
        root.setBackgroundColor(Color.TRANSPARENT);
        // tocar fuera del panel lo cierra
        root.setOnClickListener(v -> finishPanel());
        setContentView(root);

        cortana = new CortanaView(this, new Bridge(), new CortanaView.Listener() {
            @Override public void onClose() { finishPanel(); }
            @Override public List<AppEntry> apps() { return appList(); }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, (int) (screenH * 0.62f), Gravity.BOTTOM);
        root.addView(cortana, lp);

        // la lista de apps se carga aparte para no frenar la apertura
        new Thread(() -> apps = Sys.loadApps(getApplicationContext())).start();

        cortana.setTranslationY(screenH);
        cortana.animate().translationY(0f).setDuration(220).start();
        root.setBackgroundColor(0x66000000);
        cortana.open();
    }

    private void resizePanel(int h, int screenH) {
        if (cortana == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cortana.getLayoutParams();
        // con el teclado abierto la ventana se encoge: el panel ocupa todo para que quepan la charla y el campo
        int want = h < screenH * 0.8f ? h : (int) (screenH * 0.62f);
        if (lp.height != want) {
            lp.height = want;
            cortana.setLayoutParams(lp);
        }
    }

    private List<AppEntry> appList() {
        List<AppEntry> a = apps;
        if (a == null) {
            a = Sys.loadApps(getApplicationContext());
            apps = a;
        }
        return a;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (cortana != null) cortana.open(); // otra pulsación larga: vuelve a escuchar
    }

    @Override
    public void onBackPressed() {
        finishPanel();
    }

    /** Si se abre otra app (Cortana lanza una) o se va a Home, el panel ya no hace falta. */
    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) finishPanel();
    }

    private void finishPanel() {
        if (closing) return;
        closing = true;
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        WobaVoiceInteractionSession.dismissCurrent();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_MIC) {
            HostBridge.MicCallback cb = onMic;
            onMic = null;
            if (cb != null) cb.onResult(res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED);
        } else if (code == REQ_OTHER) {
            Runnable r = permDone;
            permDone = null;
            if (r != null) r.run();
        }
    }

    /** Cortana solo necesita el micrófono; el resto del puente es del launcher y aquí no hace nada. */
    private final class Bridge implements HostBridge {
        @Override public void requestMic(MicCallback cb) {
            if (checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED) {
                cb.onResult(true);
                return;
            }
            onMic = cb;
            AssistActivity.this.requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQ_MIC);
        }

        @Override public void requestPermissions(String[] p, Runnable done) {
            permDone = done;
            AssistActivity.this.requestPermissions(p, REQ_OTHER);
        }

        @Override public void requestLivePermissions(String[] p) { }
        @Override public void pickImage(ImageCallback cb) { }
        @Override public List<AppWidgetProviderInfo> widgetProviders() { return new ArrayList<>(); }
        @Override public void addWidget(AppWidgetProviderInfo provider, WidgetCallback cb) { }
        @Override public AppWidgetHostView createWidgetView(int widgetId) { return null; }
        @Override public void deleteWidget(int widgetId) { }
        @Override public boolean lockDevice() { return false; }
    }
}
