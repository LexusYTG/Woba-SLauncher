package com.woba.slauncher.ui;

import android.animation.ValueAnimator;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.data.Live;
import com.woba.slauncher.data.Notif;
import com.woba.slauncher.data.Sys;
import com.woba.slauncher.data.Tile;

/** Un tile: se dibuja a mano con Canvas; tilt, flip y escala son propiedades de View (aceleradas por GPU). */
public class TileView extends FrameLayout {
    /** Si al tocar el tile se inclina hacia el lado contrario, cambia esto a -1f. */
    static final float TILT_SIGN = 1f;

    public final Tile tile;
    private final Live live;
    private final HostBridge host;

    // Posición asignada por StartView (coordenadas de contenido).
    int cl, ct, cr, cb, lx, ly;
    boolean placed;

    private boolean edit, dragging;
    private float press, editScale = 1f, flipAngle;
    private float pressNx = 0.5f, pressNy = 0.5f;
    private String backText;
    private boolean flipEnabled, showingBack;
    private View widgetView;
    private ValueAnimator pressAnim, scaleAnim, flipAnim;

    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable redraw = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    public TileView(Context ctx, Tile tile, Live live, HostBridge host) {
        super(ctx);
        this.tile = tile;
        this.live = live;
        this.host = host;
        setWillNotDraw(false);
        setCameraDistance(Theme.density * 6000f);
        computeBackText();
        if (tile.kind == Tile.Kind.WIDGET) addWidgetView();
        updateFlip();
    }

    // ------------------------------------------------------------ estado

    public void setEditMode(boolean e) {
        if (edit == e) return;
        edit = e;
        animateEditScale();
        updateFlip();
        if (tile.kind == Tile.Kind.WIDGET) {
            if (e) removeWidgetView();
            else addWidgetView();
        }
        invalidate();
    }

    public void setDragging(boolean d) {
        dragging = d;
        animateEditScale();
        setAlpha(d ? 0.9f : 1f);
        setTranslationZ(d ? Theme.dpf(10) : 0f);
    }

    public void refreshLive() {
        computeBackText();
        updateFlip();
        invalidate();
    }

    private void computeBackText() {
        String t = null;
        switch (tile.kind) {
            case PHONE:
                if (live.missed > 0) t = live.missed + " missed call" + (live.missed > 1 ? "s" : "");
                break;
            case MESSAGES:
                if (live.sms > 0) t = live.sms + " new message" + (live.sms > 1 ? "s" : "");
                break;
            case BATTERY:
                if (live.charging) t = "Charging";
                break;
            default:
                break;
        }
        backText = t;
    }

    // ------------------------------------------------------------ widgets

    private void addWidgetView() {
        if (widgetView != null) return;
        View v = host.createWidgetView(tile.widgetId);
        widgetView = v != null ? v : new View(getContext());
        addView(widgetView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateWidgetSize(getWidth(), getHeight());
    }

    private void removeWidgetView() {
        if (widgetView != null) {
            removeView(widgetView);
            widgetView = null;
        }
    }

    private void updateWidgetSize(int w, int h) {
        if (widgetView instanceof AppWidgetHostView && w > 0 && h > 0) {
            int wd = Math.round(w / Theme.density), hd = Math.round(h / Theme.density);
            ((AppWidgetHostView) widgetView).updateAppWidgetSize(null, wd, hd, wd, hd);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        updateWidgetSize(w, h);
    }

    // ------------------------------------------------------------ animaciones

    public void pressAt(float nx, float ny) {
        if (edit || dragging) return;
        pressNx = nx;
        pressNy = ny;
        animatePress(1f);
    }

    public void releasePress() {
        animatePress(0f);
    }

    private void animatePress(float to) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, to);
        pressAnim.setDuration(to > 0 ? 90 : 160);
        pressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                press = (Float) a.getAnimatedValue();
                applyTransform();
            }
        });
        pressAnim.start();
    }

    private void animateEditScale() {
        float to = dragging ? 1.06f : (edit ? 0.92f : 1f);
        if (scaleAnim != null) scaleAnim.cancel();
        scaleAnim = ValueAnimator.ofFloat(editScale, to);
        scaleAnim.setDuration(160);
        scaleAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                editScale = (Float) a.getAnimatedValue();
                applyTransform();
            }
        });
        scaleAnim.start();
    }

    private void applyTransform() {
        float s = editScale * (1f - 0.05f * press);
        setScaleX(s);
        setScaleY(s);
        float ts = Theme.tilt ? TILT_SIGN : 0f;
        setRotationY((pressNx - 0.5f) * 28f * press * ts);
        setRotationX(flipAngle + (0.5f - pressNy) * 28f * press * ts);
    }

    private void animateFlip(float to) {
        if (flipAnim != null) flipAnim.cancel();
        flipAnim = ValueAnimator.ofFloat(flipAngle, to);
        flipAnim.setDuration(700);
        flipAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                flipAngle = (Float) a.getAnimatedValue();
                applyTransform();
                invalidate();
            }
        });
        flipAnim.start();
    }

    private final Runnable flipStep = new Runnable() {
        @Override
        public void run() {
            if (!flipEnabled) return;
            showingBack = !showingBack;
            animateFlip(showingBack ? 180f : 0f);
            postDelayed(this, showingBack ? 4000L : 9000L);
        }
    };

    private void updateFlip() {
        boolean should = Theme.liveAnim && backText != null && !edit && tile.kind != Tile.Kind.WIDGET;
        if (should == flipEnabled) return;
        flipEnabled = should;
        removeCallbacks(flipStep);
        if (should) {
            showingBack = false;
            postDelayed(flipStep, 2000L + Math.abs(tile.id.hashCode()) % 4000);
        } else {
            showingBack = false;
            if (flipAngle != 0f) animateFlip(0f);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        boolean want = flipEnabled;
        flipEnabled = false;
        if (want) updateFlip();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(flipStep);
        if (pressAnim != null) pressAnim.cancel();
        if (scaleAnim != null) scaleAnim.cancel();
        if (flipAnim != null) flipAnim.cancel();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------ dibujo

    private void txt(float sizeSp, android.graphics.Typeface tf, int color) {
        tp.setTextSize(Theme.sp(sizeSp));
        tp.setTypeface(tf);
        tp.setColor(color);
    }

    /** Título con los nombres de marca elegidos; las apps reales instaladas conservan su nombre. */
    private String shownTitle() {
        boolean branded = tile.kind == Tile.Kind.CORTANA
                || (tile.pkg != null && com.woba.slauncher.sim.SimRegistry.isSim(tile.pkg));
        return branded ? com.woba.slauncher.data.Brand.t(getContext(), tile.title) : tile.title;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (tile.kind == Tile.Kind.WIDGET) {
            fp.setStyle(Paint.Style.FILL);
            fp.setColor(0xFF1F1F1F);
            c.drawRect(0, 0, w, h, fp);
            if (edit) {
                txt(16, Theme.light, Color.WHITE);
                tp.setTextAlign(Paint.Align.CENTER);
                c.drawText(TextUtils.ellipsize(shownTitle(), tp, w - Theme.dp(24), TextUtils.TruncateAt.END).toString(),
                        w / 2f, Theme.centerBaseline(tp, h / 2f), tp);
                tp.setTextAlign(Paint.Align.LEFT);
            }
            drawEditButtons(c, w, h);
            return;
        }

        fp.setStyle(Paint.Style.FILL);
        fp.setColor(Theme.alpha(Theme.accent, Theme.tileAlpha));
        c.drawRect(0, 0, w, h, fp);

        if (flipAngle > 90f) {
            c.save();
            c.scale(1f, -1f, w / 2f, h / 2f);
            drawBack(c, w, h);
            c.restore();
        } else {
            drawFront(c, w, h);
        }
        if (edit && flipAngle < 90f) drawEditButtons(c, w, h);
    }

    private void drawFront(Canvas c, int w, int h) {
        boolean small = tile.size == Tile.Size.SMALL;
        boolean large = tile.size == Tile.Size.LARGE;
        float cx = w / 2f, cy = h / 2f;
        switch (tile.kind) {
            case APP: {
                if (com.woba.slauncher.sim.SimRegistry.isSim(tile.pkg)) {
                    com.woba.slauncher.sim.SimRegistry.Def d = com.woba.slauncher.sim.SimRegistry.get(com.woba.slauncher.sim.SimRegistry.idOf(tile.pkg));
                    if (d != null) Glyph.draw(c, d.glyph, cx, cy, Theme.dpf(small ? 28 : large ? 72 : 46), Color.WHITE);
                    break;
                }
                int px = Theme.dp(small ? 36 : large ? 88 : 56);
                Bitmap b = Sys.icon(getContext(), tile.pkg, px, redraw);
                if (b != null) c.drawBitmap(b, cx - px / 2f, cy - px / 2f, null);
                break;
            }
            case PHONE: glyph(c, w, h, Icons.PHONE, live.missed, small, large); break;
            case MESSAGES: glyph(c, w, h, Icons.MESSAGE, live.sms, small, large); break;
            case SETTINGS: glyph(c, w, h, Icons.GEAR, 0, small, large); break;
            case LOCK: glyph(c, w, h, Icons.LOCK, 0, small, large); break;
            case CORTANA: glyph(c, w, h, Icons.RING, 0, small, large); break;
            case BATTERY: {
                String t = live.battery + "%";
                float size = small ? 20 : large ? 54 : 34;
                txt(size, Theme.light, Color.WHITE);
                float tw = tp.measureText(t);
                float is = live.charging ? Theme.sp(size) * 0.8f : 0f;
                float gap = live.charging ? Theme.dpf(4) : 0f;
                float x0 = (w - (is + gap + tw)) / 2f;
                if (live.charging) Icons.draw(c, Icons.BOLT, x0 + is / 2f, cy, is, Color.WHITE);
                c.drawText(t, x0 + is + gap, Theme.centerBaseline(tp, cy), tp);
                break;
            }
            case CLOCK: {
                String time = Sys.timeText(getContext(), live.now);
                if (small) {
                    txt(20, Theme.light, Color.WHITE);
                    c.drawText(time, Theme.dpf(12), Theme.centerBaseline(tp, cy), tp);
                } else {
                    txt(54, Theme.light, Color.WHITE);
                    float timeCy = cy - Theme.dpf(7);
                    c.drawText(time, Theme.dpf(12), Theme.centerBaseline(tp, timeCy), tp);
                    txt(14, Theme.light, Color.WHITE);
                    float dateCy = timeCy + Theme.sp(54) * 0.55f + Theme.dpf(16);
                    String d = TextUtils.ellipsize(Sys.dateText(live.now), tp, w - Theme.dp(28),
                            TextUtils.TruncateAt.END).toString();
                    c.drawText(d, Theme.dpf(14), Theme.centerBaseline(tp, dateCy), tp);
                }
                break;
            }
            default:
                break;
        }
        // La etiqueta se oculta en tiles pequeños, como en Windows Phone.
        if (!small && Theme.tileLabels && tile.kind != Tile.Kind.CLOCK) {
            txt(13, Theme.regular, Color.WHITE);
            String t = TextUtils.ellipsize(shownTitle(), tp, w - Theme.dp(20), TextUtils.TruncateAt.END).toString();
            c.drawText(t, Theme.dpf(10), h - Theme.dpf(8) - tp.getFontMetrics().descent, tp);
        }
        if (tile.kind == Tile.Kind.APP) {
            int cnt = 0;
            for (Notif n : Notif.all()) if (tile.pkg.equals(n.pkg)) cnt++;
            if (cnt > 0) {
                txt(small ? 14 : 18, Theme.light, Color.WHITE);
                tp.setTextAlign(Paint.Align.RIGHT);
                c.drawText(String.valueOf(cnt), w - Theme.dpf(8), h - Theme.dpf(8) - tp.getFontMetrics().descent, tp);
                tp.setTextAlign(Paint.Align.LEFT);
            }
        }
    }

    private void glyph(Canvas c, int w, int h, int icon, int count, boolean small, boolean large) {
        float s = Theme.dpf(small ? 28 : large ? 72 : 46);
        float cy = h / 2f;
        String cnt = count > 0 ? String.valueOf(count) : null;
        float gap = Theme.dpf(8), tw = 0f;
        if (cnt != null) {
            txt(small ? 18 : 34, Theme.light, Color.WHITE);
            tw = tp.measureText(cnt);
        }
        float total = s + (cnt != null ? gap + tw : 0f);
        float x0 = (w - total) / 2f;
        Icons.draw(c, icon, x0 + s / 2f, cy, s, Color.WHITE);
        if (cnt != null) c.drawText(cnt, x0 + s + gap, Theme.centerBaseline(tp, cy), tp);
    }

    private void drawBack(Canvas c, int w, int h) {
        String text = backText == null ? "" : backText;
        txt(18, Theme.light, Color.WHITE);
        int tw = Math.max(1, w - Theme.dp(24));
        StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), tp, tw)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        c.save();
        c.translate(Theme.dpf(12), (h - sl.getHeight()) / 2f);
        sl.draw(c);
        c.restore();
    }

    // Centros de los botones de edición (coordenadas locales).
    float unpinCx() { return getWidth() - Theme.dpf(18); }
    float unpinCy() { return Theme.dpf(18); }
    float resizeCx() { return getWidth() - Theme.dpf(18); }
    float resizeCy() { return getHeight() - Theme.dpf(18); }
    boolean hasResize() { return tile.kind != Tile.Kind.WIDGET; }

    private void drawEditButtons(Canvas c, int w, int h) {
        if (!edit) return;
        fp.setStyle(Paint.Style.FILL);
        fp.setColor(Color.WHITE);
        float r = Theme.dpf(14);
        c.drawCircle(unpinCx(), unpinCy(), r, fp);
        Icons.draw(c, Icons.CLOSE, unpinCx(), unpinCy(), Theme.dpf(16), Color.BLACK);
        if (hasResize()) {
            fp.setColor(Color.WHITE);
            c.drawCircle(resizeCx(), resizeCy(), r, fp);
            Icons.draw(c, Icons.RESIZE, resizeCx(), resizeCy(), Theme.dpf(16), Color.BLACK);
        }
    }
}
