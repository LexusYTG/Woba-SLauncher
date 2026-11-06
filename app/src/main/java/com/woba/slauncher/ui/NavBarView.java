package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

/** Barra de navegación en pantalla de WP8.1: atrás, Windows (inicio) y buscar. Pulsación larga en atrás = recientes. */
public class NavBarView extends View {
    public interface Listener {
        void onBack();
        void onBackLong();
        void onHome();
        void onHomeLong();
        void onSearch();
    }

    public static int heightDp() { return 48; }

    private final Listener l;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int down = -1;
    private boolean longFired;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable longRun = new Runnable() {
        @Override public void run() {
            longFired = true;
            if (down == 0) l.onBackLong();
            else if (down == 1) l.onHomeLong();
        }
    };

    public NavBarView(Context c, Listener l) {
        super(c);
        this.l = l;
        setClickable(true);
    }

    private int slot(float x) {
        int s = (int) (x / (getWidth() / 3f));
        return Math.max(0, Math.min(2, s));
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), hh = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.bg == 0xFF000000 ? 0xFF000000 : 0xFFE6E6E6);
        c.drawRect(0, 0, w, hh, p);
        float cy = hh / 2f, sz = Theme.dpf(22);
        for (int i = 0; i < 3; i++) {
            float cx = w / 6f + i * w / 3f;
            int col = down == i ? Theme.alpha(Theme.fg, 0.5f) : Theme.fg;
            int g = i == 0 ? Glyph.LEFT : i == 1 ? Glyph.WINDOWS : Glyph.SEARCH;
            Glyph.draw(c, g, cx, cy, i == 1 ? sz * 0.9f : sz, col, Theme.dpf(1.8f));
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = slot(e.getX());
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                playSoundEffect(SoundEffectConstants.CLICK);
                longFired = false;
                h.postDelayed(longRun, 550);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                h.removeCallbacks(longRun);
                int d = down;
                down = -1;
                invalidate();
                if (!longFired && slot(e.getX()) == d) {
                    if (d == 0) l.onBack();
                    else if (d == 1) l.onHome();
                    else l.onSearch();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                h.removeCallbacks(longRun);
                down = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
