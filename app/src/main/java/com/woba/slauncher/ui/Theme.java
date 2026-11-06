package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;

/** Colores de acento WP8, tema claro/oscuro y fuentes (assets/fonts). Estado global, solo hilo UI. */
public final class Theme {
    private Theme() {}

    public static final int[] ACCENTS = {
            0xFFA4C400, 0xFF60A917, 0xFF008A00, 0xFF00ABA9,
            0xFF1BA1E2, 0xFF0050EF, 0xFF6A00FF, 0xFFAA00FF,
            0xFFF472D0, 0xFFD80073, 0xFFA20025, 0xFFE51400,
            0xFFFA6800, 0xFFF0A30A, 0xFFE3C800, 0xFF825A2C,
            0xFF6D8764, 0xFF647687, 0xFF76608A, 0xFF87794E,
            // Paleta de acentos de Windows 10 Mobile (índices 20..67), 6 columnas x 8 filas
            0xFFFFB900, 0xFFE74856, 0xFF0078D7, 0xFF0099BC, 0xFF7A7574, 0xFF767676,
            0xFFFF8C00, 0xFFE81123, 0xFF0063B1, 0xFF2D7D9A, 0xFF5D5A58, 0xFF4C4A48,
            0xFFF7630C, 0xFFEA005E, 0xFF8E8CD8, 0xFF00B7C3, 0xFF68768A, 0xFF69797E,
            0xFFCA5010, 0xFFC30052, 0xFF6B69D6, 0xFF038387, 0xFF515C6B, 0xFF4A5459,
            0xFFDA3B01, 0xFFE3008C, 0xFF8764B8, 0xFF00B294, 0xFF567C73, 0xFF647C64,
            0xFFEF6950, 0xFFBF0077, 0xFF744DA9, 0xFF018574, 0xFF486860, 0xFF525E54,
            0xFFD13438, 0xFFC239B3, 0xFFB146C2, 0xFF00CC6A, 0xFF498205, 0xFF847545,
            0xFFFF4343, 0xFF9A0089, 0xFF881798, 0xFF10893E, 0xFF107C10, 0xFF7E735F
    };

    /** Primer índice de la paleta W10 dentro de ACCENTS. */
    public static final int W10_FIRST = 20;

    public static int bg = 0xFF000000;
    public static int fg = 0xFFFFFFFF;
    public static int subtle = 0xFF9E9E9E;
    public static int accent = ACCENTS[22];

    public static Typeface light = Typeface.SANS_SERIF;
    public static Typeface regular = Typeface.SANS_SERIF;
    public static Typeface semibold = Typeface.DEFAULT_BOLD;
    public static Typeface bold = Typeface.DEFAULT_BOLD;

    // Preferencias de apariencia (las carga LauncherRoot desde Prefs).
    public static float tileAlpha = 1f;
    public static boolean tileLabels = true, liveAnim = true, tilt = true, lockStatus = true;

    public static float density = 1f;
    public static float scaledDensity = 1f;

    public static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        scaledDensity = density * c.getResources().getConfiguration().fontScale;
        light = load(c, "wp_light", Typeface.create("sans-serif-light", Typeface.NORMAL));
        regular = load(c, "wp_regular", Typeface.SANS_SERIF);
        semibold = load(c, "wp_semibold", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bold = load(c, "wp_bold", Typeface.DEFAULT_BOLD);
    }

    private static Typeface load(Context c, String name, Typeface fallback) {
        for (String ext : new String[]{"ttf", "otf"}) {
            try {
                return Typeface.createFromAsset(c.getAssets(), "fonts/" + name + "." + ext);
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    public static void apply(int accentIndex, boolean dark) {
        accent = ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, accentIndex))];
        if (dark) {
            bg = 0xFF000000;
            fg = 0xFFFFFFFF;
            subtle = 0xFF9E9E9E;
        } else {
            bg = 0xFFFFFFFF;
            fg = 0xFF000000;
            subtle = 0xFF6E6E6E;
        }
    }

    public static boolean isDark() { return (bg & 0x00FFFFFF) == 0; }

    public static int dp(float v) { return Math.round(v * density); }

    public static float dpf(float v) { return v * density; }

    public static float sp(float v) { return v * scaledDensity; }

    public static int alpha(int color, float a) {
        int al = Math.round(((color >>> 24) & 0xFF) * a);
        return (al << 24) | (color & 0x00FFFFFF);
    }

    /** Baseline para centrar verticalmente el texto de p alrededor de cy. */
    public static float centerBaseline(Paint p, float cy) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return cy - (fm.ascent + fm.descent) / 2f;
    }

    public static TextPaint textPaint(Typeface tf, float sizeSp, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(sp(sizeSp));
        p.setColor(color);
        return p;
    }
}
