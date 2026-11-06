package com.woba.slauncher.sim;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.woba.slauncher.ReminderReceiver;

/** Programa recordatorios reales con AlarmManager (se muestran como notificación aunque el launcher no esté abierto). */
public final class Reminders {
    private Reminders() {}

    public static void schedule(Context c, String key, long at, String title, String text, String openSim) {
        if (at <= System.currentTimeMillis()) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = intent(c, key, title, text, openSim, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Exception e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context c, String key) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(intent(c, key, "", "", null, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static PendingIntent intent(Context c, String key, String title, String text, String open, int flag) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("wp.reminder." + key);
        i.putExtra("title", title);
        i.putExtra("text", text);
        i.putExtra("id", key.hashCode());
        if (open != null) i.putExtra("open", open);
        return PendingIntent.getBroadcast(c, key.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | flag);
    }
}
