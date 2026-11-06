package com.woba.slauncher.ime;

import android.content.Context;

import com.woba.slauncher.data.Prefs;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Diccionario del teclado: lista de palabras por frecuencia (assets/dict_es.txt, assets/dict_en.txt, una palabra por
 * línea, la más frecuente primero; opcionalmente "palabra&lt;TAB&gt;frecuencia"), palabras aprendidas del usuario y
 * corrector de distancia 1 (borrar, intercambiar, sustituir, insertar) más restitución de tildes.
 * Si el usuario reemplaza los assets por listas grandes (decenas de miles de palabras) el corrector local pasa
 * solo a ser más agresivo y más preciso; con las listas incluidas es conservador (ver {@link #plausible}).
 */
public final class Dict {
    private static final String ES = "abcdefghijklmnopqrstuvwxyzáéíóúüñ";
    private static final String EN = "abcdefghijklmnopqrstuvwxyz'";
    private static final int MAX_LEARNED = 3000;

    private final Context ctx;
    private volatile HashMap<String, Integer> score = new HashMap<>();
    private volatile HashMap<String, String> plain = new HashMap<>();
    private volatile ArrayList<String> words = new ArrayList<>();
    private volatile boolean big;
    private volatile boolean ready;
    private volatile String alphabet = ES + "'";
    private final LinkedHashSet<String> learned = new LinkedHashSet<>();

    public Dict(Context c) {
        ctx = c.getApplicationContext();
        for (String w : Prefs.getStr(ctx, KbPrefs.LEARNED, "").split("\n")) if (!w.isEmpty()) learned.add(w);
    }

    public boolean ready() { return ready; }

    /** Carga (en un hilo aparte) las listas según el ajuste de idiomas. */
    public void load() {
        final int lang = KbPrefs.level(ctx, KbPrefs.LANG, 0);
        new Thread(new Runnable() {
            @Override public void run() {
                HashMap<String, Integer> sc = new HashMap<>();
                HashMap<String, String> pl = new HashMap<>();
                ArrayList<String> ws = new ArrayList<>();
                if (lang == 0 || lang == 1) read("dict_es.txt", sc, ws);
                if (lang == 0 || lang == 2) read("dict_en.txt", sc, ws);
                for (String w : ws) {
                    String s = strip(w);
                    if (s.equals(w)) continue;
                    String old = pl.get(s);
                    if (old == null || sc.get(w) > sc.get(old)) pl.put(s, w);
                }
                score = sc;
                plain = pl;
                words = ws;
                big = sc.size() > 20000;
                alphabet = lang == 2 ? EN : lang == 1 ? ES : ES + "'";
                ready = true;
            }
        }, "kb-dict").start();
    }

    private void read(String asset, HashMap<String, Integer> sc, ArrayList<String> ws) {
        try {
            InputStream in = ctx.getAssets().open(asset);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            int rank = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String w = line, num = null;
                int t = line.indexOf('\t');
                if (t > 0) {
                    w = line.substring(0, t);
                    num = line.substring(t + 1).trim();
                }
                w = w.toLowerCase(Locale.ROOT);
                int s;
                if (num != null) {
                    try { s = (int) Math.min(2000000000L, Long.parseLong(num)); } catch (NumberFormatException e) { s = 1; }
                } else {
                    s = (int) (1000000f / (rank + 20));
                }
                rank++;
                Integer old = sc.get(w);
                if (old == null) {
                    sc.put(w, s);
                    ws.add(w);
                } else if (s > old) {
                    sc.put(w, s);
                }
            }
            br.close();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ palabras aprendidas

    public boolean isLearned(String lw) { return learned.contains(lw); }

    public void learn(String word) {
        String lw = word.toLowerCase(Locale.getDefault());
        if (lw.length() < 2 || !isWord(lw)) return;
        learned.remove(lw);
        learned.add(lw);
        while (learned.size() > MAX_LEARNED) {
            java.util.Iterator<String> it = learned.iterator();
            it.next();
            it.remove();
        }
        StringBuilder sb = new StringBuilder();
        for (String w : learned) sb.append(w).append('\n');
        Prefs.setStr(ctx, KbPrefs.LEARNED, sb.toString());
    }

    public static int learnedCount(Context c) {
        int n = 0;
        for (String w : Prefs.getStr(c, KbPrefs.LEARNED, "").split("\n")) if (!w.isEmpty()) n++;
        return n;
    }

    public static void forgetAll(Context c) { Prefs.setStr(c, KbPrefs.LEARNED, ""); }

    // ------------------------------------------------------------------ consultas

    public boolean known(String lw) { return score.containsKey(lw) || learned.contains(lw); }

    private static boolean isWord(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!Character.isLetter(ch) && ch != '\'') return false;
        }
        return !s.isEmpty();
    }

    private static String strip(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case 'á': ch = 'a'; break;
                case 'é': ch = 'e'; break;
                case 'í': ch = 'i'; break;
                case 'ó': ch = 'o'; break;
                case 'ú': case 'ü': ch = 'u'; break;
                default: break;
            }
            b.append(ch);
        }
        return b.toString();
    }

    /** Completaciones de un prefijo (las más frecuentes primero). */
    public String[] complete(String lw, int n) {
        String[] out = new String[n];
        int[] best = new int[n];
        if (lw.length() < 1) return out;
        ArrayList<String> ws = words;
        HashMap<String, Integer> sc = score;
        for (int i = 0; i < ws.size(); i++) {
            String w = ws.get(i);
            if (w.length() <= lw.length() || !w.startsWith(lw)) continue;
            int s = sc.get(w);
            for (int k = 0; k < n; k++) {
                if (out[k] == null || s > best[k]) {
                    for (int m = n - 1; m > k; m--) {
                        out[m] = out[m - 1];
                        best[m] = best[m - 1];
                    }
                    out[k] = w;
                    best[k] = s;
                    break;
                }
            }
        }
        return out;
    }

    /**
     * ¿Parece una palabra válida que simplemente no está en la lista (plural, conjugación, diminutivo...)?
     * Con listas pequeñas hay que ser prudente para no "corregir" palabras buenas.
     */
    private boolean plausible(String lw) {
        if (big) return false;
        for (int i = lw.length() - 1; i >= 3; i--) {
            if (lw.length() - i > 6) break;
            if (score.containsKey(lw.substring(0, i)) || learned.contains(lw.substring(0, i))) return true;
        }
        return false;
    }

    /**
     * Corrección automática para una palabra ya terminada, o null si no hay que tocarla.
     * @param w palabra tal como se escribió
     * @param sentenceStart true si está al principio de una oración (una mayúscula en medio de la oración se
     *                      toma por un nombre propio y no se corrige)
     */
    public String fix(String w, boolean sentenceStart) {
        if (!ready || w.length() < 3 || !isWord(w)) return null;
        String lw = w.toLowerCase(Locale.getDefault());
        if (known(lw)) return null;
        boolean allCaps = w.equals(w.toUpperCase(Locale.getDefault())) && !w.equals(lw);
        if (allCaps) return null;
        if (Character.isUpperCase(w.charAt(0)) && !sentenceStart) return null;
        String acc = plain.get(lw);
        if (acc != null) return matchCase(w, acc);
        if (plausible(lw)) return null;
        String best = bestEdit(lw);
        return best == null ? null : matchCase(w, best);
    }

    private String bestEdit(String lw) {
        String alpha = alphabet, best = null;
        long bs = -1;
        int n = lw.length();
        HashMap<String, Integer> sc = score;
        // borrar
        for (int i = 0; i < n; i++) {
            String c = lw.substring(0, i) + lw.substring(i + 1);
            long s = rate(c, lw, sc);
            if (s > bs) { bs = s; best = c; }
        }
        // intercambiar adyacentes
        for (int i = 0; i + 1 < n; i++) {
            if (lw.charAt(i) == lw.charAt(i + 1)) continue;
            String c = lw.substring(0, i) + lw.charAt(i + 1) + lw.charAt(i) + lw.substring(i + 2);
            long s = rate(c, lw, sc);
            if (s > bs) { bs = s; best = c; }
        }
        // sustituir e insertar
        for (int i = 0; i <= n; i++) {
            for (int k = 0; k < alpha.length(); k++) {
                char a = alpha.charAt(k);
                if (i < n && a != lw.charAt(i)) {
                    String c = lw.substring(0, i) + a + lw.substring(i + 1);
                    long s = rate(c, lw, sc);
                    if (s > bs) { bs = s; best = c; }
                }
                String c2 = lw.substring(0, i) + a + lw.substring(i);
                long s2 = rate(c2, lw, sc);
                if (s2 > bs) { bs = s2; best = c2; }
            }
        }
        return best;
    }

    /** Puntuación de un candidato (-1 si no existe): frecuencia, con bonus si conserva la primera letra. */
    private long rate(String cand, String orig, HashMap<String, Integer> sc) {
        Integer v = sc.get(cand);
        if (v == null) return -1;
        long s = v;
        if (!cand.isEmpty() && cand.charAt(0) == orig.charAt(0)) s *= 3;
        return s;
    }

    /** Puntuación local de una palabra (0 si no está en la lista); sirve para ordenar sugerencias del sistema. */
    public int scoreOf(String w) {
        Integer v = score.get(w.toLowerCase(Locale.getDefault()));
        return v == null ? 0 : v;
    }

    /** Distancia de edición (con intercambio de letras contiguas), con tope: devuelve max+1 si lo supera. */
    public static int dist(String a, String b, int max) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > max) return max + 1;
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) v = Math.min(v, d[i - 2][j - 2] + 1);
                d[i][j] = v;
            }
        }
        return Math.min(d[n][m], max + 1);
    }

    public static String matchCase(String orig, String fix) {
        if (orig.isEmpty() || fix.isEmpty()) return fix;
        if (orig.length() > 1 && orig.equals(orig.toUpperCase(Locale.getDefault()))) return fix.toUpperCase(Locale.getDefault());
        if (Character.isUpperCase(orig.charAt(0))) return fix.substring(0, 1).toUpperCase(Locale.getDefault()) + fix.substring(1);
        return fix;
    }
}
