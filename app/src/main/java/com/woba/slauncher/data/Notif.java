package com.woba.slauncher.data;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

/** Notificaciones capturadas por WpNotificationService. Solo hilo UI. */
public final class Notif {
    public String key, pkg, title, text;
    public long time;
    public boolean clearable;
    public PendingIntent intent;
    /** Notificación simulada: se ejecuta al tocarla. */
    public Runnable action;

    public interface Listener { void onNotifsChanged(); }

    private static final ArrayList<Notif> ALL = new ArrayList<>();
    private static final ArrayList<Listener> LISTENERS = new ArrayList<>();
    private static boolean connected;

    public static void addListener(Listener l) { if (!LISTENERS.contains(l)) LISTENERS.add(l); }

    public static void removeListener(Listener l) { LISTENERS.remove(l); }

    public static boolean connected() { return connected; }

    public static void setConnected(boolean c) { connected = c; fire(); }

    public static List<Notif> all() { return ALL; }

    public static void replaceAll(List<Notif> list) {
        ArrayList<Notif> keep = new ArrayList<>();
        for (Notif n : ALL) if (n.key != null && n.key.startsWith("sim:")) keep.add(n);
        ALL.clear();
        ALL.addAll(list);
        ALL.addAll(keep);
        fire();
    }

    public static void upsert(Notif n) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).key.equals(n.key)) { ALL.set(i, n); fire(); return; }
        }
        ALL.add(0, n);
        fire();
    }

    public static void remove(String key) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).key.equals(key)) { ALL.remove(i); fire(); return; }
        }
    }

    private static void fire() {
        for (Listener l : new ArrayList<>(LISTENERS)) l.onNotifsChanged();
    }
}
