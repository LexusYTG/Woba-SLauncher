package com.woba.slauncher.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Iconos Metro dibujados con Canvas (sin depender de glifos de la fuente). Solo hilo UI. */
public final class Icons {
    private Icons() {}

    public static final int PHONE = 0, MESSAGE = 1, GEAR = 2, LOCK = 3, BOLT = 4,
            ARROW = 5, CLOSE = 6, RESIZE = 7, RING = 8;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF R = new RectF();

    /** Dibuja el icono centrado en (cx, cy) dentro de un cuadrado de lado s. */
    public static void draw(Canvas c, int kind, float cx, float cy, float s, int color) {
        P.setColor(color);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
        switch (kind) {
            case PHONE: phone(c, cx, cy, s); break;
            case MESSAGE: message(c, cx, cy, s); break;
            case GEAR: gear(c, cx, cy, s); break;
            case LOCK: lock(c, cx, cy, s); break;
            case BOLT: bolt(c, cx, cy, s); break;
            case ARROW: arrow(c, cx, cy, s); break;
            case CLOSE: close(c, cx, cy, s); break;
            case RESIZE: resize(c, cx, cy, s); break;
            case RING: ring(c, cx, cy, s); break;
            default: break;
        }
    }

    private static void phone(Canvas c, float cx, float cy, float s) {
        float ccx = cx + s * 0.30f, ccy = cy - s * 0.30f, r = s * 0.62f;
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(s * 0.17f);
        R.set(ccx - r, ccy - r, ccx + r, ccy + r);
        c.drawArc(R, 90, 90, false, P);
        P.setStrokeWidth(s * 0.27f);
        c.drawLine(ccx, ccy + r, ccx, ccy + r - s * 0.16f, P);
        c.drawLine(ccx - r, ccy, ccx - r + s * 0.16f, ccy, P);
    }

    private static void message(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(2f, s * 0.07f));
        float hw = s * 0.45f, hh = s * 0.31f;
        R.set(cx - hw, cy - hh, cx + hw, cy + hh);
        c.drawRect(R, P);
        PATH.reset();
        PATH.moveTo(cx - hw, cy - hh);
        PATH.lineTo(cx, cy + s * 0.06f);
        PATH.lineTo(cx + hw, cy - hh);
        c.drawPath(PATH, P);
    }

    private static void gear(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(s * 0.14f);
        c.drawCircle(cx, cy, s * 0.25f, P);
        P.setStrokeWidth(s * 0.15f);
        P.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            c.drawLine(cx + ca * s * 0.34f, cy + sa * s * 0.34f,
                    cx + ca * s * 0.48f, cy + sa * s * 0.48f, P);
        }
        P.setStrokeCap(Paint.Cap.ROUND);
    }

    private static void lock(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.FILL);
        R.set(cx - s * 0.30f, cy - s * 0.02f, cx + s * 0.30f, cy + s * 0.40f);
        c.drawRoundRect(R, s * 0.04f, s * 0.04f, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(s * 0.10f);
        float sx = s * 0.19f;
        R.set(cx - sx, cy - s * 0.42f, cx + sx, cy - s * 0.06f);
        c.drawArc(R, 180, 180, false, P);
        c.drawLine(cx - sx, cy - s * 0.24f, cx - sx, cy, P);
        c.drawLine(cx + sx, cy - s * 0.24f, cx + sx, cy, P);
    }

    private static void bolt(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.FILL);
        PATH.reset();
        PATH.moveTo(cx + 0.10f * s, cy - 0.5f * s);
        PATH.lineTo(cx - 0.24f * s, cy + 0.04f * s);
        PATH.lineTo(cx - 0.02f * s, cy + 0.04f * s);
        PATH.lineTo(cx - 0.10f * s, cy + 0.5f * s);
        PATH.lineTo(cx + 0.24f * s, cy - 0.06f * s);
        PATH.lineTo(cx + 0.02f * s, cy - 0.06f * s);
        PATH.close();
        c.drawPath(PATH, P);
    }

    private static void arrow(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(2f, s * 0.09f));
        c.drawLine(cx - s * 0.36f, cy, cx + s * 0.36f, cy, P);
        PATH.reset();
        PATH.moveTo(cx + s * 0.12f, cy - s * 0.24f);
        PATH.lineTo(cx + s * 0.36f, cy);
        PATH.lineTo(cx + s * 0.12f, cy + s * 0.24f);
        c.drawPath(PATH, P);
    }

    private static void ring(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(s * 0.13f);
        c.drawCircle(cx, cy, s * 0.36f, P);
    }

    private static void close(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(2f, s * 0.14f));
        float d = s * 0.3f;
        c.drawLine(cx - d, cy - d, cx + d, cy + d, P);
        c.drawLine(cx + d, cy - d, cx - d, cy + d, P);
    }

    private static void resize(Canvas c, float cx, float cy, float s) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(2f, s * 0.14f));
        float d = s * 0.3f;
        c.drawLine(cx - d, cy + d, cx + d, cy - d, P);
        PATH.reset();
        PATH.moveTo(cx - s * 0.02f, cy - d);
        PATH.lineTo(cx + d, cy - d);
        PATH.lineTo(cx + d, cy + s * 0.02f);
        c.drawPath(PATH, P);
    }
}
