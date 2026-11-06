package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;

/** Cartera simulada: tarjetas y pases locales, y un "toca para pagar" que simula NFC. Los números son ficticios y no salen del teléfono. */
public class WalletApp extends SimApp {
    private Cards cards;
    private Cards passes;

    public WalletApp(Context c, SimHost host) {
        super(c, host, "wallet");
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "wallet"; }

    private ArrayList<String[]> load(String key) {
        ArrayList<String[]> out = new ArrayList<>();
        for (String s : SimStore.str(key, "").split("\n")) if (!s.isEmpty()) out.add(s.split("\\|", -1));
        return out;
    }

    private void store(String key, ArrayList<String[]> l) {
        StringBuilder sb = new StringBuilder();
        for (String[] a : l) sb.append(android.text.TextUtils.join("|", a)).append('\n');
        SimStore.putStr(key, sb.toString());
    }

    private void showMain() {
        final Context c = getContext();
        cards = new Cards(c, "cards");
        passes = new Cards(c, "passes");
        Pivot pv = new Pivot(c, "wallet", new String[]{"cards", "passes", "tap to pay"}, new View[]{cards, passes, new Pay(c)});
        AppBar ab = new AppBar(c).items(new AppBar.Item(Glyph.ADD, "add", new Runnable() { @Override public void run() { add(pv0.getPage() == 1 ? "passes" : "cards"); } }));
        pv0 = pv;
        swapContent(pv, ab);
    }

    private Pivot pv0;

    private void add(final String key) {
        showPrompt(key.equals("cards") ? "card name" : "pass name", "e.g. Visa, Gym, Boarding pass", "", new TextResult() {
            @Override public void on(final String name) {
                if (name.trim().isEmpty()) return;
                showPrompt("number / code", "last digits or code", "", new TextResult() {
                    @Override public void on(String code) {
                        ArrayList<String[]> l = load(key);
                        l.add(new String[]{name.trim(), code.trim(), String.valueOf((int) (Math.random() * Theme.ACCENTS.length))});
                        store(key, l);
                        cards.invalidate(); passes.invalidate();
                    }
                });
            }
        });
    }

    private class Cards extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final String key;

        Cards(Context c, String key) { super(c); this.key = key; }

        @Override protected void onDraw(Canvas c) {
            ArrayList<String[]> l = load(key);
            float w = getWidth(), ch = (w - Theme.dpf(36)) * 0.6f;
            if (l.isEmpty()) {
                p.setColor(Theme.subtle);
                p.setTextSize(Theme.sp(19));
                p.setTypeface(Theme.light);
                c.drawText("tap + to add one", Theme.dpf(18), Theme.dpf(40), p);
                return;
            }
            for (int i = 0; i < l.size(); i++) {
                String[] a = l.get(i);
                float y = Theme.dpf(8) + i * (ch + Theme.dpf(12));
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.ACCENTS[Integer.parseInt(a[2]) % Theme.ACCENTS.length]);
                c.drawRect(Theme.dpf(18), y, w - Theme.dpf(18), y + ch, p);
                p.setColor(0xFFFFFFFF);
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp(26));
                c.drawText(a[0], Theme.dpf(34), y + Theme.dpf(46), p);
                p.setTextSize(Theme.sp(20));
                c.drawText(key.equals("cards") ? "•••• •••• •••• " + a[1] : a[1], Theme.dpf(34), y + ch - Theme.dpf(24), p);
                Glyph.draw(c, key.equals("cards") ? Glyph.WALLET : Glyph.PAGE, w - Theme.dpf(54), y + Theme.dpf(40), Theme.dpf(30), 0xFFFFFFFF);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                ArrayList<String[]> l = load(key);
                float ch = (getWidth() - Theme.dpf(36)) * 0.6f;
                int i = (int) ((e.getY() - Theme.dpf(8)) / (ch + Theme.dpf(12)));
                if (i >= 0 && i < l.size()) {
                    final int idx = i;
                    showMenu(e.getX(), e.getY() + Theme.dpf(150), new String[]{"delete"}, new Choice() {
                        @Override public void on(int k) { ArrayList<String[]> x = load(key); x.remove(idx); store(key, x); invalidate(); }
                    });
                }
            }
            return true;
        }
    }

    private class Pay extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        long paidAt;

        Pay(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2f, cy = Theme.dpf(170);
            boolean done = System.currentTimeMillis() - paidAt < 2500;
            float t = (System.currentTimeMillis() % 1600) / 1600f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dpf(3));
            for (int i = 0; i < 3; i++) {
                float f = (t + i / 3f) % 1f;
                p.setColor(Theme.alpha(done ? 0xFF2E8B2E : Theme.accent, 1f - f));
                c.drawCircle(cx, cy, Theme.dpf(40) + f * Theme.dpf(80), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(done ? 0xFF2E8B2E : Theme.accent);
            c.drawCircle(cx, cy, Theme.dpf(40), p);
            Glyph.draw(c, done ? Glyph.CHECK : Glyph.WALLET, cx, cy, Theme.dpf(40), 0xFFFFFFFF, Theme.dpf(3));
            p.setColor(Theme.fg);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(22));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(done ? "payment simulated" : "hold near the reader", cx, cy + Theme.dpf(150), p);
            p.setColor(Theme.subtle);
            p.setTextSize(Theme.sp(13));
            c.drawText("simulation only · nothing is charged", cx, cy + Theme.dpf(176), p);
            p.setTextAlign(Paint.Align.LEFT);
            postInvalidateDelayed(50);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                paidAt = System.currentTimeMillis();
                try { Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE); if (v != null) v.vibrate(80); } catch (Exception ignored) {}
            }
            return true;
        }
    }
}
