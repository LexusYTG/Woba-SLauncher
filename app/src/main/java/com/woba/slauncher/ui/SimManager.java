package com.woba.slauncher.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.data.Notif;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimRegistry;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Gestiona las apps simuladas dentro del launcher: pila de navegación, apps suspendidas (recientes),
 * transiciones "turnstile" de WP y el selector de aplicaciones recientes.
 */
public class SimManager implements SimHost {
    /** Qué necesita de la raíz. */
    public interface Root {
        FrameLayout.LayoutParams contentLp();
        void bringChromeToFront();
        void onSimStateChanged();
        void openCortana();
        void launch(Intent i);
        HostBridge bridge();
        FrameLayout layer();
    }

    /** Signo del giro "puerta": si la animación sale al revés, cambiar a +1. */
    private static final float TURN = -1f;

    private final Root root;
    private final Context ctx;
    private final ArrayList<SimApp> chain = new ArrayList<>();
    private final ArrayList<SimApp> running = new ArrayList<>(); // más reciente al final
    private final HashMap<SimApp, Bitmap> snaps = new HashMap<>();
    private boolean visible;
    private RecentsView recents;

    public SimManager(Context ctx, Root root) {
        this.ctx = ctx;
        this.root = root;
    }

    public boolean isVisible() { return visible; }

    /** La Activity pasa a segundo plano: las apps que usan hardware (cámara) lo sueltan. */
    public void hostPause() {
        for (SimApp a : new ArrayList<>(running)) a.onHostPause();
    }

    public void hostResume() {
        for (SimApp a : new ArrayList<>(running)) a.onHostResume();
    }

    /** Tecla física con una app simulada arriba (volumen = disparador de la cámara). */
    public boolean hardwareKey(int keyCode) {
        SimApp t = visible && recents == null ? top() : null;
        return t != null && t.onHardwareKey(keyCode);
    }

    public boolean hasRecents() { return recents != null; }

    private SimApp top() { return chain.isEmpty() ? null : chain.get(chain.size() - 1); }

    private SimApp find(String id) {
        for (SimApp a : running) if (a.id.equals(id)) return a;
        return null;
    }

    // ------------------------------------------------------------------ apertura

    @Override public void openSim(String id, String arg) {
        SimRegistry.Def d = SimRegistry.get(id);
        if (d == null) return;
        closeRecents();
        if (d.directAction != null) {
            try { root.launch(new Intent(d.directAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
            return;
        }
        SimApp app = find(id);
        boolean created = false;
        if (app == null) {
            app = d.factory.create(ctx, this);
            running.add(app);
            created = true;
        }
        SimApp prev = visible ? top() : null;
        if (!visible) {
            // viene del Start: la cadena anterior queda suspendida
            for (SimApp a : chain) if (a != app) a.setVisibility(View.INVISIBLE);
            chain.clear();
        }
        chain.remove(app);
        chain.add(app);
        running.remove(app);
        running.add(app);
        if (app.getParent() == null) root.layer().addView(app, root.contentLp());
        app.setVisibility(View.VISIBLE);
        root.bringChromeToFront();
        if (prev != null && prev != app) {
            snaps.put(prev, snapshot(prev));
            prev.onHide();
            final SimApp p = prev;
            p.postDelayed(new Runnable() {
                @Override public void run() { if (p != top()) p.setVisibility(View.INVISIBLE); }
            }, 300);
        }
        boolean wasVisible = visible;
        visible = true;
        if (created || arg != null) app.onOpen(arg);
        app.onShow();
        animIn(app, !wasVisible || prev != app);
        root.onSimStateChanged();
    }

    @Override public void closeSim(final SimApp app) {
        if (!chain.contains(app) && !running.contains(app)) return;
        boolean wasTop = app == top();
        chain.remove(app);
        running.remove(app);
        snaps.remove(app);
        app.onDestroy();
        if (wasTop && visible) {
            animOut(app, true, new Runnable() {
                @Override public void run() { root.layer().removeView(app); }
            });
            SimApp next = top();
            if (next != null) {
                next.setVisibility(View.VISIBLE);
                next.onShow();
            } else visible = false;
        } else {
            root.layer().removeView(app);
        }
        root.onSimStateChanged();
    }

    @Override public void goStart() {
        closeRecents();
        if (!visible) return;
        final SimApp t = top();
        if (t != null) {
            snaps.put(t, snapshot(t));
            t.onHide();
            animOut(t, false, new Runnable() {
                @Override public void run() { if (!visible) t.setVisibility(View.INVISIBLE); }
            });
        }
        for (SimApp a : chain) if (a != t) a.setVisibility(View.INVISIBLE);
        visible = false;
        root.onSimStateChanged();
    }

    /** Cierra todas las apps simuladas (reset). */
    public void closeAll() {
        closeRecents();
        for (SimApp a : new ArrayList<>(running)) {
            a.onDestroy();
            root.layer().removeView(a);
        }
        running.clear();
        chain.clear();
        snaps.clear();
        visible = false;
        root.onSimStateChanged();
    }

    /** Botón atrás. true si lo consumió. */
    public boolean onBack() {
        if (recents != null) { closeRecents(); return true; }
        if (!visible) return false;
        SimApp t = top();
        if (t == null) return false;
        if (!t.handleBack()) closeSim(t);
        return true;
    }

    public void onThemeChanged() {
        for (SimApp a : running) a.onThemeChanged();
    }

    /** Aplica el margen inferior de la barra de navegación a todas las apps. */
    public void relayout() {
        for (SimApp a : running) {
            if (a.getLayoutParams() != null) a.setLayoutParams(root.contentLp());
        }
    }

    // ------------------------------------------------------------------ animaciones

    private void animIn(View v, boolean full) {
        v.animate().cancel();
        v.setCameraDistance(Theme.dpf(9000));
        v.setPivotX(0f);
        v.setPivotY(root.layer().getHeight() / 2f);
        v.setAlpha(0f);
        v.setRotationY(TURN * 72f);
        v.animate().rotationY(0f).alpha(1f).setDuration(full ? 280 : 200)
                .setInterpolator(new DecelerateInterpolator(1.4f)).start();
    }

    private void animOut(final View v, boolean back, final Runnable end) {
        v.animate().cancel();
        v.setCameraDistance(Theme.dpf(9000));
        v.setPivotX(0f);
        v.setPivotY(root.layer().getHeight() / 2f);
        v.animate().rotationY(TURN * 72f).alpha(0f).setDuration(back ? 220 : 200)
                .setInterpolator(new AccelerateInterpolator(1.3f)).withEndAction(new Runnable() {
            @Override public void run() {
                v.setRotationY(0f);
                v.setAlpha(1f);
                if (end != null) end.run();
            }
        }).start();
    }

    private Bitmap snapshot(View v) {
        try {
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return null;
            Bitmap b = Bitmap.createBitmap(w / 3, h / 3, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.scale(1f / 3f, 1f / 3f);
            c.drawColor(Theme.bg);
            float a = v.getAlpha(), r = v.getRotationY();
            v.draw(c);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ recientes

    public void showRecents() {
        if (recents != null) return;
        if (visible && top() != null) snaps.put(top(), snapshot(top()));
        ArrayList<RecentsView.Card> cards = new ArrayList<>();
        for (int i = running.size() - 1; i >= 0; i--) {
            SimApp a = running.get(i);
            SimRegistry.Def d = SimRegistry.get(a.id);
            RecentsView.Card c = new RecentsView.Card();
            c.title = d != null ? d.title : a.id;
            c.glyph = d != null ? d.glyph : 0;
            c.snap = snaps.get(a);
            c.tag = a;
            cards.add(c);
        }
        recents = new RecentsView(ctx, cards, new RecentsView.Listener() {
            @Override public void onOpen(RecentsView.Card c) {
                SimApp a = (SimApp) c.tag;
                closeRecents();
                visible = false; // se abre "desde el Start": cadena nueva
                openSim(a.id, null);
            }

            @Override public void onClose(RecentsView.Card c) {
                SimApp a = (SimApp) c.tag;
                boolean wasTop = visible && a == top();
                chain.remove(a);
                running.remove(a);
                snaps.remove(a);
                a.onDestroy();
                root.layer().removeView(a);
                if (wasTop) {
                    SimApp next = top();
                    if (next != null) { next.setVisibility(View.VISIBLE); next.onShow(); } else visible = false;
                }
                root.onSimStateChanged();
            }

            @Override public void onDismiss() { closeRecents(); }
        });
        root.layer().addView(recents, root.contentLp());
        root.bringChromeToFront();
        recents.setAlpha(0f);
        recents.animate().alpha(1f).setDuration(160).start();
        root.onSimStateChanged();
    }

    public void closeRecents() {
        if (recents == null) return;
        root.layer().removeView(recents);
        recents = null;
        root.onSimStateChanged();
    }

    // ------------------------------------------------------------------ SimHost

    @Override public void launch(Intent intent) { root.launch(intent); }

    @Override public void requestPermissions(String[] perms, Runnable done) { root.bridge().requestPermissions(perms, done); }

    @Override public HostBridge bridge() { return root.bridge(); }

    @Override public void openCortana() { root.openCortana(); }

    @Override public void postNotification(String appId, String title, String text, final String openId, final String openArg) {
        Notif n = new Notif();
        n.key = "sim:" + System.nanoTime();
        n.pkg = SimRegistry.PREFIX + appId;
        n.title = title;
        n.text = text;
        n.time = System.currentTimeMillis();
        n.clearable = true;
        if (openId != null) {
            n.action = new Runnable() {
                @Override public void run() { openSim(openId, openArg); }
            };
        }
        Notif.upsert(n);
    }

    @Override public void startCall(String name, String number, boolean incoming) {
        openSim("call", (incoming ? "in" : "out") + "|" + (name == null ? "" : name) + "|" + (number == null ? "" : number));
    }
}
