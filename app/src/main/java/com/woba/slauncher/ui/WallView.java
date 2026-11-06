package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.woba.slauncher.data.Wallpaper;

/** Capa de fondo del Start: se desvanece al pasar a la lista de apps. */
public class WallView extends View {
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dim = new Paint();
    private float fraction, scrollY;

    public WallView(Context c) {
        super(c);
    }

    /** Desplazamiento del Start: el fondo se mueve más despacio (parallax). */
    public void setScroll(float y) {
        scrollY = y;
        if (Wallpaper.start != null) invalidate();
    }

    public void setFraction(float f) {
        fraction = Math.max(0f, Math.min(1f, f));
        if (Wallpaper.start != null) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        Bitmap b = Wallpaper.start;
        if (b == null) return;
        float a = 1f - fraction;
        if (a <= 0f) return;
        int w = getWidth(), h = getHeight();
        p.setAlpha(Math.round(255 * a));
        float extra = Theme.dpf(96);
        float off = -Math.min(extra, Math.max(0f, scrollY) * 0.12f);
        c.save();
        c.translate(0f, off);
        Wallpaper.drawCrop(c, b, w, h + Math.round(extra), p);
        c.restore();
        dim.setColor(Theme.alpha(Theme.bg, 0.25f * a));
        c.drawRect(0, 0, w, h, dim);
    }
}
