package com.woba.slauncher.ime;

import android.content.Context;

import com.woba.slauncher.data.Prefs;

/** Ajustes del teclado. Viven en las mismas preferencias que el launcher (Ajustes > Extras > Keyboard). */
public final class KbPrefs {
    private KbPrefs() { }

    public static final String HEIGHT = "kb_height", KEYSIZE = "kb_keysize", HOLD = "kb_hold", ONEHAND = "kb_onehand",
            STYLE = "kb_style", LANG = "kb_lang", SOUND = "kb_sound", VIBRATE = "kb_vibrate", PREVIEW = "kb_preview",
            AUTOCORRECT = "kb_autocorrect", SUGGEST = "kb_suggest", AUTOCAP = "kb_autocap", DBLSPACE = "kb_dblspace",
            CURSOR = "kb_cursor", MIC = "kb_mic", LEARNED = "kb_learned";

    /** Niveles 0..4 de los deslizadores. */
    public static final float[] HEIGHT_SCALE = {0.80f, 0.90f, 1.00f, 1.12f, 1.25f};
    public static final float[] KEY_SCALE = {0.80f, 0.90f, 1.00f, 1.12f, 1.25f};
    public static final int[] HOLD_MS = {200, 260, 330, 420, 520};

    public static final String[] HEIGHT_NAMES = {"Very low", "Low", "Normal", "Tall", "Very tall"};
    public static final String[] KEY_NAMES = {"Very small", "Small", "Normal", "Large", "Very large"};
    public static final String[] HOLD_NAMES = {"Very short", "Short", "Normal", "Long", "Very long"};
    public static final String[] ONEHAND_NAMES = {"Off", "Left hand", "Right hand"};
    public static final String[] STYLE_NAMES = {"Classic (grey)", "Accent tint"};
    public static final String[] LANG_NAMES = {"Spanish and English", "Spanish", "English"};

    public static int level(Context c, String key, int def) {
        try {
            return Integer.parseInt(Prefs.getStr(c, key, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    public static void setLevel(Context c, String key, int v) { Prefs.setStr(c, key, String.valueOf(v)); }

    public static boolean on(Context c, String key) { return Prefs.flag(c, key, defaultOn(key)); }

    public static void set(Context c, String key, boolean v) { Prefs.setFlag(c, key, v); }

    private static boolean defaultOn(String key) { return true; }

    public static float heightScale(Context c) { return HEIGHT_SCALE[Math.max(0, Math.min(4, level(c, HEIGHT, 2)))]; }

    public static float keyScale(Context c) { return KEY_SCALE[Math.max(0, Math.min(4, level(c, KEYSIZE, 2)))]; }

    public static int holdMs(Context c) { return HOLD_MS[Math.max(0, Math.min(4, level(c, HOLD, 2)))]; }

    /** Restablece todos los ajustes del teclado (no toca las palabras aprendidas). */
    public static void reset(Context c) {
        for (String k : new String[]{HEIGHT, KEYSIZE, HOLD, ONEHAND, STYLE, LANG}) Prefs.setStr(c, k, k.equals(LANG) ? "0" : k.equals(ONEHAND) || k.equals(STYLE) ? "0" : "2");
        for (String k : new String[]{SOUND, VIBRATE, PREVIEW, AUTOCORRECT, SUGGEST, AUTOCAP, DBLSPACE, CURSOR, MIC}) Prefs.setFlag(c, k, true);
    }
}
