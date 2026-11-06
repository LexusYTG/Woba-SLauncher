package com.woba.slauncher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** Pivote de Windows Phone: título de la app, encabezados grandes que se deslizan y páginas laterales. */
public class Pivot extends FrameLayout {
    public interface OnPage { void onPage(int index); }

    private final String appTitle;
    private final String[] heads;
    private final View[] pages;
    private final Header header;
    private final FrameLayout holder;
    private float offset; // en unidades de página
    private int current;
    private OnPage listener;
    private ValueAnimator anim;

    private float downX, downY, startOffset;
    private boolean dragging, decided;
    private VelocityTracker vt;
    private final int slop;

    public Pivot(Context c, String appTitle, String[] heads, View[] pages) {
        super(c);
        this.appTitle = appTitle;
        this.heads = heads;
        this.pages = pages;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        header = new Header(c);
        addView(header, new LayoutParams(-1, Theme.dp(108)));
        holder = new FrameLayout(c);
        LayoutParams hp = new LayoutParams(-1, -1);
        hp.topMargin = Theme.dp(108);
        addView(holder, hp);
        for (View v : pages) holder.addView(v, new LayoutParams(-1, -1));
        layoutPages();
    }

    public void setOnPage(OnPage l) { listener = l; }

    public int getPage() { return current; }

    public void setPage(int i, boolean animate) {
        i = Math.max(0, Math.min(pages.length - 1, i));
        animateTo(i, animate);
    }

    public void refreshHeader() { header.invalidate(); }

    private void animateTo(final int target, boolean animate) {
        if (anim != null) anim.cancel();
        final int prev = current;
        current = target;
        if (!animate) {
            offset = target;
            layoutPages();
            if (prev != target && listener != null) listener.onPage(target);
            return;
        }
        anim = ValueAnimator.ofFloat(offset, target);
        anim.setDuration(260);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                offset = (Float) a.getAnimatedValue();
                layoutPages();
            }
        });
        anim.start();
        if (prev != target && listener != null) listener.onPage(target);
    }

    private void layoutPages() {
        int w = Math.max(1, getWidth());
        for (int i = 0; i < pages.length; i++) {
            float d = i - offset;
            pages[i].setTranslationX(d * w);
            pages[i].setVisibility(Math.abs(d) < 1.01f ? VISIBLE : INVISIBLE);
        }
        header.invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        layoutPages();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                decided = false;
                dragging = false;
                startOffset = offset;
                if (anim != null) anim.cancel();
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (vt != null) vt.addMovement(e);
                if (!decided) {
                    float dx = e.getX() - downX, dy = e.getY() - downY;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        decided = true;
                        dragging = true;
                        return true;
                    } else if (Math.abs(dy) > slop) {
                        decided = true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (vt != null) vt.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                startOffset = offset;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                float o = startOffset - dx / Math.max(1, getWidth());
                if (o < 0) o *= 0.35f;
                if (o > pages.length - 1) o = pages.length - 1 + (o - (pages.length - 1)) * 0.35f;
                offset = o;
                layoutPages();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float vx = 0;
                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vx = vt.getXVelocity();
                    vt.recycle();
                    vt = null;
                }
                int target = Math.round(offset - vx / 4000f);
                if (offset - startOffset > 0.15f || vx < -900) target = (int) Math.ceil(startOffset + 0.01f);
                else if (startOffset - offset > 0.15f || vx > 900) target = (int) Math.floor(startOffset - 0.01f);
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) target = current;
                animateTo(Math.max(0, Math.min(pages.length - 1, target)), true);
                return true;
            }
            default: return true;
        }
    }

    /** Encabezados: nombre de la app y títulos grandes que se desplazan con el dedo. */
    private class Header extends View {
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        Header(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            tp.setTypeface(Theme.semibold);
            tp.setTextSize(Theme.sp(13));
            tp.setColor(Theme.fg);
            c.drawText(appTitle.toUpperCase(), Theme.dpf(18), Theme.dpf(24) + Theme.centerBaseline(tp, 0), tp);

            tp.setTypeface(Theme.light);
            tp.setTextSize(Theme.sp(46));
            float gap = Theme.dpf(24), x0 = Theme.dpf(18);
            float[] pos = new float[heads.length];
            float acc = 0;
            for (int i = 0; i < heads.length; i++) {
                pos[i] = acc;
                acc += tp.measureText(heads[i]) + gap;
            }
            int lo = (int) Math.floor(offset), hi = Math.min(heads.length - 1, lo + 1);
            lo = Math.max(0, Math.min(heads.length - 1, lo));
            float f = offset - (float) Math.floor(offset);
            float shift = pos[lo] + (pos[hi] - pos[lo]) * (hi == lo ? 0 : f);
            float y = Theme.dpf(70);
            for (int i = 0; i < heads.length; i++) {
                float x = x0 + pos[i] - shift;
                float near = Math.min(1f, Math.abs(i - offset));
                tp.setColor(Theme.alpha(Theme.fg, 1f - 0.55f * near));
                c.drawText(heads[i], x, y + Theme.centerBaseline(tp, 0), tp);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getY() > Theme.dpf(34)) {
                tp.setTypeface(Theme.light);
                tp.setTextSize(Theme.sp(46));
                float gap = Theme.dpf(24), x0 = Theme.dpf(18);
                float[] pos = new float[heads.length];
                float acc = 0;
                for (int i = 0; i < heads.length; i++) { pos[i] = acc; acc += tp.measureText(heads[i]) + gap; }
                int lo = Math.max(0, Math.min(heads.length - 1, (int) Math.floor(offset)));
                int hi = Math.min(heads.length - 1, lo + 1);
                float f = offset - (float) Math.floor(offset);
                float shift = pos[lo] + (pos[hi] - pos[lo]) * (hi == lo ? 0 : f);
                for (int i = 0; i < heads.length; i++) {
                    float x = x0 + pos[i] - shift;
                    if (e.getX() >= x && e.getX() <= x + tp.measureText(heads[i])) { setPage(i, true); break; }
                }
            }
            return true;
        }
    }
}
