package com.woba.slauncher.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.Telephony;
import android.widget.Toast;

public final class Actions {
    private Actions() {}

    public static void start(Context c, Intent intent) {
        try {
            c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(c, "No se pudo abrir", Toast.LENGTH_SHORT).show();
        }
    }

    public static void launchApp(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) start(c, i);
    }

    public static void openDialer(Context c) {
        start(c, new Intent(Intent.ACTION_DIAL));
    }

    public static void openMessages(Context c) {
        Intent i = null;
        String def = Telephony.Sms.getDefaultSmsPackage(c);
        if (def != null) i = c.getPackageManager().getLaunchIntentForPackage(def);
        if (i == null) i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING);
        start(c, i);
    }

    public static void openClock(Context c) {
        start(c, new Intent(AlarmClock.ACTION_SHOW_ALARMS));
    }

    public static void openBatteryUsage(Context c) {
        start(c, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
    }

    public static void uninstall(Context c, String pkg) {
        start(c, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
    }
}
