package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.woba.slauncher.data.Live;
import com.woba.slauncher.data.Notif;
import com.woba.slauncher.data.Sys;
import com.woba.slauncher.data.Wallpaper;

/** Pantalla de bloqueo WP: hora grande abajo a la izquierda; se desliza hacia arriba para entrar. */
public class LockView extends View {
    private final Live live;
    private final Runnable onUnlock;
    private final Paint bg = new Paint();
    private final Paint wpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient grad;
    private int gradAccent, gradH;
    private float startRawY, startTrans;
    private boolean unlocking;

    public LockView(Context ctx, Live live, Runnable onUnlock) {
        super(ctx);
        this.live = live;
        this.onUnlock = onUnlock;
        setClickable(true);
    }

    private void txt(float sp, int color) {
        tp.setTypeface(Theme.light);
        tp.setTextSize(Theme.sp(sp));
        tp.setColor(color);
        tp.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (grad == null || gradAccent != Theme.accent || gradH != h) {
            grad = new LinearGradient(0, 0, 0, h,
                    new int[]{Color.BLACK, Color.BLACK, Theme.alpha(Theme.accent, 0.55f)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            gradAccent = Theme.accent;
            gradH = h;
        }
        Bitmap wp = Wallpaper.lock;
        if (wp != null) {
            Wallpaper.drawCrop(c, wp, w, h, wpPaint);
            bg.setShader(null);
            bg.setColor(0x55000000);
        } else {
            bg.setShader(grad);
        }
        c.drawRect(0, 0, w, h, bg);

        if (Theme.lockStatus) {
        // Línea de estado arriba a la izquierda.
        float x = Theme.dpf(24), cy = Theme.dpf(40) + Theme.sp(16) / 2f;
        txt(16, Color.WHITE);
        String bat = live.battery + "%";
        c.drawText(bat, x, Theme.centerBaseline(tp, cy), tp);
        x += tp.measureText(bat) + Theme.dpf(8);
        float is = Theme.sp(16);
        if (live.charging) {
            Icons.draw(c, Icons.BOLT, x + is / 2f, cy, is, Color.WHITE);
            x += is + Theme.dpf(12);
        } else {
            x += Theme.dpf(12);
        }
        if (live.missed > 0) {
            Icons.draw(c, Icons.PHONE, x + is / 2f, cy, is, Color.WHITE);
            x += is + Theme.dpf(6);
            String s = String.valueOf(live.missed);
            c.drawText(s, x, Theme.centerBaseline(tp, cy), tp);
            x += tp.measureText(s) + Theme.dpf(16);
        }
        if (live.sms > 0) {
            Icons.draw(c, Icons.MESSAGE, x + is / 2f, cy, is, Color.WHITE);
            x += is + Theme.dpf(6);
            c.drawText(String.valueOf(live.sms), x, Theme.centerBaseline(tp, cy), tp);
        }

        }

        if (Theme.lockStatus) drawNotifs(c, w);

        // Hora, fecha y texto de desbloqueo, apilados desde abajo.
        float left = Theme.dpf(24);
        float y = h - Theme.dpf(64);
        txt(14, Theme.alpha(Color.WHITE, 0.6f));
        c.drawText("swipe up to unlock", left, y - tp.getFontMetrics().descent, tp);
        y -= tp.getFontMetrics().descent - tp.getFontMetrics().ascent + Theme.dpf(24);

        txt(22, Color.WHITE);
        c.drawText(Sys.dateText(live.now), left, y - tp.getFontMetrics().descent, tp);
        y -= tp.getFontMetrics().descent - tp.getFontMetrics().ascent;

        txt(96, Color.WHITE);
        c.drawText(Sys.timeText(getContext(), live.now), left, y - tp.getFontMetrics().descent, tp);
    }

    private final Notif.Listener notifL = new Notif.Listener() {
        @Override public void onNotifsChanged() { invalidate(); }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Notif.addListener(notifL);
    }

    @Override protected void onDetachedFromWindow() {
        Notif.removeListener(notifL);
        super.onDetachedFromWindow();
    }

    /** Hasta 3 notificaciones (título y texto) bajo la línea de estado, como el estado detallado de W10M. */
    private void drawNotifs(Canvas c, int w) {
        float left = Theme.dpf(24), maxW = w - 2 * left;
        float y = Theme.dpf(40) + Theme.sp(16) + Theme.dpf(32);
        int shown = 0;
        for (Notif n : Notif.all()) {
            if (shown >= 3) break;
            String t = n.title == null ? "" : n.title;
            String x = n.text == null ? "" : n.text;
            if (t.isEmpty() && x.isEmpty()) continue;
            txt(15, Color.WHITE);
            c.drawText(TextUtils.ellipsize(t, tp, maxW, TextUtils.TruncateAt.END).toString(), left, y, tp);
            y += Theme.dpf(20);
            txt(13, Theme.alpha(Color.WHITE, 0.65f));
            c.drawText(TextUtils.ellipsize(x, tp, maxW, TextUtils.TruncateAt.END).toString(), left, y, tp);
            y += Theme.dpf(30);
            shown++;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (unlocking) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startRawY = e.getRawY();
                startTrans = getTranslationY();
                animate().cancel();
                return true;
            case MotionEvent.ACTION_MOVE:
                setTranslationY(Math.min(0f, startTrans + e.getRawY() - startRawY));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getTranslationY() < -getHeight() * 0.25f) {
                    unlocking = true;
                    animate().translationY(-getHeight()).setDuration(180)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    onUnlock.run();
                                }
                            }).start();
                } else {
                    animate().translationY(0f).setDuration(260)
                            .setInterpolator(new OvershootInterpolator(1.2f)).start();
                }
                return true;
            default:
                return true;
        }
    }
}
