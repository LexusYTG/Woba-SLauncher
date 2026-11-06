package com.woba.slauncher.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.woba.slauncher.data.Toggles;

/** Iconos de la barra de estado y de las acciones rápidas, dibujados con Canvas. */
public final class StatusIcons {
    private StatusIcons() {}

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final RectF R = new RectF();

    private static void stroke(int color, float w) {
        P.setColor(color);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
    }

    private static void fill(int color) {
        P.setColor(color);
        P.setStyle(Paint.Style.FILL);
    }

    /** Barras de señal: level 0..4, caben en un cuadrado de lado s. */
    public static void signal(Canvas c, float cx, float cy, float s, int level, int color) {
        float bw = s * 0.15f, gap = s * 0.07f;
        float x = cx - (4 * bw + 3 * gap) / 2f;
        for (int i = 0; i < 4; i++) {
            float h = s * (0.28f + 0.2f * i);
            fill(i < level ? color : Theme.alpha(color, 0.3f));
            c.drawRect(x, cy + s * 0.5f - h, x + bw, cy + s * 0.5f, P);
            x += bw + gap;
        }
    }

    /** Wi-Fi: tres arcos y un punto. */
    public static void wifi(Canvas c, float cx, float cy, float s, int color) {
        stroke(color, s * 0.11f);
        float ox = cx, oy = cy + s * 0.42f;
        for (int i = 1; i <= 3; i++) {
            float r = s * 0.25f * i;
            R.set(ox - r, oy - r, ox + r, oy + r);
            c.drawArc(R, 225f, 90f, false, P);
        }
        fill(color);
        c.drawCircle(ox, oy - s * 0.02f, s * 0.07f, P);
    }

    public static void bluetooth(Canvas c, float cx, float cy, float s, int color) {
        stroke(color, s * 0.1f);
        float h = s * 0.46f, w = s * 0.25f;
        PA.reset();
        PA.moveTo(cx - w, cy - h * 0.5f);
        PA.lineTo(cx + w, cy + h * 0.5f);
        PA.lineTo(cx, cy + h);
        PA.lineTo(cx, cy - h);
        PA.lineTo(cx + w, cy - h * 0.5f);
        PA.lineTo(cx - w, cy + h * 0.5f);
        c.drawPath(PA, P);
    }

    public static void airplane(Canvas c, float cx, float cy, float s, int color) {
        fill(color);
        float u = s / 10f;
        PA.reset();
        PA.moveTo(cx + 4.8f * u, cy);
        PA.lineTo(cx + 0.8f * u, cy - 1.0f * u);
        PA.lineTo(cx - 1.2f * u, cy - 4.8f * u);
        PA.lineTo(cx - 2.4f * u, cy - 4.8f * u);
        PA.lineTo(cx - 1.4f * u, cy - 0.7f * u);
        PA.lineTo(cx - 3.8f * u, cy - 0.5f * u);
        PA.lineTo(cx - 4.6f * u, cy - 2.2f * u);
        PA.lineTo(cx - 5.0f * u, cy - 2.2f * u);
        PA.lineTo(cx - 4.6f * u, cy);
        PA.lineTo(cx - 5.0f * u, cy + 2.2f * u);
        PA.lineTo(cx - 4.6f * u, cy + 2.2f * u);
        PA.lineTo(cx - 3.8f * u, cy + 0.5f * u);
        PA.lineTo(cx - 1.4f * u, cy + 0.7f * u);
        PA.lineTo(cx - 2.4f * u, cy + 4.8f * u);
        PA.lineTo(cx - 1.2f * u, cy + 4.8f * u);
        PA.lineTo(cx + 0.8f * u, cy + 1.0f * u);
        PA.close();
        c.drawPath(PA, P);
    }

    public static void moon(Canvas c, float cx, float cy, float s, int color) {
        fill(color);
        float r = s * 0.38f;
        PA.reset();
        PA.addCircle(cx, cy, r, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(cx + r * 0.6f, cy - r * 0.3f, r * 0.85f, Path.Direction.CW);
        PA.op(cut, Path.Op.DIFFERENCE);
        c.drawPath(PA, P);
    }

    public static void rotationLock(Canvas c, float cx, float cy, float s, int color) {
        stroke(color, s * 0.1f);
        float r = s * 0.36f;
        R.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(R, 200f, 250f, false, P);
        fill(color);
        c.drawRect(cx - s * 0.17f, cy - s * 0.06f, cx + s * 0.17f, cy + s * 0.22f, P);
        stroke(color, s * 0.07f);
        R.set(cx - s * 0.1f, cy - s * 0.22f, cx + s * 0.1f, cy + s * 0.04f);
        c.drawArc(R, 180f, 180f, false, P);
    }

    public static void location(Canvas c, float cx, float cy, float s, int color) {
        stroke(color, s * 0.1f);
        c.drawCircle(cx, cy, s * 0.36f, P);
        fill(color);
        c.drawCircle(cx, cy, s * 0.17f, P);
    }

    public static void saver(Canvas c, float cx, float cy, float s, int color) {
        fill(color);
        float w = s * 0.34f, h = s * 0.44f;
        PA.reset();
        PA.moveTo(cx, cy - h);
        PA.lineTo(cx + w, cy - h * 0.6f);
        PA.lineTo(cx + w, cy + h * 0.1f);
        PA.quadTo(cx + w, cy + h * 0.7f, cx, cy + h);
        PA.quadTo(cx - w, cy + h * 0.7f, cx - w, cy + h * 0.1f);
        PA.lineTo(cx - w, cy - h * 0.6f);
        PA.close();
        c.drawPath(PA, P);
    }

    public static void sharing(Canvas c, float cx, float cy, float s, int color) {
        fill(color);
        c.drawCircle(cx, cy, s * 0.07f, P);
        stroke(color, s * 0.08f);
        for (int i = 1; i <= 2; i++) {
            float r = s * 0.16f * i + s * 0.04f;
            R.set(cx - r, cy - r, cx + r, cy + r);
            c.drawArc(R, -50f, 100f, false, P);
            c.drawArc(R, 130f, 100f, false, P);
        }
    }

    /** Icono de una acción rápida por id de Toggles. */
    public static void toggle(Canvas c, int id, float cx, float cy, float s, int color) {
        switch (id) {
            case Toggles.WIFI: wifi(c, cx, cy, s, color); break;
            case Toggles.BLUETOOTH: bluetooth(c, cx, cy, s, color); break;
            case Toggles.AIRPLANE: airplane(c, cx, cy, s, color); break;
            case Toggles.ROTATION: rotationLock(c, cx, cy, s, color); break;
            case Toggles.QUIET: moon(c, cx, cy, s, color); break;
            case Toggles.LOCATION: location(c, cx, cy, s, color); break;
            case Toggles.SAVER: saver(c, cx, cy, s, color); break;
            case Toggles.SHARING: sharing(c, cx, cy, s, color); break;
            default: break;
        }
    }

    /** Aviso de notificaciones sin leer (el icono "alert" de la systray de WP). */
    public static void alert(Canvas c, float cx, float cy, float s, int color) {
        fill(color);
        float w = s * 0.62f, h = s * 0.5f;
        c.drawRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, P);
        fill(Theme.bg);
        float lh = s * 0.07f;
        c.drawRect(cx - w * 0.3f, cy - h * 0.2f - lh / 2f, cx + w * 0.3f, cy - h * 0.2f + lh / 2f, P);
        c.drawRect(cx - w * 0.3f, cy + h * 0.15f - lh / 2f, cx + w * 0.3f, cy + h * 0.15f + lh / 2f, P);
    }

    /** Batería horizontal estilo WP: contorno, relleno y pastilla; rayo si carga. */
    public static void battery(Canvas c, float cx, float cy, float s, int pct, boolean charging, int color) {
        float w = s * 0.95f, h = s * 0.5f;
        float l = cx - w / 2f, t = cy - h / 2f;
        stroke(color, s * 0.07f);
        R.set(l, t, l + w, t + h);
        c.drawRect(R, P);
        fill(color);
        c.drawRect(l + w, cy - h * 0.2f, l + w + s * 0.08f, cy + h * 0.2f, P);
        float inner = (w - s * 0.16f) * Math.max(0, Math.min(100, pct)) / 100f;
        c.drawRect(l + s * 0.08f, t + s * 0.08f, l + s * 0.08f + inner, t + h - s * 0.08f, P);
        if (charging) {
            fill(Theme.bg);
            PA.reset();
            PA.moveTo(cx + s * 0.04f, t + s * 0.02f);
            PA.lineTo(cx - s * 0.12f, cy + s * 0.04f);
            PA.lineTo(cx - s * 0.01f, cy + s * 0.04f);
            PA.lineTo(cx - s * 0.05f, t + h - s * 0.02f);
            PA.lineTo(cx + s * 0.13f, cy - s * 0.05f);
            PA.lineTo(cx + s * 0.02f, cy - s * 0.05f);
            PA.close();
            c.drawPath(PA, P);
        }
    }
}
