package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/** OneNote simulado: notas con cuadernos, búsqueda, compartir y autoguardado. */
public class NotesApp extends SimApp {
    private WpUi.WpList all, books;
    private String book = "";

    public NotesApp(Context c, SimHost host) {
        super(c, host, "notes");
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "onenote"; }

    @Override public void onOpen(String arg) {
        if (arg != null && arg.startsWith("new|")) {
            SimStore.Note n = blank();
            n.body = arg.substring(4);
            final SimStore.Note fn = n;
            go(new Runnable() { @Override public void run() { showEdit(fn, true); } });
        }
    }

    private SimStore.Note blank() {
        SimStore.Note n = new SimStore.Note();
        n.id = SimStore.newId();
        n.title = "";
        n.body = "";
        n.time = System.currentTimeMillis();
        n.notebook = book.isEmpty() ? "Personal" : book;
        return n;
    }

    private void showMain() {
        final Context c = getContext();
        all = new WpUi.WpList(c);
        books = new WpUi.WpList(c);
        WpUi.OnItem open = new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Note) { final SimStore.Note n = (SimStore.Note) it.tag; go(new Runnable() { @Override public void run() { showEdit(n, false); } }); }
            }
        };
        all.setOnItem(open);
        all.setOnLong(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof SimStore.Note)) return;
                final SimStore.Note n = (SimStore.Note) it.tag;
                showMenu(x, y + Theme.dpf(120), new String[]{"share", "delete"}, new Choice() {
                    @Override public void on(int i) { if (i == 0) share(n); else { SimStore.notes.remove(n); SimStore.save(); refresh(); } }
                });
            }
        });
        books.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof String) { book = (String) it.tag; refresh(); }
            }
        });
        Pivot pv = new Pivot(c, "onenote", new String[]{"notes", "notebooks"}, new View[]{all, books});
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.ADD, "new note", new Runnable() { @Override public void run() {
                    final SimStore.Note n = blank();
                    go(new Runnable() { @Override public void run() { showEdit(n, true); } });
                } }),
                new AppBar.Item(Glyph.SEARCH, "search", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showSearch(); } }); } })
        ).menu(
                new AppBar.Item(0, "new notebook", new Runnable() { @Override public void run() {
                    showPrompt("new notebook", "name", "", new TextResult() {
                        @Override public void on(String t) {
                            if (t.trim().isEmpty()) return;
                            SimStore.putStr("notebooks", SimStore.str("notebooks", "Personal;Work;Ideas;") + t.trim() + ";");
                            refresh();
                        }
                    });
                } }),
                new AppBar.Item(0, "show all notes", new Runnable() { @Override public void run() { book = ""; refresh(); } })
        );
        swapContent(pv, ab);
        refresh();
    }

    private void refresh() {
        if (all == null) return;
        ArrayList<SimStore.Note> l = new ArrayList<>(SimStore.notes);
        Collections.sort(l, new Comparator<SimStore.Note>() {
            @Override public int compare(SimStore.Note a, SimStore.Note b) { return Long.compare(b.time, a.time); }
        });
        ArrayList<WpUi.Item> items = new ArrayList<>();
        if (!book.isEmpty()) items.add(WpUi.Item.section(book));
        for (SimStore.Note n : l) if (book.isEmpty() || n.notebook.equals(book)) items.add(item(n));
        if (items.isEmpty() || (items.size() == 1 && !book.isEmpty())) items.add(new WpUi.Item("no notes").dim());
        all.setItems(items);
        ArrayList<WpUi.Item> bi = new ArrayList<>();
        for (String b : SimStore.str("notebooks", "Personal;Work;Ideas;").split(";")) {
            if (b.isEmpty()) continue;
            int n = 0;
            for (SimStore.Note x : SimStore.notes) if (x.notebook.equals(b)) n++;
            bi.add(new WpUi.Item(b).sub(n + " notes").glyph(Glyph.BOOK, Theme.accent).tag(b));
        }
        books.setItems(bi);
    }

    private WpUi.Item item(SimStore.Note n) {
        String first = n.body.replace('\n', ' ');
        return new WpUi.Item(n.title.isEmpty() ? (first.isEmpty() ? "(empty note)" : first) : n.title).sub(first).right(Phone.when(n.time)).tag(n);
    }

    private void showSearch() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "onenote", "search"));
        final EditText et = WpUi.textBox(c, "search notes", "");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(et, lp);
        final WpUi.WpList res = new WpUi.WpList(c);
        col.addView(res, new LinearLayout.LayoutParams(-1, 0, 1f));
        res.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Note) { final SimStore.Note n = (SimStore.Note) it.tag; WpUi.hideKeyboard(et); go(new Runnable() { @Override public void run() { showEdit(n, false); } }); }
            }
        });
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().toLowerCase().trim();
                ArrayList<WpUi.Item> out = new ArrayList<>();
                if (!q.isEmpty()) for (SimStore.Note n : SimStore.notes) if (n.title.toLowerCase().contains(q) || n.body.toLowerCase().contains(q)) out.add(item(n));
                res.setItems(out);
            }
        });
        swapContent(col, null);
        et.requestFocus();
        et.post(new Runnable() { @Override public void run() { WpUi.showKeyboard(et); } });
    }

    private void share(SimStore.Note n) {
        Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, n.title).putExtra(Intent.EXTRA_TEXT, n.title + "\n\n" + n.body);
        host.launch(Intent.createChooser(s, "Share"));
    }

    private void showEdit(final SimStore.Note n, final boolean isNew) {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "onenote", n.notebook));
        final EditText title = WpUi.textBox(c, "title", n.title);
        final EditText body = WpUi.multiline(c, "type a note", n.body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(title, lp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bp.leftMargin = bp.rightMargin = Theme.dp(18);
        bp.topMargin = Theme.dp(8);
        bp.bottomMargin = Theme.dp(8);
        col.addView(body, bp);
        final Runnable save = new Runnable() {
            @Override public void run() {
                n.title = title.getText().toString().trim();
                n.body = body.getText().toString();
                n.time = System.currentTimeMillis();
                if (n.title.isEmpty() && n.body.trim().isEmpty()) { SimStore.notes.remove(n); return; }
                if (!SimStore.notes.contains(n)) SimStore.notes.add(n);
                SimStore.save();
            }
        };
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.SAVE, "save", new Runnable() { @Override public void run() { save.run(); WpUi.hideKeyboard(title); popPage(); refresh(); } }),
                new AppBar.Item(Glyph.SHARE, "share", new Runnable() { @Override public void run() { save.run(); share(n); } }),
                new AppBar.Item(Glyph.DELETE, "delete", new Runnable() { @Override public void run() { SimStore.notes.remove(n); SimStore.save(); WpUi.hideKeyboard(title); popPage(); refresh(); } })
        );
        swapContent(col, ab);
        pending = save;
        if (isNew) { title.requestFocus(); title.post(new Runnable() { @Override public void run() { WpUi.showKeyboard(title); } }); }
    }

    private Runnable pending;

    @Override public boolean onBack() {
        if (pending != null && hasPages()) { pending.run(); pending = null; }
        boolean r = super.onBack();
        refresh();
        return r;
    }

    @Override public void onHide() { if (pending != null) pending.run(); }
}
