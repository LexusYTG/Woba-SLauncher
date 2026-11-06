package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

/** Outlook Mail simulado: bandeja, enviados, borradores, eliminados y marcados; redactar y leer. */
public class MailApp extends SimApp implements SimStore.Listener {
    private WpUi.WpList all, unread, flagged, folders;
    private String folder = "inbox";

    public MailApp(Context c, SimHost host) {
        super(c, host, "mail");
        SimStore.addListener(this);
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "outlook mail"; }

    @Override public void onDestroy() { SimStore.removeListener(this); }

    @Override public void onSimChanged(String w) { if (all != null && w.equals("mail")) refresh(); }

    @Override public void onOpen(String arg) {
        if (arg == null) return;
        if (arg.startsWith("compose|")) {
            final String to = arg.substring(8);
            go(new Runnable() { @Override public void run() { showCompose(to, "", "", null); } });
        }
    }

    private void showMain() {
        final Context c = getContext();
        all = new WpUi.WpList(c);
        unread = new WpUi.WpList(c);
        flagged = new WpUi.WpList(c);
        folders = new WpUi.WpList(c);
        WpUi.OnItem open = new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Mail) { final SimStore.Mail m = (SimStore.Mail) it.tag; go(new Runnable() { @Override public void run() { showRead(m); } }); }
            }
        };
        WpUi.OnItem menu = new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof SimStore.Mail)) return;
                final SimStore.Mail m = (SimStore.Mail) it.tag;
                showMenu(x, y + Theme.dpf(160), new String[]{m.read ? "mark as unread" : "mark as read", m.flagged ? "clear flag" : "flag", "delete"}, new Choice() {
                    @Override public void on(int i) {
                        if (i == 0) m.read = !m.read; else if (i == 1) m.flagged = !m.flagged; else trash(m);
                        SimStore.save(); refresh();
                    }
                });
            }
        };
        all.setOnItem(open); unread.setOnItem(open); flagged.setOnItem(open);
        all.setOnLong(menu); unread.setOnLong(menu); flagged.setOnLong(menu);
        folders.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof String) { folder = (String) it.tag; refresh(); ((Pivot) pivotRef).setPage(0, true); }
            }
        });
        Pivot pv = new Pivot(c, "outlook mail", new String[]{"all", "unread", "flagged", "folders"}, new View[]{all, unread, flagged, folders});
        pivotRef = pv;
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.ADD, "new", new Runnable() { @Override public void run() {
                    go(new Runnable() { @Override public void run() { showCompose("", "", "", null); } });
                } }),
                new AppBar.Item(Glyph.REFRESH, "sync", new Runnable() { @Override public void run() {
                    Toast.makeText(getContext(), "Mailbox up to date (simulated)", Toast.LENGTH_SHORT).show();
                } })
        ).menu(new AppBar.Item(0, "open real mail app", new Runnable() { @Override public void run() {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL);
            host.launch(i);
        } }));
        swapContent(pv, ab);
        refresh();
    }

    private Object pivotRef;

    private void trash(SimStore.Mail m) {
        if (m.folder.equals("deleted")) SimStore.mails.remove(m); else m.folder = "deleted";
    }

    private WpUi.Item row(SimStore.Mail m) {
        boolean sent = m.folder.equals("sent") || m.folder.equals("drafts");
        String who = sent ? "to: " + m.to : m.from;
        WpUi.Item it = new WpUi.Item(who).sub(m.subject + "\n").right(Phone.when(m.time)).tag(m);
        it.sub = m.subject;
        if (!m.read && !sent) { it.bold = true; it.titleColor = Theme.accent; }
        return it;
    }

    private void refresh() {
        if (all == null) return;
        ArrayList<SimStore.Mail> l = new ArrayList<>(SimStore.mails);
        Collections.sort(l, new Comparator<SimStore.Mail>() {
            @Override public int compare(SimStore.Mail a, SimStore.Mail b) { return Long.compare(b.time, a.time); }
        });
        ArrayList<WpUi.Item> a = new ArrayList<>(), u = new ArrayList<>(), f = new ArrayList<>();
        a.add(WpUi.Item.section(folder));
        for (SimStore.Mail m : l) {
            if (m.folder.equals(folder)) a.add(row(m));
            if (m.folder.equals("inbox") && !m.read) u.add(row(m));
            if (m.flagged && !m.folder.equals("deleted")) f.add(row(m));
        }
        if (a.size() == 1) a.add(new WpUi.Item("nothing here").dim());
        if (u.isEmpty()) u.add(new WpUi.Item("no unread mail").dim());
        if (f.isEmpty()) f.add(new WpUi.Item("no flagged mail").dim());
        all.setItems(a); unread.setItems(u); flagged.setItems(f);
        String[][] fs = {{"inbox", "inbox"}, {"sent", "sent items"}, {"drafts", "drafts"}, {"deleted", "deleted items"}};
        ArrayList<WpUi.Item> fi = new ArrayList<>();
        for (String[] x : fs) {
            int n = 0;
            for (SimStore.Mail m : SimStore.mails) if (m.folder.equals(x[0]) && (x[0].equals("inbox") ? !m.read : true)) n++;
            fi.add(new WpUi.Item(x[1]).right(n > 0 ? String.valueOf(n) : null).tag(x[0]).glyph(x[0].equals("inbox") ? Glyph.MAIL : x[0].equals("sent") ? Glyph.SEND : x[0].equals("drafts") ? Glyph.EDIT : Glyph.DELETE, Theme.accent));
        }
        folders.setItems(fi);
    }

    // ------------------------------------------------------------------ lectura

    private void showRead(final SimStore.Mail m) {
        final Context c = getContext();
        if (!m.read) { m.read = true; SimStore.save(); SimStore.fire("mail"); }
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(20));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(WpUi.label(c, "OUTLOOK MAIL", 13, Theme.semibold, Theme.fg));
        TextView s = WpUi.label(c, m.subject, 28, Theme.light, Theme.fg);
        s.setPadding(0, Theme.dp(8), 0, Theme.dp(8));
        col.addView(s);
        col.addView(WpUi.label(c, m.from + "  <" + m.fromAddr + ">", 16, Theme.semibold, Theme.accent));
        col.addView(WpUi.label(c, "to: " + m.to, 14, Theme.light, Theme.subtle));
        TextView tm = WpUi.label(c, Phone.full(m.time), 14, Theme.light, Theme.subtle);
        tm.setPadding(0, 0, 0, Theme.dp(14));
        col.addView(tm);
        TextView b = WpUi.label(c, m.body, 17, Theme.light, Theme.fg);
        b.setLineSpacing(0, 1.15f);
        b.setTextIsSelectable(true);
        col.addView(b);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.REPLY, "reply", new Runnable() { @Override public void run() {
                    go(new Runnable() { @Override public void run() { showCompose(m.fromAddr, "RE: " + m.subject, "\n\n--- " + m.from + " wrote:\n" + m.body, null); } });
                } }),
                new AppBar.Item(Glyph.RIGHT, "forward", new Runnable() { @Override public void run() {
                    go(new Runnable() { @Override public void run() { showCompose("", "FW: " + m.subject, "\n\n--- " + m.from + " wrote:\n" + m.body, null); } });
                } }),
                new AppBar.Item(m.flagged ? Glyph.FLAG : Glyph.FLAG, m.flagged ? "unflag" : "flag", new Runnable() { @Override public void run() {
                    m.flagged = !m.flagged; SimStore.save(); SimStore.fire("mail");
                    Toast.makeText(getContext(), m.flagged ? "Flagged" : "Flag cleared", Toast.LENGTH_SHORT).show();
                } }),
                new AppBar.Item(Glyph.DELETE, "delete", new Runnable() { @Override public void run() {
                    trash(m); SimStore.save(); SimStore.fire("mail"); popPage();
                } })
        ).menu(new AppBar.Item(0, "mark as unread", new Runnable() { @Override public void run() {
            m.read = false; SimStore.save(); SimStore.fire("mail"); popPage();
        } }));
        swapContent(sv, ab);
    }

    // ------------------------------------------------------------------ redactar

    private void showCompose(String to, String subject, String body, SimStore.Mail draft) {
        final Context c = getContext();
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(WpUi.pageHeader(c, "outlook mail", "new"));
        final EditText toE = field(col, "to", to);
        toE.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        final EditText subE = field(col, "subject", subject);
        final EditText bodyE = WpUi.multiline(c, "", body);
        bodyE.setMinLines(8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        lp.topMargin = Theme.dp(10);
        col.addView(bodyE, lp);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.SEND, "send", new Runnable() { @Override public void run() {
                    String t = toE.getText().toString().trim();
                    if (t.isEmpty()) { Toast.makeText(getContext(), "Add a recipient", Toast.LENGTH_SHORT).show(); return; }
                    store("sent", t, subE.getText().toString(), bodyE.getText().toString());
                    if (SimStore.flag("mail_real", false)) {
                        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(t)));
                        i.putExtra(Intent.EXTRA_SUBJECT, subE.getText().toString());
                        i.putExtra(Intent.EXTRA_TEXT, bodyE.getText().toString());
                        host.launch(i);
                    } else Toast.makeText(getContext(), "Message sent (simulated)", Toast.LENGTH_SHORT).show();
                    WpUi.hideKeyboard(toE);
                    popPage();
                } }),
                new AppBar.Item(Glyph.SAVE, "save draft", new Runnable() { @Override public void run() {
                    store("drafts", toE.getText().toString(), subE.getText().toString(), bodyE.getText().toString());
                    WpUi.hideKeyboard(toE);
                    popPage();
                } })
        ).menu(new AppBar.Item(0, SimStore.flag("mail_real", false) ? "send only inside simulator" : "also open real mail app", new Runnable() { @Override public void run() {
            SimStore.putFlag("mail_real", !SimStore.flag("mail_real", false));
            Toast.makeText(getContext(), SimStore.flag("mail_real", false) ? "Send opens your email app" : "Send is simulated", Toast.LENGTH_SHORT).show();
        } }));
        swapContent(sv, ab);
    }

    private void store(String fol, String to, String subj, String body) {
        SimStore.Mail m = new SimStore.Mail();
        m.id = SimStore.newId();
        m.folder = fol;
        m.from = "me";
        m.fromAddr = "yo@outlook.com";
        m.to = to;
        m.subject = subj.isEmpty() ? "(no subject)" : subj;
        m.body = body;
        m.time = System.currentTimeMillis();
        m.read = true;
        SimStore.mails.add(m);
        SimStore.save();
        SimStore.fire("mail");
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
}
