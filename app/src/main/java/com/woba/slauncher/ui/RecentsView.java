package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Selector de aplicaciones recientes: tarjetas con captura; tocar abre, deslizar hacia arriba cierra. */
public class RecentsView extends FrameLayout {
    public static class Card {
        public String title;
        public int glyph;
        public Bitmap snap;
        public Object tag;
    }

    public interface Listener {
        void onOpen(Card c);
        void onClose(Card c);
        void onDismiss();
    }

    private final Listener l;
    private final LinearLayout row;
    private final TextView empty;

    public RecentsView(Context ctx, List<Card> cards, Listener l) {
        super(ctx);
        this.l = l;
        setBackgroundColor(Theme.alpha(Theme.bg, 0.94f));
        setClickable(true);
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(OVER_SCROLL_NEVER);
        row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Theme.dp(24), 0, Theme.dp(24), 0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(row, new HorizontalScrollView.LayoutParams(-2, -1));
        addView(hs, new LayoutParams(-1, -1));
        empty = WpUi.label(ctx, "no recent apps", 24, Theme.light, Theme.subtle);
        empty.setGravity(Gravity.CENTER);
        addView(empty, new LayoutParams(-1, -1));
        set(cards);
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { RecentsView.this.l.onDismiss(); }
        });
    }

    private void set(List<Card> cards) {
        row.removeAllViews();
        empty.setVisibility(cards.isEmpty() ? VISIBLE : GONE);
        for (int i = 0; i < cards.size(); i++) addCard(cards.get(i));
    }

    private void addCard(final Card c) {
        Context ctx = getContext();
        final CardView v = new CardView(ctx, c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(190), Theme.dp(360));
        lp.rightMargin = Theme.dp(16);
        row.addView(v, lp);
    }

    private class CardView extends View {
        final Card c;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float downX, downY;
        boolean vertical, moved;
        final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        CardView(Context ctx, Card c) { super(ctx); this.c = c; }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float top = Theme.dpf(34);
            p.setStyle(Paint.Style.FILL);
            Glyph.draw(cv, c.glyph, Theme.dpf(14), top / 2f, Theme.dpf(18), Theme.fg);
            p.setTypeface(Theme.regular);
            p.setTextSize(Theme.sp(14));
            p.setColor(Theme.fg);
            cv.drawText(c.title, Theme.dpf(30), Theme.centerBaseline(p, top / 2f), p);
            p.setColor(Theme.accent);
            cv.drawRect(0, top, w, h, p);
            if (c.snap != null) {
                cv.drawBitmap(c.snap, null, new android.graphics.RectF(0, top, w, h), p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dpf(1));
            p.setColor(Theme.alpha(Theme.fg, 0.4f));
            cv.drawRect(0, top, w, h - 1, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    vertical = false;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!vertical && !moved && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) && dy < 0) {
                        vertical = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (Math.abs(dx) > slop) moved = true;
                    if (vertical) {
                        setTranslationY(Math.min(0, dy));
                        setAlpha(Math.max(0.2f, 1f + dy / Theme.dpf(400)));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float dy = e.getRawY() - downY;
                    if (vertical) {
                        if (dy < -Theme.dpf(90)) {
                            animate().translationY(-getHeight()).alpha(0).setDuration(160).withEndAction(new Runnable() {
                                @Override public void run() {
                                    row.removeView(CardView.this);
                                    empty.setVisibility(row.getChildCount() == 0 ? VISIBLE : GONE);
                                    l.onClose(c);
                                }
                            }).start();
                        } else animate().translationY(0).alpha(1f).setDuration(140).start();
                    } else if (!moved) l.onOpen(c);
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    animate().translationY(0).alpha(1f).setDuration(140).start();
                    return true;
                default: return true;
            }
        }
    }
}
