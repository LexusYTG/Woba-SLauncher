package com.woba.slauncher;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.woba.slauncher.data.Notif;

import java.util.ArrayList;

/** Recibe todas las notificaciones del sistema para mostrarlas en el Action Center WP. */
public class WpNotificationService extends NotificationListenerService {
    private static WpNotificationService instance;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private static Notif convert(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return null;
        // Se omiten resúmenes de grupo y servicios en primer plano silenciosos.
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return null;
        CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (x == null) x = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        if (t == null && x == null) return null;
        Notif o = new Notif();
        o.key = sbn.getKey();
        o.pkg = sbn.getPackageName();
        o.title = t == null ? "" : t.toString();
        o.text = x == null ? "" : x.toString();
        o.time = sbn.getPostTime();
        o.clearable = sbn.isClearable();
        o.intent = n.contentIntent;
        return o;
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        final ArrayList<Notif> list = new ArrayList<>();
        try {
            StatusBarNotification[] arr = getActiveNotifications();
            if (arr != null) for (StatusBarNotification s : arr) {
                Notif o = convert(s);
                if (o != null) list.add(o);
            }
        } catch (Exception ignored) {
        }
        ui.post(new Runnable() {
            @Override public void run() {
                Notif.replaceAll(list);
                Notif.setConnected(true);
            }
        });
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        ui.post(new Runnable() {
            @Override public void run() { Notif.setConnected(false); }
        });
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        final Notif o = convert(sbn);
        if (o == null) return;
        ui.post(new Runnable() {
            @Override public void run() { Notif.upsert(o); }
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        final String k = sbn.getKey();
        ui.post(new Runnable() {
            @Override public void run() { Notif.remove(k); }
        });
    }

    /** Descarta una notificación del sistema (la X del Action Center). */
    public static void dismiss(String key) {
        if (instance != null) try { instance.cancelNotification(key); } catch (Exception ignored) { }
    }

    public static void dismissAll() {
        if (instance != null) try { instance.cancelAllNotifications(); } catch (Exception ignored) { }
    }
}
