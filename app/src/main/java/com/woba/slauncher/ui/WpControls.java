package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Controles dibujados a mano con el aspecto de Windows Phone 8. */
public final class WpControls {
    private WpControls() {}

    /** Interruptor: contorno vacío con pulgar a la izquierda (off); relleno de acento con pulgar a la derecha (on). */
    public static class Switch extends View {
        public interface OnChange { void onChange(boolean on); }

        private boolean on;
        private OnChange cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Switch(Context c, boolean on, OnChange cb) {
            super(c);
            this.on = on;
            this.cb = cb;
            setClickable(true);
            setOnClickListener(v -> {
                this.on = !this.on;
                invalidate();
                if (this.cb != null) this.cb.onChange(this.on);
            });
        }

        public boolean isOn() { return on; }

        public void setOn(boolean v) {
            on = v;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Theme.dp(54), Theme.dp(26));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Theme.dpf(2);
            p.setStyle(Paint.Style.FILL);
            if (on) {
                p.setColor(Theme.accent);
                c.drawRect(0, 0, w, h, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            p.setColor(on ? Theme.accent : Theme.fg);
            c.drawRect(s / 2, s / 2, w - s / 2, h - s / 2, p);
            float tw = Theme.dpf(12);
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0xFFFFFFFF : Theme.fg);
            float x = on ? w - tw - s - Theme.dpf(2) : s + Theme.dpf(2);
            c.drawRect(x, s + Theme.dpf(2), x + tw, h - s - Theme.dpf(2), p);
        }
    }

    /** Deslizador: pista fina, tramo de acento y pulgar rectangular. */
    public static class Slider extends View {
        public interface OnValue { void onValue(float v, boolean done); }

        private float value;
        private boolean active = true, dragging;
        private final OnValue cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Slider(Context c, float value, boolean active, OnValue cb) {
            super(c);
            this.value = Math.max(0f, Math.min(1f, value));
            this.active = active;
            this.cb = cb;
        }

        public void setValue(float v) {
            value = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(44));
        }

        private float fromX(float x) {
            float pad = Theme.dpf(7);
            return Math.max(0f, Math.min(1f, (x - pad) / Math.max(1f, getWidth() - 2 * pad)));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!active) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    value = fromX(e.getX());
                    invalidate();
                    if (cb != null) cb.onValue(value, false);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        value = fromX(e.getX());
                        invalidate();
                        if (cb != null) cb.onValue(value, false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        dragging = false;
                        if (cb != null) cb.onValue(value, true);
                    }
                    return true;
                default:
                    return true;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), cy = getHeight() / 2f, pad = Theme.dpf(7);
            float x = pad + value * (w - 2 * pad);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(Theme.fg, active ? 0.35f : 0.15f));
            c.drawRect(pad, cy - Theme.dpf(1.5f), w - pad, cy + Theme.dpf(1.5f), p);
            p.setColor(active ? Theme.accent : Theme.alpha(Theme.fg, 0.3f));
            c.drawRect(pad, cy - Theme.dpf(3), x, cy + Theme.dpf(3), p);
            p.setColor(active ? Theme.fg : Theme.alpha(Theme.fg, 0.3f));
            c.drawRect(x - Theme.dpf(6), cy - Theme.dpf(14), x + Theme.dpf(6), cy + Theme.dpf(14), p);
        }
    }

    /** Barra de uso (batería, almacenamiento, memoria). */
    public static class Bar extends View {
        private final float frac;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Bar(Context c, float frac) {
            super(c);
            this.frac = Math.max(0f, Math.min(1f, frac));
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(14));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(Theme.fg, 0.25f));
            c.drawRect(0, 0, w, h, p);
            p.setColor(Theme.accent);
            c.drawRect(0, 0, w * frac, h, p);
        }
    }
}
