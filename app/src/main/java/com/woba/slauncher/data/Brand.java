package com.woba.slauncher.data;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nombres visibles de marcas de terceros. El código y los datos guardan el texto "original"
 * (por ejemplo en las fichas de la tienda simulada); al mostrarlo se sustituye por lo que el
 * usuario haya elegido en Ajustes > app names. Solo cambia lo que se VE: los nombres de paquete
 * (com.xxx.yyy), las URL y las apps reales instaladas no se tocan.
 */
public final class Brand {
    private Brand() {}

    /** {palabra en el código, nombre por defecto}. Solo "Microsoft" viene sustituido de fábrica. */
    public static final String[][] TERMS = {
            {"Microsoft", "UOVA"},
            {"Windows Phone", "Windows Phone"},
            {"Cortana", "Cortana"},
            {"Xbox", "Xbox"},
            {"OneDrive", "OneDrive"},
            {"OneNote", "OneNote"},
            {"Outlook", "Outlook"},
            {"Skype", "Skype"},
            {"Bing", "Bing"},
            {"Edge", "Edge"},
            {"Word", "Word"},
            {"Excel", "Excel"},
            {"PowerPoint", "PowerPoint"},
            {"Office", "Office"},
            {"Lumia", "Lumia"},
            {"Nokia", "Nokia"},
    };

    private static final String PFX = "brand_";

    private static HashMap<String, String> active; // término en minúsculas -> nombre elegido (solo los que cambian)
    private static Pattern pattern;

    private static synchronized void ensure(Context c) {
        if (active != null) return;
        Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        HashMap<String, String> m = new HashMap<>();
        StringBuilder alt = new StringBuilder();
        for (String[] t : TERMS) {
            String v = Prefs.getStr(app, PFX + t[0], t[1]);
            if (v == null || v.trim().isEmpty()) v = t[1];
            v = v.trim();
            if (v.equals(t[0])) continue; // sin cambios: no hay nada que sustituir
            m.put(t[0].toLowerCase(Locale.ROOT), v);
            if (alt.length() > 0) alt.append('|');
            alt.append(Pattern.quote(t[0]));
        }
        active = m;
        // No toca nombres de paquete, correos ni URL (com.microsoft.x, user@microsoft.com, microsoft.com).
        pattern = m.isEmpty() ? null
                : Pattern.compile("(?<![\\w.@/])(?:" + alt + ")(?![\\w@/]|\\.[A-Za-z])", Pattern.CASE_INSENSITIVE);
    }

    private static String adapt(String found, String rep) {
        if (found.equals(found.toLowerCase(Locale.ROOT))) return rep.toLowerCase(Locale.ROOT);
        if (found.length() > 1 && found.equals(found.toUpperCase(Locale.ROOT))) return rep.toUpperCase(Locale.ROOT);
        return rep;
    }

    /** Aplica los nombres elegidos a un texto que se va a mostrar. */
    public static CharSequence t(Context c, CharSequence s) {
        if (s == null || s.length() == 0) return s;
        ensure(c);
        Pattern p = pattern;
        if (p == null) return s;
        String in = s.toString();
        Matcher m = p.matcher(in);
        StringBuffer sb = null;
        while (m.find()) {
            String rep = active.get(m.group().toLowerCase(Locale.ROOT));
            if (rep == null) continue;
            if (sb == null) sb = new StringBuffer();
            m.appendReplacement(sb, Matcher.quoteReplacement(adapt(m.group(), rep)));
        }
        if (sb == null) return s;
        m.appendTail(sb);
        return sb.toString();
    }

    public static String t(Context c, String s) {
        return s == null ? null : t(c, (CharSequence) s).toString();
    }

    /** Nombre que se muestra para un término (p. ej. "Microsoft" -> "UOVA"). */
    public static String get(Context c, String term) {
        String def = term;
        for (String[] t : TERMS) if (t[0].equals(term)) def = t[1];
        String v = Prefs.getStr(c, PFX + term, def);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    public static void set(Context c, String term, String value) {
        Prefs.setStr(c, PFX + term, value == null ? "" : value.trim());
        reload();
    }

    /** Devuelve todos los nombres a los valores de fábrica. */
    public static void resetAll(Context c) {
        for (String[] t : TERMS) Prefs.setStr(c, PFX + t[0], t[1]);
        reload();
    }

    private static synchronized void reload() { active = null; pattern = null; }
}
