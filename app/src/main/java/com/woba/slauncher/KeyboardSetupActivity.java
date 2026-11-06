package com.woba.slauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.woba.slauncher.data.Prefs;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

/** Activar el teclado y el asistente de WOBA (Android no deja que una app se active sola). */
public class KeyboardSetupActivity extends Activity {
    private LinearLayout col;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        Theme.apply(Prefs.loadAccent(this), Prefs.loadDark(this));
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Theme.bg);
        sv.setFillViewport(true);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, Theme.dp(24), 0, Theme.dp(24));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        setContentView(sv);
        sv.setOnApplyWindowInsetsListener((v, in) -> { v.setPadding(0, in.getSystemWindowInsetTop(), 0, in.getSystemWindowInsetBottom()); return in; });
    }

    @Override protected void onResume() {
        super.onResume();
        build();
    }

    private boolean imeEnabled() {
        InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        for (InputMethodInfo i : m.getEnabledInputMethodList()) if (i.getPackageName().equals(getPackageName())) return true;
        return false;
    }

    private boolean imeSelected() {
        String d = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return d != null && d.startsWith(getPackageName() + "/");
    }

    private boolean assistantSelected() {
        try {
            String a = Settings.Secure.getString(getContentResolver(), "assistant");
            return a != null && a.startsWith(getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    private void build() {
        col.removeAllViews();
        LinearLayout head = WpUi.pageHeader(this, "woba", "keyboard & assistant");
        col.addView(head);
        TextView n = WpUi.label(this, "Android needs you to switch these on yourself. Each step opens the right screen.", 16, Theme.light, Theme.subtle);
        n.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(12));
        col.addView(n);

        step("1. Turn on the WOBA keyboard", imeEnabled(), () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        step("2. Use the WOBA keyboard", imeSelected(), () -> {
            InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            m.showInputMethodPicker();
        });
        step("3. Make WOBA your digital assistant", assistantSelected(), this::pickAssistant);
        step("Camera & microphone permissions", checkSelfPermission("android.permission.CAMERA") == 0, () ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));

        TextView t = WpUi.label(this, "TRY IT", 13, Theme.semibold, Theme.fg);
        t.setPadding(Theme.dp(18), Theme.dp(18), Theme.dp(18), Theme.dp(6));
        col.addView(t);
        android.widget.EditText et = WpUi.textBox(this, "type here", "");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(et, lp);
    }

    private void step(String title, boolean done, final Runnable go) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(10));
        r.addView(WpUi.label(this, title, 20, Theme.regular, Theme.fg));
        TextView s = WpUi.label(this, done ? "on" : "tap to set up", 14, Theme.light, done ? Theme.accent : Theme.subtle);
        s.setPadding(0, Theme.dp(3), 0, 0);
        r.addView(s);
        r.setOnClickListener(v -> { try { go.run(); } catch (Exception ignored) { } });
        WpUi.tilt(r);
        col.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private void pickAssistant() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager rm = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable("android.app.role.ASSISTANT") && !rm.isRoleHeld("android.app.role.ASSISTANT")) {
                    startActivityForResult(rm.createRequestRoleIntent("android.app.role.ASSISTANT"), 5);
                    return;
                }
            }
        } catch (Exception ignored) { }
        try {
            startActivity(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        }
    }
}
