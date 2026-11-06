package com.woba.slauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

public class WpAccessibilityService extends AccessibilityService {
    private static volatile WpAccessibilityService instance;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /** Bloquea la pantalla de verdad (API 28+). false si el servicio no está activo. */
    public static boolean lock() {
        WpAccessibilityService s = instance;
        if (s == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        return s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }
}
