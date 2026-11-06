package com.woba.slauncher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/** Barra de aplicación inferior de WP8.1: iconos circulares + "..." que despliega etiquetas y menú. */
public class AppBar extends View {
    public static class Item {
        public int glyph;
        public String label;
        public Runnable action;
        public boolean enabled = true;

        public Item(int glyph, String label, Runnable action) {
            this.glyph = glyph;
            this.label = label;
            this.action = action;
        }
    }

    public interface OnExpand { void onExpand(boolean open); }

    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Item> menu = new ArrayList<>();
    private float frac;
    private boolean open;
    private ValueAnimator anim;
    private OnExpand onExpand;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int pressed = -2;

    public AppBar(Context c) {
        super(c);
        setClickable(true);
    }

    public AppBar items(Item... it) {
        items.clear();
        for (Item i : it) items.add(i);
        requestLayout();
        invalidate();
        return this;
    }

    public AppBar menu(Item... it) {
        menu.clear();
        for (Item i : it) menu.add(i);
        requestLayout();
        invalidate();
        return this;
    }

    public void setOnExpand(OnExpand l) { onExpand = l; }

    public boolean isOpen() { return open; }

    public void setOpen(boolean o) {
        if (o == open) return;
        if (o && menu.isEmpty() && items.isEmpty()) return;
        open = o;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(frac, o ? 1f : 0f);
        anim.setDuration(180);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                frac = (Float) a.getAnimatedValue();
                requestLayout();
                invalidate();
            }
        });
        anim.start();
        if (onExpand != null) onExpand.onExpand(o);
    }

    public void toggle() { setOpen(!open); }

    private float baseH() { return Theme.dpf(56); }

    private float labelH() { return Theme.dpf(22); }

    private float itemH() { return Theme.dpf(46); }

    private float menuH() { return menu.size() * itemH() + (menu.isEmpty() ? 0 : Theme.dpf(10)); }

    @Override protected void onMeasure(int w, int h) {
        float total = baseH() + (items.isEmpty() ? 0 : labelH() * frac) + menuH() * frac;
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(total));
    }

    private float slotX(int i) {
        float usable = getWidth() - Theme.dpf(52);
        return usable / Math.max(1, items.size()) * (i + 0.5f);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(WpUi.barBg());
        c.drawRect(0, 0, w, h, p);
        float rowTop = h - baseH() - (items.isEmpty() ? 0 : labelH() * frac);
        float cy = h - baseH() / 2f - (items.isEmpty() ? 0 : labelH() * frac) / 2f;
        // menú
        if (frac > 0.02f) {
            tp.setTypeface(Theme.light);
            tp.setTextSize(Theme.sp(19));
            for (int i = 0; i < menu.size(); i++) {
                Item it = menu.get(i);
                tp.setColor(Theme.alpha(it.enabled ? Theme.fg : Theme.subtle, frac));
                float y = rowTop - menuH() * 0 - (menu.size() - i) * itemH() * frac + itemH() * frac / 2f;
                y = Theme.dpf(5) * frac + i * itemH() * frac + itemH() * frac / 2f;
                if (pressed == 1000 + i) {
                    p.setColor(Theme.alpha(Theme.fg, 0.12f));
                    c.drawRect(0, y - itemH() * frac / 2f, w, y + itemH() * frac / 2f, p);
                }
                c.drawText(it.label.toLowerCase(), Theme.dpf(18), Theme.centerBaseline(tp, y), tp);
            }
        }
        // iconos
        float r = Theme.dpf(19);
        float iy = rowTop + baseH() / 2f;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float x = slotX(i);
            int col = Theme.alpha(Theme.fg, it.enabled ? (pressed == i ? 0.5f : 1f) : 0.3f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dpf(2));
            p.setColor(col);
            c.drawCircle(x, iy, r, p);
            Glyph.draw(c, it.glyph, x, iy, r * 1.15f, col);
            if (frac > 0.02f && it.label != null) {
                tp.setTypeface(Theme.regular);
                tp.setTextSize(Theme.sp(11));
                tp.setColor(Theme.alpha(col, frac));
                tp.setTextAlign(Paint.Align.CENTER);
                String s = TextUtils.ellipsize(it.label.toLowerCase(), tp, Theme.dpf(72), TextUtils.TruncateAt.END).toString();
                c.drawText(s, x, iy + r + Theme.dpf(13), tp);
                tp.setTextAlign(Paint.Align.LEFT);
            }
        }
        if (!items.isEmpty() || !menu.isEmpty()) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(Theme.fg, pressed == -1 ? 0.5f : 1f));
            float dx = w - Theme.dpf(26), dy = iy;
            for (int i = 0; i < 3; i++) c.drawCircle(dx - Theme.dpf(10) + i * Theme.dpf(10), dy + Theme.dpf(2), Theme.dpf(2), p);
        }
    }

    private int hit(float x, float y) {
        float h = getHeight();
        float rowTop = h - baseH() - (items.isEmpty() ? 0 : labelH() * frac);
        if (y >= rowTop) {
            if (x > getWidth() - Theme.dpf(52)) return -1;
            for (int i = 0; i < items.size(); i++) if (Math.abs(x - slotX(i)) < Theme.dpf(34)) return i;
            return -3;
        }
        if (open && !menu.isEmpty()) {
            int idx = (int) ((y - Theme.dpf(5) * frac) / (itemH() * frac));
            if (idx >= 0 && idx < menu.size()) return 1000 + idx;
        }
        return -3;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = hit(e.getX(), e.getY());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP: {
                int h = hit(e.getX(), e.getY());
                int pr = pressed;
                pressed = -2;
                invalidate();
                if (h != pr) return true;
                if (h == -1) toggle();
                else if (h >= 1000) {
                    Item it = menu.get(h - 1000);
                    if (it.enabled && it.action != null) {
                        setOpen(false);
                        it.action.run();
                    }
                } else if (h >= 0) {
                    Item it = items.get(h);
                    if (it.enabled && it.action != null) {
                        if (open) setOpen(false);
                        it.action.run();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressed = -2;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
