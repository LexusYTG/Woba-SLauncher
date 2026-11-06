package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.woba.slauncher.sim.Phone;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/** Office: Word (texto), Excel (hoja con fórmulas básicas) y PowerPoint (diapositivas con presentación). Los documentos se guardan en el simulador. */
public class OfficeApp extends SimApp {
    private WpUi.WpList recent, create;
    private JSONObject doc;
    private String kind;

    public OfficeApp(Context c, SimHost host) {
        super(c, host, "office");
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "office"; }

    @Override public void onHide() { saveDoc(); }

    private void showMain() {
        final Context c = getContext();
        recent = new WpUi.WpList(c);
        create = new WpUi.WpList(c);
        recent.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) { if (it.tag instanceof String) openDoc((String) it.tag); }
        });
        recent.setOnLong(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof String)) return;
                final String id = (String) it.tag;
                showMenu(x, y + Theme.dpf(120), new String[]{"delete"}, new Choice() {
                    @Override public void on(int i) { SimStore.recentDocs.remove(id); SimStore.putStr("doc_" + id, ""); SimStore.save(); refresh(); }
                });
            }
        });
        ArrayList<WpUi.Item> ci = new ArrayList<>();
        ci.add(new WpUi.Item("Word document").sub("text").glyph(Glyph.WORD, 0xFF2B579A).tag("word"));
        ci.add(new WpUi.Item("Excel workbook").sub("spreadsheet").glyph(Glyph.EXCEL, 0xFF217346).tag("excel"));
        ci.add(new WpUi.Item("PowerPoint presentation").sub("slides").glyph(Glyph.PPT, 0xFFD24726).tag("ppt"));
        create.setItems(ci);
        create.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) { if (it.tag instanceof String) newDoc((String) it.tag); }
        });
        Pivot pv = new Pivot(c, "office", new String[]{"recent", "new"}, new View[]{recent, create});
        swapContent(pv, null);
        refresh();
    }

    private void refresh() {
        ArrayList<WpUi.Item> items = new ArrayList<>();
        for (String id : SimStore.recentDocs) {
            try {
                JSONObject d = new JSONObject(SimStore.str("doc_" + id, "{}"));
                String k = d.optString("kind");
                items.add(new WpUi.Item(d.optString("title", "untitled")).sub(k + " · " + Phone.when(d.optLong("time"))).glyph(k.equals("word") ? Glyph.WORD : k.equals("excel") ? Glyph.EXCEL : Glyph.PPT, k.equals("word") ? 0xFF2B579A : k.equals("excel") ? 0xFF217346 : 0xFFD24726).tag(id));
            } catch (Exception ignored) {}
        }
        if (items.isEmpty()) items.add(new WpUi.Item("no documents yet — create one in 'new'").dim());
        recent.setItems(items);
    }

    private String curId;

    private void newDoc(String k) {
        try {
            doc = new JSONObject();
            curId = SimStore.newId();
            doc.put("kind", k).put("title", "").put("time", System.currentTimeMillis());
            if (k.equals("word")) doc.put("body", "");
            else if (k.equals("excel")) doc.put("cells", new JSONObject());
            else doc.put("slides", new JSONArray().put(new JSONObject().put("t", "Title").put("b", "Subtitle")));
        } catch (Exception ignored) {}
        kind = k;
        go(new Runnable() { @Override public void run() { show(); } });
    }

    private void openDoc(String id) {
        try {
            doc = new JSONObject(SimStore.str("doc_" + id, "{}"));
            curId = id;
            kind = doc.optString("kind", "word");
            go(new Runnable() { @Override public void run() { show(); } });
        } catch (Exception ignored) {}
    }

    private void show() {
        if (kind.equals("word")) showWord(); else if (kind.equals("excel")) showExcel(); else showPpt();
    }

    private void saveDoc() {
        if (doc == null || curId == null) return;
        try {
            doc.put("time", System.currentTimeMillis());
            if (doc.optString("title").isEmpty()) doc.put("title", "Untitled " + kind);
            SimStore.putStr("doc_" + curId, doc.toString());
            if (!SimStore.recentDocs.contains(curId)) SimStore.recentDocs.add(0, curId);
            SimStore.save();
        } catch (Exception ignored) {}
    }

    @Override public boolean onBack() {
        if (doc != null) { saveDoc(); doc = null; }
        boolean r = super.onBack();
        refresh();
        return r;
    }

    // ------------------------------------------------------------------ Word

    private void showWord() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "word", "document"));
        final EditText t = WpUi.textBox(c, "title", doc.optString("title"));
        final EditText b = WpUi.multiline(c, "start typing", doc.optString("body"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(t, lp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bp.leftMargin = bp.rightMargin = Theme.dp(18);
        bp.topMargin = bp.bottomMargin = Theme.dp(8);
        col.addView(b, bp);
        final Runnable sync = new Runnable() { @Override public void run() { try { doc.put("title", t.getText().toString()); doc.put("body", b.getText().toString()); } catch (Exception ignored) {} } };
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.SAVE, "save", new Runnable() { @Override public void run() { sync.run(); saveDoc(); Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show(); } }),
                new AppBar.Item(Glyph.SHARE, "share", new Runnable() { @Override public void run() {
                    sync.run();
                    Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, doc.optString("body"));
                    host.launch(Intent.createChooser(s, "Share"));
                } })
        );
        swapContent(col, ab);
        wordSync = sync;
    }

    private Runnable wordSync;

    // ------------------------------------------------------------------ Excel

    private class Sheet extends View {
        static final int COLS = 8, ROWS = 40;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final HashMap<String, String> cells = new HashMap<>();
        int sr = -1, sc = -1;
        float scrollY, scrollX;
        final GestureDetector gd;

        Sheet(Context c) {
            super(c);
            try {
                JSONObject o = doc.getJSONObject("cells");
                java.util.Iterator<String> it = o.keys();
                while (it.hasNext()) { String k = it.next(); cells.put(k, o.getString(k)); }
            } catch (Exception ignored) {}
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    scrollY = Math.max(0, Math.min(ROWS * rowH() - getHeight() + Theme.dpf(60), scrollY + dy));
                    scrollX = Math.max(0, Math.min(COLS * colW() + Theme.dpf(40) - getWidth(), scrollX + dx));
                    invalidate();
                    return true;
                }
                @Override public boolean onSingleTapUp(MotionEvent e) {
                    int c2 = (int) ((e.getX() + scrollX - Theme.dpf(40)) / colW()), r2 = (int) ((e.getY() + scrollY - Theme.dpf(30)) / rowH());
                    if (e.getX() > Theme.dpf(40) && e.getY() > Theme.dpf(30) && c2 >= 0 && c2 < COLS && r2 >= 0 && r2 < ROWS) {
                        if (c2 == sc && r2 == sr) editCell(); else { sc = c2; sr = r2; invalidate(); }
                    }
                    return true;
                }
            });
        }

        float colW() { return Theme.dpf(90); }

        float rowH() { return Theme.dpf(38); }

        String key(int c, int r) { return String.valueOf((char) ('A' + c)) + (r + 1); }

        void editCell() {
            final String k = key(sc, sr);
            showPrompt(k, "value or =formula", cells.containsKey(k) ? cells.get(k) : "", new TextResult() {
                @Override public void on(String t) { if (t.isEmpty()) cells.remove(k); else cells.put(k, t); persist(); invalidate(); }
            });
        }

        void persist() {
            try { JSONObject o = new JSONObject(); for (String k : cells.keySet()) o.put(k, cells.get(k)); doc.put("cells", o); } catch (Exception ignored) {}
        }

        double num(String k, int depth) {
            String v = cells.get(k);
            if (v == null || v.isEmpty()) return 0;
            if (v.startsWith("=")) return eval(v.substring(1), depth + 1);
            try { return Double.parseDouble(v); } catch (Exception e) { return 0; }
        }

        ArrayList<Double> range(String a, String b, int depth) {
            ArrayList<Double> out = new ArrayList<>();
            int c1 = a.charAt(0) - 'A', r1 = Integer.parseInt(a.substring(1)) - 1, c2 = b.charAt(0) - 'A', r2 = Integer.parseInt(b.substring(1)) - 1;
            for (int c = Math.min(c1, c2); c <= Math.max(c1, c2); c++) for (int r = Math.min(r1, r2); r <= Math.max(r1, r2); r++) {
                String v = cells.get(key(c, r));
                if (v != null && !v.isEmpty()) out.add(num(key(c, r), depth));
            }
            return out;
        }

        // evaluador recursivo: +,-,*,/,paréntesis, A1, SUM/AVERAGE/MIN/MAX/COUNT(A1:B3)
        int pos; String src;

        double eval(String s, int depth) {
            if (depth > 20) return Double.NaN;
            src = s.replace(" ", "").toUpperCase(Locale.ROOT);
            pos = 0;
            int savePos = pos; String saveSrc = src;
            try { double v = expr(depth); return v; } catch (Exception e) { return Double.NaN; } finally { pos = savePos; src = saveSrc; }
        }

        double expr(int d) {
            double v = term(d);
            while (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) { char o = src.charAt(pos++); double t = term(d); v = o == '+' ? v + t : v - t; }
            return v;
        }

        double term(int d) {
            double v = factor(d);
            while (pos < src.length() && (src.charAt(pos) == '*' || src.charAt(pos) == '/')) { char o = src.charAt(pos++); double t = factor(d); v = o == '*' ? v * t : v / t; }
            return v;
        }

        double factor(int d) {
            if (pos >= src.length()) throw new RuntimeException();
            char ch = src.charAt(pos);
            if (ch == '-') { pos++; return -factor(d); }
            if (ch == '(') { pos++; double v = expr(d); pos++; return v; }
            if (Character.isDigit(ch) || ch == '.') { int s = pos; while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) pos++; return Double.parseDouble(src.substring(s, pos)); }
            if (Character.isLetter(ch)) {
                int s = pos;
                while (pos < src.length() && Character.isLetter(src.charAt(pos))) pos++;
                String name = src.substring(s, pos);
                if (pos < src.length() && src.charAt(pos) == '(') {
                    int e = src.indexOf(')', pos);
                    String arg = src.substring(pos + 1, e);
                    pos = e + 1;
                    ArrayList<Double> vals = new ArrayList<>();
                    for (String part : arg.split(",")) {
                        if (part.contains(":")) { String[] ab = part.split(":"); vals.addAll(range(ab[0], ab[1], d)); }
                        else if (Character.isLetter(part.charAt(0))) vals.add(num(part, d));
                        else vals.add(Double.parseDouble(part));
                    }
                    double sum = 0, mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
                    for (double x : vals) { sum += x; mn = Math.min(mn, x); mx = Math.max(mx, x); }
                    switch (name) {
                        case "SUM": return sum;
                        case "AVERAGE": return vals.isEmpty() ? 0 : sum / vals.size();
                        case "MIN": return vals.isEmpty() ? 0 : mn;
                        case "MAX": return vals.isEmpty() ? 0 : mx;
                        case "COUNT": return vals.size();
                        default: throw new RuntimeException();
                    }
                }
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
                String ref = src.substring(s, pos);
                int sp = pos; String ss = src;
                double v = num(ref, d);
                pos = sp; src = ss;
                return v;
            }
            throw new RuntimeException();
        }

        String display(String k) {
            String v = cells.get(k);
            if (v == null) return "";
            if (!v.startsWith("=")) return v;
            double r = eval(v.substring(1), 0);
            if (Double.isNaN(r)) return "#ERR";
            return r == Math.rint(r) && Math.abs(r) < 1e12 ? String.valueOf((long) r) : String.format(Locale.US, "%.4f", r).replaceAll("0+$", "");
        }

        @Override protected void onDraw(Canvas c) {
            p.setTypeface(Theme.regular);
            p.setTextSize(Theme.sp(14));
            float x0 = Theme.dpf(40), y0 = Theme.dpf(30);
            for (int r = 0; r < ROWS; r++) {
                float y = y0 + r * rowH() - scrollY;
                if (y + rowH() < y0 || y > getHeight()) continue;
                for (int cc = 0; cc < COLS; cc++) {
                    float x = x0 + cc * colW() - scrollX;
                    if (x + colW() < x0 || x > getWidth()) continue;
                    boolean sel = r == sr && cc == sc;
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(sel ? Theme.alpha(Theme.accent, 0.35f) : Theme.bg);
                    c.drawRect(x, y, x + colW(), y + rowH(), p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(sel ? Theme.dpf(2) : Theme.dpf(0.7f));
                    p.setColor(sel ? Theme.accent : Theme.alpha(Theme.fg, 0.3f));
                    c.drawRect(x, y, x + colW(), y + rowH(), p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Theme.fg);
                    String t = display(key(cc, r));
                    c.drawText(android.text.TextUtils.ellipsize(t, new android.text.TextPaint(p), colW() - Theme.dpf(8), android.text.TextUtils.TruncateAt.END).toString(), x + Theme.dpf(4), Theme.centerBaseline(p, y + rowH() / 2f), p);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(WpUi.mix(Theme.bg, Theme.fg, 0.2f));
            c.drawRect(0, 0, getWidth(), y0, p);
            c.drawRect(0, y0, x0, getHeight(), p);
            p.setColor(Theme.fg);
            p.setTextAlign(Paint.Align.CENTER);
            for (int cc = 0; cc < COLS; cc++) c.drawText(String.valueOf((char) ('A' + cc)), x0 + cc * colW() - scrollX + colW() / 2f, Theme.centerBaseline(p, y0 / 2f), p);
            for (int r = 0; r < ROWS; r++) c.drawText(String.valueOf(r + 1), x0 / 2f, Theme.centerBaseline(p, y0 + r * rowH() - scrollY + rowH() / 2f), p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            return gd.onTouchEvent(e);
        }
    }

    private void showExcel() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hdr = WpUi.pageHeader(c, "excel", doc.optString("title").isEmpty() ? "workbook" : doc.optString("title"));
        col.addView(hdr);
        final Sheet sh = new Sheet(c);
        col.addView(sh, new LinearLayout.LayoutParams(-1, 0, 1f));
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.EDIT, "edit cell", new Runnable() { @Override public void run() { if (sh.sr >= 0) sh.editCell(); } }),
                new AppBar.Item(Glyph.DELETE, "clear cell", new Runnable() { @Override public void run() { if (sh.sr >= 0) { sh.cells.remove(sh.key(sh.sc, sh.sr)); sh.persist(); sh.invalidate(); } } }),
                new AppBar.Item(Glyph.SAVE, "save", new Runnable() { @Override public void run() { sh.persist(); saveDoc(); Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show(); } })
        ).menu(
                new AppBar.Item(0, "rename workbook", new Runnable() { @Override public void run() {
                    showPrompt("rename", "name", doc.optString("title"), new TextResult() { @Override public void on(String t) { try { doc.put("title", t); } catch (Exception ignored) {} showExcel(); } });
                } }),
                new AppBar.Item(0, "export csv", new Runnable() { @Override public void run() {
                    StringBuilder sb = new StringBuilder();
                    for (int r = 0; r < 40; r++) { boolean any = false; StringBuilder line = new StringBuilder(); for (int cc = 0; cc < 8; cc++) { String v = sh.display(sh.key(cc, r)); if (!v.isEmpty()) any = true; line.append(cc > 0 ? "," : "").append(v.contains(",") ? "\"" + v + "\"" : v); } if (any) sb.append(line).append('\n'); }
                    host.launch(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_TEXT, sb.toString()), "Share"));
                } })
        );
        swapContent(col, ab);
    }

    // ------------------------------------------------------------------ PowerPoint

    private int slide;

    private void showPpt() {
        final Context c = getContext();
        final JSONArray sl = doc.optJSONArray("slides");
        slide = Math.max(0, Math.min(slide, sl.length() - 1));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "powerpoint", "slide " + (slide + 1) + "/" + sl.length()));
        JSONObject cur = sl.optJSONObject(slide);
        final EditText t = WpUi.textBox(c, "slide title", cur.optString("t"));
        final EditText b = WpUi.multiline(c, "slide text", cur.optString("b"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(t, lp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bp.leftMargin = bp.rightMargin = Theme.dp(18);
        bp.topMargin = bp.bottomMargin = Theme.dp(8);
        col.addView(b, bp);
        final Runnable sync = new Runnable() { @Override public void run() { try { JSONObject o = sl.getJSONObject(slide); o.put("t", t.getText().toString()); o.put("b", b.getText().toString()); } catch (Exception ignored) {} } };
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.LEFT, "previous", new Runnable() { @Override public void run() { sync.run(); if (slide > 0) { slide--; showPpt(); } } }),
                new AppBar.Item(Glyph.RIGHT, "next", new Runnable() { @Override public void run() { sync.run(); if (slide < sl.length() - 1) { slide++; showPpt(); } } }),
                new AppBar.Item(Glyph.ADD, "new slide", new Runnable() { @Override public void run() { sync.run(); try { sl.put(new JSONObject().put("t", "").put("b", "")); slide = sl.length() - 1; } catch (Exception ignored) {} showPpt(); } }),
                new AppBar.Item(Glyph.PLAY, "present", new Runnable() { @Override public void run() { sync.run(); saveDoc(); present(sl); } })
        ).menu(new AppBar.Item(0, "save", new Runnable() { @Override public void run() { sync.run(); saveDoc(); Toast.makeText(getContext(), "Saved", Toast.LENGTH_SHORT).show(); } }),
                new AppBar.Item(0, "rename presentation", new Runnable() { @Override public void run() {
                    sync.run();
                    showPrompt("rename", "name", doc.optString("title"), new TextResult() { @Override public void on(String s) { try { doc.put("title", s); } catch (Exception ignored) {} } });
                } }));
        swapContent(col, ab);
    }

    private void present(final JSONArray sl) {
        final Context c = getContext();
        final int[] idx = {0};
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                p.setColor(0xFF000000);
                cv.drawRect(0, 0, getWidth(), getHeight(), p);
                JSONObject o = sl.optJSONObject(idx[0]);
                p.setColor(0xFFFFFFFF);
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp(40));
                cv.drawText(o == null ? "" : o.optString("t"), Theme.dpf(24), getHeight() * 0.3f, p);
                p.setTextSize(Theme.sp(22));
                android.text.StaticLayout sl2 = android.text.StaticLayout.Builder.obtain(o == null ? "" : o.optString("b"), 0, o == null ? 0 : o.optString("b").length(), new android.text.TextPaint(p), getWidth() - (int) Theme.dpf(48)).build();
                cv.save();
                cv.translate(Theme.dpf(24), getHeight() * 0.4f);
                sl2.draw(cv);
                cv.restore();
                p.setTextSize(Theme.sp(13));
                p.setColor(0xFF888888);
                cv.drawText((idx[0] + 1) + " / " + sl.length() + "    tap to advance · back to exit", Theme.dpf(24), getHeight() - Theme.dpf(16), p);
            }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (e.getX() < getWidth() / 3f) idx[0] = Math.max(0, idx[0] - 1); else idx[0] = Math.min(sl.length() - 1, idx[0] + 1);
                    invalidate();
                }
                return true;
            }
        };
        go(new Runnable() { @Override public void run() { swapContent(v, null); } });
    }
}
