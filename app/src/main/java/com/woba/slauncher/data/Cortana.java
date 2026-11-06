package com.woba.slauncher.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cerebro de Cortana: primero comandos locales (abrir apps, alarmas, temporizadores,
 * llamadas, búsquedas, hora, batería) y, si no es un comando, pregunta a Gemini con la API key del usuario.
 */
public final class Cortana {
    private Cortana() {}

    public static final String KEY_API = "cortana_key", KEY_MODEL = "cortana_model", KEY_SPEAK = "cortana_speak";
    public static final String DEFAULT_MODEL = "gemini-3.5-flash";

    private static String system(Context c) {
        return SYSTEM.replace("Cortana", Brand.get(c, "Cortana"));
    }

    private static final String SYSTEM =
            "You are Cortana, a friendly voice assistant inside a Windows Phone 8.1 style launcher on Android. "
                    + "Answer in the same language the user writes in. Be brief: one to three short sentences, "
                    + "plain text only, no markdown, no lists, no emojis.";

    public interface Callback {
        /** Siempre en el hilo UI. action puede ser null; se ejecuta tras mostrar la respuesta. */
        void onReply(String text, Runnable action);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ArrayList<String[]> HISTORY = new ArrayList<>(); // {rol, texto}

    public static void clearHistory() { HISTORY.clear(); }

    // ------------------------------------------------------------ entrada

    public static void ask(final Context ctx, String query, final List<AppEntry> apps, final Callback cb) {
        final Context c = ctx.getApplicationContext();
        final String q = query.trim();
        if (q.isEmpty()) return;
        Local l = local(c, q, apps);
        if (l != null) {
            cb.onReply(l.text, l.action);
            return;
        }
        final String key = Prefs.getStr(c, KEY_API, "").trim();
        if (key.isEmpty()) {
            cb.onReply("I can open apps, set alarms and timers, call and search. To chat with me, add a Gemini API key in settings > cortana.", null);
            return;
        }
        final String model = Prefs.getStr(c, KEY_MODEL, DEFAULT_MODEL).trim();
        HISTORY.add(new String[]{"user", q});
        while (HISTORY.size() > 12) HISTORY.remove(0);
        final ArrayList<String[]> snapshot = new ArrayList<>(HISTORY);
        IO.execute(new Runnable() {
            @Override
            public void run() {
                String r;
                try {
                    r = gemini(c, key, model.isEmpty() ? DEFAULT_MODEL : model, snapshot);
                } catch (Exception e) {
                    r = "I couldn't reach the network. " + (e.getMessage() == null ? "" : e.getMessage());
                }
                final String reply = r;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        HISTORY.add(new String[]{"model", reply});
                        cb.onReply(reply, null);
                    }
                });
            }
        });
    }

    private static String[] contactLookup(Context c, String name) {
        if (c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
        android.database.Cursor cur = null;
        try {
            cur = c.getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER},
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + name + "%"},
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cur != null && cur.moveToFirst()) return new String[]{cur.getString(0), cur.getString(1)};
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }

    // ------------------------------------------------------------ Gemini

    private static String gemini(Context c, String key, String model, List<String[]> hist) throws Exception {
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", system(c)))));
        JSONArray contents = new JSONArray();
        for (String[] h : hist) {
            contents.put(new JSONObject().put("role", h[0])
                    .put("parts", new JSONArray().put(new JSONObject().put("text", h[1]))));
        }
        body.put("contents", contents);
        body.put("generationConfig", new JSONObject().put("maxOutputTokens", 400));

        URL u = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + Uri.encode(model) + ":generateContent");
        HttpURLConnection h = (HttpURLConnection) u.openConnection();
        try {
            h.setRequestMethod("POST");
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setRequestProperty("Content-Type", "application/json");
            h.setRequestProperty("x-goog-api-key", key);
            h.setDoOutput(true);
            OutputStream os = h.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            int code = h.getResponseCode();
            InputStream is = code < 400 ? h.getInputStream() : h.getErrorStream();
            String raw = read(is);
            if (code >= 400) {
                String msg = raw;
                try {
                    msg = new JSONObject(raw).getJSONObject("error").optString("message", raw);
                } catch (Exception ignored) {
                }
                if (msg.length() > 160) msg = msg.substring(0, 160);
                return "Gemini said no (" + code + "): " + msg;
            }
            JSONObject root = new JSONObject(raw);
            JSONArray cands = root.optJSONArray("candidates");
            if (cands == null || cands.length() == 0) return "I don't have an answer for that.";
            JSONObject content = cands.getJSONObject(0).optJSONObject("content");
            if (content == null) return "I don't have an answer for that.";
            JSONArray parts = content.optJSONArray("parts");
            StringBuilder sb = new StringBuilder();
            if (parts != null) for (int i = 0; i < parts.length(); i++) sb.append(parts.getJSONObject(i).optString("text", ""));
            String t = sb.toString().replaceAll("[*_`#]", "").trim();
            return t.isEmpty() ? "I don't have an answer for that." : t;
        } finally {
            h.disconnect();
        }
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return bo.toString("UTF-8");
    }

    // ------------------------------------------------------------ comandos locales

    private static final class Local {
        final String text;
        final Runnable action;
        Local(String t, Runnable a) { text = t; action = a; }
    }

    private static final Pattern OPEN = Pattern.compile("^(?:open|launch|start|abre|abrir|ejecuta|inicia|iniciar)\\s+(?:the\\s+|la\\s+|el\\s+)?(.+)$");
    private static final Pattern CALL = Pattern.compile("^(?:call|dial|llama(?:\\s+a)?|llamar(?:\\s+a)?|marca)\\s+(.+)$");
    private static final Pattern SEARCH = Pattern.compile("^(?:search(?:\\s+for)?|google|busca(?:r)?(?:\\s+en\\s+internet)?)\\s+(.+)$");
    private static final Pattern ALARM = Pattern.compile("(?:alarm|alarma|wake me|despi[eé]rtame).*?(\\d{1,2})(?:[:.h](\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?");
    private static final Pattern TIMER = Pattern.compile("(?:timer|temporizador|cuenta atr[aá]s).*?(\\d+)\\s*(seconds?|secs?|segundos?|seg|minutes?|mins?|minutos?|hours?|horas?|h)");

    private static Local local(final Context c, String q, List<AppEntry> apps) {
        final String s = q.toLowerCase(Locale.ROOT).replaceAll("[¿?¡!.,]", "").trim();

        if (s.matches("^(what time is it|what is the time|whats the time|qu[eé] hora es|la hora|dime la hora)$"))
            return new Local("It's " + Sys.timeText(c, new Date()) + ".", null);
        if (s.matches("^(what day is it|what is the date|whats the date|qu[eé] d[ií]a es|qu[eé] fecha es)$"))
            return new Local(Sys.dateText(new Date()) + ".", null);
        if (s.contains("battery") || s.contains("bater")) {
            if (s.contains("how") || s.contains("level") || s.contains("cuánta") || s.contains("cuanta") || s.contains("nivel") || s.contains("%")) {
                Live l = new Live();
                Sys.readBattery(c.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)), l);
                return new Local("Battery is at " + l.battery + "%" + (l.charging ? " and charging." : "."), null);
            }
        }

        Matcher m = TIMER.matcher(s);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String u = m.group(2);
            final int secs = u.startsWith("h") ? n * 3600 : (u.startsWith("m") ? n * 60 : n);
            return new Local("Timer set for " + n + " " + u + ".", new Runnable() {
                @Override public void run() {
                    Actions.start(c, new Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true));
                }
            });
        }
        m = ALARM.matcher(s);
        if (m.find()) {
            int hh = Integer.parseInt(m.group(1));
            final int mm = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            String ap = m.group(3);
            if (ap != null) {
                boolean pm = ap.startsWith("p");
                if (pm && hh < 12) hh += 12;
                if (!pm && hh == 12) hh = 0;
            }
            if (hh > 23 || mm > 59) return new Local("That doesn't look like a valid time.", null);
            final int fh = hh;
            return new Local(String.format(Locale.US, "Alarm set for %02d:%02d.", fh, mm), new Runnable() {
                @Override public void run() {
                    Actions.start(c, new Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, fh)
                            .putExtra(AlarmClock.EXTRA_MINUTES, mm)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true));
                }
            });
        }
        m = CALL.matcher(s);
        if (m.find()) {
            final String who = m.group(1).trim();
            final String digits = who.replaceAll("[^0-9+]", "");
            if (digits.length() >= 3 && digits.length() >= who.replace(" ", "").length() - 2) {
                return new Local("Calling " + who + ".", new Runnable() {
                    @Override public void run() { Actions.start(c, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits))); }
                });
            }
            final String[] hit = contactLookup(c, who);
            if (hit != null) {
                final String num = hit[1].replaceAll("[^0-9+]", "");
                return new Local("Calling " + hit[0] + ".", new Runnable() {
                    @Override public void run() { Actions.start(c, new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num))); }
                });
            }
            if (c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return new Local("To call by name I need access to your contacts. Opening the app permissions.", new Runnable() {
                    @Override public void run() {
                        Actions.start(c, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName())));
                    }
                });
            }
            return new Local("I couldn't find " + who + " in your contacts. Opening the dialer.", new Runnable() {
                @Override public void run() { Actions.openDialer(c); }
            });
        }
        m = SEARCH.matcher(s);
        if (m.find()) {
            final String term = m.group(1).trim();
            return new Local("Searching for " + term + ".", new Runnable() {
                @Override public void run() {
                    Actions.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(term))));
                }
            });
        }
        m = OPEN.matcher(s);
        if (m.find()) {
            String name = m.group(1).trim();
            if (name.matches("wi-?fi")) return panel("Opening wi-fi.", c, SysSettings.wifiPanel());
            if (name.equals("bluetooth")) return panel("Opening bluetooth.", c, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            if (name.matches("settings|ajustes|configuraci[oó]n")) return panel("Opening settings.", c, new Intent(Settings.ACTION_SETTINGS));
            final AppEntry a = findApp(apps, name);
            if (a != null) {
                return new Local("Opening " + a.label + ".", new Runnable() {
                    @Override public void run() { Actions.launchApp(c, a.pkg); }
                });
            }
            return new Local("I couldn't find an app called " + name + ".", null);
        }
        return null;
    }

    private static Local panel(String text, final Context c, final Intent i) {
        return new Local(text, new Runnable() {
            @Override public void run() { Actions.start(c, i); }
        });
    }

    /** Coincidencia exacta, luego la etiqueta más corta que contenga el texto. */
    private static AppEntry findApp(List<AppEntry> apps, String name) {
        if (apps == null) return null;
        AppEntry best = null;
        for (AppEntry a : apps) {
            String l = a.label.toLowerCase(Locale.ROOT);
            if (l.equals(name)) return a;
            if (l.contains(name) && (best == null || l.length() < best.label.length())) best = a;
        }
        return best;
    }
}
