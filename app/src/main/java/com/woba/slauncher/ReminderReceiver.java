package com.woba.slauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recibe las alarmas de recordatorio (calendario, Cortana) y las muestra como notificación de Android. */
public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "wp_reminders";

    @Override
    public void onReceive(Context c, Intent i) {
        String title = i.getStringExtra("title"), text = i.getStringExtra("text");
        int id = i.getIntExtra("id", (int) System.currentTimeMillis());
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String sim = i.getStringExtra("open");
        if (sim != null) open.putExtra("sim_open", sim);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title == null ? "Reminder" : title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(id, b.build());
    }
}
