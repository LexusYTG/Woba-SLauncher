package com.woba.slauncher.sim.apps;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.woba.slauncher.sim.Reminders;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpControls;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Alarmas y reloj: alarmas reales (vía el reloj de Android), reloj mundial, temporizador y cronómetro. */
public class AlarmsApp extends SimApp {
    private LinearLayout alarmsCol;
    private WorldView world;
    private TimerView timer;
    private StopView stop;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Pivot pivot;

    private static final String[][] ZONES = {
            {"Madrid", "Europe/Madrid"}, {"London", "Europe/London"}, {"Paris", "Europe/Paris"}, {"Berlin", "Europe/Berlin"}, {"Moscow", "Europe/Moscow"},
            {"Istanbul", "Europe/Istanbul"}, {"Cairo", "Africa/Cairo"}, {"Dubai", "Asia/Dubai"}, {"Mumbai", "Asia/Kolkata"}, {"Bangkok", "Asia/Bangkok"},
            {"Beijing", "Asia/Shanghai"}, {"Tokyo", "Asia/Tokyo"}, {"Sydney", "Australia/Sydney"}, {"Auckland", "Pacific/Auckland"}, {"Honolulu", "Pacific/Honolulu"},
            {"Los Angeles", "America/Los_Angeles"}, {"Denver", "America/Denver"}, {"Chicago", "America/Chicago"}, {"New York", "America/New_York"}, {"Mexico City", "America/Mexico_City"},
            {"Bogotá", "America/Bogota"}, {"Lima", "America/Lima"}, {"Santiago", "America/Santiago"}, {"Buenos Aires", "America/Argentina/Buenos_Aires"}, {"São Paulo", "America/Sao_Paulo"},
            {"Lagos", "Africa/Lagos"}, {"Johannesburg", "Africa/Johannesburg"}, {"Singapore", "Asia/Singapore"}, {"Seoul", "Asia/Seoul"}, {"Reykjavik", "Atlantic/Reykjavik"}
    };

    public AlarmsApp(Context c, SimHost host) {
        super(c, host, "alarms");
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "alarms & clock"; }

    @Override public void onDestroy() { h.removeCallbacksAndMessages(null); if (timer != null) timer.stopRing(); }

    @Override public void onOpen(String arg) {
        if (arg == null || pivot == null) return;
        if (arg.equals("timer")) pivot.setPage(2, false);
        else if (arg.equals("stopwatch")) pivot.setPage(3, false);
        else if (arg.startsWith("timer|")) { try { timer.startWith(Integer.parseInt(arg.substring(6))); pivot.setPage(2, false); } catch (Exception ignored) {} }
    }

    private void showMain() {
        final Context c = getContext();
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        alarmsCol = new LinearLayout(c);
        alarmsCol.setOrientation(LinearLayout.VERTICAL);
        sv.addView(alarmsCol, new ScrollView.LayoutParams(-1, -2));
        world = new WorldView(c);
        timer = new TimerView(c);
        stop = new StopView(c);
        pivot = new Pivot(c, "alarms & clock", new String[]{"alarms", "world clock", "timer", "stopwatch"}, new View[]{sv, world, timer, stop});
        final AppBar ab = new AppBar(c).items(new AppBar.Item(Glyph.ADD, "add", new Runnable() { @Override public void run() { addForPage(); } }));
        swapContent(pivot, ab);
        pivot.setOnPage(new Pivot.OnPage() { @Override public void onPage(int i) { } });
        fillAlarms();
        h.removeCallbacksAndMessages(null);
        h.post(new Runnable() { @Override public void run() { world.invalidate(); timer.invalidate(); stop.invalidate(); h.postDelayed(this, 200); } });
    }

    private void addForPage() {
        int p = pivot.getPage();
        if (p == 0) editAlarm(null);
        else if (p == 1) pickCity();
        else if (p == 2) timer.reset();
        else stop.lap();
    }

    // ------------------------------------------------------------------ alarmas

    private void fillAlarms() {
        final Context c = getContext();
        alarmsCol.removeAllViews();
        for (final SimStore.Alarm a : SimStore.alarms) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(Theme.dp(18), Theme.dp(12), Theme.dp(18), Theme.dp(12));
            LinearLayout t = new LinearLayout(c);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(WpUi.label(c, String.format(Locale.US, "%02d:%02d", a.hour, a.minute), 48, Theme.light, a.on ? Theme.fg : Theme.subtle));
            t.addView(WpUi.label(c, (a.label == null || a.label.isEmpty() ? "alarm" : a.label) + " · " + days(a.days), 14, Theme.light, Theme.subtle));
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(new WpControls.Switch(c, a.on, new WpControls.Switch.OnChange() {
                @Override public void onChange(boolean on) { a.on = on; SimStore.save(); applySystem(a); }
            }));
            row.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { editAlarm(a); } });
            row.setOnLongClickListener(new OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    showDialog("delete alarm", "Delete this alarm?", new String[]{"delete", "cancel"}, new Choice() {
                        @Override public void on(int i) { if (i == 0) { a.on = false; applySystem(a); SimStore.alarms.remove(a); SimStore.save(); fillAlarms(); } }
                    });
                    return true;
                }
            });
            alarmsCol.addView(row);
        }
        if (SimStore.alarms.isEmpty()) {
            TextView e = WpUi.label(c, "no alarms. tap + to add one.", 19, Theme.light, Theme.subtle);
            e.setPadding(Theme.dp(18), Theme.dp(20), 0, 0);
            alarmsCol.addView(e);
        }
    }

    private String days(int m) {
        if (m == 0) return "once";
        if (m == 127) return "every day";
        if (m == 0b0111110) return "weekdays";
        String[] n = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((m & (1 << i)) != 0) sb.append(sb.length() > 0 ? ", " : "").append(n[i]);
        return sb.toString();
    }

    /** Crea o retira la alarma real en el reloj de Android. */
    private void applySystem(SimStore.Alarm a) {
        Context c = getContext();
        try {
            if (a.on) {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, a.hour).putExtra(AlarmClock.EXTRA_MINUTES, a.minute)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a.label == null ? "" : a.label).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                if (a.days != 0) {
                    ArrayList<Integer> d = new ArrayList<>();
                    for (int k = 0; k < 7; k++) if ((a.days & (1 << k)) != 0) d.add(k + 1);
                    i.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, d);
                }
                host.launch(i);
                Calendar t = Calendar.getInstance();
                t.set(Calendar.HOUR_OF_DAY, a.hour); t.set(Calendar.MINUTE, a.minute); t.set(Calendar.SECOND, 0);
                if (t.getTimeInMillis() < System.currentTimeMillis()) t.add(Calendar.DAY_OF_MONTH, 1);
                long mins = (t.getTimeInMillis() - System.currentTimeMillis()) / 60000;
                Toast.makeText(c, "Alarm set for " + mins / 60 + " h " + mins % 60 + " min from now", Toast.LENGTH_SHORT).show();
            } else {
                Intent i = new Intent(AlarmClock.ACTION_DISMISS_ALARM).putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                        .putExtra(AlarmClock.EXTRA_HOUR, a.hour).putExtra(AlarmClock.EXTRA_MINUTES, a.minute).putExtra(AlarmClock.EXTRA_IS_PM, a.hour >= 12);
                host.launch(i);
            }
        } catch (Exception e) {
            Toast.makeText(c, "Couldn't reach the Android clock", Toast.LENGTH_SHORT).show();
        }
    }

    private void editAlarm(final SimStore.Alarm existing) {
        final Context c = getContext();
        final SimStore.Alarm a = existing != null ? existing : new SimStore.Alarm();
        if (existing == null) { a.id = SimStore.newId(); a.hour = 7; a.minute = 0; a.label = ""; a.on = true; }
        new TimePickerDialog(c, new TimePickerDialog.OnTimeSetListener() {
            @Override public void onTimeSet(TimePicker tp, int hh, int mm) {
                a.hour = hh; a.minute = mm;
                showPrompt("alarm name", "name (optional)", a.label, new TextResult() {
                    @Override public void on(String t) {
                        a.label = t.trim();
                        pickDays(a, existing == null);
                    }
                });
            }
        }, a.hour, a.minute, true).show();
    }

    private void pickDays(final SimStore.Alarm a, final boolean isNew) {
        showDialog("repeat", "Repeat on weekdays?", new String[]{"once", "weekdays", "every day"}, new Choice() {
            @Override public void on(int i) {
                a.days = i == 0 ? 0 : i == 1 ? 0b0111110 : 127;
                a.on = true;
                if (isNew) SimStore.alarms.add(a);
                SimStore.save();
                applySystem(a);
                fillAlarms();
            }
        });
    }

    // ------------------------------------------------------------------ reloj mundial

    private void pickCity() {
        final String[] names = new String[ZONES.length];
        for (int i = 0; i < ZONES.length; i++) names[i] = ZONES[i][0];
        showMenu(getWidth() / 2f, getHeight() / 2f, new String[]{"New York", "London", "Tokyo", "Sydney", "Los Angeles", "Dubai", "Moscow", "Beijing"}, new Choice() {
            @Override public void on(int i) {
                String[] pick = {"New York", "London", "Tokyo", "Sydney", "Los Angeles", "Dubai", "Moscow", "Beijing"};
                String s = SimStore.str("world_cities", "");
                for (String[] z : ZONES) if (z[0].equals(pick[i]) && !s.contains(z[0])) s += z[0] + ";";
                SimStore.putStr("world_cities", s);
                world.invalidate();
            }
        });
    }

    private class WorldView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        WorldView(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth();
            float cx = w / 2f, cy = Theme.dpf(120), r = Theme.dpf(88);
            Calendar now = Calendar.getInstance();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dpf(3));
            p.setColor(Theme.fg);
            c.drawCircle(cx, cy, r, p);
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30 - 90);
                p.setStrokeWidth(Theme.dpf(i % 3 == 0 ? 3 : 1.5f));
                c.drawLine((float) (cx + Math.cos(a) * (r - Theme.dpf(10))), (float) (cy + Math.sin(a) * (r - Theme.dpf(10))), (float) (cx + Math.cos(a) * (r - Theme.dpf(3))), (float) (cy + Math.sin(a) * (r - Theme.dpf(3))), p);
            }
            double hh = (now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60.0) * 30 - 90, mm = now.get(Calendar.MINUTE) * 6 - 90, ss = now.get(Calendar.SECOND) * 6 - 90;
            p.setStrokeWidth(Theme.dpf(5));
            c.drawLine(cx, cy, (float) (cx + Math.cos(Math.toRadians(hh)) * r * 0.5), (float) (cy + Math.sin(Math.toRadians(hh)) * r * 0.5), p);
            p.setStrokeWidth(Theme.dpf(3));
            c.drawLine(cx, cy, (float) (cx + Math.cos(Math.toRadians(mm)) * r * 0.75), (float) (cy + Math.sin(Math.toRadians(mm)) * r * 0.75), p);
            p.setStrokeWidth(Theme.dpf(1.5f));
            p.setColor(Theme.accent);
            c.drawLine(cx, cy, (float) (cx + Math.cos(Math.toRadians(ss)) * r * 0.85), (float) (cy + Math.sin(Math.toRadians(ss)) * r * 0.85), p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(20));
            p.setColor(Theme.fg);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(TimeZone.getDefault().getID().replace('_', ' '), cx, cy + r + Theme.dpf(34), p);
            p.setTextAlign(Paint.Align.LEFT);
            String[] cities = SimStore.str("world_cities", "London;Tokyo;New York;").split(";");
            float y = cy + r + Theme.dpf(70);
            java.text.SimpleDateFormat tf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault()), df = new java.text.SimpleDateFormat("EEE d", Locale.getDefault());
            for (String cn : cities) {
                if (cn.isEmpty()) continue;
                for (String[] z : ZONES) if (z[0].equals(cn)) {
                    TimeZone tz = TimeZone.getTimeZone(z[1]);
                    tf.setTimeZone(tz); df.setTimeZone(tz);
                    p.setColor(Theme.fg);
                    p.setTextSize(Theme.sp(24));
                    c.drawText(cn, Theme.dpf(18), y, p);
                    p.setTextAlign(Paint.Align.RIGHT);
                    c.drawText(tf.format(new Date()), w - Theme.dpf(18), y, p);
                    p.setColor(Theme.subtle);
                    p.setTextSize(Theme.sp(13));
                    c.drawText(df.format(new Date()), w - Theme.dpf(18), y + Theme.dpf(18), p);
                    p.setTextAlign(Paint.Align.LEFT);
                    y += Theme.dpf(52);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getY() > Theme.dpf(300)) {
                int idx = (int) ((e.getY() - Theme.dpf(300)) / Theme.dpf(52));
                String[] cities = SimStore.str("world_cities", "London;Tokyo;New York;").split(";");
                ArrayList<String> l = new ArrayList<>();
                for (String s : cities) if (!s.isEmpty()) l.add(s);
                if (idx >= 0 && idx < l.size()) { l.remove(idx); StringBuilder sb = new StringBuilder(); for (String s : l) sb.append(s).append(';'); SimStore.putStr("world_cities", sb.toString()); invalidate(); }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ temporizador

    private class TimerView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int total = 300; // s
        long endAt; // 0 = parado
        int left = 300;
        boolean ringing;
        Ringtone rt;

        TimerView(Context c) { super(c); }

        void startWith(int s) { total = s; left = s; start(); }

        void start() {
            if (endAt != 0) { left = (int) Math.max(0, (endAt - System.currentTimeMillis()) / 1000); endAt = 0; Reminders.cancel(getContext(), "timer"); }
            else { if (left <= 0) left = total; endAt = System.currentTimeMillis() + left * 1000L; Reminders.schedule(getContext(), "timer", endAt, "Timer", "Time's up", "alarms"); }
            invalidate();
        }

        void reset() { endAt = 0; left = total; stopRing(); Reminders.cancel(getContext(), "timer"); invalidate(); }

        void stopRing() { ringing = false; try { if (rt != null) rt.stop(); Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE); if (v != null) v.cancel(); } catch (Exception ignored) {} rt = null; }

        int remaining() { return endAt == 0 ? left : (int) Math.max(0, Math.ceil((endAt - System.currentTimeMillis()) / 1000.0)); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth();
            int r = remaining();
            if (endAt != 0 && r == 0 && !ringing) {
                ringing = true; endAt = 0; left = 0;
                try {
                    rt = RingtoneManager.getRingtone(getContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
                    if (rt != null) rt.play();
                    Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) v.vibrate(new long[]{0, 600, 400}, 0);
                } catch (Exception ignored) {}
            }
            float cx = w / 2f, cy = Theme.dpf(130), rad = Theme.dpf(100);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dpf(6));
            p.setColor(Theme.alpha(Theme.fg, 0.25f));
            c.drawCircle(cx, cy, rad, p);
            p.setColor(Theme.accent);
            float frac = total > 0 ? remaining() / (float) total : 0;
            c.drawArc(cx - rad, cy - rad, cx + rad, cy + rad, -90, 360 * frac, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Theme.light);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(Theme.sp(44));
            p.setColor(ringing ? Theme.accent : Theme.fg);
            int rr = remaining();
            c.drawText(String.format(Locale.US, "%d:%02d:%02d", rr / 3600, rr / 60 % 60, rr % 60), cx, Theme.centerBaseline(p, cy), p);
            // botones
            String[] b = {"-1m", "+1m", "-10s", "+10s"};
            p.setTextSize(Theme.sp(18));
            for (int i = 0; i < 4; i++) {
                float bx = Theme.dpf(18) + i * (w - Theme.dpf(36)) / 4f, bw = (w - Theme.dpf(36)) / 4f - Theme.dpf(6);
                p.setColor(WpUi.mix(Theme.bg, Theme.fg, 0.18f));
                c.drawRect(bx, cy + rad + Theme.dpf(24), bx + bw, cy + rad + Theme.dpf(70), p);
                p.setColor(Theme.fg);
                c.drawText(b[i], bx + bw / 2f, Theme.centerBaseline(p, cy + rad + Theme.dpf(47)), p);
            }
            p.setColor(endAt != 0 ? 0xFFC21F1F : Theme.accent);
            c.drawRect(Theme.dpf(18), cy + rad + Theme.dpf(90), w - Theme.dpf(18), cy + rad + Theme.dpf(140), p);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(Theme.sp(20));
            c.drawText(ringing ? "dismiss" : endAt != 0 ? "pause" : "start", cx, Theme.centerBaseline(p, cy + rad + Theme.dpf(115)), p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float w = getWidth(), top = Theme.dpf(130) + Theme.dpf(100);
            if (e.getY() > top + Theme.dpf(90) && e.getY() < top + Theme.dpf(140)) { if (ringing) reset(); else start(); }
            else if (e.getY() > top + Theme.dpf(24) && e.getY() < top + Theme.dpf(70) && endAt == 0) {
                int i = (int) ((e.getX() - Theme.dpf(18)) / ((w - Theme.dpf(36)) / 4f));
                int[] d = {-60, 60, -10, 10};
                if (i >= 0 && i < 4) { total = Math.max(10, Math.min(86399, total + d[i])); left = total; invalidate(); }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ cronómetro

    private class StopView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        long base, acc;
        boolean running;
        final ArrayList<Long> laps = new ArrayList<>();

        StopView(Context c) { super(c); }

        long elapsed() { return acc + (running ? System.currentTimeMillis() - base : 0); }

        void lap() { if (running) laps.add(0, elapsed()); invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth();
            long t = elapsed();
            p.setTypeface(Theme.light);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Theme.fg);
            p.setTextSize(Theme.sp(60));
            c.drawText(String.format(Locale.US, "%02d:%02d.%02d", t / 60000, t / 1000 % 60, t / 10 % 100), w / 2f, Theme.dpf(80), p);
            p.setTextSize(Theme.sp(20));
            for (int i = 0; i < 2; i++) {
                float bx = Theme.dpf(18) + i * (w - Theme.dpf(36)) / 2f, bw = (w - Theme.dpf(36)) / 2f - Theme.dpf(6);
                p.setColor(i == 0 ? (running ? 0xFFC21F1F : Theme.accent) : WpUi.mix(Theme.bg, Theme.fg, 0.18f));
                c.drawRect(bx, Theme.dpf(110), bx + bw, Theme.dpf(160), p);
                p.setColor(i == 0 ? 0xFFFFFFFF : Theme.fg);
                c.drawText(i == 0 ? (running ? "stop" : "start") : (running ? "lap" : "reset"), bx + bw / 2f, Theme.centerBaseline(p, Theme.dpf(135)), p);
            }
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(Theme.sp(18));
            for (int i = 0; i < laps.size() && i < 12; i++) {
                long lt = laps.get(i), prev = i + 1 < laps.size() ? laps.get(i + 1) : 0, d = lt - prev;
                p.setColor(Theme.subtle);
                c.drawText("lap " + (laps.size() - i), Theme.dpf(18), Theme.dpf(200) + i * Theme.dpf(32), p);
                p.setColor(Theme.fg);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.format(Locale.US, "%02d:%02d.%02d", d / 60000, d / 1000 % 60, d / 10 % 100), w - Theme.dpf(18), Theme.dpf(200) + i * Theme.dpf(32), p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP || e.getY() < Theme.dpf(110) || e.getY() > Theme.dpf(160)) return true;
            if (e.getX() < getWidth() / 2f) {
                if (running) { acc = elapsed(); running = false; } else { base = System.currentTimeMillis(); running = true; }
            } else if (running) lap(); else { acc = 0; laps.clear(); }
            invalidate();
            return true;
        }
    }
}
