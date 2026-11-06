package com.woba.slauncher.sim;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Estado compartido de todo el simulador: contactos, mensajes, llamadas, correo, calendario,
 * alarmas, notas, favoritos del navegador, etc. Persistido en JSON. Solo hilo UI.
 * Teléfono, Mensajes, People, tiles y Action Center leen de aquí, así nunca discrepan.
 */
public final class SimStore {
    private SimStore() {}

    private static final String PREF = "sim_store";
    private static SharedPreferences sp;
    private static boolean loaded;

    public interface Listener { void onSimChanged(String what); }

    private static final ArrayList<Listener> LISTENERS = new ArrayList<>();

    public static void addListener(Listener l) { if (!LISTENERS.contains(l)) LISTENERS.add(l); }

    public static void removeListener(Listener l) { LISTENERS.remove(l); }

    public static void fire(String what) {
        for (Listener l : new ArrayList<>(LISTENERS)) l.onSimChanged(what);
    }

    // ------------------------------------------------------------------ modelos

    public static class Contact {
        public String id, name, phone, email, address, company, note, birthday;
        public boolean fav;
        public int color = -1; // índice de acento para el avatar; -1 = automático
        public boolean sim = true; // false = viene de la agenda real de Android

        public String initials() {
            String[] p = name.trim().split("\\s+");
            if (p.length == 0 || p[0].isEmpty()) return "?";
            String s = p[0].substring(0, 1);
            if (p.length > 1) s += p[p.length - 1].substring(0, 1);
            return s.toUpperCase(Locale.getDefault());
        }

        public String sortKey() { return name.toLowerCase(Locale.getDefault()); }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("id", id).put("name", name).put("phone", s(phone)).put("email", s(email))
                    .put("address", s(address)).put("company", s(company)).put("note", s(note))
                    .put("birthday", s(birthday)).put("fav", fav).put("color", color);
            return o;
        }

        static Contact fromJson(JSONObject o) {
            Contact c = new Contact();
            c.id = o.optString("id");
            c.name = o.optString("name");
            c.phone = o.optString("phone");
            c.email = o.optString("email");
            c.address = o.optString("address");
            c.company = o.optString("company");
            c.note = o.optString("note");
            c.birthday = o.optString("birthday");
            c.fav = o.optBoolean("fav");
            c.color = o.optInt("color", -1);
            return c;
        }
    }

    public static class Msg {
        public String thread; // número o id de hilo
        public String body;
        public long time;
        public boolean out, read = true, mms;
    }

    public static class Call {
        public String name, number;
        public long time;
        public int type; // 1 entrante, 2 saliente, 3 perdida
        public int seconds;
    }

    public static class Mail {
        public String id, from, fromAddr, to, subject, body, folder = "inbox";
        public long time;
        public boolean read, flagged, attach;
    }

    public static class Event {
        public String id, title, where, notes;
        public long start, end;
        public boolean allDay;
        public int remindMin = 15;
        public int color = -1;
    }

    public static class Alarm {
        public String id, label;
        public int hour, minute;
        public boolean on;
        public int days; // máscara bits 0..6 = domingo..sábado; 0 = una vez
    }

    public static class Note {
        public String id, title, body;
        public long time;
        public String notebook = "Personal";
    }

    public static class Mark {
        public String title, url;
        public long time;
    }

    // ------------------------------------------------------------------ datos en memoria

    public static final ArrayList<Contact> contacts = new ArrayList<>();
    public static final ArrayList<Msg> messages = new ArrayList<>();
    public static final ArrayList<Call> calls = new ArrayList<>();
    public static final ArrayList<Mail> mails = new ArrayList<>();
    public static final ArrayList<Event> events = new ArrayList<>();
    public static final ArrayList<Alarm> alarms = new ArrayList<>();
    public static final ArrayList<Note> notes = new ArrayList<>();
    public static final ArrayList<Mark> favorites = new ArrayList<>();
    public static final ArrayList<Mark> history = new ArrayList<>();
    public static final ArrayList<Mark> readingList = new ArrayList<>();
    public static final ArrayList<String> recentDocs = new ArrayList<>();

    private static int idSeq;

    public static String newId() { return Long.toString(System.currentTimeMillis(), 36) + (idSeq++); }

    // ------------------------------------------------------------------ carga / guardado

    public static void init(Context c) {
        if (loaded) return;
        sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        loaded = true;
        String j = sp.getString("data", null);
        if (j == null) {
            seed();
            save();
            return;
        }
        try {
            JSONObject root = new JSONObject(j);
            readList(root.optJSONArray("contacts"), contacts, Kind.CONTACT);
            readList(root.optJSONArray("messages"), messages, Kind.MSG);
            readList(root.optJSONArray("calls"), calls, Kind.CALL);
            readList(root.optJSONArray("mails"), mails, Kind.MAIL);
            readList(root.optJSONArray("events"), events, Kind.EVENT);
            readList(root.optJSONArray("alarms"), alarms, Kind.ALARM);
            readList(root.optJSONArray("notes"), notes, Kind.NOTE);
            readList(root.optJSONArray("favorites"), favorites, Kind.MARK);
            readList(root.optJSONArray("history"), history, Kind.MARK);
            readList(root.optJSONArray("reading"), readingList, Kind.MARK);
            JSONArray rd = root.optJSONArray("recentDocs");
            if (rd != null) for (int i = 0; i < rd.length(); i++) recentDocs.add(rd.optString(i));
        } catch (Exception e) {
            seed();
        }
    }

    private enum Kind { CONTACT, MSG, CALL, MAIL, EVENT, ALARM, NOTE, MARK }

    @SuppressWarnings("unchecked")
    private static void readList(JSONArray a, ArrayList list, Kind k) throws JSONException {
        list.clear();
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.getJSONObject(i);
            switch (k) {
                case CONTACT: list.add(Contact.fromJson(o)); break;
                case MSG: {
                    Msg m = new Msg();
                    m.thread = o.optString("t");
                    m.body = o.optString("b");
                    m.time = o.optLong("ts");
                    m.out = o.optBoolean("o");
                    m.read = o.optBoolean("r", true);
                    list.add(m);
                    break;
                }
                case CALL: {
                    Call c = new Call();
                    c.name = o.optString("n");
                    c.number = o.optString("p");
                    c.time = o.optLong("ts");
                    c.type = o.optInt("ty");
                    c.seconds = o.optInt("s");
                    list.add(c);
                    break;
                }
                case MAIL: {
                    Mail m = new Mail();
                    m.id = o.optString("id");
                    m.from = o.optString("f");
                    m.fromAddr = o.optString("fa");
                    m.to = o.optString("to");
                    m.subject = o.optString("s");
                    m.body = o.optString("b");
                    m.folder = o.optString("fo", "inbox");
                    m.time = o.optLong("ts");
                    m.read = o.optBoolean("r");
                    m.flagged = o.optBoolean("fl");
                    m.attach = o.optBoolean("at");
                    list.add(m);
                    break;
                }
                case EVENT: {
                    Event e = new Event();
                    e.id = o.optString("id");
                    e.title = o.optString("t");
                    e.where = o.optString("w");
                    e.notes = o.optString("n");
                    e.start = o.optLong("s");
                    e.end = o.optLong("e");
                    e.allDay = o.optBoolean("ad");
                    e.remindMin = o.optInt("rm", 15);
                    e.color = o.optInt("c", -1);
                    list.add(e);
                    break;
                }
                case ALARM: {
                    Alarm a2 = new Alarm();
                    a2.id = o.optString("id");
                    a2.label = o.optString("l");
                    a2.hour = o.optInt("h");
                    a2.minute = o.optInt("m");
                    a2.on = o.optBoolean("on");
                    a2.days = o.optInt("d");
                    list.add(a2);
                    break;
                }
                case NOTE: {
                    Note n = new Note();
                    n.id = o.optString("id");
                    n.title = o.optString("t");
                    n.body = o.optString("b");
                    n.time = o.optLong("ts");
                    n.notebook = o.optString("nb", "Personal");
                    list.add(n);
                    break;
                }
                case MARK: {
                    Mark m = new Mark();
                    m.title = o.optString("t");
                    m.url = o.optString("u");
                    m.time = o.optLong("ts");
                    list.add(m);
                    break;
                }
            }
        }
    }

    public static void save() {
        if (sp == null) return;
        try {
            JSONObject root = new JSONObject();
            JSONArray a = new JSONArray();
            for (Contact c : contacts) if (c.sim) a.put(c.toJson());
            root.put("contacts", a);
            a = new JSONArray();
            for (Msg m : messages) a.put(new JSONObject().put("t", m.thread).put("b", m.body).put("ts", m.time)
                    .put("o", m.out).put("r", m.read));
            root.put("messages", a);
            a = new JSONArray();
            for (Call c : calls) a.put(new JSONObject().put("n", s(c.name)).put("p", c.number).put("ts", c.time)
                    .put("ty", c.type).put("s", c.seconds));
            root.put("calls", a);
            a = new JSONArray();
            for (Mail m : mails) a.put(new JSONObject().put("id", m.id).put("f", s(m.from)).put("fa", s(m.fromAddr))
                    .put("to", s(m.to)).put("s", s(m.subject)).put("b", s(m.body)).put("fo", m.folder)
                    .put("ts", m.time).put("r", m.read).put("fl", m.flagged).put("at", m.attach));
            root.put("mails", a);
            a = new JSONArray();
            for (Event e : events) a.put(new JSONObject().put("id", e.id).put("t", s(e.title)).put("w", s(e.where))
                    .put("n", s(e.notes)).put("s", e.start).put("e", e.end).put("ad", e.allDay)
                    .put("rm", e.remindMin).put("c", e.color));
            root.put("events", a);
            a = new JSONArray();
            for (Alarm al : alarms) a.put(new JSONObject().put("id", al.id).put("l", s(al.label)).put("h", al.hour)
                    .put("m", al.minute).put("on", al.on).put("d", al.days));
            root.put("alarms", a);
            a = new JSONArray();
            for (Note n : notes) a.put(new JSONObject().put("id", n.id).put("t", s(n.title)).put("b", s(n.body))
                    .put("ts", n.time).put("nb", n.notebook));
            root.put("notes", a);
            root.put("favorites", marks(favorites));
            root.put("history", marks(history));
            root.put("reading", marks(readingList));
            JSONArray rd = new JSONArray();
            for (String s : recentDocs) rd.put(s);
            root.put("recentDocs", rd);
            sp.edit().putString("data", root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static JSONArray marks(List<Mark> l) throws JSONException {
        JSONArray a = new JSONArray();
        for (Mark m : l) a.put(new JSONObject().put("t", s(m.title)).put("u", s(m.url)).put("ts", m.time));
        return a;
    }

    private static String s(String v) { return v == null ? "" : v; }

    // ------------------------------------------------------------------ semilla

    private static Contact contact(String name, String phone, String email, boolean fav) {
        Contact c = new Contact();
        c.id = newId();
        c.name = name;
        c.phone = phone;
        c.email = email;
        c.fav = fav;
        return c;
    }

    private static void seed() {
        long now = System.currentTimeMillis();
        long h = 3600_000L, d = 24 * h;
        contacts.clear();
        contacts.add(contact("Ana García", "+34 600 111 222", "ana.garcia@outlook.com", true));
        contacts.add(contact("Carlos Ruiz", "+34 611 222 333", "carlos.ruiz@outlook.com", true));
        contacts.add(contact("Elena Martín", "+34 622 333 444", "elena.martin@hotmail.com", false));
        contacts.add(contact("Javier López", "+34 633 444 555", "javi.lopez@live.com", false));
        contacts.add(contact("Lucía Fernández", "+34 644 555 666", "lucia.f@outlook.com", true));
        contacts.add(contact("Mamá", "+34 655 666 777", "", true));
        contacts.add(contact("Miguel Torres", "+34 666 777 888", "miguel.torres@outlook.com", false));
        contacts.add(contact("Paula Sanz", "+34 677 888 999", "paula.sanz@hotmail.com", false));
        contacts.add(contact("Pedro Gómez", "+34 688 999 000", "", false));
        contacts.add(contact("Sofía Navarro", "+34 699 000 111", "sofia.n@outlook.com", false));
        Collections.sort(contacts, BY_NAME);

        messages.clear();
        addMsg("+34 600 111 222", "¿Quedamos mañana para comer?", now - 3 * h, false, false);
        addMsg("+34 600 111 222", "Vale, a las 14:00 en el sitio de siempre", now - 2 * h, true, true);
        addMsg("+34 655 666 777", "Llámame cuando puedas", now - 26 * h, false, true);
        addMsg("+34 611 222 333", "Te paso las fotos esta noche", now - 50 * h, false, true);

        calls.clear();
        addCall("Ana García", "+34 600 111 222", now - 5 * h, 3, 0);
        addCall("Mamá", "+34 655 666 777", now - 28 * h, 1, 312);
        addCall("Carlos Ruiz", "+34 611 222 333", now - 30 * h, 2, 95);
        addCall("", "+34 910 000 000", now - 3 * d, 3, 0);

        mails.clear();
        mails.add(mail("Microsoft account team", "account-security-noreply@accountprotection.example.com",
                "Información de seguridad de la cuenta Microsoft", "Hola,\n\nSe ha agregado una nueva dirección de correo electrónico a tu cuenta Microsoft. Si no has sido tú, protege tu cuenta ahora.\n\nEl equipo de cuentas de Microsoft", now - 2 * h, false));
        mails.add(mail("Windows Phone Team", "wpteam@example.com", "Bienvenido a tu nuevo teléfono",
                "Gracias por elegir un teléfono con Windows.\n\nAquí tienes algunos consejos para empezar: toca y mantén pulsado un mosaico para moverlo, desliza hacia la izquierda para ver todas tus aplicaciones y arrastra desde el borde superior para abrir el Centro de actividades.", now - 20 * h, false));
        mails.add(mail("Ana García", "ana.garcia@outlook.com", "Fotos del fin de semana",
                "¡Hola!\n\nTe adjunto las fotos de la excursión. Salieron muy bien.\n\nUn beso,\nAna", now - 2 * d, true));
        mails.add(mail("OneDrive", "onedrive@example.com", "Tienes 15 GB de almacenamiento gratuito",
                "Guarda tus fotos y documentos en OneDrive y accede a ellos desde cualquier dispositivo.", now - 5 * d, true));

        events.clear();
        Event e = new Event();
        e.id = newId();
        e.title = "Comida con Ana";
        e.where = "Restaurante La Plaza";
        e.start = now + 20 * h;
        e.end = e.start + h;
        events.add(e);
        e = new Event();
        e.id = newId();
        e.title = "Revisión del proyecto";
        e.where = "Sala 3";
        e.start = now + 2 * d + 4 * h;
        e.end = e.start + 2 * h;
        events.add(e);

        alarms.clear();
        Alarm a = new Alarm();
        a.id = newId();
        a.hour = 7;
        a.minute = 30;
        a.label = "Despertador";
        a.days = 0b0111110;
        a.on = false;
        alarms.add(a);

        notes.clear();
        Note n = new Note();
        n.id = newId();
        n.title = "Lista de la compra";
        n.body = "Leche\nPan\nHuevos\nFruta";
        n.time = now - d;
        notes.add(n);

        favorites.clear();
        favorites.add(mark("Bing", "https://www.bing.com"));
        favorites.add(mark("Wikipedia", "https://es.wikipedia.org"));
    }

    private static Mark mark(String t, String u) {
        Mark m = new Mark();
        m.title = t;
        m.url = u;
        m.time = System.currentTimeMillis();
        return m;
    }

    private static Mail mail(String from, String addr, String subj, String body, long t, boolean read) {
        Mail m = new Mail();
        m.id = newId();
        m.from = from;
        m.fromAddr = addr;
        m.to = "yo@outlook.com";
        m.subject = subj;
        m.body = body;
        m.time = t;
        m.read = read;
        return m;
    }

    // ------------------------------------------------------------------ operaciones

    public static final Comparator<Contact> BY_NAME = new Comparator<Contact>() {
        @Override
        public int compare(Contact a, Contact b) { return a.sortKey().compareTo(b.sortKey()); }
    };

    public static void addMsg(String thread, String body, long time, boolean out, boolean read) {
        Msg m = new Msg();
        m.thread = thread;
        m.body = body;
        m.time = time;
        m.out = out;
        m.read = read;
        messages.add(m);
    }

    public static void addCall(String name, String number, long time, int type, int secs) {
        Call c = new Call();
        c.name = name;
        c.number = number;
        c.time = time;
        c.type = type;
        c.seconds = secs;
        calls.add(c);
    }

    public static String norm(String n) {
        if (n == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : n.toCharArray()) if (Character.isDigit(ch) || ch == '+') sb.append(ch);
        String s = sb.toString();
        if (s.startsWith("+34")) s = s.substring(3);
        return s;
    }

    public static Contact byNumber(String number) {
        String n = norm(number);
        if (n.isEmpty()) return null;
        for (Contact c : contacts) if (norm(c.phone).equals(n)) return c;
        return null;
    }

    public static String nameFor(String number) {
        Contact c = byNumber(number);
        return c != null ? c.name : number;
    }

    public static int unreadMessages() {
        int n = 0;
        for (Msg m : messages) if (!m.out && !m.read) n++;
        return n;
    }

    public static int missedCalls() {
        int n = 0;
        for (Call c : calls) if (c.type == 3 && c.seconds == 0 && c.time > lastCallsSeen()) n++;
        return n;
    }

    public static long lastCallsSeen() { return sp == null ? 0 : sp.getLong("calls_seen", 0); }

    public static void markCallsSeen() {
        if (sp != null) sp.edit().putLong("calls_seen", System.currentTimeMillis()).apply();
        fire("calls");
    }

    public static int unreadMail() {
        int n = 0;
        for (Mail m : mails) if (m.folder.equals("inbox") && !m.read) n++;
        return n;
    }

    public static Event nextEvent() {
        long now = System.currentTimeMillis();
        Event best = null;
        for (Event e : events) if (e.end >= now && (best == null || e.start < best.start)) best = e;
        return best;
    }

    // Preferencias sueltas del simulador.
    public static String str(String key, String def) { return sp == null ? def : sp.getString("k_" + key, def); }

    public static void putStr(String key, String v) { if (sp != null) sp.edit().putString("k_" + key, v).apply(); }

    public static boolean flag(String key, boolean def) { return sp == null ? def : sp.getBoolean("f_" + key, def); }

    public static void putFlag(String key, boolean v) { if (sp != null) sp.edit().putBoolean("f_" + key, v).apply(); }

    public static int num(String key, int def) { return sp == null ? def : sp.getInt("n_" + key, def); }

    public static void putNum(String key, int v) { if (sp != null) sp.edit().putInt("n_" + key, v).apply(); }
}
