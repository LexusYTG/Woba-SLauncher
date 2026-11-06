package com.woba.slauncher.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class Prefs {
    private static final String PREF_NAME = "wp_launcher_prefs";
    private static final String KEY_TILES = "tiles_v2";
    private static final String KEY_ACCENT = "accent_index";
    private static final String KEY_DARK = "dark_theme";
    private static final String KEY_PERMS_ASKED = "permissions_asked";
    private static final String KEY_WP_LOCK = "wp_lock_screen";
    private static final String KEY_HIDDEN = "hidden_apps";

    public static final String F_TILE_TRANS = "f_tile_trans";
    public static final String F_TILE_LABELS = "f_tile_labels";
    public static final String F_LIVE_ANIM = "f_live_anim";
    public static final String F_TILT = "f_tilt";
    public static final String F_LOCK_STATUS = "f_lock_status";
    public static final String F_NAVBAR = "f_navbar";
    public static final String F_MORE_TILES = "f_more_tiles";

    /** Azul por defecto de Windows 10 Mobile (0078D7), índice 22 de Theme.ACCENTS. */
    public static final int DEFAULT_ACCENT = 22;

    private Prefs() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static ArrayList<Tile> defaultTiles() {
        ArrayList<Tile> l = new ArrayList<>();
        l.add(new Tile("sys:PHONE", Tile.Kind.PHONE, "Phone", Tile.Size.MEDIUM));
        l.add(new Tile("sys:MESSAGES", Tile.Kind.MESSAGES, "Messaging", Tile.Size.MEDIUM));
        l.add(new Tile("sys:CLOCK", Tile.Kind.CLOCK, "Clock", Tile.Size.WIDE));
        l.add(new Tile("sys:BATTERY", Tile.Kind.BATTERY, "Battery", Tile.Size.SMALL));
        l.add(new Tile("sys:LOCK", Tile.Kind.LOCK, "Lock", Tile.Size.SMALL));
        l.add(new Tile("sys:SETTINGS", Tile.Kind.SETTINGS, "Settings", Tile.Size.MEDIUM));
        l.add(new Tile("sys:CORTANA", Tile.Kind.CORTANA, "Cortana", Tile.Size.MEDIUM));
        return l;
    }

    public static void saveTiles(Context c, java.util.List<Tile> tiles) {
        try {
            JSONArray arr = new JSONArray();
            for (Tile t : tiles) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("kind", t.kind.name());
                o.put("title", t.title);
                o.put("pkg", t.pkg);
                o.put("size", t.size.name());
                o.put("widgetId", t.widgetId);
                arr.put(o);
            }
            prefs(c).edit().putString(KEY_TILES, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static ArrayList<Tile> loadTiles(Context c) {
        String json = prefs(c).getString(KEY_TILES, null);
        boolean cortanaDone = prefs(c).getBoolean("cortana_tile", false);
        prefs(c).edit().putBoolean("cortana_tile", true).apply();
        if (json == null) return defaultTiles();
        try {
            JSONArray arr = new JSONArray(json);
            ArrayList<Tile> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Tile(
                        o.getString("id"),
                        Tile.Kind.valueOf(o.getString("kind")),
                        o.getString("title"),
                        o.optString("pkg", ""),
                        Tile.Size.valueOf(o.getString("size")),
                        o.optInt("widgetId", -1)));
            }
            // Instalaciones anteriores: se añade el tile de Cortana una sola vez.
            if (!cortanaDone) out.add(new Tile("sys:CORTANA", Tile.Kind.CORTANA, "Cortana", Tile.Size.MEDIUM));
            return out;
        } catch (Exception e) {
            return defaultTiles();
        }
    }

    public static int loadAccent(Context c) { return prefs(c).getInt(KEY_ACCENT, DEFAULT_ACCENT); }
    public static void saveAccent(Context c, int i) { prefs(c).edit().putInt(KEY_ACCENT, i).apply(); }

    public static boolean loadDark(Context c) { return prefs(c).getBoolean(KEY_DARK, true); }
    public static void saveDark(Context c, boolean d) { prefs(c).edit().putBoolean(KEY_DARK, d).apply(); }

    public static boolean permissionAsked(Context c) { return prefs(c).getBoolean(KEY_PERMS_ASKED, false); }
    public static void setPermissionAsked(Context c) { prefs(c).edit().putBoolean(KEY_PERMS_ASKED, true).apply(); }

    public static boolean loadWpLock(Context c) { return prefs(c).getBoolean(KEY_WP_LOCK, true); }
    public static void saveWpLock(Context c, boolean on) { prefs(c).edit().putBoolean(KEY_WP_LOCK, on).apply(); }

    public static String getStr(Context c, String key, String def) { return prefs(c).getString(key, def); }
    public static void setStr(Context c, String key, String v) { prefs(c).edit().putString(key, v).apply(); }

    public static boolean flag(Context c, String key, boolean def) { return prefs(c).getBoolean(key, def); }
    public static void setFlag(Context c, String key, boolean v) { prefs(c).edit().putBoolean(key, v).apply(); }

    public static Set<String> hidden(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY_HIDDEN, new HashSet<String>()));
    }

    public static void setHidden(Context c, Set<String> s) {
        prefs(c).edit().putStringSet(KEY_HIDDEN, new HashSet<>(s)).apply();
    }

    /** Acciones rápidas del centro de actividades (ids de Toggles), 4 elementos. */
    public static int[] quick(Context c) {
        int[] d = {0, 1, 2, 3};
        String v = prefs(c).getString("quick_actions", "");
        String[] a = v.split(",");
        if (a.length != 4) return d;
        try {
            int[] o = new int[4];
            for (int k = 0; k < 4; k++) {
                o[k] = Integer.parseInt(a[k].trim());
                if (o[k] < 0 || o[k] > 7) return d;
            }
            return o;
        } catch (Exception e) {
            return d;
        }
    }

    public static void setQuick(Context c, int[] q) {
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < q.length; k++) b.append(k > 0 ? "," : "").append(q[k]);
        prefs(c).edit().putString("quick_actions", b.toString()).apply();
    }
}
