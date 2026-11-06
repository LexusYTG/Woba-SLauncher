package com.woba.slauncher.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.woba.slauncher.data.Wallpaper;

/** Controles de la app Configuración de Windows 10 Mobile (interruptor en píldora, combo, checkbox, radio...). */
public final class W10Controls {
    private W10Controls() {}

    public static int panelColor() { return Theme.isDark() ? 0xFF1F1F1F : 0xFFE6E6E6; }

    public static int buttonColor() { return Theme.isDark() ? 0xFF333333 : 0xFFCCCCCC; }

    public static int trackColor() { return Theme.isDark() ? 0xFF3D3D3D : 0xFFCCCCCC; }

    /** Fondo de una fila al pulsarla. */
    public static Drawable pressBg() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Theme.alpha(Theme.fg, 0.18f)));
        s.addState(new int[]{}, new ColorDrawable(0x00000000));
        return s;
    }

    /** Botón plano gris; al pulsarlo se invierte (fondo claro, texto oscuro). */
    public static void styleButton(TextView b) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(Theme.fg));
        s.addState(new int[]{}, new ColorDrawable(buttonColor()));
        b.setBackground(s);
        b.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_pressed}, new int[]{}},
                new int[]{Theme.bg, Theme.fg}));
    }

    // ------------------------------------------------------------------ interruptor

    /** Interruptor en píldora de 44x20: contorno con pulgar a la izquierda (off) o relleno de acento (on). */
    public static class Toggle extends View {
        public interface OnChange { void onChange(boolean on); }

        private boolean on;
        private final OnChange cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Toggle(Context c, boolean on, OnChange cb) {
            super(c);
            this.on = on;
            this.cb = cb;
            setClickable(true);
            setOnClickListener(v -> {
                this.on = !this.on;
                invalidate();
                if (this.cb != null) this.cb.onChange(this.on);
            });
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Theme.dp(44), Theme.dp(20));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Theme.dpf(2);
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.accent);
                r.set(0, 0, w, h);
                c.drawRoundRect(r, h / 2f, h / 2f, p);
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s);
                p.setColor(Theme.fg);
                r.set(s / 2f, s / 2f, w - s / 2f, h - s / 2f);
                c.drawRoundRect(r, h / 2f, h / 2f, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? 0xFFFFFFFF : Theme.fg);
            c.drawCircle(on ? w - h / 2f : h / 2f, h / 2f, Theme.dpf(on ? 6.5f : 5f), p);
        }
    }

    // ------------------------------------------------------------------ combo

    /** Cuadro desplegable: borde gris de 2 dp, texto y chevron. Desactivado = relleno gris sin borde. */
    public static class Combo extends View {
        private String text;
        private final boolean enabled;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = Theme.textPaint(Theme.regular, 15, Theme.fg);

        public Combo(Context c, String text, boolean enabled) {
            super(c);
            this.text = text == null ? "" : text;
            this.enabled = enabled;
            setClickable(enabled);
        }

        public void setText(String t) {
            text = t == null ? "" : t;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(32));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Theme.dpf(2);
            if (enabled) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.bg);
                c.drawRect(0, 0, w, h, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s);
                p.setColor(Theme.alpha(Theme.fg, 0.55f));
                c.drawRect(s / 2f, s / 2f, w - s / 2f, h - s / 2f, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.alpha(Theme.fg, 0.18f));
                c.drawRect(0, 0, w, h, p);
            }
            int col = enabled ? Theme.fg : Theme.alpha(Theme.fg, 0.4f);
            tp.setColor(col);
            float avail = w - Theme.dpf(12) - Theme.dpf(34);
            CharSequence t = TextUtils.ellipsize(text, tp, avail, TextUtils.TruncateAt.END);
            c.drawText(t, 0, t.length(), Theme.dpf(12), Theme.centerBaseline(tp, h / 2f), tp);
            SettingsIcons.draw(c, SettingsIcons.CHEVRON, w - Theme.dpf(18), h / 2f, Theme.dpf(16), col, Theme.dpf(1.5f));
        }
    }

    // ------------------------------------------------------------------ checkbox y radio

    /** Casilla cuadrada de 22 dp: contorno (off) o relleno de acento con check blanco (on). */
    public static class Check extends View {
        public interface OnChange { void onChange(boolean on); }

        private boolean on;
        private final OnChange cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Check(Context c, boolean on, OnChange cb) {
            super(c);
            this.on = on;
            this.cb = cb;
            setClickable(true);
            setOnClickListener(v -> toggle());
        }

        public void toggle() {
            on = !on;
            invalidate();
            if (cb != null) cb.onChange(on);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Theme.dp(22), Theme.dp(22));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), s = Theme.dpf(2);
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.accent);
                c.drawRect(0, 0, w, h, p);
                SettingsIcons.draw(c, SettingsIcons.CHECK, w / 2f, h / 2f, w * 0.62f, 0xFFFFFFFF, Theme.dpf(1.6f));
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s);
                p.setColor(Theme.fg);
                c.drawRect(s / 2f, s / 2f, w - s / 2f, h - s / 2f, p);
            }
        }
    }

    /** Botón de opción de 22 dp: anillo (con anillo de acento y punto si está elegido). */
    public static class Radio extends View {
        private boolean on;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Radio(Context c, boolean on) {
            super(c);
            this.on = on;
        }

        public void setChecked(boolean v) {
            on = v;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Theme.dp(22), Theme.dp(22));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2f, s = Theme.dpf(2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            p.setColor(on ? Theme.accent : Theme.fg);
            c.drawCircle(cx, cx, cx - s / 2f, p);
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.fg);
                c.drawCircle(cx, cx, Theme.dpf(5), p);
            }
        }
    }

    // ------------------------------------------------------------------ deslizador y barra

    /** Deslizador: pista fina, tramo de acento y pulgar vertical redondeado. steps > 0 = saltos discretos. */
    public static class Slider extends View {
        public interface OnValue { void onValue(float v, boolean done); }

        private float value;
        private final boolean active;
        private final int steps;
        private boolean dragging;
        private final OnValue cb;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Slider(Context c, float value, int steps, boolean active, OnValue cb) {
            super(c);
            this.steps = steps;
            this.value = snap(Math.max(0f, Math.min(1f, value)));
            this.active = active;
            this.cb = cb;
        }

        private float snap(float v) {
            return steps > 0 ? Math.round(v * steps) / (float) steps : v;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(40));
        }

        private float fromX(float x) {
            float pad = Theme.dpf(5);
            return snap(Math.max(0f, Math.min(1f, (x - pad) / Math.max(1f, getWidth() - 2 * pad))));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!active) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    value = fromX(e.getX());
                    invalidate();
                    if (cb != null) cb.onValue(value, false);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        value = fromX(e.getX());
                        invalidate();
                        if (cb != null) cb.onValue(value, false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        dragging = false;
                        if (cb != null) cb.onValue(value, true);
                    }
                    return true;
                default:
                    return true;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), cy = getHeight() / 2f, pad = Theme.dpf(5);
            float x = pad + value * (w - 2 * pad);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(Theme.fg, active ? 0.4f : 0.2f));
            c.drawRect(pad, cy - Theme.dpf(1), w - pad, cy + Theme.dpf(1), p);
            p.setColor(active ? Theme.accent : Theme.alpha(Theme.fg, 0.35f));
            c.drawRect(pad, cy - Theme.dpf(1.5f), x, cy + Theme.dpf(1.5f), p);
            p.setColor(active ? Theme.accent : Theme.alpha(Theme.fg, 0.35f));
            r.set(x - Theme.dpf(4), cy - Theme.dpf(11), x + Theme.dpf(4), cy + Theme.dpf(11));
            c.drawRoundRect(r, Theme.dpf(4), Theme.dpf(4), p);
        }
    }

    /** Barra de uso (batería, almacenamiento): pista gris y tramo de acento. */
    public static class Bar extends View {
        private final float frac, heightDp;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Bar(Context c, float frac, float heightDp) {
            super(c);
            this.frac = Math.max(0f, Math.min(1f, frac));
            this.heightDp = heightDp;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(heightDp));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(trackColor());
            c.drawRect(0, 0, w, h, p);
            p.setColor(Theme.accent);
            c.drawRect(0, 0, w * frac, h, p);
        }
    }

    // ------------------------------------------------------------------ colores, vista previa y atajos

    /** Muestra de color de la página Colors: cuadrado de acento; el elegido lleva borde y check blancos. */
    public static class Swatch extends View {
        private final int color;
        private final boolean selected;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public Swatch(Context c, int color, boolean selected) {
            super(c);
            this.color = color;
            this.selected = selected;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            c.drawRect(0, 0, w, h, p);
            if (selected) {
                float s = Theme.dpf(2);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s);
                p.setColor(0xFFFFFFFF);
                c.drawRect(s / 2f, s / 2f, w - s / 2f, h - s / 2f, p);
                SettingsIcons.draw(c, SettingsIcons.CHECK, w / 2f, h / 2f, w * 0.42f, 0xFFFFFFFF, Theme.dpf(1.6f));
            }
        }
    }

    /** Miniatura de Start (página Start): fondo o acento con la cuadrícula de mosaicos. */
    public static class StartPreview extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public StartPreview(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Theme.dp(113), Theme.dp(185));
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            Bitmap b = Wallpaper.start;
            p.setStyle(Paint.Style.FILL);
            if (b != null) {
                p.setFilterBitmap(true);
                Wallpaper.drawCrop(c, b, w, h, p);
            } else {
                p.setColor(Theme.accent);
                c.drawRect(0, 0, w, h, p);
            }
            int cols = 4, rows = 7;
            float gw = Theme.dpf(1.5f), tw = w / (float) cols, th = h / (float) rows;
            p.setColor(Theme.bg);
            for (int i = 1; i < cols; i++) c.drawRect(i * tw - gw / 2f, 0, i * tw + gw / 2f, h, p);
            for (int j = 1; j < rows; j++) c.drawRect(0, j * th - gw / 2f, w, j * th + gw / 2f, p);
            TextPaint tp = Theme.textPaint(Theme.regular, 9, 0xFFFFFFFF);
            c.drawText("Aa", Theme.dpf(5), th * 1.5f + Theme.centerBaseline(tp, 0f), tp);
            float s = Theme.dpf(1.5f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s);
            p.setColor(Theme.fg);
            c.drawRect(s / 2f, s / 2f, w - s / 2f, h - s / 2f, p);
        }
    }

    /** Mosaico gris de 60 dp con el icono de una acción rápida (Quick actions). */
    public static class QuickTile extends View {
        private final int id;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        public QuickTile(Context c, int id) {
            super(c);
            this.id = id;
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(Theme.dp(60), Theme.dp(60));
        }

        @Override
        protected void onDraw(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(buttonColor());
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            StatusIcons.toggle(c, id, getWidth() / 2f, getHeight() / 2f, Theme.dpf(26), Theme.fg);
        }
    }

    // ------------------------------------------------------------------ buscador

    /** Cuadro "Find a setting": gris con borde en reposo; blanco con borde de acento al enfocarlo. */
    public static class SearchBox extends FrameLayout {
        public interface OnQuery { void on(String q); }

        private final EditText et;
        private final SettingsIcons.IconView icon;

        public SearchBox(Context c, final OnQuery cb) {
            super(c);
            et = new EditText(c);
            et.setBackground(null);
            et.setSingleLine(true);
            et.setHint("Find a setting");
            et.setTypeface(Theme.regular);
            et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            et.setIncludeFontPadding(false);
            et.setPadding(Theme.dp(10), 0, Theme.dp(38), 0);
            et.setGravity(Gravity.CENTER_VERTICAL);
            et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            addView(et, new FrameLayout.LayoutParams(-1, -1));
            icon = new SettingsIcons.IconView(c, SettingsIcons.SEARCH, Theme.subtle);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Theme.dp(20), Theme.dp(20), Gravity.END | Gravity.CENTER_VERTICAL);
            lp.rightMargin = Theme.dp(8);
            addView(icon, lp);
            restyle(false);
            et.setOnFocusChangeListener((v, focus) -> restyle(focus));
            et.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
                @Override public void afterTextChanged(Editable s) {
                    if (cb != null) cb.on(s.toString());
                }
            });
        }

        private void restyle(boolean focus) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(focus ? 0xFFFFFFFF : (Theme.isDark() ? 0xFF1A1A1A : 0xFFFFFFFF));
            g.setStroke(Theme.dp(2), focus ? Theme.accent : Theme.alpha(Theme.fg, 0.5f));
            setBackground(g);
            et.setTextColor(focus ? 0xFF000000 : Theme.fg);
            et.setHintTextColor(focus ? 0xFF767676 : Theme.subtle);
            icon.setColor(focus ? 0xFF767676 : Theme.subtle);
        }
    }
}
