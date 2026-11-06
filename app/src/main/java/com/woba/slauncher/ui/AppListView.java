package com.woba.slauncher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.woba.slauncher.data.AppEntry;
import com.woba.slauncher.data.Sys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Lista de apps estilo WP8: cabeceras con letra, jump list, búsqueda y menú de pulsación larga. */
public class AppListView extends FrameLayout {

    public interface Listener {
        void onLaunch(AppEntry a);
        void onTogglePin(AppEntry a);
        void onUninstall(AppEntry a);
        void onOpenSettings();
    }

    private static final int T_SETTINGS = 0, T_HEADER = 1, T_APP = 2;

    private final Listener listener;
    private final TextView title, searchBtn;
    private final EditText search;
    private final ListView list;
    private final Adapter adapter = new Adapter();
    private JumpView jump;

    private List<AppEntry> all = new ArrayList<>();
    private Set<String> pinned = new HashSet<>();
    private final ArrayList<Object> rows = new ArrayList<>(); // String (cabecera) o AppEntry
    private final HashMap<String, Integer> headerIndex = new HashMap<>();
    private boolean searching;
    private String query = "";

    public AppListView(Context ctx, Listener l) {
        super(ctx);
        listener = l;

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(20), Theme.dp(28), Theme.dp(20), 0);
        addView(col, new LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(ctx);
        top.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        title = new TextView(ctx);
        title.setText("apps");
        title.setTypeface(Theme.light);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setIncludeFontPadding(false);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        searchBtn = new TextView(ctx);
        searchBtn.setText("search");
        searchBtn.setTypeface(Theme.light);
        searchBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        searchBtn.setPadding(Theme.dp(8), Theme.dp(8), Theme.dp(8), Theme.dp(8));
        searchBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setSearching(!searching);
            }
        });
        top.addView(searchBtn, new LinearLayout.LayoutParams(-2, -2));

        search = new EditText(ctx);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setTypeface(Theme.regular);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        search.setPadding(Theme.dp(12), Theme.dp(12), Theme.dp(12), Theme.dp(12));
        search.setVisibility(GONE);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                rebuildRows();
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Theme.dp(8);
        col.addView(search, sp);

        list = new ListView(ctx);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Theme.dp(24));
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                int type = adapter.getItemViewType(pos);
                if (type == T_SETTINGS) listener.onOpenSettings();
                else if (type == T_HEADER) {
                    if (!((String) rows.get(pos - 1)).startsWith("*")) showJump();
                }
                else listener.onLaunch((AppEntry) rows.get(pos - 1));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (adapter.getItemViewType(pos) != T_APP) return false;
                showMenu(v, (AppEntry) rows.get(pos - 1));
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = Theme.dp(8);
        col.addView(list, lp);

        onThemeChanged();
    }

    // ------------------------------------------------------------ API

    public void setApps(List<AppEntry> apps) {
        all = apps;
        rebuildRows();
    }

    public void setPinned(Set<String> p) {
        pinned = p;
    }

    public void onThemeChanged() {
        title.setTextColor(Theme.fg);
        searchBtn.setTextColor(Theme.accent);
        search.setTextColor(Theme.fg);
        search.setBackgroundColor(Theme.alpha(Theme.fg, 0.12f));
        adapter.notifyDataSetChanged();
        if (jump != null) jump.invalidate();
    }

    /** true si consumió el botón atrás. */
    public boolean onBack() {
        if (jump != null) {
            hideJump();
            return true;
        }
        if (searching) {
            setSearching(false);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ búsqueda

    private void setSearching(boolean s) {
        searching = s;
        searchBtn.setText(s ? "close" : "search");
        search.setVisibility(s ? VISIBLE : GONE);
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (s) {
            search.requestFocus();
            if (imm != null) imm.showSoftInput(search, 0);
        } else {
            search.setText("");
            query = "";
            search.clearFocus();
            if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
            rebuildRows();
        }
    }

    private static String firstLetter(String label) {
        String t = label.trim();
        if (t.isEmpty()) return "#";
        char c = t.charAt(0);
        return Character.isLetter(c) ? String.valueOf(Character.toUpperCase(c)) : "#";
    }

    private void rebuildRows() {
        rows.clear();
        headerIndex.clear();
        String q = query.trim().toLowerCase();
        TreeMap<String, ArrayList<AppEntry>> groups = new TreeMap<>(new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                String x = a.equals("#") ? "" : a, y = b.equals("#") ? "" : b;
                return x.compareTo(y);
            }
        });
        for (AppEntry a : all) {
            if (!q.isEmpty() && !a.label.toLowerCase().contains(q)) continue;
            String k = firstLetter(a.label);
            ArrayList<AppEntry> g = groups.get(k);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(k, g);
            }
            g.add(a);
        }
        if (q.isEmpty()) {
            ArrayList<AppEntry> recent = new ArrayList<>();
            long since = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L;
            for (AppEntry a : all) if (a.installed > since) recent.add(a);
            java.util.Collections.sort(recent, (x, y) -> Long.compare(y.installed, x.installed));
            if (!recent.isEmpty()) {
                rows.add("*recently added");
                for (int i = 0; i < recent.size() && i < 4; i++) rows.add(recent.get(i));
            }
        }
        for (java.util.Map.Entry<String, ArrayList<AppEntry>> e : groups.entrySet()) {
            headerIndex.put(e.getKey(), rows.size() + 1); // +1 por la fila "settings"
            rows.add(e.getKey());
            rows.addAll(e.getValue());
        }
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------ menú y jump list

    private void showMenu(View anchor, final AppEntry app) {
        PopupMenu pm = new PopupMenu(getContext(), anchor);
        final boolean isPinned = pinned.contains(app.pkg);
        pm.getMenu().add(0, 1, 0, isPinned ? "unpin from start" : "pin to start");
        pm.getMenu().add(0, 2, 1, "uninstall");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                if (item.getItemId() == 1) listener.onTogglePin(app);
                else listener.onUninstall(app);
                return true;
            }
        });
        pm.show();
    }

    private void showJump() {
        if (jump != null) return;
        ArrayList<String> letters = new ArrayList<>();
        letters.add("#");
        for (char c = 'A'; c <= 'Z'; c++) letters.add(String.valueOf(c));
        ArrayList<String> extra = new ArrayList<>();
        for (String k : headerIndex.keySet()) if (!letters.contains(k)) extra.add(k);
        java.util.Collections.sort(extra);
        letters.addAll(extra);
        jump = new JumpView(getContext(), letters);
        addView(jump, new LayoutParams(-1, -1));
    }

    private void hideJump() {
        if (jump != null) {
            removeView(jump);
            jump = null;
        }
    }

    private class JumpView extends View {
        private final ArrayList<String> letters;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = Theme.textPaint(Theme.light, 32, Color.WHITE);

        JumpView(Context c, ArrayList<String> letters) {
            super(c);
            this.letters = letters;
            setClickable(true);
        }

        private float cellSize() { return Theme.dpf(72); }
        private float step() { return Theme.dpf(80); }
        private float originX() { return (getWidth() - 4 * step()) / 2f; }
        private float originY() {
            int rowsN = (letters.size() + 3) / 4;
            return (getHeight() - rowsN * step()) / 2f;
        }

        @Override
        protected void onDraw(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(Theme.bg, 0.96f));
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            tp.setTypeface(Theme.light);
            tp.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < letters.size(); i++) {
                String l = letters.get(i);
                boolean enabled = headerIndex.containsKey(l);
                float x = originX() + (i % 4) * step() + Theme.dpf(4);
                float y = originY() + (i / 4) * step() + Theme.dpf(4);
                float s = cellSize();
                if (enabled) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Theme.accent);
                    c.drawRect(x, y, x + s, y + s, p);
                    tp.setColor(Color.WHITE);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Theme.dpf(2));
                    p.setColor(Theme.alpha(Theme.subtle, 0.4f));
                    c.drawRect(x + 1, y + 1, x + s - 1, y + s - 1, p);
                    tp.setColor(Theme.alpha(Theme.subtle, 0.5f));
                }
                c.drawText(l.toLowerCase(), x + s / 2f, Theme.centerBaseline(tp, y + s / 2f), tp);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float lx = e.getX() - originX(), ly = e.getY() - originY();
                String hit = null;
                if (lx >= 0 && ly >= 0) {
                    int col = (int) (lx / step()), row = (int) (ly / step());
                    int idx = row * 4 + col;
                    if (col < 4 && idx < letters.size()) hit = letters.get(idx);
                }
                hideJump();
                if (hit != null && headerIndex.containsKey(hit)) {
                    list.setSelectionFromTop(headerIndex.get(hit), 0);
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------ adapter y filas

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size() + 1; }
        @Override public Object getItem(int pos) { return pos == 0 ? null : rows.get(pos - 1); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public int getViewTypeCount() { return 3; }

        @Override
        public int getItemViewType(int pos) {
            if (pos == 0) return T_SETTINGS;
            return rows.get(pos - 1) instanceof String ? T_HEADER : T_APP;
        }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            RowView v = convert instanceof RowView ? (RowView) convert : new RowView(parent.getContext());
            int type = getItemViewType(pos);
            if (type == T_SETTINGS) v.bind(type, "settings", null);
            else if (type == T_HEADER) v.bind(type, (String) rows.get(pos - 1), null);
            else {
                AppEntry a = (AppEntry) rows.get(pos - 1);
                v.bind(type, a.label, a.pkg);
            }
            return v;
        }
    }

    private static class RowView extends View {
        private int type;
        private String label, pkg;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Runnable redraw = new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        };

        RowView(Context c) {
            super(c);
            setLayoutParams(new AbsListView.LayoutParams(-1, Theme.dp(64)));
        }

        void bind(int type, String label, String pkg) {
            this.type = type;
            this.label = label;
            this.pkg = pkg;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Theme.dp(64));
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Theme.dpf(52), top = Theme.dpf(6);
            float textX = s + Theme.dpf(14);
            tp.setTypeface(Theme.light);
            if (type == T_HEADER && label.startsWith("*")) {
                tp.setTextSize(Theme.sp(16));
                tp.setColor(Theme.accent);
                tp.setTextAlign(Paint.Align.LEFT);
                c.drawText(label.substring(1), Theme.dpf(14), Theme.centerBaseline(tp, getHeight() / 2f), tp);
            } else if (type == T_HEADER) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Theme.dpf(2));
                p.setColor(Theme.accent);
                c.drawRect(1, top + 1, s - 1, top + s - 1, p);
                tp.setTextSize(Theme.sp(28));
                tp.setColor(Theme.accent);
                tp.setTextAlign(Paint.Align.CENTER);
                c.drawText(label.toLowerCase(), s / 2f, Theme.centerBaseline(tp, top + s / 2f), tp);
                tp.setTextAlign(Paint.Align.LEFT);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.accent);
                c.drawRect(0, top, s, top + s, p);
                if (type == T_SETTINGS) {
                    Icons.draw(c, Icons.GEAR, s / 2f, top + s / 2f, Theme.dpf(30), Color.WHITE);
                } else if (com.woba.slauncher.sim.SimRegistry.isSim(pkg)) {
                    com.woba.slauncher.sim.SimRegistry.Def d = com.woba.slauncher.sim.SimRegistry.get(com.woba.slauncher.sim.SimRegistry.idOf(pkg));
                    if (d != null) Glyph.draw(c, d.glyph, s / 2f, top + s / 2f, Theme.dpf(30), Color.WHITE);
                } else {
                    int px = Theme.dp(36);
                    Bitmap b = Sys.icon(getContext(), pkg, px, redraw);
                    if (b != null) c.drawBitmap(b, (s - px) / 2f, top + (s - px) / 2f, null);
                }
                tp.setTextSize(Theme.sp(22));
                tp.setColor(Theme.fg);
                tp.setTextAlign(Paint.Align.LEFT);
                String shown = com.woba.slauncher.sim.SimRegistry.isSim(pkg)
                        ? com.woba.slauncher.data.Brand.t(getContext(), label) : label;
                String t = TextUtils.ellipsize(shown, tp, getWidth() - textX, TextUtils.TruncateAt.END).toString();
                c.drawText(t, textX, Theme.centerBaseline(tp, top + s / 2f), tp);
            }
            if (isPressed()) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.alpha(Theme.fg, 0.12f));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
            }
        }
    }
}
