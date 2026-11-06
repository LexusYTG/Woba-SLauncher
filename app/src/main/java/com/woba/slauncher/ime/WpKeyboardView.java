package com.woba.slauncher.ime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Teclado de Windows Phone dibujado con Canvas: teclas planas y cuadradas, la pulsada se tiñe de acento y
 * sube una burbuja con la letra, barra de sugerencias arriba, &123 con dos páginas de símbolos, emoji,
 * mantener pulsada una tecla para acentos (ñ, á, é...), borrar con repetición y dos pulgares a la vez.
 */
public class WpKeyboardView extends View {

    public interface Listener {
        /** Texto a escribir (una letra, un símbolo, un emoji, ".com"...). */
        void onText(String s);
        void onBackspace();
        void onEnter();
        void onSpace();
        /** Se tocó una sugerencia. */
        void onSuggestion(String s);
        /** Se tocó la tecla del micrófono (dictado). */
        void onMic();
        /** Se arrastró el punto de control del cursor: pasos horizontales y verticales (derecha/abajo positivos). */
        void onCursor(int dx, int dy);
    }

    public static final int MODE_TEXT = 0, MODE_NUM = 1, MODE_PHONE = 2;
    public static final int ENTER_NEWLINE = 0, ENTER_GO = 1, ENTER_SEARCH = 2, ENTER_SEND = 3, ENTER_NEXT = 4, ENTER_DONE = 5;

    private static final int PG_LETTERS = 0, PG_SYM1 = 1, PG_SYM2 = 2, PG_EMOJI = 3, PG_NUM = 4, PG_PHONE = 5;
    private static final int K_CHAR = 0, K_SHIFT = 1, K_DEL = 2, K_PAGE = 3, K_SPACE = 4, K_ENTER = 5, K_EMOJI = 6, K_GAP = 7;

    private static final String EMOJI = "😀 😃 😄 😁 😆 😅 😂 🤣 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 😎 🤓 🤔 🤨 😐 😑 😶 🙄 😏 😣 😥 😮 "
            + "🤐 😯 😪 😫 😴 😌 😢 😭 😤 😠 😡 🤬 😱 😨 😰 😓 🤗 🤭 🥳 😳 🥺 😞 😔 🙁 😖 👍 👎 👌 ✌️ 🤞 🤟 🤘 👏 🙌 🙏 "
            + "💪 👋 🤝 ❤️ 🧡 💛 💚 💙 💜 🖤 💔 💕 💖 🔥 ✨ ⭐ 🎉 🎂 🎁 🍕 🍔 🍟 🍺 🍷 ☕ ⚽ 🏀 🎮 🎵 📱 💻 📷 🚗 ✈️ "
            + "🏠 🌞 🌙 ⛅ ☔ 🌈 🐶 🐱 🐻 🦁 🐸 🐵 🙈 🌹 🌸 🍀 ✅ ❌ ❓ ❗ 💯 🆗 👀 🧠";
    private static final String[] EMOJIS = EMOJI.split(" ");

    private static final class Key {
        final int type;
        final String text;   // minúscula/base; lo que se escribe
        final int page;      // destino de K_PAGE
        final float w;
        final String label;  // para teclas de función
        String[] alts;
        final RectF r = new RectF();

        Key(int type, String text, String label, int page, float w) {
            this.type = type;
            this.text = text;
            this.label = label;
            this.page = page;
            this.w = w;
        }
    }

    private final class Press {
        int id;
        Key key;
        int strip = -1;
        boolean consumed, alt;
        int altSel;
        boolean cursor;
        float cx, cy;
        String[] altItems;
        float[] altX; // bordes izquierdos de cada opción + borde derecho final
        final RectF popup = new RectF();
        Runnable longRun, repRun;
    }

    private final Listener listener;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private final SparseArray<Press> presses = new SparseArray<>();
    private final ArrayList<ArrayList<Key>> rows = new ArrayList<>();
    private final ArrayList<Key> flat = new ArrayList<>();
    private final AudioManager audio;
    private final int slop;

    private int mode = MODE_TEXT, page = PG_LETTERS;
    private int shift;           // 0 = minúsculas, 1 = una mayúscula, 2 = bloqueo de mayúsculas
    private long lastShiftUp;
    private int enterType = ENTER_NEWLINE;
    private boolean email, url;
    private final String[] sugg = new String[3];
    private int suggHi = -1;   // hueco de la sugerencia que se aplicará sola al escribir espacio

    // ajustes (Ajustes > Extras > Keyboard)
    private float hScale = 1f, kScale = 1f;
    private int oneHand, style, holdMs = 330;
    private boolean pSound = true, pVib = true, pPreview = true, pCursor = true, pMic = true;
    private float kx0, kw;       // franja horizontal de las teclas (el modo de una mano la estrecha)
    private float micW;          // ancho del hueco del micrófono en la barra de sugerencias
    private float dotX, dotY, dotR;

    private float stripH, rowH;
    private float emojiScroll, emojiY0, emojiS0;
    private int emojiPtr = -1;
    private boolean emojiDrag;

    public WpKeyboardView(Context c, Listener l) {
        super(c);
        listener = l;
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setHapticFeedbackEnabled(true);
        loadPrefs();
        buildPage();
    }

    private void loadPrefs() {
        Context c = getContext();
        hScale = KbPrefs.heightScale(c);
        kScale = KbPrefs.keyScale(c);
        holdMs = KbPrefs.holdMs(c);
        oneHand = KbPrefs.level(c, KbPrefs.ONEHAND, 0);
        style = KbPrefs.level(c, KbPrefs.STYLE, 0);
        pSound = KbPrefs.on(c, KbPrefs.SOUND);
        pVib = KbPrefs.on(c, KbPrefs.VIBRATE);
        pPreview = KbPrefs.on(c, KbPrefs.PREVIEW);
        pCursor = KbPrefs.on(c, KbPrefs.CURSOR);
        pMic = KbPrefs.on(c, KbPrefs.MIC);
    }

    // ------------------------------------------------------------------ API para el servicio

    public void setMode(int m) {
        mode = m;
        page = m == MODE_NUM ? PG_NUM : m == MODE_PHONE ? PG_PHONE : PG_LETTERS;
        buildPage();
        layoutKeys();
        invalidate();
    }

    public void setEnter(int type) {
        enterType = type;
        invalidate();
    }

    public void setContext(boolean isEmail, boolean isUrl) {
        if (email == isEmail && url == isUrl) return;
        email = isEmail;
        url = isUrl;
        buildPage();
        layoutKeys();
        invalidate();
    }

    public int shiftState() { return shift; }

    public void setShift(int s) {
        if (s == shift) return;
        shift = s;
        invalidate();
    }

    public void setSuggestions(String a, String b, String c) { setSuggestions(a, b, c, -1); }

    /** @param hi hueco (0..2) de la sugerencia que se aplica sola al pulsar espacio, o -1 */
    public void setSuggestions(String a, String b, String c, int hi) {
        sugg[0] = a;
        sugg[1] = b;
        sugg[2] = c;
        suggHi = hi;
        invalidate();
    }

    /** Vuelve a leer los ajustes y el tema (se llama cada vez que aparece el teclado). */
    public void refreshTheme() {
        loadPrefs();
        requestLayout();
        layoutKeys();
        invalidate();
    }

    // ------------------------------------------------------------------ páginas de teclas

    private static String[] altsFor(String ch) {
        switch (ch) {
            case "a": return new String[]{"á", "à", "â", "ä", "ã", "å", "æ"};
            case "e": return new String[]{"é", "è", "ê", "ë", "€"};
            case "i": return new String[]{"í", "ì", "î", "ï"};
            case "o": return new String[]{"ó", "ò", "ô", "ö", "õ", "œ"};
            case "u": return new String[]{"ú", "ù", "û", "ü"};
            case "n": return new String[]{"ñ", "ń"};
            case "c": return new String[]{"ç", "ć", "č"};
            case "s": return new String[]{"ß", "š", "ś"};
            case "y": return new String[]{"ý", "ÿ"};
            case "z": return new String[]{"ž", "ź", "ż"};
            case "l": return new String[]{"ł"};
            case "d": return new String[]{"ð"};
            default: return null;
        }
    }

    private Key ch(String s, float w) {
        Key k = new Key(K_CHAR, s, null, 0, w);
        k.alts = altsFor(s);
        if (s.equals(".com")) k.alts = new String[]{".net", ".org", ".com.ar", ".es", ".edu", ".gov"};
        return k;
    }

    private void addChars(ArrayList<Key> r, String chars) {
        for (int i = 0; i < chars.length(); i++) r.add(ch(chars.substring(i, i + 1), 1f));
    }

    private ArrayList<Key> charRow(String chars) {
        ArrayList<Key> r = new ArrayList<>();
        addChars(r, chars);
        return r;
    }

    private Key fn(int type, String label, int page, float w) { return new Key(type, "", label, page, w); }

    /** Fila de abajo común a letras y símbolos. */
    private ArrayList<Key> bottomRow(int pageKeyTarget, String pageKeyLabel) {
        ArrayList<Key> r = new ArrayList<>();
        r.add(fn(K_PAGE, pageKeyLabel, pageKeyTarget, 1.4f));
        r.add(fn(K_EMOJI, "", PG_EMOJI, 1.1f));
        Key left = ch(email ? "@" : url ? "/" : ",", 1f);
        if (!email && !url) left.alts = new String[]{"¿", "¡", ";", ":", "'", "\""};
        r.add(left);
        boolean com = email || url;
        r.add(fn(K_SPACE, "", 0, com ? 2.6f : 4f));
        if (com) r.add(ch(".com", 1.4f));
        Key dot = ch(".", 1f);
        dot.alts = (email || url) ? new String[]{".net", ".org", ".com.ar", ".es", ".io", "?", "!"}
                : new String[]{"?", "!", ":", ";", "'", "\"", "-", "…"};
        r.add(dot);
        r.add(fn(K_ENTER, "", 0, 1.5f));
        return r;
    }

    private void buildPage() {
        rows.clear();
        switch (page) {
            case PG_LETTERS: {
                rows.add(charRow("qwertyuiop"));
                ArrayList<Key> r2 = new ArrayList<>();
                r2.add(fn(K_GAP, "", 0, 0.5f));
                addChars(r2, "asdfghjkl");
                r2.add(fn(K_GAP, "", 0, 0.5f));
                rows.add(r2);
                ArrayList<Key> r3 = new ArrayList<>();
                r3.add(fn(K_SHIFT, "", 0, 1.5f));
                addChars(r3, "zxcvbnm");
                r3.add(fn(K_DEL, "", 0, 1.5f));
                rows.add(r3);
                rows.add(bottomRow(PG_SYM1, "&123"));
                break;
            }
            case PG_SYM1: {
                rows.add(charRow("1234567890"));
                rows.add(charRow("@#$%&*-+()"));
                ArrayList<Key> r3 = new ArrayList<>();
                r3.add(fn(K_PAGE, "1/2", PG_SYM2, 1.5f));
                addChars(r3, "!\"':;/?");
                r3.add(fn(K_DEL, "", 0, 1.5f));
                rows.add(r3);
                rows.add(bottomRow(PG_LETTERS, "abc"));
                break;
            }
            case PG_SYM2: {
                rows.add(charRow("~`|^°<>{}\\"));
                rows.add(charRow("[]¿¡€£¥•§©"));
                ArrayList<Key> r3 = new ArrayList<>();
                r3.add(fn(K_PAGE, "2/2", PG_SYM1, 1.5f));
                addChars(r3, "…®™±÷×¶");
                r3.add(fn(K_DEL, "", 0, 1.5f));
                rows.add(r3);
                rows.add(bottomRow(PG_LETTERS, "abc"));
                break;
            }
            case PG_EMOJI: {
                ArrayList<Key> r = new ArrayList<>();
                r.add(fn(K_PAGE, "abc", PG_LETTERS, 2f));
                r.add(fn(K_SPACE, "", 0, 6f));
                r.add(fn(K_DEL, "", 0, 2f));
                rows.add(r);
                break;
            }
            case PG_NUM: {
                ArrayList<Key> r1 = charRow("123");
                r1.add(fn(K_DEL, "", 0, 1f));
                rows.add(r1);
                ArrayList<Key> r2 = charRow("456-");
                rows.add(r2);
                rows.add(charRow("789."));
                ArrayList<Key> r4 = new ArrayList<>();
                r4.add(ch(",", 1f));
                r4.add(ch("0", 1f));
                r4.add(fn(K_SPACE, "", 0, 1f));
                r4.add(fn(K_ENTER, "", 0, 1f));
                rows.add(r4);
                break;
            }
            default: { // PG_PHONE
                ArrayList<Key> r1 = charRow("123");
                r1.add(fn(K_DEL, "", 0, 1f));
                rows.add(r1);
                rows.add(charRow("456+"));
                rows.add(charRow("789#"));
                ArrayList<Key> r4 = charRow("*0,");
                r4.add(fn(K_ENTER, "", 0, 1f));
                rows.add(r4);
                break;
            }
        }
        flat.clear();
        for (ArrayList<Key> r : rows) flat.addAll(r);
    }

    // ------------------------------------------------------------------ geometría

    @Override protected void onMeasure(int wm, int hm) {
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        rowH = Theme.dpf(land ? 42 : 54) * hScale;
        stripH = Theme.dpf(land ? 36 : 42) * Math.max(0.9f, Math.min(1.1f, hScale));
        int w = MeasureSpec.getSize(wm);
        setMeasuredDimension(w, Math.round(stripH + rowH * 4));
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        super.onSizeChanged(w, hh, ow, oh);
        layoutKeys();
    }

    private void layoutKeys() {
        int W = getWidth();
        if (W == 0) return;
        boolean oh = oneHand != 0 && (page == PG_LETTERS || page == PG_SYM1 || page == PG_SYM2);
        kw = oh ? W * 0.82f : W;
        kx0 = oh && oneHand == 2 ? W - kw : 0;
        micW = pMic ? Math.min(Theme.dpf(64), W * 0.16f) : 0;
        float y = page == PG_EMOJI ? stripH + rowH * 3 : stripH;
        for (ArrayList<Key> row : rows) {
            float total = 0;
            for (Key k : row) total += k.w;
            float unit = kw / total, x = kx0;
            for (Key k : row) {
                k.r.set(x, y, x + k.w * unit, y + rowH);
                x += k.w * unit;
            }
            y += rowH;
        }
        dotR = Theme.dpf(6.5f);
        dotX = kx0 + kw * 0.25f;
        dotY = stripH + rowH * 3;
    }

    private Key keyAt(float x, float y) {
        Key best = null;
        float bd = Float.MAX_VALUE;
        for (Key k : flat) {
            if (k.type == K_GAP) continue;
            if (k.r.contains(x, y)) return k;
        }
        // el dedo cayó en un hueco: la tecla más cercana (en la franja de teclas)
        if (y < stripH) return null;
        for (Key k : flat) {
            if (k.type == K_GAP) continue;
            float dx = Math.max(Math.max(k.r.left - x, x - k.r.right), 0), dy = Math.max(Math.max(k.r.top - y, y - k.r.bottom), 0);
            float d = dx * dx + dy * dy;
            if (d < bd && d < Theme.dpf(10) * Theme.dpf(10)) { bd = d; best = k; }
        }
        return best;
    }

    private String shown(String s) {
        return shift != 0 ? s.toUpperCase(Locale.getDefault()) : s;
    }

    // ------------------------------------------------------------------ colores

    private boolean dark() { return Theme.isDark(); }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private int boardBg() {
        int b = dark() ? 0xFF121212 : 0xFFCDCFD2;
        return style == 1 ? mix(b, Theme.accent, 0.10f) : b;
    }

    private int charBg() {
        int b = dark() ? 0xFF3B3E43 : 0xFFEFF0F2;
        return style == 1 ? mix(b, Theme.accent, dark() ? 0.28f : 0.18f) : b;
    }

    private int fnBg() {
        int b = dark() ? 0xFF5B5E64 : 0xFFE0E2E5;
        return style == 1 ? mix(b, Theme.accent, dark() ? 0.45f : 0.30f) : b;
    }

    /** Tecla Enter: clara sobre fondo oscuro (y oscura sobre el claro), con la flecha fina del color contrario. */
    private int enterBg() { return dark() ? 0xFFE9E9E9 : 0xFF2B2B2B; }

    private int enterInk() { return dark() ? 0xFF111111 : 0xFFFFFFFF; }

    private int ink() { return dark() ? 0xFFFFFFFF : 0xFF000000; }

    // ------------------------------------------------------------------ dibujo

    @Override protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(boardBg());
        c.drawRect(0, 0, W, H, p);
        drawStrip(c, W);
        if (page == PG_EMOJI) drawEmoji(c, W);

        float gap = Theme.dpf(1.5f);
        for (Key k : flat) {
            if (k.type == K_GAP) continue;
            boolean down = isDown(k);
            int bg;
            boolean shiftOn = k.type == K_SHIFT && shift != 0;
            if (down || shiftOn) bg = Theme.accent;
            else if (k.type == K_ENTER) bg = enterBg();
            else bg = (k.type == K_CHAR || k.type == K_SPACE) ? charBg() : fnBg();
            p.setStyle(Paint.Style.FILL);
            p.setColor(bg);
            c.drawRect(k.r.left + gap, k.r.top + gap, k.r.right - gap, k.r.bottom - gap, p);
            int fg = (down || shiftOn) ? 0xFFFFFFFF : k.type == K_ENTER ? enterInk() : ink();
            drawKeyFace(c, k, fg);
        }

        drawCursorDot(c);

        // burbuja con la letra y selector de acentos por encima de todo
        for (int i = 0; i < presses.size(); i++) {
            Press pr = presses.valueAt(i);
            if (pr.key == null) continue;
            if (pr.alt) drawAlt(c, pr);
            else if (pPreview && pr.key.type == K_CHAR && page != PG_NUM && page != PG_PHONE) drawBubble(c, pr.key);
        }
    }

    private boolean isDown(Key k) {
        for (int i = 0; i < presses.size(); i++) if (presses.valueAt(i).key == k) return true;
        return false;
    }

    private void drawKeyFace(Canvas c, Key k, int fg) {
        float cx = k.r.centerX(), cy = k.r.centerY();
        p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL);
        p.setColor(fg);
        switch (k.type) {
            case K_CHAR: {
                boolean letter = k.text.length() == 1 && Character.isLetter(k.text.charAt(0));
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp((letter ? 26 : k.text.length() > 1 ? 17 : 24) * kScale));
                c.drawText(letter ? shown(k.text) : k.text, cx, Theme.centerBaseline(p, cy), p);
                break;
            }
            case K_SPACE:
                break; // la barra espaciadora de WP va lisa
            case K_PAGE:
                p.setTypeface(Theme.regular);
                p.setTextSize(Theme.sp(17 * kScale));
                c.drawText(k.label, cx, Theme.centerBaseline(p, cy), p);
                break;
            case K_SHIFT:
                shiftIcon(c, cx, cy, Theme.dpf(11), shift == 2, fg);
                break;
            case K_DEL:
                backspaceIcon(c, cx, cy, Theme.dpf(12), fg);
                break;
            case K_EMOJI:
                smileyIcon(c, cx, cy, Theme.dpf(11), fg);
                break;
            case K_ENTER:
                enterFace(c, cx, cy, fg);
                break;
            default: break;
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void enterFace(Canvas c, float cx, float cy, int fg) {
        float s = Theme.dpf(11) * Math.min(1.2f, kScale);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1.6f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(fg);
        path.reset();
        switch (enterType) {
            case ENTER_SEARCH:
                Glyph.draw(c, Glyph.SEARCH, cx, cy, Theme.dpf(24) * Math.min(1.2f, kScale), fg, Theme.dpf(1.6f));
                p.setStyle(Paint.Style.FILL);
                return;
            case ENTER_GO:
            case ENTER_NEXT: // flecha fina hacia la derecha
                path.moveTo(cx - s, cy);
                path.lineTo(cx + s, cy);
                path.moveTo(cx + s * .35f, cy - s * .6f);
                path.lineTo(cx + s, cy);
                path.lineTo(cx + s * .35f, cy + s * .6f);
                break;
            case ENTER_DONE:
                path.moveTo(cx - s * .8f, cy + s * .05f);
                path.lineTo(cx - s * .25f, cy + s * .6f);
                path.lineTo(cx + s * .85f, cy - s * .55f);
                break;
            case ENTER_SEND:
                // avión de papel
                path.moveTo(cx - s, cy - s * .7f);
                path.lineTo(cx + s, cy);
                path.lineTo(cx - s, cy + s * .7f);
                path.lineTo(cx - s * .6f, cy);
                path.close();
                path.moveTo(cx - s * .6f, cy);
                path.lineTo(cx + s, cy);
                break;
            default: // salto de línea: flecha de retorno fina
                path.moveTo(cx + s * .9f, cy - s * .8f);
                path.lineTo(cx + s * .9f, cy + s * .2f);
                path.lineTo(cx - s * .8f, cy + s * .2f);
                path.moveTo(cx - s * .3f, cy - s * .3f);
                path.lineTo(cx - s * .8f, cy + s * .2f);
                path.lineTo(cx - s * .3f, cy + s * .7f);
                break;
        }
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    /** Flecha hacia arriba de trazo fino; con bloqueo de mayúsculas lleva una barra debajo. */
    private void shiftIcon(Canvas c, float cx, float cy, float s, boolean lock, int fg) {
        s *= Math.min(1.2f, kScale);
        p.setColor(fg);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1.8f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        float top = lock ? cy - s * .95f : cy - s;
        float bot = lock ? cy + s * .6f : cy + s;
        path.reset();
        path.moveTo(cx, bot);
        path.lineTo(cx, top);
        path.moveTo(cx - s * .75f, top + s * .75f);
        path.lineTo(cx, top);
        path.lineTo(cx + s * .75f, top + s * .75f);
        c.drawPath(path, p);
        if (lock) c.drawLine(cx - s * .6f, cy + s * 1.0f, cx + s * .6f, cy + s * 1.0f, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void backspaceIcon(Canvas c, float cx, float cy, float s, int fg) {
        p.setColor(fg);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(2));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        path.moveTo(cx - s, cy);
        path.lineTo(cx - s * .45f, cy - s * .7f);
        path.lineTo(cx + s, cy - s * .7f);
        path.lineTo(cx + s, cy + s * .7f);
        path.lineTo(cx - s * .45f, cy + s * .7f);
        path.close();
        c.drawPath(path, p);
        float q = s * .28f, mx = cx + s * .22f;
        c.drawLine(mx - q, cy - q, mx + q, cy + q, p);
        c.drawLine(mx - q, cy + q, mx + q, cy - q, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void smileyIcon(Canvas c, float cx, float cy, float s, int fg) {
        p.setColor(fg);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(2));
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawCircle(cx, cy, s, p);
        rf.set(cx - s * .5f, cy - s * .35f, cx + s * .5f, cy + s * .5f);
        c.drawArc(rf, 25, 130, false, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx - s * .33f, cy - s * .28f, Theme.dpf(1.6f), p);
        c.drawCircle(cx + s * .33f, cy - s * .28f, Theme.dpf(1.6f), p);
    }

    private void drawStrip(Canvas c, int W) {
        if (page == PG_NUM || page == PG_PHONE || page == PG_EMOJI) return;
        float sw = (W - micW) / 3f;
        p.setStyle(Paint.Style.FILL);
        if (micW > 0) {
            Press mp = stripPress(-2);
            if (mp != null) {
                p.setColor(Theme.alpha(Theme.accent, 0.9f));
                c.drawRect(0, 0, micW, stripH, p);
            }
            Glyph.draw(c, Glyph.MIC, micW / 2f, stripH / 2f, Theme.dpf(22), mp != null ? 0xFFFFFFFF : ink(), Theme.dpf(1.6f));
        }
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 3; i++) {
            float x0 = micW + i * sw;
            Press st = stripPress(i);
            if (st != null) {
                p.setColor(Theme.alpha(Theme.accent, 0.9f));
                c.drawRect(x0, 0, x0 + sw, stripH, p);
            }
            String s = sugg[i];
            if (s == null) continue;
            boolean bold = suggHi >= 0 ? i == suggHi : i == 0;
            p.setTypeface(bold ? Theme.semibold : Theme.regular);
            p.setTextSize(Theme.sp(18));
            p.setColor(st != null ? 0xFFFFFFFF : (i == suggHi ? Theme.accent : ink()));
            String t = s;
            while (t.length() > 1 && p.measureText(t) > sw - Theme.dpf(12)) t = t.substring(0, t.length() - 1);
            c.drawText(t, x0 + sw / 2f, Theme.centerBaseline(p, stripH / 2f), p);
        }
        p.setColor(dark() ? 0xFF3A3A3A : 0xFFB0B2B5);
        for (int i = micW > 0 ? 0 : 1; i < 3; i++) {
            float x = micW + i * sw;
            c.drawRect(x - 0.5f, stripH * 0.22f, x + 0.5f, stripH * 0.78f, p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    /** Punto de acento en la unión de las dos filas de abajo: arrastrado mueve el cursor del texto. */
    private void drawCursorDot(Canvas c) {
        if (!pCursor || !(page == PG_LETTERS || page == PG_SYM1 || page == PG_SYM2)) return;
        boolean active = false;
        for (int i = 0; i < presses.size(); i++) if (presses.valueAt(i).cursor) active = true;
        p.setStyle(Paint.Style.FILL);
        p.setColor(active ? Theme.alpha(Theme.accent, 0.35f) : 0x00000000);
        if (active) c.drawCircle(dotX, dotY, dotR * 3.2f, p);
        p.setColor(Theme.accent);
        c.drawCircle(dotX, dotY, dotR * (active ? 1.25f : 1f), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(1.5f));
        p.setColor(dark() ? 0xFF121212 : 0xFFFFFFFF);
        c.drawCircle(dotX, dotY, dotR * (active ? 1.25f : 1f), p);
        p.setStyle(Paint.Style.FILL);
    }

    private Press stripPress(int slot) {
        for (int i = 0; i < presses.size(); i++) if (presses.valueAt(i).strip == slot) return presses.valueAt(i);
        return null;
    }

    private void drawBubble(Canvas c, Key k) {
        float bw = k.r.width() * 1.25f, bh = rowH * 1.1f, W = getWidth();
        float bx = Math.max(0, Math.min(W - bw, k.r.centerX() - bw / 2f));
        float by = Math.max(0, k.r.top - bh - Theme.dpf(2));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.accent);
        c.drawRect(bx, by, bx + bw, by + bh, p);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Theme.light);
        p.setTextSize(Theme.sp(34));
        p.setTextAlign(Paint.Align.CENTER);
        boolean letter = k.text.length() == 1 && Character.isLetter(k.text.charAt(0));
        c.drawText(letter ? shown(k.text) : k.text, bx + bw / 2f, Theme.centerBaseline(p, by + bh / 2f), p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawAlt(Canvas c, Press pr) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark() ? 0xFF2C2C2C : 0xFFFFFFFF);
        c.drawRect(pr.popup, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Theme.dpf(2));
        p.setColor(Theme.accent);
        c.drawRect(pr.popup, p);
        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < pr.altItems.length; i++) {
            float l = pr.altX[i], r = pr.altX[i + 1];
            if (i == pr.altSel) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.accent);
                c.drawRect(l, pr.popup.top, r, pr.popup.bottom, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(i == pr.altSel ? 0xFFFFFFFF : ink());
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(pr.altItems[i].length() > 2 ? 20 : 28));
            c.drawText(pr.altItems[i], (l + r) / 2f, Theme.centerBaseline(p, pr.popup.centerY()), p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawEmoji(Canvas c, int W) {
        float cols = 8, cell = W / cols, top = 0, bottom = stripH + rowH * 3;
        int rowsN = (int) Math.ceil(EMOJIS.length / cols);
        float content = rowsN * cell, max = Math.max(0, content - (bottom - top));
        emojiScroll = Math.max(0, Math.min(max, emojiScroll));
        int sc = c.save();
        c.clipRect(0, top, W, bottom);
        p.setTypeface(android.graphics.Typeface.DEFAULT);
        p.setTextSize(Theme.sp(25));
        p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ink());
        for (int i = 0; i < EMOJIS.length; i++) {
            float x = (i % 8) * cell + cell / 2f, y = top + (i / 8) * cell + cell / 2f - emojiScroll;
            if (y < top - cell || y > bottom + cell) continue;
            c.drawText(EMOJIS[i], x, Theme.centerBaseline(p, y), p);
        }
        c.restoreToCount(sc);
        p.setTextAlign(Paint.Align.LEFT);
    }

    // ------------------------------------------------------------------ toques

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                down(e.getPointerId(i), e.getX(i), e.getY(i));
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                up(e.getPointerId(i), e.getX(i), e.getY(i));
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < presses.size(); i++) stopTimers(presses.valueAt(i));
                presses.clear();
                emojiPtr = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void tick(int fx) {
        if (pVib) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (pSound && audio != null) audio.playSoundEffect(fx);
    }

    private void down(int id, float x, float y) {
        if (page == PG_EMOJI && y < stripH + rowH * 3) {
            emojiPtr = id;
            emojiY0 = y;
            emojiS0 = emojiScroll;
            emojiDrag = false;
            return;
        }
        final Press pr = new Press();
        pr.id = id;
        if (pCursor && (page == PG_LETTERS || page == PG_SYM1 || page == PG_SYM2)
                && Math.hypot(x - dotX, y - dotY) < Theme.dpf(20)) {
            pr.cursor = true;
            pr.cx = x;
            pr.cy = y;
            presses.put(id, pr);
            if (pVib) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            invalidate();
            return;
        }
        if (y < stripH) {
            if (page == PG_NUM || page == PG_PHONE || page == PG_EMOJI) return;
            if (x < micW) {
                pr.strip = -2;
                presses.put(id, pr);
                invalidate();
                return;
            }
            int slot = slotAt(x);
            if (sugg[slot] == null) return;
            pr.strip = slot;
            presses.put(id, pr);
            invalidate();
            return;
        }
        Key k = keyAt(x, y);
        if (k == null) return;
        pr.key = k;
        presses.put(id, pr);
        armKey(pr);
        invalidate();
    }

    /** Efecto inmediato (borrar), o preparar la pulsación larga (acentos). */
    private void armKey(final Press pr) {
        stopTimers(pr);
        final Key k = pr.key;
        tick(k.type == K_SPACE ? AudioManager.FX_KEYPRESS_SPACEBAR : k.type == K_DEL ? AudioManager.FX_KEYPRESS_DELETE
                : k.type == K_ENTER ? AudioManager.FX_KEYPRESS_RETURN : AudioManager.FX_KEYPRESS_STANDARD);
        if (k.type == K_DEL) {
            listener.onBackspace();
            pr.consumed = true;
            pr.repRun = new Runnable() {
                @Override public void run() {
                    if (presses.get(pr.id) != pr) return;
                    listener.onBackspace();
                    h.postDelayed(this, 45);
                }
            };
            h.postDelayed(pr.repRun, 380);
        } else if (k.type == K_CHAR && k.alts != null) {
            pr.longRun = new Runnable() {
                @Override public void run() {
                    if (presses.get(pr.id) == pr && !pr.alt) showAlt(pr);
                }
            };
            h.postDelayed(pr.longRun, holdMs);
        }
    }

    private int slotAt(float x) {
        return Math.max(0, Math.min(2, (int) ((x - micW) / ((getWidth() - micW) / 3f))));
    }

    private void stopTimers(Press pr) {
        if (pr.longRun != null) h.removeCallbacks(pr.longRun);
        if (pr.repRun != null) h.removeCallbacks(pr.repRun);
        pr.longRun = pr.repRun = null;
    }

    private void showAlt(Press pr) {
        Key k = pr.key;
        String[] src = k.alts;
        pr.altItems = new String[src.length];
        for (int i = 0; i < src.length; i++) pr.altItems[i] = (k.text.length() == 1 && Character.isLetter(k.text.charAt(0))) ? shown(src[i]) : src[i];
        float minW = k.r.width() * 0.95f, total = 0;
        float[] wd = new float[src.length];
        p.setTypeface(Theme.light);
        for (int i = 0; i < src.length; i++) {
            p.setTextSize(Theme.sp(pr.altItems[i].length() > 2 ? 20 : 28));
            wd[i] = Math.max(minW, p.measureText(pr.altItems[i]) + Theme.dpf(18));
            total += wd[i];
        }
        float W = getWidth(), h2 = rowH * 1.05f;
        float left = Math.max(0, Math.min(W - total, k.r.centerX() - total / 2f));
        float top = Math.max(0, k.r.top - h2 - Theme.dpf(2));
        pr.popup.set(left, top, left + total, top + h2);
        pr.altX = new float[src.length + 1];
        pr.altX[0] = left;
        for (int i = 0; i < src.length; i++) pr.altX[i + 1] = pr.altX[i] + wd[i];
        pr.alt = true;
        pr.altSel = 0;
        if (pVib) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
    }

    private void move(int id, float x, float y) {
        if (id == emojiPtr) {
            float dy = y - emojiY0;
            if (!emojiDrag && Math.abs(dy) > slop) emojiDrag = true;
            if (emojiDrag) {
                emojiScroll = emojiS0 - dy;
                invalidate();
            }
            return;
        }
        Press pr = presses.get(id);
        if (pr == null) return;
        if (pr.alt) {
            int sel = pr.altSel;
            for (int i = 0; i < pr.altItems.length; i++) if (x >= pr.altX[i] && x < pr.altX[i + 1]) sel = i;
            if (x < pr.altX[0]) sel = 0;
            if (x >= pr.altX[pr.altItems.length]) sel = pr.altItems.length - 1;
            if (sel != pr.altSel) { pr.altSel = sel; invalidate(); }
            return;
        }
        if (pr.cursor) {
            float step = Theme.dpf(13), vstep = Theme.dpf(30);
            int dx = 0, dy = 0;
            while (x - pr.cx >= step) { dx++; pr.cx += step; }
            while (pr.cx - x >= step) { dx--; pr.cx -= step; }
            while (y - pr.cy >= vstep) { dy++; pr.cy += vstep; }
            while (pr.cy - y >= vstep) { dy--; pr.cy -= vstep; }
            if (dx != 0 || dy != 0) listener.onCursor(dx, dy);
            return;
        }
        if (pr.strip != -1) return;
        if (pr.key != null && pr.key.type == K_DEL) return;
        Key k = keyAt(x, y);
        if (k != null && k != pr.key) {
            pr.key = k; // el dedo se deslizó a otra tecla: vale la que queda bajo el dedo al soltar
            armKey(pr);
            invalidate();
        } else if (k == null && pr.key != null) {
            stopTimers(pr);
            pr.key = null;
            invalidate();
        }
    }

    private void up(int id, float x, float y) {
        if (id == emojiPtr) {
            emojiPtr = -1;
            if (!emojiDrag) {
                float cell = getWidth() / 8f;
                int col = (int) (x / cell), row = (int) ((y + emojiScroll) / cell), i = row * 8 + col;
                if (col >= 0 && col < 8 && i >= 0 && i < EMOJIS.length) {
                    tick(AudioManager.FX_KEYPRESS_STANDARD);
                    listener.onText(EMOJIS[i]);
                }
            }
            return;
        }
        Press pr = presses.get(id);
        if (pr == null) return;
        presses.remove(id);
        stopTimers(pr);
        invalidate();
        if (pr.cursor) return;
        if (pr.strip == -2) {
            if (x < micW && y < stripH + Theme.dpf(20)) listener.onMic();
            return;
        }
        if (pr.strip >= 0) {
            int slot = slotAt(x);
            if (slot == pr.strip && y < stripH + Theme.dpf(20) && sugg[slot] != null) listener.onSuggestion(sugg[slot]);
            return;
        }
        if (pr.alt) {
            listener.onText(pr.altItems[pr.altSel]);
            return;
        }
        Key k = pr.key;
        if (k == null || pr.consumed) return;
        switch (k.type) {
            case K_CHAR:
                listener.onText((k.text.length() == 1 && Character.isLetter(k.text.charAt(0))) ? shown(k.text) : k.text);
                break;
            case K_SPACE: listener.onSpace(); break;
            case K_ENTER: listener.onEnter(); break;
            case K_SHIFT: {
                long now = android.os.SystemClock.uptimeMillis();
                if (shift == 1 && now - lastShiftUp < 350) shift = 2;   // doble toque: bloqueo de mayúsculas
                else if (shift == 0) shift = 1;
                else shift = 0;
                lastShiftUp = now;
                invalidate();
                break;
            }
            case K_PAGE:
            case K_EMOJI:
                page = k.page;
                buildPage();
                layoutKeys();
                emojiScroll = 0;
                invalidate();
                break;
            default: break;
        }
    }

    @Override protected void onDetachedFromWindow() {
        for (int i = 0; i < presses.size(); i++) stopTimers(presses.valueAt(i));
        presses.clear();
        super.onDetachedFromWindow();
    }
}
