package com.woba.slauncher.ui;

import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.woba.slauncher.data.Actions;
import com.woba.slauncher.data.AppEntry;
import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.data.Live;
import com.woba.slauncher.data.Prefs;
import com.woba.slauncher.data.Sys;
import com.woba.slauncher.data.Tile;
import com.woba.slauncher.data.Toggles;
import com.woba.slauncher.data.Wallpaper;
import com.woba.slauncher.sim.SimRegistry;
import com.woba.slauncher.sim.SimStore;
import android.view.Gravity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Raíz del launcher: pager Start/Apps, ajustes y bloqueo superpuestos, datos en vivo y estado. */
public class LauncherRoot extends FrameLayout {

    private final HostBridge host;
    private final Live live = new Live();
    private final ArrayList<Tile> tiles;
    private List<AppEntry> apps = new ArrayList<>();

    private final PagerView pager;
    private final WallView wall;
    private final StartView start;
    private final AppListView appList;
    private SettingsView settings;
    private LockView lock;
    private CortanaView cortana;
    private final StatusBarView statusBar;
    private final ActionCenterView actionCenter;
    private final int sbH;
    private int navH;
    private boolean navOn;
    private NavBarView nav;
    private SimManager simMgr;
    private int realMissed, realSms;
    private final SimStore.Listener simListener = new SimStore.Listener() {
        @Override public void onSimChanged(String what) { recountSim(); }
    };

    /** Contadores de los tiles: lo mayor entre lo real (Android) y lo simulado. */
    private void recountSim() {
        live.missed = Math.max(realMissed, SimStore.missedCalls());
        live.sms = Math.max(realSms, SimStore.unreadMessages());
        start.refreshLive();
    }

    // Gesto de bajar el Action Center desde el borde superior.
    private boolean pullCandidate, pulling;
    private float pullDownX, pullDownY;
    private VelocityTracker pullVt;
    private final int touchSlop;

    private int accentIndex;
    private boolean dark;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public LauncherRoot(Context ctx, HostBridge host) {
        super(ctx);
        this.host = host;
        accentIndex = Prefs.loadAccent(ctx);
        dark = Prefs.loadDark(ctx);
        Theme.apply(accentIndex, dark);
        setBackgroundColor(Theme.bg);
        Wallpaper.load(ctx);
        sbH = StatusBarView.heightFor(ctx);
        touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
        loadFlags();
        SimStore.init(ctx);
        SimStore.addListener(simListener);
        navOn = Prefs.flag(ctx, Prefs.F_NAVBAR, true);
        navH = navOn ? Theme.dp(NavBarView.heightDp()) : 0;
        tiles = Prefs.loadTiles(ctx);

        pager = new PagerView(ctx);
        start = new StartView(ctx, tiles, live, host, new StartView.Listener() {
            @Override public void onTileClick(Tile t) { handleTileClick(t); }
            @Override public void onTilesChanged() { saveTiles(); }
            @Override public void onUnpin(Tile t) { unpin(t); }
            @Override public void onResize(Tile t) { resize(t); }
            @Override public void onOpenApps() { pager.setPage(1, true); }
            @Override public void onDoubleTap() { lockNow(); }
            @Override public void onEditModeChanged(boolean edit) { updateSwipe(); }
        });
        appList = new AppListView(ctx, new AppListView.Listener() {
            @Override public void onLaunch(AppEntry a) { launchEntry(a.pkg); }
            @Override public void onTogglePin(AppEntry a) { togglePin(a); }
            @Override public void onUninstall(AppEntry a) { Actions.uninstall(getContext(), a.pkg); }
            @Override public void onOpenSettings() { openSettings(); }
        });
        pager.addView(start);
        pager.addView(appList);
        wall = new WallView(ctx);
        start.setScrollListener(new StartView.ScrollListener() {
            @Override public void onScroll(int y) { wall.setScroll(y); }
        });
        start.setColumns(Prefs.flag(ctx, Prefs.F_MORE_TILES, false) ? 6 : 4);
        pager.setFractionListener(new PagerView.FractionListener() {
            @Override public void onFraction(float f) { wall.setFraction(f); }
        });
        addView(wall, new LayoutParams(-1, -1));
        LayoutParams plp = new LayoutParams(-1, -1);
        plp.topMargin = sbH;
        plp.bottomMargin = navH;
        addView(pager, plp);
        simMgr = new SimManager(ctx, simRoot);
        nav = new NavBarView(ctx, new NavBarView.Listener() {
            @Override public void onBack() { LauncherRoot.this.onBack(); }
            @Override public void onBackLong() { if (lock == null) { actionCenter.close(); simMgr.showRecents(); } }
            @Override public void onHome() { goHome(); }
            @Override public void onHomeLong() { openCortana(); }
            @Override public void onSearch() { openCortana(); }
        });
        addView(nav, new LayoutParams(-1, Theme.dp(NavBarView.heightDp()), Gravity.BOTTOM));
        nav.setVisibility(navOn ? VISIBLE : GONE);
        actionCenter = new ActionCenterView(ctx, sbH, new ActionCenterView.Listener() {
            @Override public void onOpenChanged(boolean open) { updateSwipe(); }
        });
        addView(actionCenter, new LayoutParams(-1, -1));
        statusBar = new StatusBarView(ctx, live);
        addView(statusBar, new LayoutParams(-1, sbH));
        Toggles.enableListenerDirect(ctx); // solo funciona si se concedió WRITE_SECURE_SETTINGS
        updatePinned();

        // Permisos para los live tiles: se piden una sola vez.
        ui.post(new Runnable() {
            @Override
            public void run() {
                Context c = getContext();
                if (Prefs.permissionAsked(c)) return;
                Prefs.setPermissionAsked(c);
                ArrayList<String> missing = new ArrayList<>();
                for (String p : Sys.wantedPermissions()) {
                    if (!Sys.has(c, p)) missing.add(p);
                }
                if (!missing.isEmpty()) LauncherRoot.this.host.requestLivePermissions(missing.toArray(new String[0]));
            }
        });
    }

    // ------------------------------------------------------------ ciclo de vida y datos

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Sys.readBattery(i, live);
            statusBar.invalidate();
            start.refreshLive();
            if (lock != null) lock.invalidate();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            live.now = new Date();
            statusBar.refresh();
            start.refreshLive();
            if (lock != null) lock.invalidate();
            ui.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50L);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Context c = getContext();
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Sys.readBattery(c.registerReceiver(null, f), live);
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(batteryReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else c.registerReceiver(batteryReceiver, f);
        ui.removeCallbacks(tick);
        tick.run();
        refreshData();
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            getContext().unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
        ui.removeCallbacks(tick);
        SimStore.removeListener(simListener);
        super.onDetachedFromWindow();
    }

    /** Llamadas perdidas, SMS y lista de apps, fuera del hilo UI. */
    public void refreshData() {
        final Context c = getContext().getApplicationContext();
        try {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    final int m = Sys.countMissedCalls(c);
                    final int s = Sys.countUnreadSms(c);
                    final List<AppEntry> a = Sys.loadApps(c);
                    for (SimRegistry.Def d : SimRegistry.all()) {
                        if (d.listed) a.add(new AppEntry(d.title, SimRegistry.PREFIX + d.id));
                    }
                    java.util.Collections.sort(a, new java.util.Comparator<AppEntry>() {
                        @Override public int compare(AppEntry x, AppEntry y) { return x.label.toLowerCase().compareTo(y.label.toLowerCase()); }
                    });
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            realMissed = m;
                            realSms = s;
                            recountSim();
                            apps = a;
                            appList.setApps(visibleApps());
                            pruneGoneApps();
                            start.refreshLive();
                            if (settings != null) settings.refresh();
                        }
                    });
                }
            });
        } catch (Exception ignored) {
        }
    }

    /** Quita los tiles de apps que ya no están instaladas. */
    private void pruneGoneApps() {
        if (apps.isEmpty()) return;
        HashSet<String> installed = new HashSet<>();
        for (AppEntry a : apps) installed.add(a.pkg);
        ArrayList<Tile> gone = new ArrayList<>();
        for (Tile t : tiles) if (t.kind == Tile.Kind.APP && !SimRegistry.isSim(t.pkg) && !installed.contains(t.pkg)) gone.add(t);
        if (!gone.isEmpty()) {
            tiles.removeAll(gone);
            saveTiles();
            start.rebuild();
            updatePinned();
        }
    }

    // ------------------------------------------------------------ tiles

    private void saveTiles() {
        Prefs.saveTiles(getContext(), tiles);
    }

    private void updatePinned() {
        Set<String> pinned = new HashSet<>();
        for (Tile t : tiles) if (t.kind == Tile.Kind.APP) pinned.add(t.pkg);
        appList.setPinned(pinned);
    }

    private void handleTileClick(Tile t) {
        Context c = getContext();
        switch (t.kind) {
            case APP: launchEntry(t.pkg); break;
            case PHONE: simMgr.openSim("phone", null); break;
            case MESSAGES: simMgr.openSim("messaging", null); break;
            case CLOCK: Actions.openClock(c); break;
            case BATTERY: Actions.openBatteryUsage(c); break;
            case SETTINGS: openSettings(); break;
            case CORTANA: openCortana(); break;
            case LOCK: lockNow(); break;
            default: break;
        }
    }

    private void togglePin(AppEntry app) {
        Tile existing = null;
        for (Tile t : tiles) if (t.kind == Tile.Kind.APP && t.pkg.equals(app.pkg)) { existing = t; break; }
        if (existing != null) {
            tiles.remove(existing);
            Toast.makeText(getContext(), "Unpinned from start", Toast.LENGTH_SHORT).show();
        } else {
            tiles.add(new Tile("app:" + app.pkg, Tile.Kind.APP, app.label, app.pkg, Tile.Size.MEDIUM, -1));
            Toast.makeText(getContext(), "Pinned to start", Toast.LENGTH_SHORT).show();
        }
        saveTiles();
        start.rebuild();
        updatePinned();
    }

    private void unpin(Tile t) {
        if (t.kind == Tile.Kind.WIDGET) host.deleteWidget(t.widgetId);
        tiles.remove(t);
        saveTiles();
        start.rebuild();
        updatePinned();
        if (tiles.isEmpty()) start.setEditMode(false);
    }

    private void resize(Tile t) {
        t.size = t.size.next();
        saveTiles();
        start.relayout();
    }

    // ------------------------------------------------------------ ajustes, bloqueo y navegación

    private void updateSwipe() {
        pager.setSwipeEnabled(!start.isEditMode() && settings == null && lock == null && cortana == null && !actionCenter.isOpen() && !simMgr.isVisible() && !simMgr.hasRecents());
    }

    private void openSettings() {
        if (settings != null) return;
        settings = new SettingsView(getContext(), new SettingsView.Listener() {
            @Override public int accentIndex() { return accentIndex; }
            @Override public boolean dark() { return dark; }
            @Override public void onAccent(int index) {
                accentIndex = index;
                Prefs.saveAccent(getContext(), index);
                applyTheme();
            }
            @Override public void onDark(boolean d) {
                dark = d;
                Prefs.saveDark(getContext(), d);
                applyTheme();
            }
            @Override public List<AppWidgetProviderInfo> widgetProviders() { return host.widgetProviders(); }
            @Override public void onAddWidget(final AppWidgetProviderInfo provider) {
                host.addWidget(provider, new HostBridge.WidgetCallback() {
                    @Override
                    public void onWidget(int widgetId) {
                        tiles.add(new Tile("widget:" + widgetId, Tile.Kind.WIDGET,
                                String.valueOf(provider.loadLabel(getContext().getPackageManager())),
                                "", Tile.Size.WIDE, widgetId));
                        saveTiles();
                        closeSettings();
                        start.rebuild();
                        pager.setPage(0, false);
                        start.post(new Runnable() {
                            @Override
                            public void run() {
                                start.scrollToEnd();
                            }
                        });
                    }
                });
            }
            @Override public boolean wpLock() { return Prefs.loadWpLock(getContext()); }
            @Override public void onWpLock(boolean on) { Prefs.saveWpLock(getContext(), on); }
            @Override public void onPreviewLock() { showLock(); }
            @Override public void onResetStart() { resetStart(); }
            @Override public void onRequestLive() { requestLive(); }
            @Override public boolean isPinned(String pkg) {
                for (Tile t : tiles) if (t.kind == Tile.Kind.APP && t.pkg.equals(pkg)) return true;
                return false;
            }
            @Override public void onTogglePin(String label, String pkg) { togglePin(new AppEntry(label, pkg)); }
            @Override public Live live() { return live; }
            @Override public void pickWallpaper(final boolean forStart) {
                host.pickImage(new HostBridge.ImageCallback() {
                    @Override
                    public void onImage(android.net.Uri uri) {
                        Wallpaper.importAsync(getContext(), uri, forStart, new Runnable() {
                            @Override
                            public void run() {
                                applyPrefs();
                                if (settings != null) settings.refresh();
                            }
                        });
                    }
                });
            }
            @Override public List<AppEntry> allApps() { return apps; }
            @Override public void onHiddenChanged() { appList.setApps(visibleApps()); }
            @Override public void onPrefsChanged() { applyPrefs(); }
            @Override public void onClose() { closeSettings(); }
        });
        LayoutParams slp = new LayoutParams(-1, -1);
        slp.topMargin = sbH;
        slp.bottomMargin = navH;
        addView(settings, slp);
        actionCenter.bringToFront();
        statusBar.bringToFront();
        settings.setTranslationX(getWidth());
        settings.animate().translationX(0f).setDuration(220).start();
        updateSwipe();
    }

    private void openCortana() {
        if (cortana != null) return;
        cortana = new CortanaView(getContext(), host, new CortanaView.Listener() {
            @Override public void onClose() { closeCortana(); }
            @Override public List<AppEntry> apps() { return apps; }
        });
        LayoutParams lp = new LayoutParams(-1, -1);
        lp.topMargin = sbH;
        lp.bottomMargin = navH;
        addView(cortana, lp);
        actionCenter.bringToFront();
        statusBar.bringToFront();
        updateSwipe();
        cortana.open();
    }

    private void closeCortana() {
        if (cortana == null) return;
        View v = cortana;
        cortana = null;
        removeView(v);
        updateSwipe();
    }

    private void closeSettings() {
        if (settings == null) return;
        final View s = settings;
        settings = null;
        s.animate().translationX(getWidth()).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                removeView(s);
            }
        }).start();
        updateSwipe();
    }

    private void applyTheme() {
        Theme.apply(accentIndex, dark);
        setBackgroundColor(Theme.bg);
        start.onThemeChanged();
        appList.onThemeChanged();
        simMgr.onThemeChanged();
        nav.invalidate();
        if (settings != null) settings.rebuild();
        if (lock != null) lock.invalidate();
    }

    public void showLock() {
        if (lock != null) return;
        lock = new LockView(getContext(), live, new Runnable() {
            @Override
            public void run() {
                hideLock();
            }
        });
        addView(lock, new LayoutParams(-1, -1));
        updateSwipe();
    }

    private void hideLock() {
        if (lock == null) return;
        removeView(lock);
        lock = null;
        updateSwipe();
    }

    private void lockNow() {
        if (!host.lockDevice()) showLock();
    }

    private List<AppEntry> visibleApps() {
        Set<String> hidden = Prefs.hidden(getContext());
        if (hidden.isEmpty()) return apps;
        ArrayList<AppEntry> out = new ArrayList<>();
        for (AppEntry a : apps) if (!hidden.contains(a.pkg)) out.add(a);
        return out;
    }

    /** Lee las preferencias de apariencia y las deja en Theme. */
    private void loadFlags() {
        Context c = getContext();
        Theme.tileAlpha = (Wallpaper.start != null && Prefs.flag(c, Prefs.F_TILE_TRANS, true)) ? 0.6f : 1f;
        Theme.tileLabels = Prefs.flag(c, Prefs.F_TILE_LABELS, true);
        Theme.liveAnim = Prefs.flag(c, Prefs.F_LIVE_ANIM, true);
        Theme.tilt = Prefs.flag(c, Prefs.F_TILT, true);
        Theme.lockStatus = Prefs.flag(c, Prefs.F_LOCK_STATUS, true);
    }

    /** Cambió el fondo o alguna opción de apariencia: repinta todo. */
    private void applyPrefs() {
        loadFlags();
        applyNavBar();
        wall.invalidate();
        start.setColumns(Prefs.flag(getContext(), Prefs.F_MORE_TILES, false) ? 6 : 4);
        start.onThemeChanged();
        start.refreshLive();
        if (lock != null) lock.invalidate();
    }

    public void onHostPause() { simMgr.hostPause(); }

    public void onHostResume() { simMgr.hostResume(); }

    public boolean onHardwareKey(int keyCode) { return lock == null && simMgr.hardwareKey(keyCode); }

    /** La pantalla se apagó: mostrar la lock screen de WP si el usuario la tiene activada. */
    public void onScreenOff() {
        if (Prefs.loadWpLock(getContext())) showLock();
    }

    private void resetStart() {
        for (Tile t : tiles) if (t.kind == Tile.Kind.WIDGET) host.deleteWidget(t.widgetId);
        tiles.clear();
        tiles.addAll(Prefs.defaultTiles());
        saveTiles();
        start.rebuild();
        updatePinned();
        Toast.makeText(getContext(), "Start reset", Toast.LENGTH_SHORT).show();
    }

    private void requestLive() {
        ArrayList<String> missing = new ArrayList<>();
        for (String p : Sys.wantedPermissions()) {
            if (!Sys.has(getContext(), p)) missing.add(p);
        }
        if (!missing.isEmpty()) host.requestLivePermissions(missing.toArray(new String[0]));
    }

    // ------------------------------------------------------------ simulador y barra de navegación

    /** Abre una app real o simulada según el id de paquete. */
    private void launchEntry(String pkg) {
        if (SimRegistry.isSim(pkg)) simMgr.openSim(SimRegistry.idOf(pkg), null);
        else Actions.launchApp(getContext(), pkg);
    }

    public void openSim(String id, String arg) { simMgr.openSim(id, arg); }

    private final SimManager.Root simRoot = new SimManager.Root() {
        @Override public LayoutParams contentLp() {
            LayoutParams lp = new LayoutParams(-1, -1);
            lp.topMargin = sbH;
            lp.bottomMargin = navH;
            return lp;
        }
        @Override public void bringChromeToFront() {
            if (nav != null) nav.bringToFront();
            actionCenter.bringToFront();
            statusBar.bringToFront();
        }
        @Override public void onSimStateChanged() { updateSwipe(); }
        @Override public void openCortana() { LauncherRoot.this.openCortana(); }
        @Override public void launch(Intent i) { Actions.start(getContext(), i); }
        @Override public HostBridge bridge() { return host; }
        @Override public FrameLayout layer() { return LauncherRoot.this; }
    };

    private void applyNavBar() {
        boolean on = Prefs.flag(getContext(), Prefs.F_NAVBAR, true);
        if (on == navOn) return;
        navOn = on;
        navH = on ? Theme.dp(NavBarView.heightDp()) : 0;
        nav.setVisibility(on ? VISIBLE : GONE);
        LayoutParams plp = (LayoutParams) pager.getLayoutParams();
        plp.bottomMargin = navH;
        pager.setLayoutParams(plp);
        if (settings != null) settings.setLayoutParams(simRoot.contentLp());
        if (cortana != null) cortana.setLayoutParams(simRoot.contentLp());
        simMgr.relayout();
    }

    // ------------------------------------------------------------ gesto del Action Center

    private boolean pullAllowed() {
        return lock == null && settings == null && cortana == null && !actionCenter.isOpen() && !start.isEditMode();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pullCandidate = pullAllowed() && e.getY() < sbH * 1.3f;
                pulling = false;
                pullDownX = e.getX();
                pullDownY = e.getY();
                if (pullCandidate) {
                    if (pullVt != null) pullVt.recycle();
                    pullVt = VelocityTracker.obtain();
                    pullVt.addMovement(e);
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (pullCandidate && !pulling) {
                    pullVt.addMovement(e);
                    float dy = e.getY() - pullDownY, dx = e.getX() - pullDownX;
                    if (dy > touchSlop && dy > Math.abs(dx)) {
                        pulling = true;
                        actionCenter.pullStart(getHeight());
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            // Si ningún hijo lo tomó (la propia barra de estado), seguimos nosotros.
            pullCandidate = pullAllowed() && e.getY() < sbH * 1.3f;
            pulling = false;
            pullDownX = e.getX();
            pullDownY = e.getY();
            if (pullCandidate) {
                if (pullVt != null) pullVt.recycle();
                pullVt = VelocityTracker.obtain();
                pullVt.addMovement(e);
            }
            return pullCandidate;
        }
        if (!pullCandidate || pullVt == null) return false;
        pullVt.addMovement(e);
        if (a == MotionEvent.ACTION_MOVE) {
            float dy = e.getY() - pullDownY, dx = e.getX() - pullDownX;
            if (!pulling && dy > touchSlop && dy > Math.abs(dx)) {
                pulling = true;
                actionCenter.pullStart(getHeight());
            }
            if (pulling) actionCenter.pullTo(dy);
            return true;
        }
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            if (pulling) {
                pullVt.computeCurrentVelocity(1000);
                actionCenter.pullEnd(a == MotionEvent.ACTION_UP ? pullVt.getYVelocity() : -2000f);
            }
            pullVt.recycle();
            pullVt = null;
            pullCandidate = false;
            pulling = false;
            return true;
        }
        return true;
    }

    /** Botón Home con el launcher ya abierto: volver al Start. */
    public void goHome() {
        actionCenter.close();
        if (cortana != null) cortana.close();
        closeSettings();
        simMgr.goStart();
        start.setEditMode(false);
        appList.onBack();
        pager.setPage(0, false);
        start.scrollToTop();
    }

    /** El botón atrás nunca cierra un launcher. */
    public void onBack() {
        if (lock != null) return;
        if (actionCenter.onBack()) return;
        if (cortana != null) { cortana.close(); return; }
        if (settings != null) {
            if (!settings.onBack()) closeSettings();
        } else if (simMgr.onBack()) return;
        else if (appList.onBack()) return;
        else if (start.isEditMode()) start.setEditMode(false);
        else if (pager.getPage() == 1) pager.setPage(0, true);
    }
}
