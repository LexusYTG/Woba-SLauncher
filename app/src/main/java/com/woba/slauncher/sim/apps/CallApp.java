package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import com.woba.slauncher.sim.Phone;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

/** Pantalla de llamada entrante / saliente / activa simulada (con tono, vibración y registro). */
public class CallApp extends SimApp {
    private final Screen screen;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Ringtone ring;
    private String name = "", number = "";
    private int state; // 0 entrante, 1 marcando, 2 activa, 3 terminada
    private int secs;
    private boolean mute, speaker, hold, keypad;
    private String dtmf = "";
    private boolean incoming;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (state == 2 && !hold) secs++;
            screen.invalidate();
            h.postDelayed(this, 1000);
        }
    };
    private final Runnable connect = new Runnable() {
        @Override public void run() { if (state == 1) { state = 2; secs = 0; screen.invalidate(); } }
    };
    private final Runnable missed = new Runnable() {
        @Override public void run() { if (state == 0) ignore(true); }
    };

    public CallApp(Context c, SimHost host) {
        super(c, host, "call");
        screen = new Screen(c);
        setContent(screen, null);
    }

    @Override public String appTitle() { return "phone"; }

    @Override public void onOpen(String arg) {
        String[] p = (arg == null ? "out||" : arg).split("\\|", -1);
        incoming = p[0].equals("in");
        name = p.length > 1 ? p[1] : "";
        number = p.length > 2 ? p[2] : "";
        if (name.isEmpty()) name = SimStore.nameFor(number);
        secs = 0;
        h.removeCallbacksAndMessages(null);
        h.post(tick);
        if (incoming) {
            state = 0;
            startRing();
            h.postDelayed(missed, 30000);
        } else {
            state = 1;
            h.postDelayed(connect, 2500);
        }
        screen.invalidate();
    }

    private void startRing() {
        try {
            ring = RingtoneManager.getRingtone(getContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ring != null) {
                if (android.os.Build.VERSION.SDK_INT >= 28) ring.setLooping(true);
                ring.play();
            }
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(new long[]{0, 800, 800}, 0);
        } catch (Exception ignored) {
        }
    }

    private void stopRing() {
        try {
            if (ring != null) ring.stop();
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.cancel();
        } catch (Exception ignored) {
        }
        ring = null;
    }

    private void answer() {
        if (state != 0) return;
        stopRing();
        h.removeCallbacks(missed);
        state = 2;
        secs = 0;
        screen.invalidate();
    }

    private void ignore(boolean timeout) {
        stopRing();
        h.removeCallbacks(missed);
        SimStore.addCall(name.equals(number) ? "" : name, number, System.currentTimeMillis(), 3, 0);
        SimStore.save();
        SimStore.fire("calls");
        host.postNotification("phone", "Missed call", name.isEmpty() ? number : name, "phone", null);
        finish();
    }

    private void end() {
        stopRing();
        if (state == 2 || state == 1) {
            SimStore.addCall(name.equals(number) ? "" : name, number, System.currentTimeMillis(), incoming ? 1 : 2, state == 2 ? secs : 0);
            SimStore.save();
            SimStore.fire("calls");
        }
        state = 3;
        screen.invalidate();
        h.postDelayed(new Runnable() { @Override public void run() { finish(); } }, 500);
    }

    private void finish() {
        h.removeCallbacksAndMessages(null);
        close();
    }

    @Override public void onDestroy() {
        stopRing();
        h.removeCallbacksAndMessages(null);
    }

    @Override public boolean onBack() {
        if (keypad) { keypad = false; screen.invalidate(); return true; }
        if (state == 0) { ignore(false); return true; }
        end();
        return true;
    }

    private class Screen extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int down = -1;
        float sliderX; // para deslizar a contestar

        Screen(Context c) { super(c); }

        String[] labels() {
            if (state == 0) return new String[]{"answer", "ignore"};
            return new String[]{"mute", "speaker", "keypad", "hold"};
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.bg);
            c.drawRect(0, 0, w, h, p);
            p.setTypeface(Theme.semibold);
            p.setTextSize(Theme.sp(13));
            p.setColor(Theme.fg);
            c.drawText("PHONE", Theme.dpf(18), Theme.dpf(30), p);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(40));
            String nm = name.isEmpty() ? "unknown" : name;
            while (p.measureText(nm) > w - Theme.dpf(36) && p.getTextSize() > Theme.sp(20)) p.setTextSize(p.getTextSize() - 2);
            c.drawText(nm, Theme.dpf(18), Theme.dpf(100), p);
            p.setTextSize(Theme.sp(18));
            p.setColor(Theme.subtle);
            if (!number.equals(nm)) c.drawText(number, Theme.dpf(18), Theme.dpf(128), p);
            String st = state == 0 ? "incoming call" : state == 1 ? "calling…" : state == 3 ? "call ended" : hold ? "on hold" : Phone.duration(secs);
            p.setColor(Theme.accent);
            p.setTextSize(Theme.sp(22));
            c.drawText(st, Theme.dpf(18), Theme.dpf(160), p);
            // avatar grande
            float av = Math.min(w * 0.55f, Theme.dpf(200));
            SimStore.Contact k = SimStore.byNumber(number);
            WpUi.drawAvatar(c, (w - av) / 2f, Theme.dpf(190), av, k != null ? k.initials() : "?", k == null ? Glyph.PERSON : 0, WpUi.colorFor(nm), null);
            if (keypad) { drawKeypad(c, w, h); return; }
            String[] ls = labels();
            if (state == 0) {
                for (int i = 0; i < 2; i++) {
                    float x = i == 0 ? Theme.dpf(18) : w / 2f + Theme.dpf(4), bw = w / 2f - Theme.dpf(22);
                    p.setColor(i == 0 ? 0xFF2E8B2E : 0xFFC21F1F);
                    if (down == i) p.setColor(Theme.fg);
                    c.drawRect(x, h - Theme.dpf(120), x + bw, h - Theme.dpf(56), p);
                    p.setColor(0xFFFFFFFF);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(Theme.sp(20));
                    c.drawText(ls[i], x + bw / 2f, Theme.centerBaseline(p, h - Theme.dpf(88)), p);
                    p.setTextAlign(Paint.Align.LEFT);
                }
                p.setColor(Theme.subtle);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(Theme.sp(14));
                c.drawText("simulated call", w / 2f, h - Theme.dpf(20), p);
                p.setTextAlign(Paint.Align.LEFT);
                return;
            }
            // 4 botones circulares + colgar
            int[] gl = {mute ? Glyph.MUTE : Glyph.MIC, speaker ? Glyph.SPEAKER : Glyph.SPEAKER_OFF, Glyph.KEYPAD, Glyph.HOLD};
            boolean[] on = {mute, speaker, false, hold};
            float r = Theme.dpf(30), gy = h - Theme.dpf(170);
            for (int i = 0; i < 4; i++) {
                float cx = w / 8f + i * w / 4f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Theme.dpf(2));
                p.setColor(Theme.fg);
                c.drawCircle(cx, gy, r, p);
                if (on[i] || down == i) { p.setStyle(Paint.Style.FILL); p.setColor(Theme.accent); c.drawCircle(cx, gy, r - Theme.dpf(1), p); }
                Glyph.draw(c, gl[i], cx, gy, r * 0.9f, Theme.fg);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.subtle);
                p.setTextSize(Theme.sp(12));
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(ls[i], cx, gy + r + Theme.dpf(16), p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(down == 9 ? Theme.fg : 0xFFC21F1F);
            c.drawRect(Theme.dpf(18), h - Theme.dpf(88), w - Theme.dpf(18), h - Theme.dpf(32), p);
            Glyph.draw(c, Glyph.ENDCALL, w / 2f, h - Theme.dpf(60), Theme.dpf(28), 0xFFFFFFFF);
        }

        void drawKeypad(Canvas c, float w, float h) {
            p.setColor(Theme.fg);
            p.setTextSize(Theme.sp(26));
            c.drawText(dtmf, Theme.dpf(18), Theme.dpf(30) + Theme.dpf(24), p);
            String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
            float kw = w / 3f, kh = Theme.dpf(54), y0 = h - Theme.dpf(88) - 4 * kh - Theme.dpf(10);
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < 12; i++) {
                float x = (i % 3) * kw, y = y0 + (i / 3) * kh;
                p.setColor(down == 20 + i ? Theme.accent : WpUi.mix(Theme.bg, Theme.fg, 0.14f));
                c.drawRect(x + 2, y + 2, x + kw - 2, y + kh - 2, p);
                p.setColor(Theme.fg);
                p.setTextSize(Theme.sp(26));
                c.drawText(keys[i], x + kw / 2f, Theme.centerBaseline(p, y + kh / 2f), p);
            }
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(down == 9 ? Theme.fg : 0xFFC21F1F);
            c.drawRect(Theme.dpf(18), h - Theme.dpf(88), w - Theme.dpf(18), h - Theme.dpf(32), p);
            Glyph.draw(c, Glyph.ENDCALL, w / 2f, h - Theme.dpf(60), Theme.dpf(28), 0xFFFFFFFF);
        }

        int hit(float x, float y) {
            float w = getWidth(), h = getHeight();
            if (keypad) {
                if (y > h - Theme.dpf(88) && y < h - Theme.dpf(32)) return 9;
                float kw = w / 3f, kh = Theme.dpf(54), y0 = h - Theme.dpf(88) - 4 * kh - Theme.dpf(10);
                int col = (int) (x / kw), row = (int) ((y - y0) / kh);
                if (row >= 0 && row < 4 && col >= 0 && col < 3) return 20 + row * 3 + col;
                return -1;
            }
            if (state == 0) {
                if (y > h - Theme.dpf(120) && y < h - Theme.dpf(56)) return x < w / 2f ? 0 : 1;
                return -1;
            }
            if (y > h - Theme.dpf(88) && y < h - Theme.dpf(32)) return 9;
            float gy = h - Theme.dpf(170);
            if (Math.abs(y - gy) < Theme.dpf(50)) return Math.min(3, (int) (x / (w / 4f)));
            return -1;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: down = hit(e.getX(), e.getY()); invalidate(); return true;
                case MotionEvent.ACTION_UP: {
                    int k = hit(e.getX(), e.getY());
                    int d = down;
                    down = -1;
                    invalidate();
                    if (k != d || k < 0) return true;
                    if (k >= 20) {
                        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
                        dtmf += keys[k - 20];
                        try {
                            android.media.ToneGenerator tg = new android.media.ToneGenerator(AudioManager.STREAM_DTMF, 80);
                            tg.startTone(android.media.ToneGenerator.TONE_DTMF_0 + (k - 20 == 10 ? 0 : k - 20 == 9 ? 10 : k - 20 == 11 ? 11 : k - 19), 120);
                        } catch (Exception ignored) {
                        }
                        return true;
                    }
                    if (state == 0) { if (k == 0) answer(); else if (k == 1) ignore(false); return true; }
                    if (k == 9) end();
                    else if (k == 0) {
                        mute = !mute;
                        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) try { am.setMicrophoneMute(mute); } catch (Exception ignored) {}
                    } else if (k == 1) speaker = !speaker;
                    else if (k == 2) keypad = true;
                    else if (k == 3) hold = !hold;
                    invalidate();
                    return true;
                }
                default: down = -1; invalidate(); return true;
            }
        }
    }
}
