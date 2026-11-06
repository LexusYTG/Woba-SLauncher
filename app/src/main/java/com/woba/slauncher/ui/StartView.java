package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.data.Live;
import com.woba.slauncher.data.Notif;
import com.woba.slauncher.data.Tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Pantalla Start: rejilla de 4 columnas con empaquetado tipo WP8 (los tiles pequeños se agrupan
 * en bloques de 2x2), scroll propio con fling, pulsación larga para editar y arrastrar para
 * reordenar con auto-scroll y animación de los demás tiles.
 */
public class StartView extends ViewGroup {

    public interface Listener {
        void onTileClick(Tile t);
        void onTilesChanged();
        void onUnpin(Tile t);
        void onResize(Tile t);
        void onOpenApps();
        void onDoubleTap();
        void onEditModeChanged(boolean edit);
    }

    private static final int IDLE = 0, PRESS = 1, SCROLL = 2, DRAG = 3;

    private final Listener listener;
    private final List<Tile> tiles;
    private final Live live;
    private final HostBridge host;
    private final HashMap<String, TileView> views = new HashMap<>();

    private final int pad, gap, topPad, bottomPad;
    private int cell, footerTop, contentH;
    private boolean laidOut;

    private final OverScroller scroller;
    private final int slop, minFling, maxFling;
    private VelocityTracker vt;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean edit;
    private int state = IDLE;
    private long lastDownTime = -1;
    private float downX, downY, lastY, touchX, touchY;
    private TileView downTile, dragView;
    private float grabX, grabY;
    private long suppressUntil, lastEmptyTap, lastSwap;

    public StartView(Context ctx, List<Tile> tiles, Live live, HostBridge host, Listener l) {
        super(ctx);
        this.tiles = tiles;
        this.live = live;
        this.host = host;
        this.listener = l;
        setWillNotDraw(false);
        pad = Theme.dp(8);
        gap = Theme.dp(6);
        topPad = Theme.dp(6);
        bottomPad = Theme.dp(24);
        scroller = new OverScroller(ctx);
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        rebuild();
    }

    // ------------------------------------------------------------ API

    public interface ScrollListener { void onScroll(int y); }

    private ScrollListener scrollListener;
    private int cols = 4;

    public void setScrollListener(ScrollListener l) { scrollListener = l; }

    /** 4 columnas, o 6 con "show more tiles" (los tamaños de tile no cambian). */
    public void setColumns(int n) {
        if (cols == n) return;
        cols = n;
        relayout();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (scrollListener != null) scrollListener.onScroll(t);
    }

    private final Notif.Listener notifL = new Notif.Listener() {
        @Override public void onNotifsChanged() {
            for (TileView v : views.values()) v.invalidate();
        }
    };

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Notif.addListener(notifL);
    }

    @Override protected void onDetachedFromWindow() {
        Notif.removeListener(notifL);
        super.onDetachedFromWindow();
    }

    public boolean isEditMode() { return edit; }

    public void setEditMode(boolean e) {
        if (edit == e) return;
        edit = e;
        if (!e && state == DRAG) finishDrag();
        for (TileView v : views.values()) v.setEditMode(e);
        listener.onEditModeChanged(e);
        invalidate();
    }

    /** Sincroniza las vistas con la lista de tiles (altas y bajas). */
    public void rebuild() {
        HashSet<String> ids = new HashSet<>();
        for (Tile t : tiles) ids.add(t.id);
        Iterator<Map.Entry<String, TileView>> it = views.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TileView> e = it.next();
            if (!ids.contains(e.getKey())) {
                removeView(e.getValue());
                it.remove();
            }
        }
        for (Tile t : tiles) {
            if (!views.containsKey(t.id)) {
                TileView v = new TileView(getContext(), t, live, host);
                v.setEditMode(edit);
                views.put(t.id, v);
                addView(v);
            }
        }
        requestLayout();
    }

    /** Recalcula posiciones (p. ej. tras cambiar un tamaño) animando los desplazamientos. */
    public void relayout() {
        if (getWidth() == 0) {
            requestLayout();
            return;
        }
        computePlacement(getWidth());
        placeChildren(true);
        clampScroll();
        invalidate();
    }

    public void refreshLive() {
        for (TileView v : views.values()) v.refreshLive();
    }

    public void onThemeChanged() {
        for (TileView v : views.values()) v.invalidate();
        invalidate();
    }

    public void scrollToTop() {
        scroller.abortAnimation();
        scrollTo(0, 0);
    }

    public void scrollToEnd() {
        scroller.abortAnimation();
        scrollTo(0, maxScroll());
    }

    // ------------------------------------------------------------ layout

    private void computePlacement(int width) {
        cell = (width - 2 * pad - (cols - 1) * gap) / cols;
        ArrayList<boolean[]> occ = new ArrayList<>();
        int maxRow = 0;
        for (Tile t : tiles) {
            TileView v = views.get(t.id);
            if (v == null) continue;
            int cw = t.size.cols, rh = t.size.rows;
            int pr = 0, pc = 0;
            boolean found = false;
            for (int r = 0; !found; r++) {
                while (occ.size() < r + rh) occ.add(new boolean[cols]);
                for (int c = 0; c <= cols - cw && !found; c++) {
                    boolean free = true;
                    for (int rr = r; rr < r + rh && free; rr++)
                        for (int cc = c; cc < c + cw; cc++)
                            if (occ.get(rr)[cc]) { free = false; break; }
                    if (free) { pr = r; pc = c; found = true; }
                }
            }
            for (int rr = pr; rr < pr + rh; rr++)
                for (int cc = pc; cc < pc + cw; cc++) occ.get(rr)[cc] = true;
            v.cl = pad + pc * (cell + gap);
            v.ct = topPad + pr * (cell + gap);
            v.cr = v.cl + cw * cell + (cw - 1) * gap;
            v.cb = v.ct + rh * cell + (rh - 1) * gap;
            maxRow = Math.max(maxRow, pr + rh);
        }
        int gridBottom = topPad + (maxRow > 0 ? maxRow * (cell + gap) - gap : 0);
        footerTop = gridBottom + Theme.dp(16);
        contentH = footerTop + Theme.dp(48) + bottomPad;
    }

    private void placeChildren(boolean animate) {
        for (Tile t : tiles) {
            TileView v = views.get(t.id);
            if (v == null) continue;
            int w = v.cr - v.cl, h = v.cb - v.ct;
            if (animate && v.placed && v != dragView && (v.cl != v.lx || v.ct != v.ly)) {
                v.setTranslationX(v.getTranslationX() + v.lx - v.cl);
                v.setTranslationY(v.getTranslationY() + v.ly - v.ct);
                v.animate().translationX(0f).translationY(0f).setDuration(220).start();
            }
            v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            v.layout(v.cl, v.ct, v.cr, v.cb);
            v.lx = v.cl;
            v.ly = v.ct;
            v.placed = true;
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(getDefaultSize(0, wSpec), getDefaultSize(0, hSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        computePlacement(r - l);
        placeChildren(laidOut);
        laidOut = true;
        clampScroll();
        if (dragView != null) updateDragTranslation();
    }

    private int maxScroll() {
        return Math.max(0, contentH - getHeight());
    }

    private void clampScroll() {
        int y = Math.max(0, Math.min(maxScroll(), getScrollY()));
        if (y != getScrollY()) scrollTo(0, y);
    }

    private void doScrollBy(int dy) {
        int y = Math.max(0, Math.min(maxScroll(), getScrollY() + dy));
        if (y != getScrollY()) scrollTo(0, y);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(0, scroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }

    // ------------------------------------------------------------ dibujo (botón →)

    private float arrowCx() { return getWidth() - pad - Theme.dpf(24); }
    private float arrowCy() { return footerTop + Theme.dpf(24); }

    @Override
    protected void onDraw(Canvas c) {
        float cx = arrowCx(), cy = arrowCy();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Theme.dpf(2));
        paint.setColor(Theme.fg);
        c.drawCircle(cx, cy, Theme.dpf(23), paint);
        Icons.draw(c, Icons.ARROW, cx, cy, Theme.dpf(22), Theme.fg);
    }

    // ------------------------------------------------------------ búsqueda de tiles

    private TileView tileAt(float x, float cy) {
        for (Tile t : tiles) {
            TileView v = views.get(t.id);
            if (v != null && x >= v.cl && x < v.cr && cy >= v.ct && cy < v.cb) return v;
        }
        return null;
    }

    // ------------------------------------------------------------ touch

    private void track(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
    }

    private void onDown(MotionEvent e) {
        if (e.getDownTime() == lastDownTime) return;
        lastDownTime = e.getDownTime();
        boolean caught = !scroller.isFinished();
        if (caught) scroller.abortAnimation();
        downX = touchX = e.getX();
        downY = lastY = touchY = e.getY();
        state = caught ? SCROLL : PRESS;
        if (vt != null) vt.recycle();
        vt = VelocityTracker.obtain();
        vt.addMovement(e);
        downTile = tileAt(downX, downY + getScrollY());
        if (downTile != null && !edit && state == PRESS) {
            downTile.pressAt((downX - downTile.cl) / (float) Math.max(1, downTile.cr - downTile.cl),
                    (downY + getScrollY() - downTile.ct) / (float) Math.max(1, downTile.cb - downTile.ct));
        }
        removeCallbacks(longPress);
        if (state == PRESS) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
    }

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (state == PRESS && downTile != null) {
                downTile.releasePress();
                requestParentDisallow();
                startDrag(downTile);
            }
        }
    };

    private void requestParentDisallow() {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
    }

    private void beginScroll(float y) {
        state = SCROLL;
        lastY = y;
        removeCallbacks(longPress);
        if (downTile != null) downTile.releasePress();
        requestParentDisallow();
    }

    private void endGesture() {
        removeCallbacks(longPress);
        if (downTile != null) downTile.releasePress();
        downTile = null;
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
        state = IDLE;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            onDown(e);
            return edit || state == SCROLL;
        }
        track(e);
        if (state == DRAG) {
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) finishDrag();
            return true;
        }
        if (a == MotionEvent.ACTION_MOVE && state == PRESS
                && Math.abs(e.getY() - downY) > slop && Math.abs(e.getY() - downY) > Math.abs(e.getX() - downX)) {
            beginScroll(e.getY());
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (state == PRESS) endGesture();
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                onDown(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                track(e);
                touchX = e.getX();
                touchY = e.getY();
                if (state == DRAG) {
                    updateDragTranslation();
                    reorder();
                    return true;
                }
                if (state == PRESS && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                    beginScroll(e.getY());
                }
                if (state == SCROLL) {
                    doScrollBy(-Math.round(e.getY() - lastY));
                    lastY = e.getY();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                track(e);
                if (state == DRAG) {
                    finishDrag();
                } else if (state == SCROLL) {
                    vt.computeCurrentVelocity(1000, maxFling);
                    float vy = vt.getYVelocity();
                    if (Math.abs(vy) > minFling) {
                        scroller.fling(0, getScrollY(), 0, Math.round(-vy), 0, 0, 0, maxScroll());
                        postInvalidateOnAnimation();
                    }
                } else if (state == PRESS) {
                    float x = e.getX(), cy = e.getY() + getScrollY();
                    TileView pressed = downTile;
                    if (pressed != null) pressed.releasePress();
                    handleTap(x, cy);
                }
                endGesture();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (state == DRAG) finishDrag();
                endGesture();
                return true;
            default:
                return true;
        }
    }

    private boolean near(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private void handleTap(float x, float cy) {
        if (near(x, cy, arrowCx(), arrowCy(), Theme.dpf(30))) {
            if (!edit) listener.onOpenApps();
            return;
        }
        TileView v = tileAt(x, cy);
        if (v != null) {
            if (edit) {
                float r = Theme.dpf(24);
                if (near(x, cy, v.cr - Theme.dpf(18), v.ct + Theme.dpf(18), r)) {
                    listener.onUnpin(v.tile);
                } else if (v.hasResize() && near(x, cy, v.cr - Theme.dpf(18), v.cb - Theme.dpf(18), r)) {
                    listener.onResize(v.tile);
                }
            } else if (System.currentTimeMillis() >= suppressUntil) {
                listener.onTileClick(v.tile);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (edit) {
            setEditMode(false);
        } else if (now - lastEmptyTap < 300) {
            lastEmptyTap = 0;
            listener.onDoubleTap();
        } else {
            lastEmptyTap = now;
        }
    }

    // ------------------------------------------------------------ arrastre

    private void startDrag(TileView v) {
        state = DRAG;
        dragView = v;
        v.animate().cancel();
        grabX = touchX - v.cl;
        grabY = touchY + getScrollY() - v.ct;
        setEditMode(true);
        v.setDragging(true);
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        updateDragTranslation();
        postOnAnimation(autoScroll);
    }

    private void updateDragTranslation() {
        if (dragView == null) return;
        dragView.setTranslationX(touchX - grabX - dragView.cl);
        dragView.setTranslationY(touchY + getScrollY() - grabY - dragView.ct);
    }

    private void finishDrag() {
        TileView v = dragView;
        dragView = null;
        removeCallbacks(autoScroll);
        state = IDLE;
        if (v != null) {
            v.setDragging(false);
            v.animate().translationX(0f).translationY(0f).setDuration(160).start();
            suppressUntil = System.currentTimeMillis() + 350L;
            listener.onTilesChanged();
        }
    }

    private void reorder() {
        if (dragView == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSwap < 220) return;
        float cx = dragView.cl + dragView.getTranslationX() + (dragView.cr - dragView.cl) / 2f;
        float cy = dragView.ct + dragView.getTranslationY() + (dragView.cb - dragView.ct) / 2f;
        TileView target = tileAt(cx, cy);
        if (target == null || target == dragView) return;
        int from = tiles.indexOf(dragView.tile);
        int to = tiles.indexOf(target.tile);
        if (from < 0 || to < 0 || from == to) return;
        tiles.add(to, tiles.remove(from));
        lastSwap = now;
        computePlacement(getWidth());
        placeChildren(true);
        updateDragTranslation();
        invalidate();
    }

    private final Runnable autoScroll = new Runnable() {
        @Override
        public void run() {
            if (state != DRAG || dragView == null) return;
            float zone = Theme.dpf(100), maxSpeed = Theme.dpf(14);
            float y = touchY;
            int dy = 0;
            if (y < zone) dy = -Math.round(maxSpeed * (zone - Math.max(0f, y)) / zone);
            else if (y > getHeight() - zone) dy = Math.round(maxSpeed * (Math.min(getHeight(), y) - (getHeight() - zone)) / zone);
            if (dy != 0) {
                int before = getScrollY();
                doScrollBy(dy);
                if (getScrollY() != before) {
                    updateDragTranslation();
                    reorder();
                }
            }
            postOnAnimation(this);
        }
    };
}
