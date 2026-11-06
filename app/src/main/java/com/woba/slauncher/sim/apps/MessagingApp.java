package com.woba.slauncher.sim.apps;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
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
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mensajes: lista de conversaciones, burbujas, envío real por SMS (si hay permiso) o simulado. */
public class MessagingApp extends SimApp implements SimStore.Listener {
    private WpUi.WpList list;
    private String thread;
    private LinearLayout bubbles;
    private ScrollView bubbleScroll;

    public MessagingApp(Context c, SimHost host) {
        super(c, host, "messaging");
        SimStore.addListener(this);
        go(new Runnable() { @Override public void run() { showList(); } });
    }

    @Override public String appTitle() { return "messaging"; }

    @Override public void onDestroy() { SimStore.removeListener(this); }

    @Override public void onOpen(String arg) {
        if (arg == null || arg.isEmpty()) return;
        if (arg.equals("new")) { go(new Runnable() { @Override public void run() { showNew(""); } }); return; }
        final String n = arg;
        go(new Runnable() { @Override public void run() { showThread(n); } });
    }

    @Override public void onShow() { importRealSms(false); }

    @Override public void onSimChanged(String what) {
        if (!what.equals("messages")) return;
        if (list != null && thread == null) refreshList();
        else if (thread != null && bubbles != null) fillBubbles();
    }

    // ------------------------------------------------------------------ SMS reales

    private void importRealSms(boolean ask) {
        final Context c = getContext();
        if (c.checkSelfPermission(Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (ask) host.requestPermissions(new String[]{Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS}, new Runnable() {
                @Override public void run() { importRealSms(false); }
            });
            return;
        }
        Cursor cur = null;
        int added = 0;
        try {
            cur = c.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ},
                    null, null, Telephony.Sms.DATE + " DESC LIMIT 400");
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (SimStore.Msg m : SimStore.messages) seen.add(m.time + "|" + m.body);
            while (cur != null && cur.moveToNext()) {
                String addr = cur.getString(0), body = cur.getString(1);
                long t = cur.getLong(2);
                int type = cur.getInt(3);
                if (addr == null || body == null || !seen.add(t + "|" + body)) continue;
                SimStore.addMsg(addr, body, t, type != Telephony.Sms.MESSAGE_TYPE_INBOX, cur.getInt(4) == 1 || type != Telephony.Sms.MESSAGE_TYPE_INBOX);
                added++;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        if (added > 0) { SimStore.save(); SimStore.fire("messages"); }
    }

    // ------------------------------------------------------------------ lista

    private void showList() {
        thread = null;
        bubbles = null;
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "messaging", "threads"));
        list = new WpUi.WpList(c);
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        list.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof String) { final String t = (String) it.tag; go(new Runnable() { @Override public void run() { showThread(t); } }); }
            }
        });
        list.setOnLong(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof String)) return;
                final String t = (String) it.tag;
                showMenu(x, y + Theme.dpf(130), new String[]{"delete conversation", "call"}, new Choice() {
                    @Override public void on(int i) {
                        if (i == 0) deleteThread(t); else Phone.call(getContext(), host, SimStore.nameFor(t), t);
                    }
                });
            }
        });
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.ADD, "new", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showNew(""); } }); } }),
                new AppBar.Item(Glyph.REFRESH, "sync", new Runnable() { @Override public void run() { importRealSms(true); } })
        ).menu(
                new AppBar.Item(0, "simulate incoming message", new Runnable() { @Override public void run() { simulateIncoming(); } }),
                new AppBar.Item(0, SimStore.flag("sim_sms", false) ? "send real sms" : "simulate sending", new Runnable() { @Override public void run() {
                    SimStore.putFlag("sim_sms", !SimStore.flag("sim_sms", false)); showList();
                } })
        );
        swapContent(col, ab);
        refreshList();
    }

    private void deleteThread(final String t) {
        showDialog("delete", "Delete this conversation?", new String[]{"delete", "cancel"}, new Choice() {
            @Override public void on(int i) {
                if (i != 0) return;
                for (int k = SimStore.messages.size() - 1; k >= 0; k--) if (same(SimStore.messages.get(k).thread, t)) SimStore.messages.remove(k);
                SimStore.save();
                SimStore.fire("messages");
                if (thread != null) popPage();
            }
        });
    }

    private static boolean same(String a, String b) { return SimStore.norm(a).equals(SimStore.norm(b)) || a.equals(b); }

    private LinkedHashMap<String, ArrayList<SimStore.Msg>> threads() {
        ArrayList<SimStore.Msg> all = new ArrayList<>(SimStore.messages);
        Collections.sort(all, new Comparator<SimStore.Msg>() {
            @Override public int compare(SimStore.Msg a, SimStore.Msg b) { return Long.compare(b.time, a.time); }
        });
        LinkedHashMap<String, ArrayList<SimStore.Msg>> map = new LinkedHashMap<>();
        for (SimStore.Msg m : all) {
            String key = null;
            for (String k : map.keySet()) if (same(k, m.thread)) { key = k; break; }
            if (key == null) { key = m.thread; map.put(key, new ArrayList<SimStore.Msg>()); }
            map.get(key).add(m);
        }
        return map;
    }

    private void refreshList() {
        if (list == null) return;
        ArrayList<WpUi.Item> items = new ArrayList<>();
        for (Map.Entry<String, ArrayList<SimStore.Msg>> e : threads().entrySet()) {
            SimStore.Msg last = e.getValue().get(0);
            int unread = 0;
            for (SimStore.Msg m : e.getValue()) if (!m.out && !m.read) unread++;
            String nm = SimStore.nameFor(e.getKey());
            SimStore.Contact k = SimStore.byNumber(e.getKey());
            WpUi.Item it = new WpUi.Item(nm).sub((last.out ? "you: " : "") + last.body.replace('\n', ' ')).right(Phone.when(last.time)).tag(e.getKey());
            it.avatar(k != null ? k.initials() : "#", WpUi.colorFor(nm));
            if (unread > 0) { it.bold = true; it.titleColor = Theme.accent; }
            items.add(it);
        }
        if (items.isEmpty()) items.add(new WpUi.Item("no messages").dim());
        list.setItems(items);
    }

    private void simulateIncoming() {
        if (SimStore.contacts.isEmpty()) return;
        SimStore.Contact k = SimStore.contacts.get((int) (Math.random() * SimStore.contacts.size()));
        String[] texts = {"¿Qué tal todo?", "Te llamo luego", "¿Has visto lo de esta tarde?", "Gracias por ayer", "Nos vemos en un rato", "Ok, perfecto"};
        incoming(k.phone, texts[(int) (Math.random() * texts.length)]);
    }

    private void incoming(String number, String body) {
        SimStore.addMsg(number, body, System.currentTimeMillis(), false, false);
        SimStore.save();
        SimStore.fire("messages");
        host.postNotification("messaging", SimStore.nameFor(number), body, "messaging", number);
    }

    // ------------------------------------------------------------------ conversación

    private void showThread(final String t) {
        thread = t;
        final Context c = getContext();
        for (SimStore.Msg m : SimStore.messages) if (same(m.thread, t)) m.read = true;
        SimStore.save();
        SimStore.fire("messages");
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hdr = new LinearLayout(c);
        hdr.setOrientation(LinearLayout.VERTICAL);
        hdr.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(6));
        hdr.addView(WpUi.label(c, "MESSAGING", 13, Theme.semibold, Theme.fg));
        TextView nm = WpUi.label(c, SimStore.nameFor(t), 32, Theme.light, Theme.fg);
        nm.setPadding(0, Theme.dp(4), 0, 0);
        hdr.addView(nm);
        col.addView(hdr);
        bubbleScroll = new ScrollView(c);
        bubbleScroll.setVerticalScrollBarEnabled(false);
        bubbles = new LinearLayout(c);
        bubbles.setOrientation(LinearLayout.VERTICAL);
        bubbles.setPadding(Theme.dp(12), Theme.dp(6), Theme.dp(12), Theme.dp(6));
        bubbleScroll.addView(bubbles, new ScrollView.LayoutParams(-1, -2));
        col.addView(bubbleScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        col.addView(composeBar(c, new TextResult() {
            @Override public void on(String text) { send(t, text); }
        }), new LinearLayout.LayoutParams(-1, -2));
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.PHONE, "call", new Runnable() { @Override public void run() { Phone.call(getContext(), host, SimStore.nameFor(t), t); } }),
                new AppBar.Item(Glyph.PERSON, "contact", new Runnable() { @Override public void run() { host.openSim("people", SimStore.byNumber(t) != null ? "num:" + t : "new"); } }),
                new AppBar.Item(Glyph.DELETE, "delete", new Runnable() { @Override public void run() { deleteThread(t); } })
        );
        swapContent(col, ab);
        fillBubbles();
    }

    private View composeBar(Context c, final TextResult onSend) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText et = WpUi.multiline(c, "type a message", "");
        et.setMaxLines(4);
        row.addView(et, new LinearLayout.LayoutParams(0, -2, 1f));
        View send = new View(c) {
            @Override protected void onDraw(Canvas cv) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Theme.accent);
                cv.drawRect(0, 0, getWidth(), getHeight(), p);
                Glyph.draw(cv, Glyph.SEND, getWidth() / 2f, getHeight() / 2f, getWidth() * 0.5f, 0xFFFFFFFF);
            }
        };
        send.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                String s = et.getText().toString().trim();
                if (s.isEmpty()) return;
                et.setText("");
                onSend.on(s);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(48), Theme.dp(44));
        lp.leftMargin = Theme.dp(6);
        row.addView(send, lp);
        return row;
    }

    private void fillBubbles() {
        if (bubbles == null || thread == null) return;
        bubbles.removeAllViews();
        ArrayList<SimStore.Msg> ms = new ArrayList<>();
        for (SimStore.Msg m : SimStore.messages) if (same(m.thread, thread)) ms.add(m);
        Collections.sort(ms, new Comparator<SimStore.Msg>() {
            @Override public int compare(SimStore.Msg a, SimStore.Msg b) { return Long.compare(a.time, b.time); }
        });
        Context c = getContext();
        for (SimStore.Msg m : ms) {
            LinearLayout wrap = new LinearLayout(c);
            wrap.setOrientation(LinearLayout.VERTICAL);
            wrap.setGravity(m.out ? Gravity.END : Gravity.START);
            TextView b = WpUi.label(c, m.body, 17, Theme.regular, 0xFFFFFFFF);
            b.setPadding(Theme.dp(12), Theme.dp(8), Theme.dp(12), Theme.dp(8));
            b.setBackgroundColor(m.out ? Theme.accent : (Theme.bg == 0xFF000000 ? 0xFF3A3A3A : 0xFF8A8A8A));
            b.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));
            wrap.addView(b, new LinearLayout.LayoutParams(-2, -2));
            TextView tm = WpUi.label(c, Phone.full(m.time), 11, Theme.light, Theme.subtle);
            tm.setPadding(Theme.dp(2), Theme.dp(2), Theme.dp(2), 0);
            wrap.addView(tm, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Theme.dp(8);
            bubbles.addView(wrap, lp);
        }
        bubbleScroll.post(new Runnable() { @Override public void run() { bubbleScroll.fullScroll(View.FOCUS_DOWN); } });
    }

    private void send(final String to, final String text) {
        final Context c = getContext();
        boolean real = !SimStore.flag("sim_sms", false);
        if (real && c.checkSelfPermission(Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS}, new Runnable() {
                @Override public void run() { deliver(to, text); }
            });
            return;
        }
        deliver(to, text);
    }

    private void deliver(String to, String text) {
        Context c = getContext();
        boolean real = !SimStore.flag("sim_sms", false)
                && c.checkSelfPermission(Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (real) {
            try {
                SmsManager sm = android.os.Build.VERSION.SDK_INT >= 31 ? c.getSystemService(SmsManager.class) : SmsManager.getDefault();
                ArrayList<String> parts = sm.divideMessage(text);
                sm.sendMultipartTextMessage(to, null, parts, null, null);
            } catch (Exception e) {
                Toast.makeText(c, "Couldn't send the message", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        SimStore.addMsg(to, text, System.currentTimeMillis(), true, true);
        SimStore.save();
        SimStore.fire("messages");
    }

    // ------------------------------------------------------------------ nuevo mensaje

    private void showNew(String initial) {
        thread = null;
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "messaging", "new message"));
        final EditText to = WpUi.textBox(c, "to: name or number", initial);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(to, lp);
        final WpUi.WpList sug = new WpUi.WpList(c);
        col.addView(sug, new LinearLayout.LayoutParams(-1, 0, 1f));
        sug.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Contact) { to.setText(((SimStore.Contact) it.tag).phone); to.setSelection(to.getText().length()); sug.setItems(new ArrayList<WpUi.Item>()); }
            }
        });
        to.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().toLowerCase().trim();
                ArrayList<WpUi.Item> out = new ArrayList<>();
                if (!q.isEmpty()) for (SimStore.Contact k : SimStore.contacts) {
                    if (k.name.toLowerCase().contains(q) || k.phone.contains(q)) out.add(new WpUi.Item(k.name).sub(k.phone).avatar(k.initials(), WpUi.colorFor(k.name)).tag(k));
                }
                sug.setItems(out);
            }
        });
        col.addView(composeBar(c, new TextResult() {
            @Override public void on(String text) {
                String dest = to.getText().toString().trim();
                if (dest.isEmpty()) { Toast.makeText(getContext(), "Enter a recipient", Toast.LENGTH_SHORT).show(); return; }
                SimStore.Contact k = null;
                for (SimStore.Contact x : SimStore.contacts) if (x.name.equalsIgnoreCase(dest)) { k = x; break; }
                final String num = k != null ? k.phone : dest;
                WpUi.hideKeyboard(to);
                send(num, text);
                replacePage(new Runnable() { @Override public void run() { showThread(num); } });
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        swapContent(col, null);
        to.requestFocus();
        to.post(new Runnable() { @Override public void run() { WpUi.showKeyboard(to); } });
    }
}
