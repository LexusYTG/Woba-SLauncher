package com.woba.slauncher.sim.apps;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.CalendarContract;
import android.view.MotionEvent;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.woba.slauncher.sim.Phone;
import com.woba.slauncher.sim.Reminders;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;
import com.woba.slauncher.ui.WpControls;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** Calendario: día, agenda y mes; eventos propios con recordatorio real e importación del calendario de Android. */
public class CalendarApp extends SimApp {
    private long selected = System.currentTimeMillis();
    private Month month;
    private WpUi.WpList agenda, dayList;
    private int viewYear, viewMonth;

    public CalendarApp(Context c, SimHost host) {
        super(c, host, "calendar");
        Calendar cal = Calendar.getInstance();
        viewYear = cal.get(Calendar.YEAR);
        viewMonth = cal.get(Calendar.MONTH);
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "calendar"; }

    @Override public void onOpen(String arg) {
        if (arg != null && arg.equals("new")) openEdit(newEvent(selected));
    }

    private static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private ArrayList<SimStore.Event> eventsOn(long day) {
        long s = startOfDay(day), e = s + 86_400_000L;
        ArrayList<SimStore.Event> out = new ArrayList<>();
        for (SimStore.Event ev : SimStore.events) if (ev.start < e && Math.max(ev.end, ev.start) >= s) out.add(ev);
        Collections.sort(out, new Comparator<SimStore.Event>() {
            @Override public int compare(SimStore.Event a, SimStore.Event b) { return Long.compare(a.start, b.start); }
        });
        return out;
    }

    private SimStore.Event newEvent(long day) {
        SimStore.Event e = new SimStore.Event();
        e.id = SimStore.newId();
        e.title = "";
        Calendar c = Calendar.getInstance();
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(day);
        c.set(d.get(Calendar.YEAR), d.get(Calendar.MONTH), d.get(Calendar.DAY_OF_MONTH));
        c.add(Calendar.HOUR_OF_DAY, 1);
        c.set(Calendar.MINUTE, 0);
        e.start = c.getTimeInMillis();
        e.end = e.start + 3600_000L;
        return e;
    }

    // ------------------------------------------------------------------ principal

    private void showMain() {
        final Context c = getContext();
        dayList = new WpUi.WpList(c);
        agenda = new WpUi.WpList(c);
        LinearLayout monthPage = new LinearLayout(c);
        monthPage.setOrientation(LinearLayout.VERTICAL);
        month = new Month(c);
        monthPage.addView(month, new LinearLayout.LayoutParams(-1, Theme.dp(330)));
        final WpUi.WpList monthEvents = new WpUi.WpList(c);
        monthPage.addView(monthEvents, new LinearLayout.LayoutParams(-1, 0, 1f));
        month.events = monthEvents;
        WpUi.OnItem open = new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Event) openEdit((SimStore.Event) it.tag);
            }
        };
        dayList.setOnItem(open); agenda.setOnItem(open); monthEvents.setOnItem(open);
        Pivot pv = new Pivot(c, "calendar", new String[]{"day", "agenda", "month"}, new View[]{dayList, agenda, monthPage});
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.ADD, "new", new Runnable() { @Override public void run() { openEdit(newEvent(selected)); } }),
                new AppBar.Item(Glyph.CALENDAR, "today", new Runnable() { @Override public void run() {
                    selected = System.currentTimeMillis();
                    Calendar cal = Calendar.getInstance();
                    viewYear = cal.get(Calendar.YEAR); viewMonth = cal.get(Calendar.MONTH);
                    refresh();
                } }),
                new AppBar.Item(Glyph.LEFT, "previous", new Runnable() { @Override public void run() { shift(-1); } }),
                new AppBar.Item(Glyph.RIGHT, "next", new Runnable() { @Override public void run() { shift(1); } })
        ).menu(new AppBar.Item(0, "import android calendar", new Runnable() { @Override public void run() { importReal(); } }));
        swapContent(pv, ab);
        refresh();
    }

    private void shift(int d) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selected);
        cal.add(Calendar.DAY_OF_MONTH, d);
        selected = cal.getTimeInMillis();
        viewYear = cal.get(Calendar.YEAR);
        viewMonth = cal.get(Calendar.MONTH);
        refresh();
    }

    private WpUi.Item evItem(SimStore.Event e) {
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String when = e.allDay ? "all day" : tf.format(new Date(e.start)) + " – " + tf.format(new Date(e.end));
        return new WpUi.Item(e.title.isEmpty() ? "(no title)" : e.title).sub(when + (e.where.isEmpty() ? "" : " · " + e.where)).tag(e);
    }

    private void refresh() {
        if (dayList == null) return;
        SimpleDateFormat df = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault());
        ArrayList<WpUi.Item> items = new ArrayList<>();
        items.add(WpUi.Item.section(df.format(new Date(selected))));
        ArrayList<SimStore.Event> today = eventsOn(selected);
        for (SimStore.Event e : today) items.add(evItem(e));
        if (today.isEmpty()) items.add(new WpUi.Item("nothing planned").dim());
        dayList.setItems(items);
        // agenda: próximos 60 días
        ArrayList<WpUi.Item> ag = new ArrayList<>();
        long t = startOfDay(System.currentTimeMillis());
        for (int i = 0; i < 60; i++) {
            ArrayList<SimStore.Event> l = eventsOn(t + i * 86_400_000L + 3600_000L);
            if (l.isEmpty()) continue;
            ag.add(WpUi.Item.section(df.format(new Date(t + i * 86_400_000L + 3600_000L))));
            for (SimStore.Event e : l) ag.add(evItem(e));
        }
        if (ag.isEmpty()) ag.add(new WpUi.Item("no upcoming events").dim());
        agenda.setItems(ag);
        if (month != null) month.refresh();
    }

    private class Month extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        WpUi.WpList events;

        Month(Context c) { super(c); }

        void refresh() {
            invalidate();
            if (events == null) return;
            ArrayList<WpUi.Item> items = new ArrayList<>();
            for (SimStore.Event e : eventsOn(selected)) items.add(evItem(e));
            if (items.isEmpty()) items.add(new WpUi.Item("nothing planned").dim());
            events.setItems(items);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth();
            Calendar cal = Calendar.getInstance();
            cal.set(viewYear, viewMonth, 1, 12, 0, 0);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(26));
            p.setColor(Theme.fg);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.getTime()).toLowerCase(), Theme.dpf(18), Theme.dpf(32), p);
            Glyph.draw(c, Glyph.LEFT, w - Theme.dpf(80), Theme.dpf(24), Theme.dpf(20), Theme.fg);
            Glyph.draw(c, Glyph.RIGHT, w - Theme.dpf(30), Theme.dpf(24), Theme.dpf(20), Theme.fg);
            int first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7; // lunes = 0
            int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            float cw = (w - Theme.dpf(12)) / 7f, ch = Theme.dpf(46), y0 = Theme.dpf(78);
            String[] hd = {"M", "T", "W", "T", "F", "S", "S"};
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Theme.sp(14));
            p.setColor(Theme.subtle);
            for (int i = 0; i < 7; i++) c.drawText(hd[i], Theme.dpf(6) + cw * (i + 0.5f), Theme.dpf(64), p);
            Calendar sel = Calendar.getInstance();
            sel.setTimeInMillis(selected);
            Calendar now = Calendar.getInstance();
            for (int d = 1; d <= days; d++) {
                int idx = first + d - 1;
                float cx = Theme.dpf(6) + cw * (idx % 7 + 0.5f), cy = y0 + ch * (idx / 7) + ch / 2f;
                boolean isSel = sel.get(Calendar.YEAR) == viewYear && sel.get(Calendar.MONTH) == viewMonth && sel.get(Calendar.DAY_OF_MONTH) == d;
                boolean today = now.get(Calendar.YEAR) == viewYear && now.get(Calendar.MONTH) == viewMonth && now.get(Calendar.DAY_OF_MONTH) == d;
                if (isSel || today) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(isSel ? Theme.accent : WpUi.mix(Theme.bg, Theme.fg, 0.2f));
                    c.drawRect(cx - cw / 2f + 2, cy - ch / 2f + 2, cx + cw / 2f - 2, cy + ch / 2f - 2, p);
                }
                p.setStyle(Paint.Style.FILL);
                p.setColor(isSel ? 0xFFFFFFFF : Theme.fg);
                p.setTextSize(Theme.sp(19));
                c.drawText(String.valueOf(d), cx, Theme.centerBaseline(p, cy - Theme.dpf(3)), p);
                Calendar dd = Calendar.getInstance();
                dd.set(viewYear, viewMonth, d, 12, 0, 0);
                if (!eventsOn(dd.getTimeInMillis()).isEmpty()) {
                    p.setColor(isSel ? 0xFFFFFFFF : Theme.accent);
                    c.drawRect(cx - Theme.dpf(8), cy + Theme.dpf(12), cx + Theme.dpf(8), cy + Theme.dpf(15), p);
                }
            }
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float w = getWidth(), x = e.getX(), y = e.getY();
            if (y < Theme.dpf(50)) {
                if (x > w - Theme.dpf(110) && x < w - Theme.dpf(55)) { viewMonth--; if (viewMonth < 0) { viewMonth = 11; viewYear--; } }
                else if (x >= w - Theme.dpf(55)) { viewMonth++; if (viewMonth > 11) { viewMonth = 0; viewYear++; } }
                invalidate();
                return true;
            }
            Calendar cal = Calendar.getInstance();
            cal.set(viewYear, viewMonth, 1, 12, 0, 0);
            int first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            float cw = (w - Theme.dpf(12)) / 7f, ch = Theme.dpf(46), y0 = Theme.dpf(78);
            int col = (int) ((x - Theme.dpf(6)) / cw), row = (int) ((y - y0) / ch);
            int d = row * 7 + col - first + 1;
            if (col >= 0 && col < 7 && row >= 0 && d >= 1 && d <= cal.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                cal.set(Calendar.DAY_OF_MONTH, d);
                selected = cal.getTimeInMillis();
                CalendarApp.this.refresh();
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ edición

    private void openEdit(final SimStore.Event e) {
        go(new Runnable() { @Override public void run() { showEdit(e); } });
    }

    private void showEdit(final SimStore.Event e) {
        final Context c = getContext();
        final boolean isNew = !SimStore.events.contains(e);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(WpUi.pageHeader(c, "calendar", isNew ? "new event" : "event"));
        final EditText title = field(col, "what", e.title);
        final EditText where = field(col, "where", e.where);
        final TextView start = picker(col, "starts", e, true);
        final TextView end = picker(col, "ends", e, false);
        final EditText notes = field(col, "notes", e.notes);
        LinearLayout rr = new LinearLayout(c);
        rr.setPadding(Theme.dp(18), Theme.dp(12), Theme.dp(18), 0);
        rr.addView(WpUi.label(c, "all day", 19, Theme.light, Theme.fg), new LinearLayout.LayoutParams(0, -2, 1f));
        rr.addView(new WpControls.Switch(c, e.allDay, new WpControls.Switch.OnChange() {
            @Override public void onChange(boolean on) { e.allDay = on; }
        }));
        col.addView(rr);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.SAVE, "save", new Runnable() { @Override public void run() {
                    e.title = title.getText().toString().trim();
                    e.where = where.getText().toString().trim();
                    e.notes = notes.getText().toString().trim();
                    if (e.title.isEmpty()) { Toast.makeText(getContext(), "Enter a title", Toast.LENGTH_SHORT).show(); return; }
                    if (e.end < e.start) e.end = e.start + 3600_000L;
                    if (!SimStore.events.contains(e)) SimStore.events.add(e);
                    SimStore.save();
                    Reminders.schedule(getContext(), "ev" + e.id, e.start - e.remindMin * 60_000L, e.title, e.where.isEmpty() ? "In " + e.remindMin + " minutes" : e.where, "calendar");
                    WpUi.hideKeyboard(title);
                    SimStore.fire("calendar");
                    popPage();
                    refresh();
                } }),
                new AppBar.Item(Glyph.DELETE, "delete", new Runnable() { @Override public void run() {
                    SimStore.events.remove(e);
                    Reminders.cancel(getContext(), "ev" + e.id);
                    SimStore.save();
                    SimStore.fire("calendar");
                    popPage();
                    refresh();
                } })
        );
        swapContent(sv, ab);
    }

    private EditText field(LinearLayout col, String label, String val) {
        Context c = getContext();
        TextView l = WpUi.label(c, label, 14, Theme.light, Theme.subtle);
        l.setPadding(Theme.dp(18), Theme.dp(8), 0, Theme.dp(3));
        col.addView(l);
        EditText e = WpUi.textBox(c, "", val);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(e, lp);
        return e;
    }

    private TextView picker(LinearLayout col, String label, final SimStore.Event e, final boolean isStart) {
        final Context c = getContext();
        TextView l = WpUi.label(c, label, 14, Theme.light, Theme.subtle);
        l.setPadding(Theme.dp(18), Theme.dp(8), 0, Theme.dp(3));
        col.addView(l);
        final TextView v = WpUi.label(c, Phone.full(isStart ? e.start : e.end), 20, Theme.light, Theme.fg);
        v.setPadding(Theme.dp(18), Theme.dp(8), Theme.dp(18), Theme.dp(8));
        v.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View x) {
                final Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(isStart ? e.start : e.end);
                new DatePickerDialog(c, new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(DatePicker dp, int y, int m, int d) {
                        cal.set(y, m, d);
                        new TimePickerDialog(c, new TimePickerDialog.OnTimeSetListener() {
                            @Override public void onTimeSet(TimePicker tp, int h, int mi) {
                                cal.set(Calendar.HOUR_OF_DAY, h);
                                cal.set(Calendar.MINUTE, mi);
                                if (isStart) { long len = e.end - e.start; e.start = cal.getTimeInMillis(); e.end = e.start + Math.max(len, 0); }
                                else e.end = cal.getTimeInMillis();
                                v.setText(Phone.full(isStart ? e.start : e.end));
                            }
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        WpUi.tilt(v);
        col.addView(v);
        return v;
    }

    // ------------------------------------------------------------------ calendario de Android

    private void importReal() {
        final Context c = getContext();
        if (c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, new Runnable() { @Override public void run() { doImport(); } });
        } else doImport();
    }

    private void doImport() {
        Context c = getContext();
        if (c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        long now = System.currentTimeMillis();
        int n = 0;
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
                    new String[]{CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_LOCATION, CalendarContract.Events.ALL_DAY},
                    CalendarContract.Events.DTSTART + " > ? AND " + CalendarContract.Events.DTSTART + " < ?",
                    new String[]{String.valueOf(now - 86_400_000L), String.valueOf(now + 120L * 86_400_000L)}, CalendarContract.Events.DTSTART);
            while (cur != null && cur.moveToNext()) {
                String t = cur.getString(0);
                long s = cur.getLong(1);
                boolean dup = false;
                for (SimStore.Event e : SimStore.events) if (e.start == s && String.valueOf(t).equals(e.title)) { dup = true; break; }
                if (dup) continue;
                SimStore.Event e = new SimStore.Event();
                e.id = SimStore.newId();
                e.title = t == null ? "" : t;
                e.start = s;
                e.end = cur.getLong(2) > s ? cur.getLong(2) : s + 3600_000L;
                e.where = cur.getString(3) == null ? "" : cur.getString(3);
                e.allDay = cur.getInt(4) == 1;
                SimStore.events.add(e);
                n++;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        SimStore.save();
        Toast.makeText(c, n + " events imported", Toast.LENGTH_SHORT).show();
        refresh();
    }
}
