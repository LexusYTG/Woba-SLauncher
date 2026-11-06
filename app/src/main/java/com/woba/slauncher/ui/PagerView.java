package com.woba.slauncher.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

/** Paginador horizontal mínimo (Start / Apps) con arrastre y fling. */
public class PagerView extends ViewGroup {
    private final OverScroller scroller;
    private final int slop, minFling, maxFling;
    private int page;
    private boolean swipe = true, dragging;
    private float downX, downY, lastX;
    private VelocityTracker vt;

    public PagerView(Context ctx) {
        super(ctx);
        scroller = new OverScroller(ctx);
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
    }

    public interface FractionListener { void onFraction(float f); }

    private FractionListener fraction;

    public void setFractionListener(FractionListener f) { fraction = f; }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (fraction != null && getWidth() > 0) fraction.onFraction(l / (float) getWidth());
    }

    public void setSwipeEnabled(boolean e) { swipe = e; }

    public int getPage() { return page; }

    public void setPage(int p, boolean animate) {
        page = Math.max(0, Math.min(getChildCount() - 1, p));
        int target = page * getWidth();
        if (animate && getWidth() > 0) {
            scroller.startScroll(getScrollX(), 0, target - getScrollX(), 0, 260);
            postInvalidateOnAnimation();
        } else {
            scroller.abortAnimation();
            scrollTo(target, 0);
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        if (scroller.isFinished() && !dragging) scrollTo(page * w, 0);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    private void track(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
    }

    private void release() {
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!swipe) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                release();
                track(e);
                if (!scroller.isFinished()) {
                    scroller.abortAnimation();
                    dragging = true;
                    return true;
                }
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE: {
                track(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                    dragging = true;
                    lastX = e.getX();
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                release();
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                release();
                track(e);
                return swipe;
            case MotionEvent.ACTION_MOVE: {
                track(e);
                if (!dragging && Math.abs(e.getX() - downX) > slop) {
                    dragging = true;
                    lastX = e.getX();
                }
                if (dragging) {
                    int max = Math.max(0, (getChildCount() - 1) * getWidth());
                    int nx = Math.max(0, Math.min(max, getScrollX() + Math.round(lastX - e.getX())));
                    lastX = e.getX();
                    scrollTo(nx, 0);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                track(e);
                if (dragging && getWidth() > 0) {
                    vt.computeCurrentVelocity(1000, maxFling);
                    float vx = vt.getXVelocity();
                    int target;
                    if (Math.abs(vx) > minFling) target = vx < 0 ? page + 1 : page - 1;
                    else target = Math.round(getScrollX() / (float) getWidth());
                    setPage(target, true);
                }
                dragging = false;
                release();
                return true;
            }
            default:
                return true;
        }
    }
}
