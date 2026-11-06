package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.view.MotionEvent;
import android.view.View;

import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.util.Random;

/** Radio FM simulada (no hay receptor FM en Android): dial, presets, ruido entre emisoras y melodías sintetizadas. */
public class RadioApp extends SimApp {
    private static final String[] NAMES = {"Wave FM", "Metro Hits", "Lumia Radio", "Nokia Classic", "Cortana 24h", "Redmond Jazz", "Silent Night"};
    private static final float[] FREQ = {88.6f, 91.3f, 94.7f, 98.1f, 101.5f, 104.2f, 107.0f};
    private static final int[][] MEL = {
            {0, 4, 7, 12, 7, 4}, {0, 3, 7, 10, 7, 3}, {0, 2, 4, 7, 4, 2}, {0, 5, 9, 12, 9, 5}, {0, 4, 5, 7, 5, 4}, {0, 3, 5, 7, 10, 7}, {0, 7, 4, 7, 0, 7}
    };
    private final Dial dial;
    private float freq = Float.parseFloat(SimStore.str("radio_freq", "94.7"));
    private boolean on;
    private Thread th;
    private volatile boolean run;
    private volatile float curFreq;
    private float[] presets = {88.6f, 94.7f, 101.5f};

    public RadioApp(Context c, SimHost host) {
        super(c, host, "radio");
        dial = new Dial(c);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.PLAY, "power", new Runnable() { @Override public void run() { setOn(!on); } }),
                new AppBar.Item(Glyph.PREV, "seek -", new Runnable() { @Override public void run() { seek(-1); } }),
                new AppBar.Item(Glyph.NEXT, "seek +", new Runnable() { @Override public void run() { seek(1); } }),
                new AppBar.Item(Glyph.STAR, "preset", new Runnable() { @Override public void run() { presets[0] = presets[1]; presets[1] = presets[2]; presets[2] = freq; dial.invalidate(); } })
        );
        setContent(dial, ab);
    }

    @Override public String appTitle() { return "fm radio"; }

    @Override public void onDestroy() { setOn(false); }

    @Override public void onHide() { }

    private void seek(int dir) {
        int best = -1;
        float bd = 99;
        for (int i = 0; i < FREQ.length; i++) {
            float d = (FREQ[i] - freq) * dir;
            if (d > 0.05f && d < bd) { bd = d; best = i; }
        }
        freq = best >= 0 ? FREQ[best] : (dir > 0 ? FREQ[0] : FREQ[FREQ.length - 1]);
        tune();
    }

    private void tune() {
        freq = Math.max(87.5f, Math.min(108f, Math.round(freq * 10) / 10f));
        curFreq = freq;
        SimStore.putStr("radio_freq", String.valueOf(freq));
        dial.invalidate();
    }

    private int stationIndex(float f) {
        for (int i = 0; i < FREQ.length; i++) if (Math.abs(FREQ[i] - f) < 0.25f) return i;
        return -1;
    }

    private void setOn(boolean v) {
        if (v == on) return;
        on = v;
        curFreq = freq;
        if (on) startAudio(); else { run = false; }
        dial.invalidate();
    }

    private void startAudio() {
        run = true;
        th = new Thread(new Runnable() {
            @Override public void run() {
                final int sr = 22050;
                int buf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioTrack at = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(Math.max(buf, 8192)).build();
                at.play();
                Random rnd = new Random();
                short[] b = new short[1024];
                double ph = 0, ph2 = 0;
                long n = 0;
                while (run) {
                    float f = curFreq;
                    int st = stationIndex(f);
                    double clarity = st < 0 ? 0 : 1 - Math.min(1, Math.abs(FREQ[st] - f) / 0.25);
                    for (int i = 0; i < b.length; i++, n++) {
                        double noise = (rnd.nextDouble() * 2 - 1) * (0.22 * (1 - clarity) + 0.01);
                        double s = 0;
                        if (st >= 0) {
                            int note = MEL[st][(int) ((n / (sr / 3)) % MEL[st].length)];
                            double fr = 220 * Math.pow(2, note / 12.0);
                            ph += 2 * Math.PI * fr / sr;
                            ph2 += 2 * Math.PI * fr * 2 / sr;
                            s = (Math.sin(ph) * 0.5 + Math.sin(ph2) * 0.15) * 0.45 * clarity;
                        }
                        b[i] = (short) ((s + noise) * 32000);
                    }
                    at.write(b, 0, b.length);
                }
                at.stop();
                at.release();
            }
        });
        th.start();
    }

    private class Dial extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean drag;

        Dial(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setTypeface(Theme.semibold);
            p.setTextSize(Theme.sp(13));
            p.setColor(Theme.fg);
            c.drawText("FM RADIO", Theme.dpf(18), Theme.dpf(30), p);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(80));
            c.drawText(String.format(java.util.Locale.US, "%.1f", freq), Theme.dpf(18), Theme.dpf(130), p);
            p.setTextSize(Theme.sp(22));
            p.setColor(Theme.accent);
            int st = stationIndex(freq);
            c.drawText(st >= 0 ? NAMES[st] : (on ? "searching…" : "off"), Theme.dpf(18), Theme.dpf(168), p);
            p.setColor(Theme.subtle);
            p.setTextSize(Theme.sp(13));
            c.drawText("simulated: Android phones have no FM receiver API", Theme.dpf(18), Theme.dpf(192), p);
            // regla
            float y = Theme.dpf(280);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dpf(1));
            for (int i = 0; i <= 205; i++) {
                float f = 87.5f + i * 0.1f;
                float x = w / 2f + (f - freq) * Theme.dpf(40);
                if (x < 0 || x > w) continue;
                boolean major = i % 10 == 0;
                p.setColor(Theme.alpha(Theme.fg, major ? 0.9f : 0.4f));
                c.drawLine(x, y, x, y + Theme.dpf(major ? 36 : 18), p);
                if (major) {
                    p.setStyle(Paint.Style.FILL);
                    p.setTextSize(Theme.sp(12));
                    p.setTextAlign(Paint.Align.CENTER);
                    c.drawText(String.valueOf(Math.round(f)), x, y + Theme.dpf(54), p);
                    p.setTextAlign(Paint.Align.LEFT);
                    p.setStyle(Paint.Style.STROKE);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.accent);
            c.drawRect(w / 2f - Theme.dpf(1.5f), y - Theme.dpf(14), w / 2f + Theme.dpf(1.5f), y + Theme.dpf(40), p);
            // presets
            p.setTextSize(Theme.sp(13));
            p.setColor(Theme.accent);
            p.setTypeface(Theme.semibold);
            c.drawText("presets", Theme.dpf(18), y + Theme.dpf(100), p);
            for (int i = 0; i < 3; i++) {
                float bx = Theme.dpf(18) + i * (w - Theme.dpf(36)) / 3f, bw = (w - Theme.dpf(36)) / 3f - Theme.dpf(8);
                p.setColor(WpUi.mix(Theme.bg, Theme.fg, 0.18f));
                c.drawRect(bx, y + Theme.dpf(112), bx + bw, y + Theme.dpf(170), p);
                p.setColor(Theme.fg);
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp(24));
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.format(java.util.Locale.US, "%.1f", presets[i]), bx + bw / 2f, Theme.centerBaseline(p, y + Theme.dpf(141)), p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            p.setColor(on ? Theme.accent : Theme.subtle);
            p.setTextSize(Theme.sp(16));
            c.drawText(on ? "● on air" : "○ off", Theme.dpf(18), h - Theme.dpf(80), p);
        }

        float lastX, startFreq;

        @Override public boolean onTouchEvent(MotionEvent e) {
            float y = Theme.dpf(280);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    startFreq = freq;
                    drag = e.getY() > y - Theme.dpf(30) && e.getY() < y + Theme.dpf(70);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (drag) { freq = startFreq - (e.getX() - lastX) / Theme.dpf(40); tune(); }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!drag && e.getY() > y + Theme.dpf(112) && e.getY() < y + Theme.dpf(170)) {
                        int i = (int) ((e.getX() - Theme.dpf(18)) / ((getWidth() - Theme.dpf(36)) / 3f));
                        if (i >= 0 && i < 3) { freq = presets[i]; tune(); if (!on) setOn(true); }
                    }
                    drag = false;
                    return true;
                default: return true;
            }
        }
    }
}
