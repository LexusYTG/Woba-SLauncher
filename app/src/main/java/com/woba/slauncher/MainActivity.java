package com.woba.slauncher;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.ui.LauncherRoot;
import com.woba.slauncher.ui.Theme;

import java.util.List;

public class MainActivity extends Activity implements HostBridge {
    private static final int HOST_ID = 2026;
    private static final int REQ_BIND = 1001, REQ_CONFIGURE = 1002, REQ_PERM = 1003, REQ_PICK = 1004, REQ_MIC = 1005, REQ_PERM2 = 1006;
    private MicCallback onMic;

    private AppWidgetManager awm;
    private AppWidgetHost awHost;
    private LauncherRoot root;

    // Flujo para añadir un widget: bind -> configure -> listo.
    private int pendingWidgetId = -1;
    private WidgetCallback onWidgetReady;
    private ImageCallback onImage;

    // Al apagar la pantalla, al volver se muestra la pantalla de bloqueo WP.
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (root != null) root.onScreenOff();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        Window w = getWindow();
        // Fullscreen real: el contenido se dibuja bajo las barras y el notch.
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        awm = AppWidgetManager.getInstance(this);
        awHost = new AppWidgetHost(this, HOST_ID);
        root = new LauncherRoot(this, this);
        setContentView(root);
        enterImmersive();

        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, f);
    }

    @SuppressWarnings("deprecation")
    private void enterImmersive() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                c.hide(WindowInsets.Type.systemBars());
            }
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Las barras reaparecen tras diálogos, ajustes o el teclado: se vuelven a ocultar.
        if (hasFocus) enterImmersive();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        root.goHome(); // Botón Home con el launcher ya abierto.
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            awHost.startListening();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        root.refreshData();
        root.onHostResume();
    }

    @Override
    protected void onPause() {
        root.onHostPause(); // la cámara propia suelta el hardware
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent e) {
        // volumen / tecla de cámara = disparador cuando la cámara está arriba (se traga también la repetición)
        if (root.onHardwareKey(keyCode)) return true;
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            awHost.stopListening();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(screenOff);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        root.onBack(); // Un launcher no se cierra con "atrás".
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERM) root.refreshData();
        if (code == REQ_PERM2) {
            Runnable r = permDone;
            permDone = null;
            root.refreshData();
            if (r != null) r.run();
        }
        if (code == REQ_MIC) {
            MicCallback cb = onMic;
            onMic = null;
            if (cb != null) cb.onResult(results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_BIND) {
            if (res == RESULT_OK) configureOrFinishWidget();
            else cancelPendingWidget();
        } else if (req == REQ_CONFIGURE) {
            if (res == RESULT_OK) finishWidget();
            else cancelPendingWidget();
        } else if (req == REQ_PICK) {
            ImageCallback cb = onImage;
            onImage = null;
            if (res == RESULT_OK && data != null && data.getData() != null && cb != null) cb.onImage(data.getData());
        }
    }

    // ------------------------------------------------------------ HostBridge

    @Override
    @SuppressWarnings("deprecation")
    public void pickImage(ImageCallback cb) {
        onImage = cb;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            onImage = null;
        }
    }

    @Override
    public List<AppWidgetProviderInfo> widgetProviders() {
        return awm.getInstalledProviders();
    }

    @Override
    public AppWidgetHostView createWidgetView(int widgetId) {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(widgetId);
        if (info == null) return null;
        try {
            return awHost.createView(this, widgetId, info);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void deleteWidget(int widgetId) {
        try {
            awHost.deleteAppWidgetId(widgetId);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean lockDevice() {
        return WpAccessibilityService.lock();
    }

    @Override
    public void requestMic(MicCallback cb) {
        if (checkSelfPermission("android.permission.RECORD_AUDIO") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cb.onResult(true);
            return;
        }
        onMic = cb;
        requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQ_MIC);
    }

    private Runnable permDone;

    @Override
    public void requestPermissions(String[] permissions, Runnable done) {
        permDone = done;
        requestPermissions(permissions, REQ_PERM2);
    }

    @Override
    public void requestLivePermissions(String[] permissions) {
        requestPermissions(permissions, REQ_PERM);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void addWidget(AppWidgetProviderInfo provider, WidgetCallback cb) {
        cancelPendingWidget();
        pendingWidgetId = awHost.allocateAppWidgetId();
        onWidgetReady = cb;
        if (awm.bindAppWidgetIdIfAllowed(pendingWidgetId, provider.provider)) {
            configureOrFinishWidget();
        } else {
            Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider);
            try {
                startActivityForResult(i, REQ_BIND);
            } catch (Exception e) {
                cancelPendingWidget();
            }
        }
    }

    private void configureOrFinishWidget() {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(pendingWidgetId);
        if (info != null && info.configure != null) {
            try {
                // Usa el host para lanzar la configuración: funciona también con
                // actividades de configuración no exportadas de otras apps.
                awHost.startAppWidgetConfigureActivityForResult(this, pendingWidgetId, 0, REQ_CONFIGURE, null);
            } catch (Exception e) {
                finishWidget(); // Se añade tal cual si no se puede configurar.
            }
        } else {
            finishWidget();
        }
    }

    private void finishWidget() {
        int id = pendingWidgetId;
        WidgetCallback cb = onWidgetReady;
        pendingWidgetId = -1;
        onWidgetReady = null;
        if (id != -1 && cb != null) cb.onWidget(id);
    }

    private void cancelPendingWidget() {
        if (pendingWidgetId != -1) {
            try {
                awHost.deleteAppWidgetId(pendingWidgetId);
            } catch (Exception ignored) {
            }
        }
        pendingWidgetId = -1;
        onWidgetReady = null;
    }
}
