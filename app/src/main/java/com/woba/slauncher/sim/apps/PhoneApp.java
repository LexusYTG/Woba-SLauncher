package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

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

/** Teléfono: historial (real + simulado), marcación rápida, buzón de voz y teclado de marcación. */
public class PhoneApp extends SimApp implements SimStore.Listener {
    private WpUi.WpList history, speed, voice;
    private Dial dial;
    private boolean realMerged;

    public PhoneApp(Context c, SimHost host) {
        super(c, host, "phone");
        SimStore.addListener(this);
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "phone"; }

    @Override public void onDestroy() { SimStore.removeListener(this); }

    @Override public void onShow() { SimStore.markCallsSeen(); refresh(); }

    @Override public void onOpen(String arg) {
        if (arg != null && arg.startsWith("dial|")) {
            final String n = arg.substring(5);
            go(new Runnable() { @Override public void run() { showDial(n); } });
        }
    }

    @Override public void onSimChanged(String what) {
        if (history != null && (what.equals("calls") || what.equals("contacts"))) refresh();
    }

    private void showMain() {
        final Context c = getContext();
        history = new WpUi.WpList(c);
        speed = new WpUi.WpList(c);
        voice = new WpUi.WpList(c);
        history.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Call) { SimStore.Call cl = (SimStore.Call) it.tag; Phone.call(getContext(), host, cl.name, cl.number); }
            }
        });
        history.setOnLong(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof SimStore.Call)) return;
                final SimStore.Call cl = (SimStore.Call) it.tag;
                final boolean known = SimStore.byNumber(cl.number) != null;
                showMenu(x, y + Theme.dpf(160), new String[]{"call", "send a message", known ? "view contact" : "save to people", "delete"}, new Choice() {
                    @Override public void on(int i) {
                        if (i == 0) Phone.call(getContext(), host, cl.name, cl.number);
                        else if (i == 1) Phone.text(host, cl.number);
                        else if (i == 2) host.openSim("people", known ? "num:" + cl.number : "new");
                        else { SimStore.calls.remove(cl); SimStore.save(); refresh(); }
                    }
                });
            }
        });
        speed.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Contact) { SimStore.Contact k = (SimStore.Contact) it.tag; Phone.call(getContext(), host, k.name, k.phone); }
            }
        });
        voice.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) { Phone.call(getContext(), host, "Voicemail", "123"); }
        });
        Pivot pv = new Pivot(c, "phone", new String[]{"history", "speed dial", "voicemail"}, new View[]{history, speed, voice});
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.KEYPAD, "call", new Runnable() { @Override public void run() {
                    go(new Runnable() { @Override public void run() { showDial(""); } });
                } }),
                new AppBar.Item(Glyph.PEOPLE, "people", new Runnable() { @Override public void run() { host.openSim("people", null); } }),
                new AppBar.Item(Glyph.SEARCH, "search", new Runnable() { @Override public void run() { host.openSim("people", null); } })
        ).menu(
                new AppBar.Item(0, "delete all history", new Runnable() { @Override public void run() {
                    showDialog("delete history", "Delete all calls?", new String[]{"delete", "cancel"}, new Choice() {
                        @Override public void on(int i) { if (i == 0) { SimStore.calls.clear(); SimStore.save(); refresh(); } }
                    });
                } }),
                new AppBar.Item(0, SimStore.flag("sim_calls", false) ? "use real calls" : "use simulated calls", new Runnable() { @Override public void run() {
                    SimStore.putFlag("sim_calls", !SimStore.flag("sim_calls", false));
                    showMain();
                } }),
                new AppBar.Item(0, "simulate incoming call", new Runnable() { @Override public void run() {
                    SimStore.Contact k = SimStore.contacts.isEmpty() ? null : SimStore.contacts.get((int) (Math.random() * SimStore.contacts.size()));
                    host.startCall(k == null ? "" : k.name, k == null ? "+34 600 000 000" : k.phone, true);
                } })
        );
        swapContent(pv, ab);
        refresh();
    }

    private void refresh() {
        if (history == null) return;
        ArrayList<SimStore.Call> list = new ArrayList<>(SimStore.calls);
        Collections.sort(list, new Comparator<SimStore.Call>() {
            @Override public int compare(SimStore.Call a, SimStore.Call b) { return Long.compare(b.time, a.time); }
        });
        ArrayList<WpUi.Item> items = new ArrayList<>();
        for (SimStore.Call cl : list) {
            String nm = cl.name != null && !cl.name.isEmpty() ? cl.name : SimStore.nameFor(cl.number);
            String type = cl.type == 3 ? "missed" : cl.type == 2 ? "outgoing" : "incoming";
            WpUi.Item it = new WpUi.Item(nm).sub(type + (cl.seconds > 0 ? " · " + Phone.duration(cl.seconds) : "") + (nm.equals(cl.number) ? "" : " · " + cl.number)).right(Phone.when(cl.time)).tag(cl);
            if (cl.type == 3) it.titleColor = 0xFFE51400;
            SimStore.Contact k = SimStore.byNumber(cl.number);
            it.avatar(k != null ? k.initials() : "#", WpUi.colorFor(nm));
            items.add(it);
        }
        if (items.isEmpty()) items.add(new WpUi.Item("no calls").dim());
        history.setItems(items);
        ArrayList<WpUi.Item> f = new ArrayList<>();
        int n = 1;
        for (SimStore.Contact k : SimStore.contacts) if (k.fav && !k.phone.isEmpty()) {
            f.add(new WpUi.Item(k.name).sub(k.phone).right(String.valueOf(n++)).avatar(k.initials(), WpUi.colorFor(k.name)).tag(k));
        }
        if (f.isEmpty()) f.add(new WpUi.Item("add favorites in People").dim());
        speed.setItems(f);
        ArrayList<WpUi.Item> v = new ArrayList<>();
        v.add(new WpUi.Item("call voicemail").sub("no messages").glyph(Glyph.VOICEMAIL, Theme.accent));
        voice.setItems(v);
    }

    // ------------------------------------------------------------------ teclado

    private void showDial(String initial) {
        final Context c = getContext();
        dial = new Dial(c, initial);
        swapContent(dial, null);
    }

    private class Dial extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        StringBuilder num = new StringBuilder();
        int down = -1;
        ToneGenerator tg;
        final String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        final String[] subs = {"", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""};

        Dial(Context c, String init) {
            super(c);
            num.append(init == null ? "" : init);
            try { tg = new ToneGenerator(AudioManager.STREAM_DTMF, 70); } catch (Exception ignored) {}
        }

        float keyH() { return Theme.dpf(62); }

        float top() { return getHeight() - Theme.dpf(70) - 4 * keyH(); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Theme.semibold);
            p.setTextSize(Theme.sp(13));
            p.setColor(Theme.fg);
            c.drawText("PHONE", Theme.dpf(18), Theme.dpf(30), p);
            p.setTypeface(Theme.light);
            String s = num.toString();
            p.setTextSize(Theme.sp(40));
            while (p.measureText(s) > w - Theme.dpf(90) && p.getTextSize() > Theme.sp(18)) p.setTextSize(p.getTextSize() - 2);
            p.setColor(Theme.fg);
            c.drawText(s, Theme.dpf(18), Theme.dpf(100), p);
            SimStore.Contact k = s.isEmpty() ? null : match(s);
            if (k != null) {
                p.setTextSize(Theme.sp(19));
                p.setColor(Theme.accent);
                c.drawText(k.name, Theme.dpf(18), Theme.dpf(132), p);
            }
            if (!s.isEmpty()) Glyph.draw(c, Glyph.BACKSPACE, w - Theme.dpf(36), Theme.dpf(84), Theme.dpf(28), down == 50 ? Theme.accent : Theme.fg);
            float kw = w / 3f, kh = keyH(), y0 = top();
            p.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < 12; i++) {
                float x = (i % 3) * kw, y = y0 + (i / 3) * kh;
                p.setColor(down == i ? Theme.accent : WpUi.mix(Theme.bg, Theme.fg, 0.14f));
                c.drawRect(x + 2, y + 2, x + kw - 2, y + kh - 2, p);
                p.setColor(down == i ? 0xFFFFFFFF : Theme.fg);
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp(28));
                c.drawText(keys[i], x + kw / 2f, Theme.centerBaseline(p, y + kh * (subs[i].isEmpty() ? 0.5f : 0.42f)), p);
                if (!subs[i].isEmpty()) {
                    p.setTextSize(Theme.sp(10));
                    p.setColor(down == i ? 0xFFFFFFFF : Theme.subtle);
                    c.drawText(subs[i], x + kw / 2f, Theme.centerBaseline(p, y + kh * 0.78f), p);
                }
            }
            p.setTextAlign(Paint.Align.LEFT);
            float by = h - Theme.dpf(60);
            p.setColor(down == 40 ? Theme.fg : 0xFF2E8B2E);
            c.drawRect(Theme.dpf(18), by, w - Theme.dpf(18), by + Theme.dpf(48), p);
            Glyph.draw(c, Glyph.PHONE, w / 2f, by + Theme.dpf(24), Theme.dpf(26), 0xFFFFFFFF);
        }

        SimStore.Contact match(String s) {
            String q = SimStore.norm(s);
            if (q.length() < 3) return null;
            for (SimStore.Contact k : SimStore.contacts) if (SimStore.norm(k.phone).contains(q)) return k;
            return null;
        }

        int hit(float x, float y) {
            float h = getHeight(), w = getWidth(), kw = w / 3f;
            if (y > h - Theme.dpf(60) && y < h - Theme.dpf(12)) return 40;
            if (y < Theme.dpf(110) && x > w - Theme.dpf(70) && num.length() > 0) return 50;
            int col = (int) (x / kw), row = (int) ((y - top()) / keyH());
            if (row >= 0 && row < 4 && col >= 0 && col < 3) return row * 3 + col;
            return -1;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down = hit(e.getX(), e.getY());
                    if (down >= 0 && down < 12 && tg != null) {
                        int tone = down == 10 ? 0 : down == 9 ? 10 : down == 11 ? 11 : down + 1;
                        tg.startTone(ToneGenerator.TONE_DTMF_0 + tone, 150);
                    }
                    if (down >= 0 && down < 12 && down == 10) postDelayed(plusRun, 600);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP: {
                    removeCallbacks(plusRun);
                    int k = hit(e.getX(), e.getY());
                    int d = down;
                    down = -1;
                    invalidate();
                    if (k != d || k < 0 || plusDone) { plusDone = false; return true; }
                    if (k < 12) num.append(keys[k]);
                    else if (k == 50 && num.length() > 0) num.deleteCharAt(num.length() - 1);
                    else if (k == 40) {
                        String s = num.toString();
                        if (!s.isEmpty()) {
                            SimStore.Contact m = SimStore.byNumber(s);
                            Phone.call(getContext(), host, m != null ? m.name : "", s);
                        }
                    }
                    invalidate();
                    return true;
                }
                default: removeCallbacks(plusRun); down = -1; invalidate(); return true;
            }
        }

        boolean plusDone;
        final Runnable plusRun = new Runnable() {
            @Override public void run() {
                if (down == 10) { num.append('+'); plusDone = true; invalidate(); }
            }
        };
    }
}
