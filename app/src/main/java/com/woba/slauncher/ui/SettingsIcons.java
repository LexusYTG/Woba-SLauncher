package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Iconos de línea fina de la app Configuración de Windows 10 Mobile (categorías, engranaje,
 * lupa, chevron, check). Se dibujan en una cuadrícula de 28 unidades escalada a s píxeles.
 */
public final class SettingsIcons {
    private SettingsIcons() {}

    public static final int GEAR = 1, SYSTEM = 2, DEVICES = 3, NETWORK = 4, PERSONALIZATION = 5, APPS = 6,
            ACCOUNTS = 7, TIME = 8, EASE = 9, PRIVACY = 10, UPDATE = 11, EXTRAS = 12, SEARCH = 13,
            CHEVRON = 14, CHECK = 15;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final RectF R = new RectF();

    /** Dibuja el icono id en un cuadrado de lado s centrado en (cx, cy). sw = grosor del trazo en px. */
    public static void draw(Canvas c, int id, float cx, float cy, float s, int color, float sw) {
        float u = s / 28f;
        c.save();
        c.translate(cx - s / 2f, cy - s / 2f);
        c.scale(u, u);
        P.reset();
        P.setAntiAlias(true);
        P.setColor(color);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(sw / u);
        P.setStrokeCap(Paint.Cap.BUTT);
        P.setStrokeJoin(Paint.Join.MITER);
        switch (id) {
            case GEAR: gear(c); break;
            case SYSTEM: system(c, sw / u); break;
            case DEVICES: devices(c); break;
            case NETWORK: network(c); break;
            case PERSONALIZATION: personalization(c, sw / u); break;
            case APPS: apps(c); break;
            case ACCOUNTS: accounts(c); break;
            case TIME: time(c); break;
            case EASE: ease(c); break;
            case PRIVACY: privacy(c); break;
            case UPDATE: update(c); break;
            case EXTRAS: extras(c); break;
            case SEARCH: search(c, sw / u); break;
            case CHEVRON: chevron(c); break;
            case CHECK: check(c, sw / u); break;
            default: break;
        }
        c.restore();
    }

    private static void gear(Canvas c) {
        PA.reset();
        for (int i = 0; i < 8; i++) {
            float a = i * 45f;
            float[] ang = {a - 13f, a - 8f, a + 8f, a + 13f};
            float[] rad = {10f, 13f, 13f, 10f};
            for (int k = 0; k < 4; k++) {
                double t = Math.toRadians(ang[k]);
                float x = 14f + (float) (rad[k] * Math.cos(t));
                float y = 14f + (float) (rad[k] * Math.sin(t));
                if (i == 0 && k == 0) PA.moveTo(x, y);
                else PA.lineTo(x, y);
            }
        }
        PA.close();
        c.drawPath(PA, P);
        c.drawCircle(14f, 14f, 4.5f, P);
    }

    private static void system(Canvas c, float w) {
        c.drawRect(6.5f, 1.5f, 21.5f, 26.5f, P);
        P.setStrokeWidth(w * 1.6f);
        c.drawLine(11.5f, 22.5f, 16.5f, 22.5f, P);
    }

    private static void devices(Canvas c) {
        c.drawRect(1.5f, 2.5f, 19.5f, 13.5f, P);
        P.setStyle(Paint.Style.FILL);
        for (int row = 0; row < 2; row++) {
            float y = 5.2f + row * 3.6f;
            for (int k = 0; k < 4; k++) {
                float x = 4f + k * 3.6f;
                c.drawRect(x, y, x + 1.8f, y + 1.4f, P);
            }
        }
        P.setStyle(Paint.Style.STROKE);
        c.drawRect(11.5f, 10.5f, 25.5f, 26.5f, P);
        c.drawCircle(18.5f, 15.2f, 1.6f, P);
        c.drawCircle(18.5f, 21.3f, 3.2f, P);
    }

    private static void network(Canvas c) {
        c.drawCircle(14f, 14f, 12.5f, P);
        R.set(8f, 1.5f, 20f, 26.5f);
        c.drawOval(R, P);
        c.drawLine(14f, 1.5f, 14f, 26.5f, P);
        c.drawLine(1.5f, 14f, 26.5f, 14f, P);
        c.drawLine(3.8f, 7.5f, 24.2f, 7.5f, P);
        c.drawLine(3.8f, 20.5f, 24.2f, 20.5f, P);
    }

    private static void personalization(Canvas c, float w) {
        c.drawRect(1.5f, 4.5f, 22.5f, 20.5f, P);
        c.drawLine(12f, 20.5f, 12f, 25.5f, P);
        c.drawLine(6.5f, 25.5f, 17.5f, 25.5f, P);
        P.setStrokeWidth(w * 2.2f);
        c.drawLine(12f, 17f, 23.5f, 3.5f, P);
        P.setStrokeWidth(w);
        c.drawLine(9f, 19f, 12f, 17f, P);
    }

    private static void apps(Canvas c) {
        for (int i = 0; i < 4; i++) {
            float y = 3.5f + i * 7f;
            c.drawRect(1.5f, y - 2.2f, 5.7f, y + 2.2f, P);
            c.drawLine(10.5f, y, 26.5f, y, P);
        }
    }

    private static void accounts(Canvas c) {
        c.drawCircle(14f, 8f, 5.5f, P);
        PA.reset();
        PA.moveTo(2.5f, 27f);
        PA.quadTo(2.5f, 15f, 14f, 15f);
        PA.quadTo(25.5f, 15f, 25.5f, 27f);
        c.drawPath(PA, P);
    }

    private static void time(Canvas c) {
        c.drawCircle(10f, 10f, 8.5f, P);
        PA.reset();
        PA.moveTo(10f, 5f);
        PA.lineTo(10f, 10f);
        PA.lineTo(13.5f, 10f);
        c.drawPath(PA, P);
        P.setStyle(Paint.Style.FILL);
        P.setTextSize(13f);
        c.drawText("A", 1f, 27f, P);
        c.drawText("\u5B57", 14f, 27f, P);
    }

    private static void ease(Canvas c) {
        P.setPathEffect(new DashPathEffect(new float[]{3.2f, 2.6f}, 0f));
        c.drawCircle(14f, 15f, 11f, P);
        P.setPathEffect(null);
        c.drawLine(13f, 4f, 13f, 15f, P);
        PA.reset();
        PA.moveTo(9.5f, 11.5f);
        PA.lineTo(13f, 15f);
        PA.lineTo(16.5f, 11.5f);
        c.drawPath(PA, P);
        c.drawLine(13f, 15f, 26.5f, 15f, P);
        PA.reset();
        PA.moveTo(23f, 11.5f);
        PA.lineTo(26.5f, 15f);
        PA.lineTo(23f, 18.5f);
        c.drawPath(PA, P);
    }

    private static void privacy(Canvas c) {
        c.drawRect(3.5f, 12.5f, 24.5f, 26.5f, P);
        R.set(8f, 2f, 20f, 14f);
        c.drawArc(R, 180f, 180f, false, P);
        c.drawLine(8f, 8f, 8f, 12.5f, P);
        c.drawLine(20f, 8f, 20f, 12.5f, P);
    }

    private static void update(Canvas c) {
        R.set(3f, 3f, 25f, 25f);
        c.drawArc(R, 30f, 290f, false, P);
        float ex = 14f + 11f * (float) Math.cos(Math.toRadians(-40));
        float ey = 14f + 11f * (float) Math.sin(Math.toRadians(-40));
        double tan = Math.toRadians(50);
        PA.reset();
        PA.moveTo(ex + 5f * (float) Math.cos(tan + Math.toRadians(150)), ey + 5f * (float) Math.sin(tan + Math.toRadians(150)));
        PA.lineTo(ex, ey);
        PA.lineTo(ex + 5f * (float) Math.cos(tan - Math.toRadians(150)), ey + 5f * (float) Math.sin(tan - Math.toRadians(150)));
        c.drawPath(PA, P);
    }

    private static void extras(Canvas c) {
        c.drawRect(1.5f, 2.5f, 12.5f, 13.5f, P);
        c.drawRect(1.5f, 15.5f, 12.5f, 26.5f, P);
        c.drawRect(15.5f, 15.5f, 26.5f, 26.5f, P);
        PA.reset();
        PA.moveTo(21f, 1.5f);
        PA.lineTo(26.5f, 8f);
        PA.lineTo(21f, 14.5f);
        PA.lineTo(15.5f, 8f);
        PA.close();
        c.drawPath(PA, P);
    }

    private static void search(Canvas c, float w) {
        P.setStrokeWidth(w * 1.2f);
        c.drawCircle(16f, 11f, 7.5f, P);
        c.drawLine(10.5f, 16.5f, 2.5f, 24.5f, P);
    }

    private static void chevron(Canvas c) {
        PA.reset();
        PA.moveTo(4f, 9f);
        PA.lineTo(14f, 19f);
        PA.lineTo(24f, 9f);
        c.drawPath(PA, P);
    }

    private static void check(Canvas c, float w) {
        P.setStrokeWidth(w * 1.4f);
        PA.reset();
        PA.moveTo(4f, 15f);
        PA.lineTo(11f, 22f);
        PA.lineTo(24.5f, 6f);
        c.drawPath(PA, P);
    }

    /** Vista que pinta uno de los iconos. */
    public static class IconView extends View {
        private final int id;
        private int color;
        private final float stroke;

        public IconView(Context c, int id, int color) {
            super(c);
            this.id = id;
            this.color = color;
            this.stroke = Theme.dpf(1.5f);
        }

        public void setColor(int color) {
            this.color = color;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            SettingsIcons.draw(c, id, getWidth() / 2f, getHeight() / 2f, s, color, stroke);
        }
    }
}
