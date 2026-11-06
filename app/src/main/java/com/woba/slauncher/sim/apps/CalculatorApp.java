package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Calculadora estándar y científica (al girar o con el botón) con aspecto WP. */
public class CalculatorApp extends SimApp {
    private final Pad pad;

    public CalculatorApp(Context c, SimHost host) {
        super(c, host, "calculator");
        pad = new Pad(c);
        setContent(pad, null);
    }

    @Override public String appTitle() { return "calculator"; }

    private static final String[][] STD = {
            {"%", "√", "x²", "1/x"},
            {"CE", "C", "⌫", "÷"},
            {"7", "8", "9", "×"},
            {"4", "5", "6", "−"},
            {"1", "2", "3", "+"},
            {"±", "0", ".", "="}
    };
    private static final String[][] SCI = {
            {"sin", "cos", "tan", "π"},
            {"ln", "log", "e^x", "x^y"},
            {"(", ")", "n!", "mod"}
    };

    private class Pad extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        String expr = "", shown = "0", pending = "";
        double acc = 0;
        String op = null;
        boolean fresh = true, sci = false, error = false;
        int down = -1;

        Pad(Context c) { super(c); }

        int rows() { return STD.length + (sci ? SCI.length : 0); }

        String cell(int r, int c) { return (sci && r < SCI.length) ? SCI[r][c] : STD[r - (sci ? SCI.length : 0)][c]; }

        float topArea() { return Theme.dpf(150); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Theme.semibold);
            p.setTextSize(Theme.sp(13));
            p.setColor(Theme.fg);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText("CALCULATOR", Theme.dpf(18), Theme.dpf(30), p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(16));
            p.setColor(Theme.subtle);
            c.drawText(pending, w - Theme.dpf(18), Theme.dpf(70), p);
            float size = 56;
            p.setTextSize(Theme.sp(size));
            while (p.measureText(shown) > w - Theme.dpf(36) && size > 20) { size -= 3; p.setTextSize(Theme.sp(size)); }
            p.setColor(Theme.fg);
            c.drawText(shown, w - Theme.dpf(18), Theme.dpf(128), p);
            p.setTextAlign(Paint.Align.CENTER);
            float gap = Theme.dpf(3), gx = Theme.dpf(6);
            int rows = rows();
            float cw = (w - gx * 2 - gap * 3) / 4f, ch = (h - topArea() - gx - gap * (rows - 1)) / rows;
            for (int r = 0; r < rows; r++) {
                for (int col = 0; col < 4; col++) {
                    String t = cell(r, col);
                    float x = gx + col * (cw + gap), y = topArea() + r * (ch + gap);
                    boolean eq = t.equals("=");
                    boolean digit = t.length() == 1 && (Character.isDigit(t.charAt(0)) || t.equals("."));
                    int idx = r * 4 + col;
                    p.setColor(eq ? Theme.accent : (digit ? WpUi.mix(Theme.bg, Theme.fg, 0.22f) : WpUi.mix(Theme.bg, Theme.fg, 0.12f)));
                    if (down == idx) p.setColor(Theme.fg);
                    c.drawRect(x, y, x + cw - 0, y + ch, p);
                    p.setColor(down == idx ? Theme.bg : (eq ? 0xFFFFFFFF : Theme.fg));
                    p.setTypeface(digit ? Theme.regular : Theme.light);
                    p.setTextSize(Theme.sp(t.length() > 2 ? 17 : 26));
                    c.drawText(t, x + cw / 2f, Theme.centerBaseline(p, y + ch / 2f), p);
                }
            }
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth(), h = getHeight(), gap = Theme.dpf(3), gx = Theme.dpf(6);
            int rows = rows();
            float cw = (w - gx * 2 - gap * 3) / 4f, ch = (h - topArea() - gx - gap * (rows - 1)) / rows;
            int col = (int) ((e.getX() - gx) / (cw + gap)), r = (int) ((e.getY() - topArea()) / (ch + gap));
            int idx = (col >= 0 && col < 4 && r >= 0 && r < rows) ? r * 4 + col : -1;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: down = idx; invalidate(); return true;
                case MotionEvent.ACTION_MOVE: if (idx != down) { down = -1; invalidate(); } return true;
                case MotionEvent.ACTION_UP:
                    if (idx >= 0 && idx == down) press(cell(r, col));
                    down = -1;
                    invalidate();
                    return true;
                default: down = -1; invalidate(); return true;
            }
        }

        // -------------------------------------------------------------- lógica
        // Se evalúa con un pequeño intérprete de expresiones: shown = número en pantalla.

        StringBuilder line = new StringBuilder(); // expresión completa

        void press(String t) {
            if (error && !t.equals("C") && !t.equals("CE")) { reset(); }
            switch (t) {
                case "C": reset(); return;
                case "CE": shown = "0"; fresh = true; return;
                case "⌫":
                    if (!fresh && shown.length() > 1) shown = shown.substring(0, shown.length() - 1);
                    else { shown = "0"; fresh = true; }
                    if (shown.equals("-")) { shown = "0"; fresh = true; }
                    return;
                case "±":
                    if (!shown.equals("0")) shown = shown.startsWith("-") ? shown.substring(1) : "-" + shown;
                    return;
                case ".":
                    if (fresh) { shown = "0."; fresh = false; }
                    else if (!shown.contains(".")) shown += ".";
                    return;
                case "+": case "−": case "×": case "÷": case "x^y": case "mod": binary(t); return;
                case "=": equals(); return;
                case "(": line.append(fresh ? "" : shown).append("*("); fresh = true; return;
                case ")": return;
                case "π": shown = fmt(Math.PI); fresh = true; return;
                default: break;
            }
            if (t.length() == 1 && Character.isDigit(t.charAt(0))) {
                if (fresh || shown.equals("0")) shown = t; else if (shown.length() < 16) shown += t;
                fresh = false;
                return;
            }
            double v = val();
            double r;
            switch (t) {
                case "%": r = (op != null ? acc * v / 100.0 : v / 100.0); break;
                case "√": r = Math.sqrt(v); break;
                case "x²": r = v * v; break;
                case "1/x": r = 1.0 / v; break;
                case "sin": r = Math.sin(Math.toRadians(v)); break;
                case "cos": r = Math.cos(Math.toRadians(v)); break;
                case "tan": r = Math.tan(Math.toRadians(v)); break;
                case "ln": r = Math.log(v); break;
                case "log": r = Math.log10(v); break;
                case "e^x": r = Math.exp(v); break;
                case "n!": r = fact(v); break;
                default: return;
            }
            setResult(r);
            fresh = true;
        }

        double fact(double v) {
            if (v < 0 || v != Math.floor(v) || v > 170) return Double.NaN;
            double r = 1;
            for (int i = 2; i <= (int) v; i++) r *= i;
            return r;
        }

        void binary(String t) {
            if (op != null && !fresh) equalsKeep();
            else acc = val();
            op = t;
            pending = fmt(acc) + " " + t;
            fresh = true;
        }

        void equalsKeep() {
            double v = val(), r = acc;
            switch (op) {
                case "+": r = acc + v; break;
                case "−": r = acc - v; break;
                case "×": r = acc * v; break;
                case "÷": r = acc / v; break;
                case "x^y": r = Math.pow(acc, v); break;
                case "mod": r = acc % v; break;
                default: break;
            }
            acc = r;
            setResult(r);
        }

        void equals() {
            if (op == null) return;
            pending = fmt(acc) + " " + op + " " + shown + " =";
            equalsKeep();
            op = null;
            fresh = true;
        }

        void setResult(double r) {
            if (Double.isNaN(r) || Double.isInfinite(r)) { shown = "Cannot divide by zero"; error = true; return; }
            shown = fmt(r);
        }

        void reset() { shown = "0"; pending = ""; acc = 0; op = null; fresh = true; error = false; line.setLength(0); }

        double val() {
            try { return Double.parseDouble(shown); } catch (Exception e) { return 0; }
        }

        String fmt(double d) {
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
            BigDecimal b = new BigDecimal(d).round(new MathContext(12, RoundingMode.HALF_EVEN));
            String s = b.stripTrailingZeros().toPlainString();
            return s.length() > 16 ? b.stripTrailingZeros().toString() : s;
        }
    }

    @Override public void onThemeChanged() { super.onThemeChanged(); pad.invalidate(); }

    @Override public boolean onBack() { return false; }

    @Override protected void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        boolean sci = w > h; // paisaje: científica
        if (pad.sci != sci) { pad.sci = sci; pad.invalidate(); }
    }
}
