package com.woba.slauncher.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Glifos Metro de trazo fino para la barra de aplicación y los tiles de las apps simuladas.
 * Se dibujan con Canvas dentro de un cuadrado de lado s centrado en (cx, cy). Solo hilo UI.
 */
public final class Glyph {
    private Glyph() {}

    public static final int ADD = 1, SEARCH = 2, LEFT = 3, RIGHT = 4, UP = 5, DOWN = 6, CHECK = 7, CLOSE = 8,
            PEOPLE = 9, PERSON = 10, SPEECH = 11, MAIL = 12, CALENDAR = 13, CAMERA = 14, PHOTOS = 15,
            GLOBE = 16, STORE = 17, PIN = 18, MUSIC = 19, VIDEO = 20, RADIO = 21, FOLDER = 22, CALC = 23,
            ALARM = 24, WALLET = 25, XBOX = 26, HELP = 27, NOTE = 28, WORD = 29, EXCEL = 30, PPT = 31,
            CLOUD = 32, WEATHER = 33, SUN = 34, PLAY = 35, PAUSE = 36, STOP = 37, NEXT = 38, PREV = 39,
            SHUFFLE = 40, REPEAT = 41, DELETE = 42, SHARE = 43, EDIT = 44, SAVE = 45, REFRESH = 46,
            STAR = 47, STAR_FILL = 48, MORE = 49, FILTER = 50, WINDOWS = 51, MIC = 52, SPEAKER = 53,
            MUTE = 54, KEYPAD = 55, HOLD = 56, DIAL = 57, ENDCALL = 58, TABS = 59, FLASH = 60, ONEDRIVE = 61,
            ATTACH = 62, SEND = 63, REPLY = 64, FLAG = 65, ZOOM_IN = 66, ZOOM_OUT = 67, BACKSPACE = 68,
            PHONE = 69, GEAR = 70, LOCK = 71, RING = 72, BATTERY = 73, KID = 74, DATA = 75, STORAGE = 76,
            HEART = 77, CROSSHAIR = 78, SORT = 79, LIST = 80, GRID = 81, TIMER = 82, STOPWATCH = 83,
            GAMEPAD = 84, TIPS = 85, BOOK = 86, SLIDES = 87, TABLE = 88, TEXT = 89, VOICEMAIL = 90,
            SPEAKER_OFF = 91, ROTATE = 92, FULLSCREEN = 93, PAGE = 94, NEW_TAB = 95, READER = 96;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PA = new Path();
    private static final RectF R = new RectF();

    private static float u, X, Y;

    private static void stroke(float w) {
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(w);
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setStrokeJoin(Paint.Join.ROUND);
    }

    private static void fill() { P.setStyle(Paint.Style.FILL); }

    private static float px(float v) { return X + v * u; }

    private static float py(float v) { return Y + v * u; }

    private static void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(px(x1), py(y1), px(x2), py(y2), P);
    }

    private static void rect(Canvas c, float l, float t, float r, float b) { c.drawRect(px(l), py(t), px(r), py(b), P); }

    private static void rrect(Canvas c, float l, float t, float r, float b, float rad) {
        R.set(px(l), py(t), px(r), py(b));
        c.drawRoundRect(R, rad * u, rad * u, P);
    }

    private static void circle(Canvas c, float x, float y, float r) { c.drawCircle(px(x), py(y), r * u, P); }

    private static void arc(Canvas c, float x, float y, float r, float start, float sweep) {
        R.set(px(x - r), py(y - r), px(x + r), py(y + r));
        c.drawArc(R, start, sweep, false, P);
    }

    private static void begin() { PA.reset(); }

    private static void m(float x, float y) { PA.moveTo(px(x), py(y)); }

    private static void l(float x, float y) { PA.lineTo(px(x), py(y)); }

    private static void close() { PA.close(); }

    private static void quad(float cx, float cy, float x, float y) { PA.quadTo(px(cx), py(cy), px(x), py(y)); }

    private static void path(Canvas c) { c.drawPath(PA, P); }

    private static void letter(Canvas c, String s, float size, float x, float y) {
        P.setStyle(Paint.Style.FILL);
        P.setTypeface(Typeface.DEFAULT_BOLD);
        P.setTextSize(size * u);
        P.setTextAlign(Paint.Align.CENTER);
        c.drawText(s, px(x), py(y) - (P.ascent() + P.descent()) / 2f, P);
        P.setTextAlign(Paint.Align.LEFT);
        P.setTypeface(null);
    }

    /** Dibuja el glifo id centrado en (cx,cy) dentro de un cuadrado de lado s, con color y trazo ~s/12. */
    public static void draw(Canvas c, int id, float cx, float cy, float s, int color) {
        draw(c, id, cx, cy, s, color, Math.max(Theme.dpf(1.6f), s * 0.075f));
    }

    public static void draw(Canvas c, int id, float cx, float cy, float s, int color, float sw) {
        P.setColor(color);
        P.setPathEffect(null);
        u = s / 2f;
        X = cx;
        Y = cy;
        stroke(sw);
        switch (id) {
            case ADD: line(c, -.7f, 0, .7f, 0); line(c, 0, -.7f, 0, .7f); break;
            case SEARCH: circle(c, -.2f, -.2f, .55f); line(c, .2f, .2f, .8f, .8f); break;
            case LEFT: begin(); m(.2f, -.6f); l(-.4f, 0); l(.2f, .6f); path(c); line(c, -.4f, 0, .8f, 0); break;
            case RIGHT: begin(); m(-.2f, -.6f); l(.4f, 0); l(-.2f, .6f); path(c); line(c, .4f, 0, -.8f, 0); break;
            case UP: begin(); m(-.6f, .2f); l(0, -.4f); l(.6f, .2f); path(c); line(c, 0, -.4f, 0, .8f); break;
            case DOWN: begin(); m(-.6f, -.2f); l(0, .4f); l(.6f, -.2f); path(c); line(c, 0, .4f, 0, -.8f); break;
            case CHECK: begin(); m(-.7f, 0.05f); l(-.2f, .55f); l(.7f, -.5f); path(c); break;
            case CLOSE: line(c, -.6f, -.6f, .6f, .6f); line(c, .6f, -.6f, -.6f, .6f); break;
            case PEOPLE:
                circle(c, -.28f, -.35f, .26f);
                begin(); m(-.8f, .6f); quad(-.8f, .05f, -.28f, .05f); quad(.24f, .05f, .24f, .6f); path(c);
                circle(c, .42f, -.2f, .22f);
                begin(); m(.5f, .1f); quad(.9f, .1f, .9f, .6f); path(c);
                break;
            case PERSON:
                circle(c, 0, -.38f, .3f);
                begin(); m(-.65f, .75f); quad(-.65f, .1f, 0, .1f); quad(.65f, .1f, .65f, .75f); close(); path(c);
                break;
            case SPEECH:
                begin(); m(-.8f, -.6f); l(.8f, -.6f); l(.8f, .3f); l(-.1f, .3f); l(-.5f, .7f); l(-.5f, .3f); l(-.8f, .3f); close(); path(c);
                break;
            case MAIL:
                rect(c, -.8f, -.55f, .8f, .55f);
                begin(); m(-.8f, -.55f); l(0, .1f); l(.8f, -.55f); path(c);
                break;
            case CALENDAR:
                rect(c, -.75f, -.6f, .75f, .75f);
                line(c, -.75f, -.2f, .75f, -.2f);
                line(c, -.4f, -.85f, -.4f, -.4f); line(c, .4f, -.85f, .4f, -.4f);
                fill();
                for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) c.drawRect(px(-.55f + i * .4f), py(.0f + j * .35f), px(-.35f + i * .4f), py(.2f + j * .35f), P);
                break;
            case CAMERA:
                rrect(c, -.8f, -.45f, .8f, .6f, .08f);
                begin(); m(-.3f, -.45f); l(-.15f, -.7f); l(.15f, -.7f); l(.3f, -.45f); path(c);
                circle(c, 0, .08f, .32f);
                break;
            case PHOTOS:
                rect(c, -.8f, -.6f, .8f, .6f);
                begin(); m(-.8f, .5f); l(-.25f, -.1f); l(.1f, .3f); l(.35f, 0f); l(.8f, .5f); path(c);
                circle(c, .35f, -.3f, .12f);
                break;
            case GLOBE:
                circle(c, 0, 0, .8f);
                begin(); m(-.8f, 0); l(.8f, 0); path(c);
                R.set(px(-.38f), py(-.8f), px(.38f), py(.8f));
                c.drawOval(R, P);
                line(c, 0, -.8f, 0, .8f);
                break;
            case STORE:
                begin(); m(-.7f, -.3f); l(.7f, -.3f); l(.8f, .8f); l(-.8f, .8f); close(); path(c);
                arc(c, 0, -.3f, .35f, 180, 180);
                break;
            case PIN:
                begin(); m(0, .9f); quad(-.75f, -.1f, -.55f, -.5f); quad(-.2f, -.9f, 0, -.9f); quad(.2f, -.9f, .55f, -.5f); quad(.75f, -.1f, 0, .9f); close(); path(c);
                circle(c, 0, -.35f, .22f);
                break;
            case MUSIC:
                circle(c, -.35f, .45f, .25f);
                circle(c, .5f, .25f, .25f);
                line(c, -.1f, .45f, -.1f, -.65f); line(c, .75f, .25f, .75f, -.85f);
                line(c, -.1f, -.65f, .75f, -.85f);
                break;
            case VIDEO:
                rect(c, -.85f, -.55f, .85f, .55f);
                fill(); begin(); m(-.2f, -.3f); l(.35f, 0); l(-.2f, .3f); close(); path(c);
                break;
            case RADIO:
                rect(c, -.85f, -.3f, .85f, .7f);
                line(c, -.4f, -.3f, .6f, -.8f);
                circle(c, .35f, .2f, .25f);
                line(c, -.65f, .05f, -.2f, .05f); line(c, -.65f, .3f, -.2f, .3f);
                break;
            case FOLDER:
                begin(); m(-.85f, -.55f); l(-.2f, -.55f); l(0, -.3f); l(.85f, -.3f); l(.85f, .6f); l(-.85f, .6f); close(); path(c);
                break;
            case CALC:
                rect(c, -.6f, -.8f, .6f, .8f);
                rect(c, -.4f, -.6f, .4f, -.3f);
                fill();
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) c.drawRect(px(-.4f + i * .3f), py(-.1f + j * .28f), px(-.25f + i * .3f), py(.05f + j * .28f), P);
                break;
            case ALARM:
                circle(c, 0, .1f, .62f);
                line(c, 0, .1f, 0, -.3f); line(c, 0, .1f, .28f, .25f);
                line(c, -.75f, -.6f, -.4f, -.85f); line(c, .75f, -.6f, .4f, -.85f);
                break;
            case WALLET:
                rrect(c, -.85f, -.45f, .85f, .65f, .1f);
                begin(); m(-.7f, -.45f); l(.45f, -.8f); l(.55f, -.45f); path(c);
                circle(c, .5f, .1f, .09f);
                break;
            case XBOX:
                circle(c, 0, 0, .8f);
                begin(); m(-.45f, -.5f); quad(0, 0f, .45f, .5f); path(c);
                begin(); m(.45f, -.5f); quad(0, 0f, -.45f, .5f); path(c);
                break;
            case HELP:
                circle(c, 0, 0, .8f);
                letter(c, "?", 1.1f, 0, 0);
                break;
            case NOTE:
                rect(c, -.6f, -.8f, .6f, .8f);
                line(c, -.3f, -.4f, .3f, -.4f); line(c, -.3f, -.05f, .3f, -.05f); line(c, -.3f, .3f, .1f, .3f);
                break;
            case WORD: fill(); rrect(c, -.8f, -.8f, .8f, .8f, .08f); P.setColor(0xFFFFFFFF); letter(c, "W", 1.2f, 0, 0); break;
            case EXCEL: fill(); rrect(c, -.8f, -.8f, .8f, .8f, .08f); P.setColor(0xFFFFFFFF); letter(c, "X", 1.2f, 0, 0); break;
            case PPT: fill(); rrect(c, -.8f, -.8f, .8f, .8f, .08f); P.setColor(0xFFFFFFFF); letter(c, "P", 1.2f, 0, 0); break;
            case CLOUD:
            case ONEDRIVE:
                begin(); m(-.6f, .5f); quad(-1.0f, .5f, -.85f, .05f); quad(-.7f, -.2f, -.4f, -.15f);
                quad(-.3f, -.75f, .25f, -.55f); quad(.4f, -.45f, .45f, -.15f);
                quad(.95f, -.1f, .75f, .5f); close(); path(c);
                break;
            case WEATHER:
                circle(c, -.25f, -.3f, .3f);
                line(c, -.25f, -.85f, -.25f, -.72f); line(c, -.8f, -.3f, -.67f, -.3f); line(c, -.64f, -.69f, -.55f, -.6f);
                begin(); m(-.3f, .6f); quad(-.7f, .6f, -.55f, .25f); quad(-.2f, .0f, .1f, .15f);
                quad(.3f, -.1f, .55f, .1f); quad(.95f, .1f, .8f, .6f); close(); path(c);
                break;
            case SUN:
                circle(c, 0, 0, .35f);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0);
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    line(c, ca * .6f, sa * .6f, ca * .85f, sa * .85f);
                }
                break;
            case PLAY: fill(); begin(); m(-.4f, -.65f); l(.7f, 0); l(-.4f, .65f); close(); path(c); break;
            case PAUSE: fill(); rect(c, -.5f, -.6f, -.15f, .6f); rect(c, .15f, -.6f, .5f, .6f); break;
            case STOP: fill(); rect(c, -.55f, -.55f, .55f, .55f); break;
            case NEXT: fill(); begin(); m(-.7f, -.55f); l(.3f, 0); l(-.7f, .55f); close(); path(c); rect(c, .4f, -.55f, .7f, .55f); break;
            case PREV: fill(); begin(); m(.7f, -.55f); l(-.3f, 0); l(.7f, .55f); close(); path(c); rect(c, -.7f, -.55f, -.4f, .55f); break;
            case SHUFFLE:
                begin(); m(-.8f, -.45f); l(-.3f, -.45f); l(.3f, .45f); l(.7f, .45f); path(c);
                begin(); m(-.8f, .45f); l(-.3f, .45f); l(-.1f, .15f); path(c);
                begin(); m(.1f, -.15f); l(.3f, -.45f); l(.7f, -.45f); path(c);
                begin(); m(.5f, -.7f); l(.8f, -.45f); l(.5f, -.2f); path(c);
                begin(); m(.5f, .2f); l(.8f, .45f); l(.5f, .7f); path(c);
                break;
            case REPEAT:
                begin(); m(-.8f, 0); l(-.8f, -.25f); quad(-.8f, -.5f, -.5f, -.5f); l(.6f, -.5f); path(c);
                begin(); m(.35f, -.75f); l(.65f, -.5f); l(.35f, -.25f); path(c);
                begin(); m(.8f, 0); l(.8f, .25f); quad(.8f, .5f, .5f, .5f); l(-.6f, .5f); path(c);
                begin(); m(-.35f, .25f); l(-.65f, .5f); l(-.35f, .75f); path(c);
                break;
            case DELETE:
                line(c, -.7f, -.55f, .7f, -.55f);
                begin(); m(-.5f, -.55f); l(-.4f, .8f); l(.4f, .8f); l(.5f, -.55f); path(c);
                begin(); m(-.25f, -.55f); l(-.25f, -.8f); l(.25f, -.8f); l(.25f, -.55f); path(c);
                line(c, 0, -.3f, 0, .55f);
                break;
            case SHARE:
                circle(c, -.55f, 0, .2f); circle(c, .5f, -.5f, .2f); circle(c, .5f, .5f, .2f);
                line(c, -.38f, -.1f, .35f, -.42f); line(c, -.38f, .1f, .35f, .42f);
                break;
            case EDIT:
                begin(); m(-.7f, .7f); l(-.6f, .25f); l(.4f, -.75f); l(.75f, -.4f); l(-.25f, .6f); close(); path(c);
                line(c, .15f, -.5f, .5f, -.15f);
                break;
            case SAVE:
                begin(); m(-.75f, -.75f); l(.5f, -.75f); l(.75f, -.5f); l(.75f, .75f); l(-.75f, .75f); close(); path(c);
                rect(c, -.4f, -.75f, .3f, -.2f); rect(c, -.4f, .15f, .4f, .75f);
                break;
            case REFRESH:
                arc(c, 0, 0, .65f, -60, 280);
                begin(); m(.5f, -.8f); l(.55f, -.35f); l(.1f, -.4f); path(c);
                break;
            case STAR:
            case STAR_FILL: {
                begin();
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36);
                    float r = (i % 2 == 0) ? .85f : .36f;
                    float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r;
                    if (i == 0) m(x, y); else l(x, y);
                }
                close();
                if (id == STAR_FILL) fill();
                path(c);
                break;
            }
            case MORE: fill(); circle(c, -.6f, 0, .12f); circle(c, 0, 0, .12f); circle(c, .6f, 0, .12f); break;
            case FILTER: begin(); m(-.8f, -.7f); l(.8f, -.7f); l(.15f, .05f); l(.15f, .75f); l(-.15f, .55f); l(-.15f, .05f); close(); path(c); break;
            case WINDOWS:
                fill();
                rect(c, -.8f, -.7f, -.05f, -.05f); rect(c, .05f, -.8f, .8f, -.05f);
                rect(c, -.8f, .05f, -.05f, .7f); rect(c, .05f, .05f, .8f, .8f);
                break;
            case MIC:
                rrect(c, -.25f, -.8f, .25f, .15f, .25f);
                arc(c, 0, .05f, .55f, 0, 180);
                line(c, 0, .6f, 0, .85f);
                break;
            case SPEAKER:
                begin(); m(-.8f, -.25f); l(-.4f, -.25f); l(.05f, -.65f); l(.05f, .65f); l(-.4f, .25f); l(-.8f, .25f); close(); path(c);
                arc(c, .05f, 0, .4f, -50, 100); arc(c, .05f, 0, .7f, -50, 100);
                break;
            case SPEAKER_OFF:
                begin(); m(-.8f, -.25f); l(-.4f, -.25f); l(.05f, -.65f); l(.05f, .65f); l(-.4f, .25f); l(-.8f, .25f); close(); path(c);
                line(c, .3f, -.3f, .8f, .3f); line(c, .8f, -.3f, .3f, .3f);
                break;
            case MUTE:
                rrect(c, -.25f, -.8f, .25f, .15f, .25f);
                arc(c, 0, .05f, .55f, 0, 180);
                line(c, -.8f, -.8f, .8f, .8f);
                break;
            case KEYPAD:
                fill();
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) circle(c, -.6f + i * .6f, -.6f + j * .6f, .1f);
                break;
            case HOLD: fill(); rect(c, -.45f, -.6f, -.15f, .6f); rect(c, .15f, -.6f, .45f, .6f); break;
            case DIAL:
                Icons.draw(c, Icons.PHONE, cx, cy, s * 0.9f, color);
                break;
            case ENDCALL:
                c.save();
                c.rotate(135f, cx, cy);
                Icons.draw(c, Icons.PHONE, cx, cy, s * 0.9f, color);
                c.restore();
                break;
            case TABS: rect(c, -.7f, -.55f, .35f, .55f); begin(); m(-.35f, -.55f); l(-.35f, -.8f); l(.7f, -.8f); l(.7f, .3f); l(.35f, .3f); path(c); break;
            case NEW_TAB: rect(c, -.7f, -.7f, .7f, .7f); line(c, 0, -.35f, 0, .35f); line(c, -.35f, 0, .35f, 0); break;
            case FLASH: Icons.draw(c, Icons.BOLT, cx, cy, s, color); break;
            case ATTACH:
                begin(); m(.45f, -.5f); l(-.4f, .4f); quad(-.75f, .6f, -.8f, .1f); l(.2f, -.85f); quad(.7f, -.9f, .6f, -.45f); l(-.45f, .65f); path(c);
                break;
            case SEND: begin(); m(-.8f, -.6f); l(.85f, 0); l(-.8f, .6f); l(-.5f, 0); close(); path(c); break;
            case REPLY: begin(); m(.2f, -.6f); l(-.6f, 0); l(.2f, .6f); path(c); begin(); m(-.6f, 0); l(.2f, 0); quad(.8f, 0, .8f, .7f); path(c); break;
            case FLAG: line(c, -.6f, -.8f, -.6f, .8f); begin(); m(-.6f, -.8f); l(.7f, -.8f); l(.3f, -.4f); l(.7f, 0); l(-.6f, 0); path(c); break;
            case ZOOM_IN: circle(c, -.15f, -.15f, .55f); line(c, .25f, .25f, .8f, .8f); line(c, -.4f, -.15f, .1f, -.15f); line(c, -.15f, -.4f, -.15f, .1f); break;
            case ZOOM_OUT: circle(c, -.15f, -.15f, .55f); line(c, .25f, .25f, .8f, .8f); line(c, -.4f, -.15f, .1f, -.15f); break;
            case BACKSPACE:
                begin(); m(-.35f, -.55f); l(.8f, -.55f); l(.8f, .55f); l(-.35f, .55f); l(-.85f, 0); close(); path(c);
                line(c, 0, -.2f, .45f, .2f); line(c, .45f, -.2f, 0, .2f);
                break;
            case PHONE: Icons.draw(c, Icons.PHONE, cx, cy, s, color); break;
            case GEAR: Icons.draw(c, Icons.GEAR, cx, cy, s, color); break;
            case LOCK: Icons.draw(c, Icons.LOCK, cx, cy, s, color); break;
            case RING: Icons.draw(c, Icons.RING, cx, cy, s, color); break;
            case BATTERY: rect(c, -.8f, -.4f, .6f, .4f); fill(); rect(c, .6f, -.15f, .8f, .15f); rect(c, -.65f, -.25f, .1f, .25f); break;
            case KID: circle(c, 0, -.15f, .5f); circle(c, -.18f, -.2f, .05f); circle(c, .18f, -.2f, .05f); arc(c, 0, -.05f, .25f, 20, 140); circle(c, -.5f, -.55f, .18f); circle(c, .5f, -.55f, .18f); break;
            case DATA: begin(); m(-.5f, .7f); l(-.5f, -.5f); path(c); begin(); m(-.8f, -.2f); l(-.5f, -.5f); l(-.2f, -.2f); path(c); begin(); m(.5f, -.7f); l(.5f, .5f); path(c); begin(); m(.2f, .2f); l(.5f, .5f); l(.8f, .2f); path(c); break;
            case STORAGE: rect(c, -.8f, -.45f, .8f, .45f); fill(); circle(c, .5f, 0, .09f); stroke(sw); line(c, -.6f, 0, .1f, 0); break;
            case HEART:
                begin(); m(0, .75f); quad(-1.1f, -.2f, -.5f, -.6f); quad(-.1f, -.8f, 0, -.35f); quad(.1f, -.8f, .5f, -.6f); quad(1.1f, -.2f, 0, .75f); close(); path(c);
                break;
            case CROSSHAIR: circle(c, 0, 0, .5f); line(c, -.85f, 0, -.5f, 0); line(c, .5f, 0, .85f, 0); line(c, 0, -.85f, 0, -.5f); line(c, 0, .5f, 0, .85f); break;
            case SORT: line(c, -.7f, -.5f, .7f, -.5f); line(c, -.7f, 0, .3f, 0); line(c, -.7f, .5f, -.1f, .5f); break;
            case LIST: line(c, -.2f, -.5f, .8f, -.5f); line(c, -.2f, 0, .8f, 0); line(c, -.2f, .5f, .8f, .5f); fill(); circle(c, -.65f, -.5f, .08f); circle(c, -.65f, 0, .08f); circle(c, -.65f, .5f, .08f); break;
            case GRID: rect(c, -.7f, -.7f, -.1f, -.1f); rect(c, .1f, -.7f, .7f, -.1f); rect(c, -.7f, .1f, -.1f, .7f); rect(c, .1f, .1f, .7f, .7f); break;
            case TIMER: circle(c, 0, .1f, .65f); line(c, 0, .1f, 0, -.3f); line(c, -.2f, -.85f, .2f, -.85f); break;
            case STOPWATCH: circle(c, 0, .15f, .6f); line(c, 0, .15f, .25f, -.2f); line(c, -.2f, -.8f, .2f, -.8f); line(c, 0, -.8f, 0, -.45f); break;
            case GAMEPAD: rrect(c, -.85f, -.4f, .85f, .5f, .35f); line(c, -.5f, -.1f, -.5f, .2f); line(c, -.65f, .05f, -.35f, .05f); fill(); circle(c, .35f, -.02f, .08f); circle(c, .55f, .15f, .08f); break;
            case TIPS: circle(c, 0, -.2f, .5f); line(c, -.2f, .55f, .2f, .55f); line(c, -.15f, .8f, .15f, .8f); break;
            case BOOK: begin(); m(-.8f, -.6f); l(0, -.4f); l(.8f, -.6f); l(.8f, .5f); l(0, .7f); l(-.8f, .5f); close(); path(c); line(c, 0, -.4f, 0, .7f); break;
            case SLIDES: rect(c, -.8f, -.55f, .8f, .35f); line(c, 0, .35f, 0, .7f); line(c, -.4f, .75f, .4f, .75f); break;
            case TABLE: rect(c, -.8f, -.7f, .8f, .7f); line(c, -.8f, -.2f, .8f, -.2f); line(c, -.8f, .25f, .8f, .25f); line(c, -.1f, -.7f, -.1f, .7f); break;
            case TEXT: line(c, -.7f, -.55f, .7f, -.55f); line(c, -.7f, -.15f, .7f, -.15f); line(c, -.7f, .25f, .7f, .25f); line(c, -.7f, .65f, .2f, .65f); break;
            case VOICEMAIL: circle(c, -.45f, 0, .3f); circle(c, .45f, 0, .3f); line(c, -.45f, .3f, .45f, .3f); break;
            case ROTATE: arc(c, 0, 0, .6f, 20, 300); begin(); m(.55f, -.35f); l(.62f, .1f); l(.2f, -.05f); path(c); break;
            case FULLSCREEN: begin(); m(-.8f, -.3f); l(-.8f, -.8f); l(-.3f, -.8f); path(c); begin(); m(.3f, -.8f); l(.8f, -.8f); l(.8f, -.3f); path(c); begin(); m(.8f, .3f); l(.8f, .8f); l(.3f, .8f); path(c); begin(); m(-.3f, .8f); l(-.8f, .8f); l(-.8f, .3f); path(c); break;
            case PAGE: begin(); m(-.6f, -.85f); l(.2f, -.85f); l(.65f, -.4f); l(.65f, .85f); l(-.6f, .85f); close(); path(c); break;
            case READER: rect(c, -.8f, -.7f, .8f, .7f); line(c, -.5f, -.3f, .5f, -.3f); line(c, -.5f, 0, .5f, 0); line(c, -.5f, .3f, .1f, .3f); break;
            default: break;
        }
        P.setStyle(Paint.Style.FILL);
    }
}
