package com.woba.slauncher.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.Telephony;
import android.text.format.DateFormat;
import android.util.LruCache;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Consultas al sistema: permisos, llamadas, SMS, apps, iconos, batería y formato de hora. */
public final class Sys {
    private Sys() {}

    /** Permisos de tiempo de ejecución que usa el launcher (llamadas, SMS, estado del teléfono y Bluetooth). */
    public static String[] wantedPermissions() {
        ArrayList<String> l = new ArrayList<>();
        l.add("android.permission.READ_CALL_LOG");
        l.add("android.permission.READ_SMS");
        l.add("android.permission.READ_PHONE_STATE");
        if (android.os.Build.VERSION.SDK_INT >= 31) l.add("android.permission.BLUETOOTH_CONNECT");
        return l.toArray(new String[0]);
    }

    public static boolean has(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    public static int countMissedCalls(Context c) {
        if (!has(c, "android.permission.READ_CALL_LOG")) return 0;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls._ID},
                    CallLog.Calls.TYPE + " = ? AND " + CallLog.Calls.NEW + " = ?",
                    new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE), "1"},
                    null);
            return cur == null ? 0 : cur.getCount();
        } catch (Exception e) {
            return 0;
        } finally {
            if (cur != null) cur.close();
        }
    }

    public static int countUnreadSms(Context c) {
        if (!has(c, "android.permission.READ_SMS")) return 0;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    new String[]{Telephony.Sms._ID},
                    Telephony.Sms.READ + " = 0",
                    null, null);
            return cur == null ? 0 : cur.getCount();
        } catch (Exception e) {
            return 0;
        } finally {
            if (cur != null) cur.close();
        }
    }

    public static List<AppEntry> loadApps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        HashSet<String> seen = new HashSet<>();
        ArrayList<AppEntry> out = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
            AppEntry e = new AppEntry(ri.loadLabel(pm).toString(), pkg);
            try {
                e.installed = pm.getPackageInfo(pkg, 0).firstInstallTime;
            } catch (Exception ignored) {
            }
            out.add(e);
        }
        Collections.sort(out, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.toLowerCase().compareTo(b.label.toLowerCase());
            }
        });
        return out;
    }

    public static void readBattery(Intent i, Live live) {
        if (i == null) return;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        live.battery = (level >= 0 && scale > 0) ? level * 100 / scale : 0;
        live.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static String timeText(Context c, Date d) {
        String p = DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm";
        return new SimpleDateFormat(p, Locale.getDefault()).format(d);
    }

    public static String dateText(Date d) {
        return new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(d);
    }

    // ------------------------------------------------------------ iconos

    private static final LruCache<String, Bitmap> ICONS = new LruCache<>(300);
    private static final HashMap<String, ArrayList<Runnable>> WAITING = new HashMap<>();
    private static final HashSet<String> FAILED = new HashSet<>();
    private static final ExecutorService ICON_IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * Devuelve el icono ya cacheado o null; en ese caso lo carga en segundo plano y
     * llama a onLoaded (hilo UI) cuando esté listo. Solo usar desde el hilo UI.
     */
    public static Bitmap icon(Context ctx, final String pkg, final int px, Runnable onLoaded) {
        final String key = pkg + "@" + px;
        Bitmap b = ICONS.get(key);
        if (b != null) return b;
        if (FAILED.contains(key)) return null;
        ArrayList<Runnable> waiting = WAITING.get(key);
        if (waiting != null) {
            if (onLoaded != null) waiting.add(onLoaded);
            return null;
        }
        waiting = new ArrayList<>();
        if (onLoaded != null) waiting.add(onLoaded);
        WAITING.put(key, waiting);
        final Context app = ctx.getApplicationContext();
        ICON_IO.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    Drawable d = app.getPackageManager().getApplicationIcon(pkg);
                    bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
                    Canvas cv = new Canvas(bmp);
                    d.setBounds(0, 0, px, px);
                    d.draw(cv);
                } catch (Exception e) {
                    bmp = null;
                }
                final Bitmap res = bmp;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (res != null) ICONS.put(key, res);
                        else FAILED.add(key);
                        ArrayList<Runnable> cbs = WAITING.remove(key);
                        if (cbs != null) for (Runnable r : cbs) r.run();
                    }
                });
            }
        });
        return null;
    }
}
