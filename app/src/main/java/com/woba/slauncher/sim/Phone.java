package com.woba.slauncher.sim;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.database.Cursor;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Utilidades de telefonía simulada/real compartidas por las apps. */
public final class Phone {
    private Phone() {}

    /** Llama: simulada si el ajuste está activo, real con CALL_PHONE o marcador de Android en otro caso. */
    public static void call(Context c, SimHost host, String name, String number) {
        if (number == null || number.trim().isEmpty()) return;
        if (SimStore.flag("sim_calls", false)) {
            host.startCall(name, number, false);
            return;
        }
        SimStore.addCall(name == null ? "" : name, number, System.currentTimeMillis(), 2, 0);
        SimStore.save();
        SimStore.fire("calls");
        Intent i = new Intent(c.checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)));
        host.launch(i);
    }

    public static void email(SimHost host, String to) {
        host.openSim("mail", "compose|" + to);
    }

    public static void text(SimHost host, String number) {
        host.openSim("messaging", number);
    }

    public static void map(SimHost host, String query) {
        host.openSim("maps", "q|" + query);
    }

    public static String duration(int secs) {
        int m = secs / 60, s = secs % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    private static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    /** Hora si es hoy, "ayer", día de la semana o fecha corta. */
    public static String when(long t) {
        long now = System.currentTimeMillis();
        if (sameDay(t, now)) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
        if (sameDay(t, now - 86_400_000L)) return "yesterday";
        if (now - t < 6 * 86_400_000L) return new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date(t)).toLowerCase();
        return new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date(t));
    }

    public static String full(long t) {
        return new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date(t));
    }

    /** Copia los contactos de Android a la agenda simulada (evita duplicados por número). Devuelve cuántos añadió. */
    public static int importAndroidContacts(Context c) {
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return -1;
        int added = 0;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            while (cur != null && cur.moveToNext() && added < 2000) {
                String name = cur.getString(0), num = cur.getString(1);
                if (name == null || num == null || SimStore.byNumber(num) != null) continue;
                SimStore.Contact k = new SimStore.Contact();
                k.id = SimStore.newId();
                k.name = name;
                k.phone = num;
                SimStore.contacts.add(k);
                added++;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        java.util.Collections.sort(SimStore.contacts, SimStore.BY_NAME);
        SimStore.save();
        SimStore.fire("contacts");
        return added;
    }
}
