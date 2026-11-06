package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Piezas comunes con el aspecto de Windows Phone: textos, botones, cuadros de texto, filas y listas. */
public final class WpUi {
    private WpUi() {}

    /** Fondo de la barra de aplicación y de los menús/diálogos. */
    public static int barBg() { return Theme.bg == 0xFF000000 ? 0xFF1F1F1F : 0xFFE6E6E6; }

    /** Texto sobre tarjetas planas de contraste (campos de texto). */
    public static int fieldBg() { return Theme.bg == 0xFF000000 ? 0xFFFFFFFF : 0xFFFFFFFF; }

    public static TextView label(Context c, CharSequence s, float sp, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(com.woba.slauncher.data.Brand.t(c, s));
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.sp(sp));
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Theme.dp(dp)));
        return v;
    }

    /** Botón WP: contorno de 2dp, texto en minúsculas; al pulsar se rellena. */
    public static TextView button(Context c, String text) {
        final TextView b = label(c, text.toLowerCase(), 17, Theme.regular, Theme.fg);
        b.setGravity(Gravity.CENTER);
        setButtonBg(b, false);
        b.setClickable(true);
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) setButtonBg(b, true);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) setButtonBg(b, false);
                return false;
            }
        });
        return b;
    }

    private static void setButtonBg(TextView b, boolean pressed) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(pressed ? Theme.fg : 0);
        d.setStroke(Theme.dp(2), Theme.fg);
        b.setBackground(d);
        b.setTextColor(pressed ? Theme.bg : Theme.fg);
    }

    /** Cuadro de texto WP: relleno claro, texto oscuro, borde de acento al enfocar. */
    public static EditText textBox(Context c, String hint, String text) {
        final EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(text == null ? "" : text);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.sp(19));
        e.setTypeface(Theme.regular);
        e.setTextColor(0xFF000000);
        e.setHintTextColor(0xFF7A7A7A);
        e.setSingleLine(true);
        e.setPadding(Theme.dp(10), Theme.dp(8), Theme.dp(10), Theme.dp(8));
        e.setIncludeFontPadding(false);
        e.setHighlightColor(Theme.alpha(Theme.accent, 0.4f));
        applyFieldBg(e, false);
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { applyFieldBg(e, f); }
        });
        return e;
    }

    private static void applyFieldBg(EditText e, boolean focus) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFFFFFFFF);
        d.setStroke(Theme.dp(2), focus ? Theme.accent : (Theme.bg == 0xFF000000 ? 0xFFFFFFFF : 0xFFBFBFBF));
        e.setBackground(d);
    }

    public static EditText multiline(Context c, String hint, String text) {
        EditText e = textBox(c, hint, text);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setGravity(Gravity.TOP | Gravity.START);
        return e;
    }

    public static void showKeyboard(View v) {
        InputMethodManager m = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (m != null) m.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    public static void hideKeyboard(View v) {
        InputMethodManager m = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (m != null) m.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /** Efecto "tilt" de WP: al pulsar, el elemento se encoge hacia el punto tocado. */
    public static void tilt(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPivotX(e.getX());
                        view.setPivotY(e.getY());
                        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        break;
                    default: break;
                }
                return false;
            }
        });
    }

    // ------------------------------------------------------------------ cabecera de página

    /** "NOMBRE DE APP" pequeño + título grande en minúsculas (páginas sin pivote). */
    public static LinearLayout pageHeader(Context c, String app, String title) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(8));
        l.addView(label(c, app.toUpperCase(), 13, Theme.semibold, Theme.fg));
        TextView t = label(c, title.toLowerCase(), 52, Theme.light, Theme.fg);
        t.setPadding(0, Theme.dp(6), 0, 0);
        l.addView(t);
        return l;
    }

    // ------------------------------------------------------------------ avatar / casilla

    public static void drawAvatar(Canvas c, float x, float y, float size, String text, int glyph, int color, Bitmap bmp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRect(x, y, x + size, y + size, p);
        if (bmp != null) {
            android.graphics.Rect src = new android.graphics.Rect(0, 0, bmp.getWidth(), bmp.getHeight());
            int sq = Math.min(src.width(), src.height());
            src.set((bmp.getWidth() - sq) / 2, (bmp.getHeight() - sq) / 2, (bmp.getWidth() + sq) / 2, (bmp.getHeight() + sq) / 2);
            c.drawBitmap(bmp, src, new android.graphics.RectF(x, y, x + size, y + size), p);
        } else if (glyph != 0) {
            Glyph.draw(c, glyph, x + size / 2f, y + size / 2f, size * 0.55f, 0xFFFFFFFF);
        } else if (text != null) {
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Theme.light);
            p.setTextSize(size * 0.45f);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(text, x + size / 2f, Theme.centerBaseline(p, y + size / 2f), p);
        }
    }

    public static int colorFor(String key) {
        int h = key == null ? 0 : Math.abs(key.hashCode());
        return Theme.ACCENTS[h % Theme.ACCENTS.length];
    }

    // ------------------------------------------------------------------ filas y lista

    public static final int T_ROW = 0, T_HEADER = 1, T_SECTION = 2, T_SPACER = 3;

    /** Elemento de lista genérico. */
    public static class Item {
        public int type = T_ROW;
        public String title = "", sub, right, avatar;
        public int glyph, avatarColor, titleColor = Theme.fg;
        public Bitmap bmp;
        public Object tag;
        public boolean bold, dim, avatarBox = true, big;
        public int heightDp;
        public boolean checked, showCheck;

        public Item(String title) { this.title = title; }

        public Item sub(String s) { sub = s; return this; }

        public Item right(String s) { right = s; return this; }

        public Item avatar(String text, int color) { avatar = text; avatarColor = color; return this; }

        public Item glyph(int g, int color) { glyph = g; avatarColor = color; return this; }

        public Item tag(Object t) { tag = t; return this; }

        public Item bold() { bold = true; return this; }

        public Item dim() { dim = true; return this; }

        public Item bmp(Bitmap b, int color) { bmp = b; avatarColor = color; return this; }

        public static Item header(String letter) {
            Item i = new Item(letter);
            i.type = T_HEADER;
            return i;
        }

        public static Item section(String title) {
            Item i = new Item(title);
            i.type = T_SECTION;
            return i;
        }

        public static Item spacer(int dp) {
            Item i = new Item("");
            i.type = T_SPACER;
            i.heightDp = dp;
            return i;
        }
    }

    public interface OnItem { void on(Item item, int position, float x, float y); }

    /** Vista de una fila: avatar opcional, título, subtítulo y texto a la derecha. */
    static class RowView extends View {
        Item it;
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);

        RowView(Context c) { super(c); }

        void bind(Item i) { it = i; requestLayout(); invalidate(); }

        @Override protected void onMeasure(int w, int h) {
            int hh;
            switch (it.type) {
                case T_HEADER: hh = 58; break;
                case T_SECTION: hh = 46; break;
                case T_SPACER: hh = it.heightDp; break;
                default: hh = it.heightDp > 0 ? it.heightDp : ((it.avatar != null || it.glyph != 0 || it.bmp != null) ? 72 : (it.sub != null ? 68 : 56));
            }
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(hh));
        }

        @Override protected void onDraw(Canvas c) {
            if (it == null) return;
            float w = getWidth(), h = getHeight();
            if (it.type == T_SPACER) return;
            if (it.type == T_HEADER) {
                float s = Theme.dpf(46);
                fp.setStyle(Paint.Style.FILL);
                fp.setColor(Theme.accent);
                c.drawRect(Theme.dpf(18), (h - s) / 2f, Theme.dpf(18) + s, (h + s) / 2f, fp);
                tp.setTypeface(Theme.light);
                tp.setTextSize(Theme.sp(30));
                tp.setColor(0xFFFFFFFF);
                tp.setTextAlign(Paint.Align.CENTER);
                c.drawText(it.title.toLowerCase(), Theme.dpf(18) + s / 2f, Theme.centerBaseline(tp, h / 2f), tp);
                tp.setTextAlign(Paint.Align.LEFT);
                return;
            }
            if (it.type == T_SECTION) {
                tp.setTypeface(Theme.semibold);
                tp.setTextSize(Theme.sp(15));
                tp.setColor(Theme.accent);
                c.drawText(it.title.toLowerCase(), Theme.dpf(18), Theme.centerBaseline(tp, h * 0.6f), tp);
                return;
            }
            float x = Theme.dpf(18);
            boolean av = it.avatar != null || it.glyph != 0 || it.bmp != null;
            if (av) {
                float s = Theme.dpf(56);
                drawAvatar(c, x, (h - s) / 2f, s, it.avatar, it.glyph, it.avatarColor != 0 ? it.avatarColor : Theme.accent, it.bmp);
                x += s + Theme.dpf(14);
            }
            float rightW = 0;
            if (it.right != null) {
                tp.setTypeface(Theme.light);
                tp.setTextSize(Theme.sp(14));
                tp.setColor(Theme.subtle);
                rightW = tp.measureText(it.right) + Theme.dpf(10);
                c.drawText(it.right, w - Theme.dpf(18) - rightW + Theme.dpf(10), Theme.centerBaseline(tp, h * (it.sub != null ? 0.36f : 0.5f)), tp);
            }
            float maxW = w - x - Theme.dpf(18) - rightW;
            tp.setTypeface(it.bold ? Theme.semibold : Theme.light);
            tp.setTextSize(Theme.sp(it.big ? 28 : 21));
            tp.setColor(it.dim ? Theme.subtle : it.titleColor);
            String t = TextUtils.ellipsize(it.title, tp, Math.max(10, maxW), TextUtils.TruncateAt.END).toString();
            float ty = it.sub != null ? h * 0.36f : h * 0.5f;
            c.drawText(t, x, Theme.centerBaseline(tp, ty), tp);
            if (it.sub != null) {
                tp.setTypeface(Theme.light);
                tp.setTextSize(Theme.sp(14.5f));
                tp.setColor(Theme.subtle);
                String s2 = TextUtils.ellipsize(it.sub, tp, Math.max(10, w - x - Theme.dpf(18)), TextUtils.TruncateAt.END).toString();
                c.drawText(s2, x, Theme.centerBaseline(tp, h * 0.68f), tp);
            }
            if (it.showCheck) {
                float s = Theme.dpf(26), cx = w - Theme.dpf(30), cy = h / 2f;
                fp.setStyle(Paint.Style.STROKE);
                fp.setStrokeWidth(Theme.dpf(2));
                fp.setColor(Theme.fg);
                c.drawRect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, fp);
                if (it.checked) {
                    fp.setStyle(Paint.Style.FILL);
                    fp.setColor(Theme.accent);
                    c.drawRect(cx - s / 2 + Theme.dpf(4), cy - s / 2 + Theme.dpf(4), cx + s / 2 - Theme.dpf(4), cy + s / 2 - Theme.dpf(4), fp);
                }
            }
        }
    }

    /** ListView sin divisores ni overscroll con filas WP. */
    public static class WpList extends ListView {
        private final ArrayList<Item> items = new ArrayList<>();
        private OnItem click, longClick;
        private float lastX, lastY;
        private final BaseAdapter adapter;

        public WpList(Context c) {
            super(c);
            setDivider(null);
            setDividerHeight(0);
            setSelector(new ColorDrawable(0));
            setCacheColorHint(0);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(false);
            setFastScrollEnabled(false);
            adapter = new BaseAdapter() {
                @Override public int getCount() { return items.size(); }
                @Override public Object getItem(int p) { return items.get(p); }
                @Override public long getItemId(int p) { return p; }
                @Override public View getView(int p, View cv, ViewGroup parent) {
                    RowView r = cv instanceof RowView ? (RowView) cv : new RowView(parent.getContext());
                    r.bind(items.get(p));
                    return r;
                }
            };
            setAdapter(adapter);
            setOnItemClickListener(new OnItemClickListener() {
                @Override public void onItemClick(android.widget.AdapterView<?> a, View v, int p, long id) {
                    Item it = items.get(p);
                    if (it.type != T_ROW && it.type != T_HEADER) return;
                    if (click != null) click.on(it, p, lastX, lastY);
                }
            });
            setOnItemLongClickListener(new OnItemLongClickListener() {
                @Override public boolean onItemLongClick(android.widget.AdapterView<?> a, View v, int p, long id) {
                    Item it = items.get(p);
                    if (it.type != T_ROW || longClick == null) return false;
                    longClick.on(it, p, lastX, lastY);
                    return true;
                }
            });
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = e.getRawX();
                lastY = e.getRawY();
                int[] loc = new int[2];
                getLocationOnScreen(loc);
                lastX -= loc[0];
                lastY -= loc[1];
            }
            return super.onInterceptTouchEvent(e);
        }

        public void setItems(List<Item> list) {
            items.clear();
            items.addAll(list);
            adapter.notifyDataSetChanged();
        }

        public List<Item> getItems() { return items; }

        public void notifyChanged() { adapter.notifyDataSetChanged(); }

        public void setOnItem(OnItem c) { click = c; }

        public void setOnLong(OnItem c) { longClick = c; }

        public int indexOfHeader(String letter) {
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (it.type == T_HEADER && it.title.equalsIgnoreCase(letter)) return i;
            }
            return -1;
        }
    }

    public static AbsListView.LayoutParams absLp() { return new AbsListView.LayoutParams(-1, -2); }

    public static int mix(int a, int b, float t) {
        return Color.argb(
                Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
