package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
import java.util.LinkedHashSet;
import java.util.Locale;

/** People: lista alfabética con salto por letras, favoritos, novedades y ficha de contacto editable. */
public class PeopleApp extends SimApp implements SimStore.Listener {
    private WpUi.WpList all, favs, news;
    private String filter = "";
    private SimStore.Contact current;
    private Pivot pivot;

    public PeopleApp(Context c, SimHost host) {
        super(c, host, "people");
        SimStore.addListener(this);
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "people"; }

    @Override public void onDestroy() { SimStore.removeListener(this); }

    @Override public void onSimChanged(String what) {
        if (all != null && (what.equals("contacts") || what.equals("calls") || what.equals("messages"))) refresh();
    }

    @Override public void onOpen(String arg) {
        if (arg == null) return;
        if (arg.equals("new")) { final SimStore.Contact k = blank(); go(new Runnable() { @Override public void run() { showEdit(k, true); } }); return; }
        final SimStore.Contact k = arg.startsWith("num:") ? SimStore.byNumber(arg.substring(4)) : findId(arg);
        if (k != null) go(new Runnable() { @Override public void run() { showCard(k); } });
    }

    private SimStore.Contact findId(String id) {
        for (SimStore.Contact k : SimStore.contacts) if (k.id.equals(id)) return k;
        return null;
    }

    private SimStore.Contact blank() {
        SimStore.Contact k = new SimStore.Contact();
        k.id = SimStore.newId();
        k.name = "";
        k.phone = "";
        return k;
    }

    // ------------------------------------------------------------------ principal

    private void showMain() {
        final Context c = getContext();
        all = new WpUi.WpList(c);
        favs = new WpUi.WpList(c);
        news = new WpUi.WpList(c);
        WpUi.OnItem open = new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.type == WpUi.T_HEADER) { jump(); return; }
                if (it.tag instanceof SimStore.Contact) {
                    final SimStore.Contact k = (SimStore.Contact) it.tag;
                    go(new Runnable() { @Override public void run() { showCard(k); } });
                }
            }
        };
        WpUi.OnItem menu = new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof SimStore.Contact)) return;
                final SimStore.Contact k = (SimStore.Contact) it.tag;
                showMenu(x, y + Theme.dpf(160), new String[]{k.fav ? "remove from favorites" : "add to favorites", "edit", "delete"}, new Choice() {
                    @Override public void on(int i) {
                        if (i == 0) { k.fav = !k.fav; SimStore.save(); refresh(); }
                        else if (i == 1) go(new Runnable() { @Override public void run() { showEdit(k, false); } });
                        else confirmDelete(k);
                    }
                });
            }
        };
        all.setOnItem(open); all.setOnLong(menu);
        favs.setOnItem(open); favs.setOnLong(menu);
        news.setOnItem(open);
        pivot = new Pivot(c, "people", new String[]{"all", "favorites", "what's new"}, new View[]{all, favs, news});
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.ADD, "add", new Runnable() { @Override public void run() {
                    final SimStore.Contact k = blank();
                    go(new Runnable() { @Override public void run() { showEdit(k, true); } });
                } }),
                new AppBar.Item(Glyph.SEARCH, "search", new Runnable() { @Override public void run() {
                    go(new Runnable() { @Override public void run() { showSearch(); } });
                } })
        ).menu(
                new AppBar.Item(0, "import android contacts", new Runnable() { @Override public void run() { importReal(); } })
        );
        swapContent(pivot, ab);
        refresh();
    }

    private void importReal() {
        final Context c = getContext();
        if (c.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, new Runnable() {
                @Override public void run() { doImport(); }
            });
        } else doImport();
    }

    private void doImport() {
        int n = Phone.importAndroidContacts(getContext());
        Toast.makeText(getContext(), n < 0 ? "Permission denied" : n + " contacts imported", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private static String letterOf(SimStore.Contact k) {
        String n = k.name.trim();
        if (n.isEmpty()) return "#";
        char ch = Character.toUpperCase(n.charAt(0));
        return Character.isLetter(ch) ? String.valueOf(ch) : "#";
    }

    private WpUi.Item row(SimStore.Contact k) {
        WpUi.Item it = new WpUi.Item(k.name).avatar(k.initials(), k.color >= 0 ? Theme.ACCENTS[k.color % Theme.ACCENTS.length] : WpUi.colorFor(k.name)).tag(k);
        return it;
    }

    private void refresh() {
        if (all == null) return;
        ArrayList<SimStore.Contact> list = new ArrayList<>(SimStore.contacts);
        Collections.sort(list, new Comparator<SimStore.Contact>() {
            @Override public int compare(SimStore.Contact a, SimStore.Contact b) {
                boolean la = !letterOf(a).equals("#"), lb = !letterOf(b).equals("#");
                if (la != lb) return la ? -1 : 1;
                return a.sortKey().compareTo(b.sortKey());
            }
        });
        ArrayList<WpUi.Item> items = new ArrayList<>();
        String last = "";
        for (SimStore.Contact k : list) {
            if (!filter.isEmpty() && !k.name.toLowerCase(Locale.getDefault()).contains(filter) && !k.phone.contains(filter)) continue;
            String l = letterOf(k);
            if (!l.equals(last)) { items.add(WpUi.Item.header(l)); last = l; }
            items.add(row(k));
        }
        if (items.isEmpty()) items.add(new WpUi.Item("no contacts").dim());
        all.setItems(items);
        ArrayList<WpUi.Item> f = new ArrayList<>();
        for (SimStore.Contact k : list) if (k.fav) f.add(row(k).sub(k.phone));
        if (f.isEmpty()) f.add(new WpUi.Item("no favorites yet").dim());
        favs.setItems(f);
        // novedades: últimas llamadas y mensajes
        ArrayList<WpUi.Item> n = new ArrayList<>();
        ArrayList<Object[]> ev = new ArrayList<>();
        for (SimStore.Call cl : SimStore.calls) ev.add(new Object[]{cl.time, cl.name != null && !cl.name.isEmpty() ? cl.name : SimStore.nameFor(cl.number),
                cl.type == 3 ? "missed call" : cl.type == 2 ? "outgoing call" : "incoming call", cl.number});
        for (SimStore.Msg m : SimStore.messages) ev.add(new Object[]{m.time, SimStore.nameFor(m.thread), m.body, m.thread});
        Collections.sort(ev, new Comparator<Object[]>() {
            @Override public int compare(Object[] a, Object[] b) { return Long.compare((Long) b[0], (Long) a[0]); }
        });
        for (int i = 0; i < Math.min(ev.size(), 30); i++) {
            Object[] e = ev.get(i);
            SimStore.Contact k = SimStore.byNumber((String) e[3]);
            WpUi.Item it = new WpUi.Item((String) e[1]).sub((String) e[2]).right(Phone.when((Long) e[0]));
            it.avatar(k != null ? k.initials() : "#", WpUi.colorFor((String) e[1]));
            if (k != null) it.tag(k);
            n.add(it);
        }
        if (n.isEmpty()) n.add(new WpUi.Item("nothing new").dim());
        news.setItems(n);
    }

    private void jump() {
        LinkedHashSet<String> have = new LinkedHashSet<>();
        for (SimStore.Contact k : SimStore.contacts) have.add(letterOf(k));
        final String[] ls = new String[27];
        boolean[] en = new boolean[27];
        for (int i = 0; i < 26; i++) { ls[i] = String.valueOf((char) ('A' + i)); en[i] = have.contains(ls[i]); }
        ls[26] = "#";
        en[26] = have.contains("#");
        showJump(ls, en, new Pick() {
            @Override public void on(String l) {
                int idx = all.indexOfHeader(l);
                if (idx >= 0) all.setSelection(idx);
            }
        });
    }

    // ------------------------------------------------------------------ búsqueda

    private void showSearch() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "people", "search"));
        final EditText et = WpUi.textBox(c, "search contacts", "");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        lp.bottomMargin = Theme.dp(8);
        col.addView(et, lp);
        final WpUi.WpList res = new WpUi.WpList(c);
        col.addView(res, new LinearLayout.LayoutParams(-1, 0, 1f));
        res.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Contact) {
                    final SimStore.Contact k = (SimStore.Contact) it.tag;
                    WpUi.hideKeyboard(et);
                    go(new Runnable() { @Override public void run() { showCard(k); } });
                }
            }
        });
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().toLowerCase(Locale.getDefault()).trim();
                ArrayList<WpUi.Item> out = new ArrayList<>();
                if (!q.isEmpty()) for (SimStore.Contact k : SimStore.contacts) {
                    if (k.name.toLowerCase(Locale.getDefault()).contains(q) || k.phone.contains(q) || k.email.toLowerCase().contains(q)) out.add(row(k).sub(k.phone));
                }
                res.setItems(out);
            }
        });
        swapContent(col, null);
        et.requestFocus();
        et.post(new Runnable() { @Override public void run() { WpUi.showKeyboard(et); } });
    }

    // ------------------------------------------------------------------ ficha

    private View action(final int glyph, String line1, String line2, final Runnable r) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(10));
        View ic = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Theme.dpf(2));
                p.setColor(Theme.fg);
                float r2 = getWidth() / 2f - Theme.dpf(2);
                cv.drawCircle(getWidth() / 2f, getHeight() / 2f, r2, p);
                Glyph.draw(cv, glyph, getWidth() / 2f, getHeight() / 2f, getWidth() * 0.5f, Theme.fg);
            }
        };
        row.addView(ic, new LinearLayout.LayoutParams(Theme.dp(44), Theme.dp(44)));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Theme.dp(16), 0, 0, 0);
        t.addView(WpUi.label(c, line1, 20, Theme.light, Theme.fg));
        if (line2 != null) t.addView(WpUi.label(c, line2, 14, Theme.light, Theme.subtle));
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        WpUi.tilt(row);
        return row;
    }

    private void showCard(final SimStore.Contact k) {
        final Context c = getContext();
        current = k;
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(WpUi.pageHeader(c, "people", k.name.isEmpty() ? "contact" : k.name));
        // el título largo se reduce
        ((TextView) ((LinearLayout) col.getChildAt(0)).getChildAt(1)).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Theme.sp(k.name.length() > 12 ? 34 : 46));
        LinearLayout head = new LinearLayout(c);
        head.setPadding(Theme.dp(18), Theme.dp(4), Theme.dp(18), Theme.dp(14));
        View av = new View(c) {
            @Override protected void onDraw(Canvas cv) {
                WpUi.drawAvatar(cv, 0, 0, getWidth(), k.initials(), 0, k.color >= 0 ? Theme.ACCENTS[k.color % Theme.ACCENTS.length] : WpUi.colorFor(k.name), null);
            }
        };
        head.addView(av, new LinearLayout.LayoutParams(Theme.dp(110), Theme.dp(110)));
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Theme.dp(16), 0, 0, 0);
        if (!k.company.isEmpty()) info.addView(WpUi.label(c, k.company, 17, Theme.light, Theme.fg));
        if (!k.birthday.isEmpty()) info.addView(WpUi.label(c, "birthday " + k.birthday, 14, Theme.light, Theme.subtle));
        if (k.fav) info.addView(WpUi.label(c, "★ favorite", 14, Theme.light, Theme.accent));
        head.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head);
        if (!k.phone.isEmpty()) {
            col.addView(action(Glyph.PHONE, "call mobile", k.phone, new Runnable() { @Override public void run() { Phone.call(getContext(), host, k.name, k.phone); } }));
            col.addView(action(Glyph.SPEECH, "text mobile", k.phone, new Runnable() { @Override public void run() { Phone.text(host, k.phone); } }));
        }
        if (!k.email.isEmpty()) col.addView(action(Glyph.MAIL, "email", k.email, new Runnable() { @Override public void run() { Phone.email(host, k.email); } }));
        if (!k.address.isEmpty()) col.addView(action(Glyph.PIN, "map", k.address, new Runnable() { @Override public void run() { Phone.map(host, k.address); } }));
        if (!k.note.isEmpty()) {
            col.addView(WpUi.label(c, "notes", 15, Theme.semibold, Theme.accent), padLp());
            TextView n = WpUi.label(c, k.note, 17, Theme.light, Theme.fg);
            n.setPadding(Theme.dp(18), 0, Theme.dp(18), Theme.dp(12));
            col.addView(n);
        }
        // historial con esta persona
        ArrayList<String> hist = new ArrayList<>();
        for (SimStore.Call cl : SimStore.calls) if (!k.phone.isEmpty() && SimStore.norm(cl.number).equals(SimStore.norm(k.phone)))
            hist.add(Phone.full(cl.time) + "  ·  " + (cl.type == 3 ? "missed call" : cl.type == 2 ? "outgoing call" : "incoming call"));
        if (!hist.isEmpty()) {
            col.addView(WpUi.label(c, "history", 15, Theme.semibold, Theme.accent), padLp());
            for (String s : hist) {
                TextView t = WpUi.label(c, s, 15, Theme.light, Theme.subtle);
                t.setPadding(Theme.dp(18), Theme.dp(2), Theme.dp(18), Theme.dp(2));
                col.addView(t);
            }
        }
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(k.fav ? Glyph.STAR_FILL : Glyph.STAR, "favorite", new Runnable() { @Override public void run() {
                    k.fav = !k.fav; SimStore.save(); SimStore.fire("contacts"); showCard(k);
                } }),
                new AppBar.Item(Glyph.EDIT, "edit", new Runnable() { @Override public void run() {
                    go(new Runnable() { @Override public void run() { showEdit(k, false); } });
                } }),
                new AppBar.Item(Glyph.DELETE, "delete", new Runnable() { @Override public void run() { confirmDelete(k); } })
        );
        swapContent(sv, ab);
    }

    private LinearLayout.LayoutParams padLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Theme.dp(18);
        lp.topMargin = Theme.dp(14);
        lp.bottomMargin = Theme.dp(4);
        return lp;
    }

    private void confirmDelete(final SimStore.Contact k) {
        showDialog("delete contact", "Delete " + k.name + "?", new String[]{"delete", "cancel"}, new Choice() {
            @Override public void on(int i) {
                if (i == 0) {
                    SimStore.contacts.remove(k);
                    SimStore.save();
                    SimStore.fire("contacts");
                    popPage();
                    if (!popped()) refresh();
                }
            }
        });
    }

    private boolean popped() { return false; }

    // ------------------------------------------------------------------ edición

    private void showEdit(final SimStore.Contact k, final boolean isNew) {
        final Context c = getContext();
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(WpUi.pageHeader(c, "people", isNew ? "new" : "edit"));
        final EditText name = field(col, "name", k.name);
        final EditText phone = field(col, "mobile phone", k.phone);
        phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        final EditText email = field(col, "email", k.email);
        email.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText company = field(col, "company", k.company);
        final EditText address = field(col, "address", k.address);
        final EditText bday = field(col, "birthday", k.birthday);
        final EditText note = field(col, "notes", k.note);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.SAVE, "save", new Runnable() { @Override public void run() {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = phone.getText().toString().trim();
                    if (n.isEmpty()) { Toast.makeText(getContext(), "Enter a name", Toast.LENGTH_SHORT).show(); return; }
                    k.name = n;
                    k.phone = phone.getText().toString().trim();
                    k.email = email.getText().toString().trim();
                    k.company = company.getText().toString().trim();
                    k.address = address.getText().toString().trim();
                    k.birthday = bday.getText().toString().trim();
                    k.note = note.getText().toString().trim();
                    if (isNew) SimStore.contacts.add(k);
                    Collections.sort(SimStore.contacts, SimStore.BY_NAME);
                    SimStore.save();
                    SimStore.fire("contacts");
                    WpUi.hideKeyboard(name);
                    popPage();
                } }),
                new AppBar.Item(Glyph.CLOSE, "cancel", new Runnable() { @Override public void run() {
                    WpUi.hideKeyboard(name);
                    popPage();
                } })
        );
        swapContent(sv, ab);
    }

    private EditText field(LinearLayout col, String label, String val) {
        Context c = getContext();
        TextView l = WpUi.label(c, label, 14, Theme.light, Theme.subtle);
        l.setPadding(Theme.dp(18), Theme.dp(8), 0, Theme.dp(3));
        col.addView(l);
        EditText e = WpUi.textBox(c, "", val);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(e, lp);
        return e;
    }

    @Override public boolean onBack() {
        if (!filter.isEmpty()) { filter = ""; refresh(); return true; }
        return super.onBack();
    }
}
