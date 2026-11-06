package com.woba.slauncher.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import com.woba.slauncher.WpNotificationService;
import com.woba.slauncher.data.Actions;
import com.woba.slauncher.data.Notif;
import com.woba.slauncher.data.Sys;
import com.woba.slauncher.data.SysSettings;
import com.woba.slauncher.data.Toggles;

import android.content.Intent;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Action Center de Windows Phone 8.1: cuatro acciones rápidas, "clear all" / "all settings"
 * y la lista de notificaciones agrupadas por app. Baja desde arriba y se cierra con la barra inferior.
 */
public class ActionCenterView extends View {

    public interface Listener {
        void onOpenChanged(boolean open);
    }

    private static final int BG = 0xFF000000, TILE_OFF = 0xFF2B2B2B, SUBTLE = 0xFF9E9E9E;

    private final Listener listener;
    private final int sbH;
    private int[] quick = {Toggles.WIFI, Toggles.BLUETOOTH, Toggles.AIRPLANE, Toggles.ROTATION};
    private boolean expanded;

    /** Acciones visibles: las 4 elegidas; al expandir, también el resto (en orden de id). */
    private int[] shown() {
        if (!expanded) return quick;
        int[] out = new int[Toggles.LABELS.length];
        int n = 0;
        for (int id : quick) out[n++] = id;
        for (int id = 0; id < Toggles.LABELS.length; id++) {
            boolean used = false;
            for (int q : quick) if (q == id) used = true;
            if (!used) out[n++] = id;
        }
        return out;
    }

    private int tileRows() { return (shown().length + 3) / 4; }

    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tiny = Theme.textPaint(Theme.semibold, 9f, 0xFFFFFFFF);
    private final TextPaint head = Theme.textPaint(Theme.light, 20f, 0xFFFFFFFF);
    private final TextPaint title = Theme.textPaint(Theme.semibold, 14f, 0xFFFFFFFF);
    private final TextPaint body = Theme.textPaint(Theme.light, 13f, SUBTLE);
    private final TextPaint small = Theme.textPaint(Theme.regular, 11f, SUBTLE);
    private final TextPaint empty = Theme.textPaint(Theme.light, 28f, 0xFF6E6E6E);
    private final TextPaint btn = Theme.textPaint(Theme.semibold, 11f, 0xFFFFFFFF);

    private static final class Row {
        boolean header;
        String pkg;
        Notif n;
        float offX;
    }

    private final ArrayList<Row> rows = new ArrayList<>();
    private final HashMap<String, String> labels = new HashMap<>();

    private int H;              // alto de la pantalla (para el desplazamiento)
    private boolean open;
    private float scrollY;
    private final OverScroller scroller;
    private VelocityTracker vt;
    private final int slop;

    // gesto
    private static final int M_NONE = 0, M_PENDING = 1, M_LIST = 2, M_CLOSE = 3, M_ROW = 4;
    private int mode = M_NONE;
    private float downX, downY, lastY;
    private Row swipeRow;

    private final Notif.Listener notifListener = new Notif.Listener() {
        @Override public void onNotifsChanged() { rebuildRows(); }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Notif.addListener(notifListener);
        quick = com.woba.slauncher.data.Prefs.quick(getContext());
        rebuildRows();
    }

    private final Runnable invalidateRun = new Runnable() {
        @Override public void run() { invalidate(); }
    };

    public ActionCenterView(Context ctx, int statusBarHeight, Listener l) {
        super(ctx);
        this.sbH = statusBarHeight;
        this.listener = l;
        scroller = new OverScroller(ctx);
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        setVisibility(GONE);
        rebuildRows();
    }

    public boolean isOpen() { return open; }

    // ------------------------------------------------------------ apertura y cierre

    /** Empieza a arrastrar desde el borde superior (la raíz le pasa el alto de pantalla). */
    public void pullStart(int rootHeight) {
        H = rootHeight;
        animate().cancel();
        scrollY = 0;
        setVisibility(VISIBLE);
        setTranslationY(-H);
        rebuildRows();
    }

    public void pullTo(float dy) {
        setTranslationY(Math.max(-H, Math.min(0, -H + dy)));
    }

    public void pullEnd(float vy) {
        boolean shouldOpen = vy > 800f || (vy > -800f && getTranslationY() > -H * 0.7f);
        settle(shouldOpen);
    }

    private void settle(final boolean toOpen) {
        animate().cancel();
        animate().translationY(toOpen ? 0f : -H).setDuration(180).withEndAction(new Runnable() {
            @Override public void run() {
                if (!toOpen) setVisibility(GONE);
            }
        }).start();
        if (open != toOpen) {
            open = toOpen;
            if (toOpen) quick = com.woba.slauncher.data.Prefs.quick(getContext());
            if (listener != null) listener.onOpenChanged(open);
        }
        if (toOpen) refreshStates();
    }

    public void close() { if (getVisibility() == VISIBLE) settle(false); }

    public boolean onBack() {
        if (!open) return false;
        close();
        return true;
    }

    public void refreshStates() { invalidate(); }

    // ------------------------------------------------------------ datos

    private void rebuildRows() {
        rows.clear();
        Context c = getContext();
        LinkedHashMap<String, ArrayList<Notif>> groups = new LinkedHashMap<>();
        ArrayList<Notif> sorted = new ArrayList<>(Notif.all());
        java.util.Collections.sort(sorted, new java.util.Comparator<Notif>() {
            @Override public int compare(Notif a, Notif b) { return Long.compare(b.time, a.time); }
        });
        for (Notif n : sorted) {
            ArrayList<Notif> g = groups.get(n.pkg);
            if (g == null) { g = new ArrayList<>(); groups.put(n.pkg, g); }
            g.add(n);
        }
        for (Map.Entry<String, ArrayList<Notif>> e : groups.entrySet()) {
            Row h = new Row();
            h.header = true;
            h.pkg = e.getKey();
            rows.add(h);
            if (!labels.containsKey(h.pkg)) labels.put(h.pkg, String.valueOf(SysSettings.appLabel(c, h.pkg)));
            for (Notif n : e.getValue()) {
                Row r = new Row();
                r.n = n;
                r.pkg = n.pkg;
                rows.add(r);
            }
        }
        scrollY = Math.min(scrollY, maxScroll());
        invalidate();
    }

    // ------------------------------------------------------------ geometría

    private float margin() { return Theme.dpf(12f); }
    private float handleH() { return Theme.dpf(20f); }
    private float tileGap() { return Theme.dpf(4f); }
    private float tileW() { return (getWidth() - 2 * margin() - 3 * tileGap()) / 4f; }
    private float tileH() { return tileW() * 0.66f; }
    private float tilesTop() { return sbH + Theme.dpf(4f); }
    private float tilesH() { int r = tileRows(); return r * tileH() + (r - 1) * tileGap(); }
    private float barTop() { return tilesTop() + tilesH() + Theme.dpf(8f); }
    private float barH() { return Theme.dpf(36f); }
    private float listTop() { return barTop() + barH() + Theme.dpf(4f); }
    private float listBottom() { return getHeight() - handleH(); }
    private float headerH() { return Theme.dpf(44f); }
    private float rowH() { return Theme.dpf(58f); }

    private float contentH() {
        float h = 0;
        for (Row r : rows) h += r.header ? headerH() : rowH();
        return h + Theme.dpf(12f);
    }

    private float maxScroll() {
        return Math.max(0f, contentH() - (listBottom() - listTop()));
    }

    // ------------------------------------------------------------ dibujo

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(BG);

        // acciones rápidas
        float m = margin(), tw = tileW(), th = tileH(), ty = tilesTop();
        int[] sh = shown();
        for (int i = 0; i < sh.length; i++) {
            float x = m + (i % 4) * (tw + tileGap());
            float y0 = ty + (i / 4) * (th + tileGap());
            boolean on = Toggles.isOn(getContext(), sh[i]);
            fillP.setColor(on ? Theme.accent : TILE_OFF);
            c.drawRect(x, y0, x + tw, y0 + th, fillP);
            StatusIcons.toggle(c, sh[i], x + tw / 2f, y0 + th * 0.38f, Theme.dpf(24f), 0xFFFFFFFF);
            String lab = Toggles.LABELS[sh[i]];
            if (sh[i] == Toggles.WIFI && on) {
                String n = Toggles.wifiName(getContext());
                if (n != null) lab = n.toUpperCase();
            }
            lab = TextUtils.ellipsize(lab, tiny, tw - Theme.dpf(8f), TextUtils.TruncateAt.END).toString();
            c.drawText(lab, x + Theme.dpf(5f), y0 + th - Theme.dpf(6f), tiny);
        }

        // barra: clear all / all settings
        float by = barTop(), bh = barH();
        if (!rows.isEmpty()) {
            Icons.draw(c, Icons.CLOSE, m + Theme.dpf(8f), by + bh / 2f, Theme.dpf(12f), 0xFFFFFFFF);
            c.drawText("CLEAR ALL", m + Theme.dpf(22f), Theme.centerBaseline(btn, by + bh / 2f), btn);
        }
        String ex = expanded ? "COLLAPSE" : "EXPAND";
        float ew = btn.measureText(ex);
        float ex0 = w / 2f - (ew + Theme.dpf(18f)) / 2f;
        c.drawText(ex, ex0, Theme.centerBaseline(btn, by + bh / 2f), btn);
        float ccx = ex0 + ew + Theme.dpf(10f), ccy = by + bh / 2f, k = Theme.dpf(4f), d = expanded ? k / 2f : -k / 2f;
        fillP.setStyle(Paint.Style.STROKE);
        fillP.setStrokeWidth(Theme.dpf(1.5f));
        fillP.setColor(0xFFFFFFFF);
        c.drawLine(ccx - k, ccy + d, ccx, ccy - d, fillP);
        c.drawLine(ccx, ccy - d, ccx + k, ccy + d, fillP);
        fillP.setStyle(Paint.Style.FILL);
        String all = "ALL SETTINGS";
        float aw = btn.measureText(all);
        c.drawText(all, w - m - aw, Theme.centerBaseline(btn, by + bh / 2f), btn);
        Icons.draw(c, Icons.GEAR, w - m - aw - Theme.dpf(14f), by + bh / 2f, Theme.dpf(16f), 0xFFFFFFFF);

        // lista
        float lt = listTop(), lb = listBottom();
        c.save();
        c.clipRect(0, lt, w, lb);
        if (rows.isEmpty()) {
            String msg = Notif.connected() ? "No notifications" : "Allow notifications";
            c.drawText(msg, m, lt + Theme.dpf(48f), empty);
            if (!Notif.connected()) {
                c.drawText("Tap here to let this launcher show them", m, lt + Theme.dpf(72f), body);
            }
        } else {
            float y = lt - scrollY;
            int iconPx = Theme.dp(26);
            for (Row r : rows) {
                float rh = r.header ? headerH() : rowH();
                if (y + rh >= lt && y <= lb) {
                    if (r.header) drawHeader(c, r, y, iconPx);
                    else drawNotif(c, r, y, w);
                }
                y += rh;
            }
        }
        c.restore();

        // barra de arrastre inferior
        fillP.setColor(Theme.accent);
        c.drawRect(0, h - handleH(), w, h, fillP);
        fillP.setColor(0xFFFFFFFF);
        c.drawRect(w / 2f - Theme.dpf(10f), h - handleH() / 2f - Theme.dpf(1f),
                w / 2f + Theme.dpf(10f), h - handleH() / 2f + Theme.dpf(1f), fillP);
    }

    private void drawHeader(Canvas c, Row r, float y, int iconPx) {
        float m = margin();
        Bitmap b = Sys.icon(getContext(), r.pkg, iconPx, invalidateRun);
        float cy = y + headerH() / 2f + Theme.dpf(4f);
        if (b != null) c.drawBitmap(b, m, cy - iconPx / 2f, null);
        else {
            fillP.setColor(Theme.accent);
            c.drawRect(m, cy - iconPx / 2f, m + iconPx, cy + iconPx / 2f, fillP);
        }
        String l = labels.get(r.pkg);
        c.drawText(l == null ? r.pkg : l, m + iconPx + Theme.dpf(10f), Theme.centerBaseline(head, cy), head);
    }

    private void drawNotif(Canvas c, Row r, float y, int w) {
        float m = margin();
        float x = r.offX;
        float alpha = 1f - Math.min(1f, Math.abs(x) / (w * 0.7f));
        int save = c.save();
        c.translate(x, 0);
        title.setAlpha(Math.round(255 * alpha));
        body.setAlpha(Math.round(255 * alpha));
        small.setAlpha(Math.round(255 * alpha));
        String time = Sys.timeText(getContext(), new Date(r.n.time));
        float tw = small.measureText(time);
        c.drawText(time, w - m - tw, y + Theme.dpf(20f), small);
        float maxW = w - 2 * m - tw - Theme.dpf(12f);
        c.drawText(TextUtils.ellipsize(r.n.title, title, maxW, TextUtils.TruncateAt.END).toString(),
                m, y + Theme.dpf(20f), title);
        c.drawText(TextUtils.ellipsize(r.n.text.replace('\n', ' '), body, w - 2 * m, TextUtils.TruncateAt.END).toString(),
                m, y + Theme.dpf(42f), body);
        c.restoreToCount(save);
        title.setAlpha(255);
        body.setAlpha(255);
        small.setAlpha(255);
    }

    // ------------------------------------------------------------ toques

    private Row rowAt(float y) {
        float yy = listTop() - scrollY;
        for (Row r : rows) {
            float rh = r.header ? headerH() : rowH();
            if (y >= yy && y < yy + rh && y >= listTop() && y < listBottom()) return r;
            yy += rh;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animate().cancel();
                scroller.forceFinished(true);
                downX = x; downY = y; lastY = y;
                swipeRow = null;
                mode = (y > getHeight() - handleH() * 2.2f) ? M_CLOSE : M_PENDING;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = x - downX, dy = y - downY;
                if (mode == M_PENDING && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                    Row r = rowAt(downY);
                    if (Math.abs(dx) > Math.abs(dy) && r != null && !r.header && r.n.clearable) {
                        mode = M_ROW;
                        swipeRow = r;
                    } else if (dy < 0 && maxScroll() == 0f) {
                        mode = M_CLOSE;
                    } else {
                        mode = M_LIST;
                    }
                }
                if (mode == M_LIST) {
                    scrollY = Math.max(0f, Math.min(maxScroll(), scrollY - (y - lastY)));
                    invalidate();
                } else if (mode == M_CLOSE) {
                    setTranslationY(Math.max(-H, Math.min(0f, y - downY)));
                } else if (mode == M_ROW && swipeRow != null) {
                    swipeRow.offX = dx;
                    invalidate();
                }
                lastY = y;
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                vt.computeCurrentVelocity(1000);
                float vy = vt.getYVelocity();
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                if (mode == M_PENDING && up) tap(x, y);
                else if (mode == M_CLOSE) settle(!(getTranslationY() < -H * 0.2f || vy < -900f));
                else if (mode == M_LIST) {
                    scroller.fling(0, Math.round(scrollY), 0, Math.round(-vy), 0, 0, 0, Math.round(maxScroll()));
                    postInvalidateOnAnimation();
                } else if (mode == M_ROW && swipeRow != null) {
                    if (up && Math.abs(swipeRow.offX) > getWidth() * 0.35f) dismiss(swipeRow.n);
                    else { swipeRow.offX = 0f; invalidate(); }
                }
                vt.recycle();
                vt = null;
                mode = M_NONE;
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.getCurrY();
            postInvalidateOnAnimation();
        }
    }

    private void tap(float x, float y) {
        Context c = getContext();
        // acciones rápidas
        if (y >= tilesTop() && y <= tilesTop() + tilesH()) {
            int[] sh = shown();
            int row = (int) ((y - tilesTop()) / (tileH() + tileGap()));
            for (int col = 0; col < 4; col++) {
                int i = row * 4 + col;
                if (i >= sh.length) break;
                float tx = margin() + col * (tileW() + tileGap());
                if (x >= tx && x <= tx + tileW()) {
                    Intent panel = Toggles.toggle(c, sh[i]);
                    if (panel != null) { close(); Actions.start(c, panel); }
                    else postDelayed(invalidateRun, 450);
                    return;
                }
            }
        }
        // clear all / all settings
        if (y >= barTop() && y <= barTop() + barH()) {
            if (x > getWidth() * 0.62f) { close(); Actions.start(c, Toggles.allSettings()); }
            else if (x > getWidth() * 0.28f) {
                expanded = !expanded;
                scrollY = Math.min(scrollY, maxScroll());
                invalidate();
            }
            else if (!rows.isEmpty()) { WpNotificationService.dismissAll(); }
            return;
        }
        Row r = rowAt(y);
        if (rows.isEmpty() && !Notif.connected() && y >= listTop() && y < listTop() + Theme.dpf(90f)) {
            if (!Toggles.enableListenerDirect(c)) { close(); Actions.start(c, Toggles.listenerSettings()); }
            return;
        }
        if (r == null) return;
        if (r.header) {
            close();
            Actions.launchApp(c, r.pkg);
        } else {
            PendingIntent pi = r.n.intent;
            Runnable act = r.n.action;
            close();
            if (act != null) {
                act.run();
                Notif.remove(r.n.key);
            } else if (pi != null) {
                try { pi.send(); } catch (Exception ex) { Actions.launchApp(c, r.pkg); }
            } else Actions.launchApp(c, r.pkg);
            if (r.n.clearable) WpNotificationService.dismiss(r.n.key);
        }
    }

    private void dismiss(Notif n) {
        WpNotificationService.dismiss(n.key);
        Notif.remove(n.key);
    }

    @Override
    protected void onDetachedFromWindow() {
        Notif.removeListener(notifListener);
        super.onDetachedFromWindow();
    }
}
