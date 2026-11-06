package com.woba.slauncher.data;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.telecom.TelecomManager;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;

import java.util.List;
import java.util.Locale;

/**
 * Puente entre los ajustes de Windows Phone y los de Android.
 * Cada método lee o escribe el ajuste real; nunca lanza excepciones.
 * Escribir Settings.System exige el permiso especial WRITE_SETTINGS (canWrite).
 */
public final class SysSettings {
    private SysSettings() {}

    // ------------------------------------------------------------ permisos especiales

    public static boolean canWrite(Context c) {
        return Settings.System.canWrite(c);
    }

    public static Intent writeSettingsIntent(Context c) {
        return new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName()));
    }

    public static boolean hasPolicyAccess(Context c) {
        try {
            return c.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted();
        } catch (Exception e) {
            return false;
        }
    }

    public static Intent policyIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
    }

    public static boolean accessibilityEnabled(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(c.getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ pantalla

    public static boolean autoBrightness(Context c) {
        return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    public static boolean setAutoBrightness(Context c, boolean on) {
        return putInt(c, Settings.System.SCREEN_BRIGHTNESS_MODE, on
                ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    /** 0..1 */
    public static float brightness(Context c) {
        int v = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        return Math.max(0f, Math.min(1f, v / 255f));
    }

    public static boolean setBrightness(Context c, float v) {
        int i = Math.max(1, Math.min(255, Math.round(v * 255f)));
        return putInt(c, Settings.System.SCREEN_BRIGHTNESS, i);
    }

    public static boolean autoRotate(Context c) {
        return Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1) == 1;
    }

    public static boolean setAutoRotate(Context c, boolean on) {
        return putInt(c, Settings.System.ACCELEROMETER_ROTATION, on ? 1 : 0);
    }

    public static int timeoutMs(Context c) {
        return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 30000);
    }

    public static boolean setTimeoutMs(Context c, int ms) {
        return putInt(c, Settings.System.SCREEN_OFF_TIMEOUT, ms);
    }

    public static boolean is24h(Context c) {
        return DateFormat.is24HourFormat(c);
    }

    public static boolean set24h(Context c, boolean on) {
        try {
            return Settings.System.putString(c.getContentResolver(), Settings.System.TIME_12_24, on ? "24" : "12");
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean putInt(Context c, String key, int v) {
        try {
            return Settings.System.putInt(c.getContentResolver(), key, v);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ sonido

    public static final int STREAM_RING = AudioManager.STREAM_RING;
    public static final int STREAM_MEDIA = AudioManager.STREAM_MUSIC;
    public static final int STREAM_ALARM = AudioManager.STREAM_ALARM;

    private static AudioManager audio(Context c) {
        return (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    /** 0..1 */
    public static float volume(Context c, int stream) {
        AudioManager am = audio(c);
        int max = Math.max(1, am.getStreamMaxVolume(stream));
        return am.getStreamVolume(stream) / (float) max;
    }

    /** false si Android lo rechaza (p. ej. silenciar el timbre sin acceso a No molestar). */
    public static boolean setVolume(Context c, int stream, float v) {
        try {
            AudioManager am = audio(c);
            int max = am.getStreamMaxVolume(stream);
            am.setStreamVolume(stream, Math.max(0, Math.min(max, Math.round(v * max))), 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 0 = normal, 1 = vibrar, 2 = silencio */
    public static int ringerMode(Context c) {
        switch (audio(c).getRingerMode()) {
            case AudioManager.RINGER_MODE_VIBRATE: return 1;
            case AudioManager.RINGER_MODE_SILENT: return 2;
            default: return 0;
        }
    }

    public static boolean setRingerMode(Context c, int mode) {
        try {
            int m = mode == 1 ? AudioManager.RINGER_MODE_VIBRATE
                    : mode == 2 ? AudioManager.RINGER_MODE_SILENT : AudioManager.RINGER_MODE_NORMAL;
            audio(c).setRingerMode(m);
            return audio(c).getRingerMode() == m;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ No molestar ("quiet hours")

    public static boolean quietHours(Context c) {
        try {
            return c.getSystemService(NotificationManager.class).getCurrentInterruptionFilter()
                    != NotificationManager.INTERRUPTION_FILTER_ALL;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean setQuietHours(Context c, boolean on) {
        try {
            c.getSystemService(NotificationManager.class).setInterruptionFilter(on
                    ? NotificationManager.INTERRUPTION_FILTER_ALARMS : NotificationManager.INTERRUPTION_FILTER_ALL);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ estados de red (solo lectura)

    public static boolean wifiOn(Context c) {
        try {
            WifiManager w = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return w != null && w.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean bluetoothOn(Context c) {
        try {
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean airplaneOn(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    public static boolean locationOn(Context c) {
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean nfcOn(Context c) {
        try {
            android.nfc.NfcAdapter a = android.nfc.NfcAdapter.getDefaultAdapter(c);
            return a != null && a.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean nfcAvailable(Context c) {
        return c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    public static boolean batterySaverOn(Context c) {
        try {
            return ((PowerManager) c.getSystemService(Context.POWER_SERVICE)).isPowerSaveMode();
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ paneles rápidos de Android (API 29+)

    /** Intent del panel de Wi-Fi, o la pantalla clásica en Android 9 o anterior. */
    public static Intent wifiPanel() {
        if (Build.VERSION.SDK_INT >= 30) return new Intent(Settings.Panel.ACTION_WIFI);
        return new Intent(Settings.ACTION_WIFI_SETTINGS);
    }

    public static Intent internetPanel() {
        if (Build.VERSION.SDK_INT >= 29) return new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
        return new Intent(Settings.ACTION_WIRELESS_SETTINGS);
    }

    public static Intent nfcPanel() {
        if (Build.VERSION.SDK_INT >= 29) return new Intent(Settings.Panel.ACTION_NFC);
        return new Intent(Settings.ACTION_NFC_SETTINGS);
    }

    // ------------------------------------------------------------ almacenamiento, memoria, dispositivo

    public static long storageTotal() {
        try {
            return new StatFs(Environment.getDataDirectory().getPath()).getTotalBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    public static long storageFree() {
        try {
            return new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    public static long ramTotal(Context c) {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            return mi.totalMem;
        } catch (Exception e) {
            return 0;
        }
    }

    public static long ramFree(Context c) {
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
            return mi.availMem;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String bytes(long b) {
        double gb = b / 1073741824.0;
        if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb);
        return String.format(Locale.US, "%d MB", Math.round(b / 1048576.0));
    }

    public static String model() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String d = Build.MODEL == null ? "" : Build.MODEL;
        if (!m.isEmpty() && !d.toLowerCase(Locale.ROOT).startsWith(m.toLowerCase(Locale.ROOT))) {
            m = Character.toUpperCase(m.charAt(0)) + m.substring(1);
            return m + " " + d;
        }
        return d;
    }

    public static String screenInfo(Context c) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return dm.widthPixels + " x " + dm.heightPixels + "  (" + dm.densityDpi + " dpi)";
    }

    public static String kernel() {
        String k = System.getProperty("os.version");
        return k == null ? "-" : k;
    }

    // ------------------------------------------------------------ apps predeterminadas por categoría

    /** Paquete de la app predeterminada para una categoría de Android, o null. */
    public static String appForCategory(Context c, String category) {
        Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
        Intent q = i.getSelector() != null ? i.getSelector() : i;
        return firstPackage(c, q);
    }

    public static String dialerPackage(Context c) {
        try {
            String p = ((TelecomManager) c.getSystemService(Context.TELECOM_SERVICE)).getDefaultDialerPackage();
            if (p != null) return p;
        } catch (Exception ignored) {
        }
        return firstPackage(c, new Intent(Intent.ACTION_DIAL));
    }

    public static String smsPackage(Context c) {
        try {
            return Telephony.Sms.getDefaultSmsPackage(c);
        } catch (Exception e) {
            return null;
        }
    }

    public static String cameraPackage(Context c) {
        return firstPackage(c, new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    }

    public static String storePackage(Context c) {
        return c.getPackageManager().getLaunchIntentForPackage("com.android.vending") != null
                ? "com.android.vending" : null;
    }

    private static String firstPackage(Context c, Intent q) {
        try {
            PackageManager pm = c.getPackageManager();
            ResolveInfo def = pm.resolveActivity(q, 0);
            if (def != null && def.activityInfo != null && !"android".equals(def.activityInfo.packageName)
                    && !def.activityInfo.packageName.equals(c.getPackageName())) {
                return def.activityInfo.packageName;
            }
            List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : all) {
                String p = ri.activityInfo.packageName;
                if (!"android".equals(p) && !p.equals(c.getPackageName())) return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static CharSequence appLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (Exception e) {
            return pkg;
        }
    }

    public static ContentResolver resolver(Context c) {
        return c.getContentResolver();
    }

    // ------------------------------------------------------------ opciones genéricas de Settings.System

    public static boolean sysFlag(Context c, String key, boolean def) {
        try {
            return Settings.System.getInt(c.getContentResolver(), key, def ? 1 : 0) == 1;
        } catch (Exception e) {
            return def;
        }
    }

    public static boolean setSysFlag(Context c, String key, boolean v) {
        return putInt(c, key, v ? 1 : 0);
    }

    public static float fontScale(Context c) {
        try {
            return Settings.System.getFloat(c.getContentResolver(), Settings.System.FONT_SCALE, 1f);
        } catch (Exception e) {
            return 1f;
        }
    }

    public static boolean setFontScale(Context c, float v) {
        try {
            return Settings.System.putFloat(c.getContentResolver(), Settings.System.FONT_SCALE, v);
        } catch (Exception e) {
            return false;
        }
    }
}
