package com.woba.slauncher.data;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;

/**
 * Acciones rápidas del Action Center. toggle() intenta cambiar el ajuste de verdad
 * (API pública, o WRITE_SECURE_SETTINGS si se concedió por adb) y si Android no lo
 * permite abre el panel oficial.
 *
 * Para activar el modo "real":
 *   adb shell pm grant com.woba.slauncher android.permission.WRITE_SECURE_SETTINGS
 */
public final class Toggles {
    private Toggles() {}

    public static final int WIFI = 0, BLUETOOTH = 1, AIRPLANE = 2, ROTATION = 3,
            QUIET = 4, LOCATION = 5, SAVER = 6, SHARING = 7;

    public static final String[] LABELS = {
            "WI-FI", "BLUETOOTH", "AIRPLANE MODE", "ROTATION LOCK",
            "QUIET HOURS", "LOCATION", "BATTERY SAVER", "INTERNET SHARING"};

    public static boolean secure(Context c) {
        return Sys.has(c, "android.permission.WRITE_SECURE_SETTINGS");
    }

    public static boolean isOn(Context c, int id) {
        switch (id) {
            case WIFI: return SysSettings.wifiOn(c);
            case BLUETOOTH: return SysSettings.bluetoothOn(c);
            case AIRPLANE: return SysSettings.airplaneOn(c);
            case ROTATION: return !SysSettings.autoRotate(c);
            case QUIET: return SysSettings.quietHours(c);
            case LOCATION: return SysSettings.locationOn(c);
            case SAVER: return SysSettings.batterySaverOn(c);
            default: return false;
        }
    }

    /** Nombre de la red Wi-Fi si está conectada (WP muestra el SSID en el mosaico). */
    public static String wifiName(Context c) {
        try {
            WifiManager w = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String s = w.getConnectionInfo().getSSID();
            if (s == null || s.equals("<unknown ssid>")) return null;
            return s.replace("\"", "");
        } catch (Exception e) {
            return null;
        }
    }

    /** Cambia el ajuste. Devuelve un Intent de panel si hay que abrirlo, o null si ya se aplicó. */
    @SuppressWarnings("deprecation")
    public static Intent toggle(Context c, int id) {
        boolean on = isOn(c, id);
        switch (id) {
            case WIFI:
                try {
                    WifiManager w = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (w.setWifiEnabled(!on)) return null;
                } catch (Exception ignored) {
                }
                return SysSettings.wifiPanel();
            case BLUETOOTH:
                try {
                    BluetoothManager bm = c.getSystemService(BluetoothManager.class);
                    BluetoothAdapter a = bm.getAdapter();
                    if (on ? a.disable() : a.enable()) return null;
                } catch (Exception ignored) {
                }
                return new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            case AIRPLANE:
                return new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            case ROTATION:
                if (SysSettings.setAutoRotate(c, on)) return null;
                return SysSettings.writeSettingsIntent(c);
            case QUIET:
                if (SysSettings.hasPolicyAccess(c)) {
                    SysSettings.setQuietHours(c, !on);
                    return null;
                }
                return SysSettings.policyIntent();
            case LOCATION:
                if (secure(c)) {
                    try {
                        Settings.Secure.putInt(c.getContentResolver(), "location_mode", on ? 0 : 3);
                        return null;
                    } catch (Exception ignored) {
                    }
                }
                return new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            case SAVER:
                if (secure(c)) {
                    try {
                        Settings.Global.putInt(c.getContentResolver(), "low_power", on ? 0 : 1);
                        return null;
                    } catch (Exception ignored) {
                    }
                }
                return new Intent("android.settings.BATTERY_SAVER_SETTINGS");
            case SHARING:
                return new Intent("android.settings.TETHER_SETTINGS");
            default:
                return null;
        }
    }

    public static Intent allSettings() {
        return new Intent(Settings.ACTION_SETTINGS);
    }

    public static boolean listenerEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    /** Con WRITE_SECURE_SETTINGS el acceso a notificaciones se activa sin pasar por Ajustes. */
    public static boolean enableListenerDirect(Context c) {
        if (!secure(c)) return false;
        try {
            String cn = new android.content.ComponentName(c, "com.woba.slauncher.WpNotificationService").flattenToString();
            String cur = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            if (cur != null && cur.contains(cn)) return true;
            Settings.Secure.putString(c.getContentResolver(), "enabled_notification_listeners",
                    (cur == null || cur.isEmpty()) ? cn : cur + ":" + cn);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Intent listenerSettings() {
        if (Build.VERSION.SDK_INT >= 22) return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        return new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
    }
}
