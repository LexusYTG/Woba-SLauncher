package com.woba.slauncher.ui;

import android.app.Dialog;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.woba.slauncher.data.Actions;
import com.woba.slauncher.data.AppEntry;
import com.woba.slauncher.data.Brand;
import com.woba.slauncher.data.Cortana;
import com.woba.slauncher.data.Live;
import com.woba.slauncher.data.Prefs;
import com.woba.slauncher.data.Sys;
import com.woba.slauncher.data.SysSettings;
import com.woba.slauncher.data.Toggles;
import com.woba.slauncher.data.Wallpaper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * App Configuración al estilo Windows 10 Mobile: lista de categorías con iconos y buscador,
 * páginas de categoría y páginas de detalle con los controles de W10 (interruptor en píldora,
 * combo, checkbox, radio...). Cada ajuste sigue mapeado a su equivalente real de Android:
 * control propio (si Android deja a una app cambiarlo, con el permiso correspondiente) o
 * acceso directo al panel de Android.
 */
public class SettingsView extends FrameLayout {

    public interface Listener {
        int accentIndex();
        boolean dark();
        void onAccent(int index);
        void onDark(boolean dark);
        List<AppWidgetProviderInfo> widgetProviders();
        void onAddWidget(AppWidgetProviderInfo provider);
        boolean wpLock();
        void onWpLock(boolean on);
        void onPreviewLock();
        void onResetStart();
        void onRequestLive();
        boolean isPinned(String pkg);
        void onTogglePin(String label, String pkg);
        Live live();
        void pickWallpaper(boolean forStart);
        List<AppEntry> allApps();
        void onHiddenChanged();
        void onPrefsChanged();
        void onClose();
    }

    private interface IntCb { void on(int i); }

    private interface TextResult { void on(String v); }

    private static class Page {
        final String title;
        final Runnable build;
        Page(String title, Runnable build) { this.title = title; this.build = build; }
    }

    private static class AppRef {
        final String title, pkg;
        AppRef(String title, String pkg) { this.title = title; this.pkg = pkg; }
    }

    private static class Hit {
        final String title, where;
        final Runnable action;
        Hit(String title, String where, Runnable action) {
            this.title = title;
            this.where = where;
            this.action = action;
        }
    }

    private static final String[] CAT_TITLES = {"System", "Devices", "Network & wireless", "Personalization",
            "Apps", "Accounts", "Time & language", "Ease of Access", "Privacy", "Update & recovery", "Extras"};
    private static final String[] CAT_SUBS = {"Display, notifications, battery", "Bluetooth, keyboard",
            "Wi-Fi, airplane mode, cellular", "Start, lock screen, sounds", "Uninstall, defaults, app list",
            "Your accounts, email, sync", "Speech, region, keyboard", "Text size, narrator, high contrast",
            "Location, feedback", "Phone update, backup", "Cortana, app names, launcher"};
    private static final int[] CAT_ICONS = {SettingsIcons.SYSTEM, SettingsIcons.DEVICES, SettingsIcons.NETWORK,
            SettingsIcons.PERSONALIZATION, SettingsIcons.APPS, SettingsIcons.ACCOUNTS, SettingsIcons.TIME,
            SettingsIcons.EASE, SettingsIcons.PRIVACY, SettingsIcons.UPDATE, SettingsIcons.EXTRAS};

    private static final int[] TIMEOUTS = {15000, 30000, 60000, 120000, 300000, 600000, 1800000};
    private static final String[] TIMEOUT_NAMES = {"15 seconds", "30 seconds", "1 minute", "2 minutes",
            "5 minutes", "10 minutes", "30 minutes"};
    private static final String[] RINGER = {"Normal", "Vibrate", "Silent"};
    private static final String[] QUICK_NAMES = {"Wi-Fi", "Bluetooth", "Airplane mode", "Rotation lock",
            "Quiet hours", "Location", "Battery saver", "Internet sharing"};

    private final Listener l;
    private final ArrayList<Page> stack = new ArrayList<>();
    private ArrayList<AppRef> appRefs;
    private ScrollView scroll;
    private LinearLayout content;

    public SettingsView(Context ctx, Listener listener) {
        super(ctx);
        l = listener;
        setClickable(true);
        render(false);
    }

    // ------------------------------------------------------------ navegación

    public void rebuild() { render(true); }

    /** Vuelve a leer los ajustes (al regresar de una pantalla de permisos de Android). */
    public void refresh() { render(true); }

    /** true si consumió el "atrás" (había una página abierta). */
    public boolean onBack() {
        if (stack.isEmpty()) return false;
        stack.remove(stack.size() - 1);
        render(false);
        animateIn(false);
        return true;
    }

    private void push(String title, Runnable build) {
        stack.add(new Page(title, build));
        render(false);
        animateIn(true);
    }

    private void animateIn(boolean forward) {
        scroll.setAlpha(0f);
        scroll.setTranslationX(Theme.dp(forward ? 48 : -48));
        scroll.animate().alpha(1f).translationX(0f).setDuration(160).start();
    }

    private void render(boolean keepScroll) {
        final int y = (keepScroll && scroll != null) ? scroll.getScrollY() : 0;
        Context ctx = getContext();
        setBackgroundColor(Theme.bg);
        removeAllViews();

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        String title = stack.isEmpty() ? "Settings" : stack.get(stack.size() - 1).title;
        col.addView(header(title), new LinearLayout.LayoutParams(-1, Theme.dp(48)));

        scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setFillViewport(false);
        content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Theme.dp(12), 0, Theme.dp(12), Theme.dp(36));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        addView(col, new FrameLayout.LayoutParams(-1, -1));

        if (stack.isEmpty()) listPage(-1);
        else stack.get(stack.size() - 1).build.run();
        if (y > 0) scroll.post(() -> scroll.scrollTo(0, y));
    }

    private View header(String title) {
        Context ctx = getContext();
        LinearLayout h = new LinearLayout(ctx);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setBackgroundColor(W10Controls.panelColor());
        h.setPadding(Theme.dp(12), 0, Theme.dp(12), 0);
        h.addView(new SettingsIcons.IconView(ctx, SettingsIcons.GEAR, Theme.accent),
                new LinearLayout.LayoutParams(Theme.dp(30), Theme.dp(30)));
        TextView t = text(title.toUpperCase(Locale.ROOT), 17, Theme.fg, Theme.regular);
        t.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Theme.dp(16);
        h.addView(t, lp);
        return h;
    }

    // ------------------------------------------------------------ lista principal, categorías y búsqueda

    /** cat = -1: categorías con iconos; 0..10: filas de esa categoría. Ambas con el cuadro de búsqueda. */
    private void listPage(final int cat) {
        Context c = getContext();
        final LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        W10Controls.SearchBox sb = new W10Controls.SearchBox(c, q -> {
            LinearLayout saved = content;
            content = body;
            body.removeAllViews();
            fill(cat, q);
            content = saved;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Theme.dp(34));
        lp.topMargin = Theme.dp(10);
        lp.bottomMargin = Theme.dp(8);
        content.addView(sb, lp);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout saved = content;
        content = body;
        fill(cat, "");
        content = saved;
    }

    private void fill(final int cat, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            List<Hit> hits = search(q);
            if (hits.isEmpty()) note("No results found.");
            for (Hit h : hits) resultRow(h);
            return;
        }
        if (cat < 0) {
            for (int i = 0; i < CAT_TITLES.length; i++) {
                final int ci = i;
                catRow(CAT_ICONS[i], CAT_TITLES[i], CAT_SUBS[i],
                        () -> push(CAT_TITLES[ci], () -> listPage(ci)));
            }
        } else {
            for (Hit h : rowsFor(cat)) listRow(h.title, h.action);
        }
    }

    private void add(List<Hit> o, int cat, String title, Runnable a) {
        o.add(new Hit(title, CAT_TITLES[cat], a));
    }

    private Runnable open(final String title, final Runnable build) {
        return () -> push(title, build);
    }

    private AppRef refByTitle(String title) {
        for (AppRef r : appRefs()) if (r.title.equals(title)) return r;
        return null;
    }

    /** Filas de cada categoría (sin subtítulos, como en W10). */
    private List<Hit> rowsFor(final int cat) {
        final Context c = getContext();
        ArrayList<Hit> o = new ArrayList<>();
        switch (cat) {
            case 0: {
                add(o, cat, "Display", open("Display", this::pageDisplay));
                add(o, cat, "Notifications & actions", open("Notifications & actions", this::pageNotifications));
                add(o, cat, "Quiet hours", open("Quiet hours", this::pageQuiet));
                final AppRef ph = refByTitle("Phone");
                if (ph != null) add(o, cat, "Phone", open("Phone", () -> pageApp(ph)));
                final AppRef sms = refByTitle("Messaging");
                if (sms != null) add(o, cat, "Messaging", open("Messaging", () -> pageApp(sms)));
                add(o, cat, "Battery saver", open("Battery saver", this::pageBattery));
                add(o, cat, "Storage", open("Storage", this::pageStorage));
                add(o, cat, "Device encryption", go(Settings.ACTION_SECURITY_SETTINGS));
                add(o, cat, "About", open("About", this::pageAbout));
                break;
            }
            case 1: {
                add(o, cat, "Bluetooth", open("Bluetooth", () -> pageOnOff("Bluetooth",
                        SysSettings.bluetoothOn(c), new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))));
                if (SysSettings.nfcAvailable(c)) {
                    add(o, cat, "NFC", open("NFC", () -> pageOnOff("NFC", SysSettings.nfcOn(c),
                            SysSettings.nfcPanel())));
                }
                add(o, cat, "Typing", go(Settings.ACTION_INPUT_METHOD_SETTINGS));
                add(o, cat, "Printers & scanners", go(Settings.ACTION_PRINT_SETTINGS));
                add(o, cat, "Project my screen", go(Settings.ACTION_CAST_SETTINGS));
                break;
            }
            case 2: {
                add(o, cat, "Cellular & SIM", go(SysSettings.internetPanel()));
                add(o, cat, "Wi-Fi", open("Wi-Fi", () -> pageOnOff("Wi-Fi", SysSettings.wifiOn(c),
                        SysSettings.wifiPanel())));
                add(o, cat, "Airplane mode", open("Airplane mode", () -> pageOnOff("Airplane mode",
                        SysSettings.airplaneOn(c), new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))));
                add(o, cat, "Data usage", go("android.settings.DATA_USAGE_SETTINGS"));
                add(o, cat, "Mobile hotspot", this::openTether);
                add(o, cat, "VPN", go("android.net.vpn.SETTINGS"));
                break;
            }
            case 3: {
                add(o, cat, "Start", open("Start", this::pageStart));
                add(o, cat, "Colors", open("Colors", this::pageColors));
                add(o, cat, "Lock screen", open("Lock screen", this::pageLock));
                add(o, cat, "Sounds", open("Sounds", this::pageSound));
                break;
            }
            case 4: {
                add(o, cat, "Apps & features", go(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));
                add(o, cat, "Default apps", open("Default apps", this::pageDefaultApps));
                add(o, cat, "App list", open("App list", this::pageAppList));
                break;
            }
            case 5: {
                add(o, cat, "Email & app accounts", go(Settings.ACTION_SYNC_SETTINGS));
                add(o, cat, "Sign-in options", go(Settings.ACTION_SECURITY_SETTINGS));
                break;
            }
            case 6: {
                add(o, cat, "Date & time", open("Date & time", this::pageDate));
                add(o, cat, "Region & language", go(Settings.ACTION_LOCALE_SETTINGS));
                add(o, cat, "Keyboard & assistant", go(new Intent(getContext(), com.woba.slauncher.KeyboardSetupActivity.class)));
                add(o, cat, "Keyboard", go(Settings.ACTION_INPUT_METHOD_SETTINGS));
                add(o, cat, "Speech", go(Settings.ACTION_VOICE_INPUT_SETTINGS));
                break;
            }
            case 7: {
                add(o, cat, "Text size", open("Text size", this::pageTextSize));
                add(o, cat, "Narrator", go(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                add(o, cat, "Magnifier", go(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                add(o, cat, "High contrast", go(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                add(o, cat, "Closed captions", go(Settings.ACTION_CAPTIONING_SETTINGS));
                add(o, cat, "Other options", go(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            }
            case 8: {
                add(o, cat, "Location", open("Location", () -> pageOnOff("Location", SysSettings.locationOn(c),
                        new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));
                add(o, cat, "App permissions", go(Settings.ACTION_PRIVACY_SETTINGS));
                break;
            }
            case 9: {
                add(o, cat, "Phone update", go("android.settings.SYSTEM_UPDATE_SETTINGS"));
                add(o, cat, "Backup", go(Settings.ACTION_PRIVACY_SETTINGS));
                add(o, cat, "For developers", go(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                break;
            }
            default: {
                add(o, cat, "Cortana", open("Cortana", this::pageCortana));
                add(o, cat, "Keyboard", open("Keyboard", this::pageKeyboard));
                add(o, cat, "App names", open("App names", this::pageNames));
                add(o, cat, "This launcher", open("This launcher", this::pageLauncher));
                add(o, cat, "All Android settings", go(Settings.ACTION_SETTINGS));
                break;
            }
        }
        return o;
    }

    /** Ajustes que viven dentro de una página y que el buscador también debe encontrar. */
    private List<Hit> inner() {
        ArrayList<Hit> o = new ArrayList<>();
        final Runnable disp = open("Display", this::pageDisplay);
        final Runnable start = open("Start", this::pageStart);
        final Runnable lock = open("Lock screen", this::pageLock);
        final Runnable snd = open("Sounds", this::pageSound);
        final Runnable col = open("Colors", this::pageColors);
        final Runnable dt = open("Date & time", this::pageDate);
        o.add(new Hit("Brightness", "System > Display", disp));
        o.add(new Hit("Rotation lock", "System > Display", disp));
        o.add(new Hit("Quick actions", "System > Notifications & actions", open("Notifications & actions", this::pageNotifications)));
        o.add(new Hit("Battery", "System > Battery saver", open("Battery saver", this::pageBattery)));
        o.add(new Hit("Storage usage", "System > Storage", open("Storage", this::pageStorage)));
        o.add(new Hit("Background", "Personalization > Start", start));
        o.add(new Hit("Tiles", "Personalization > Start", start));
        o.add(new Hit("Add a widget", "Personalization > Start", start));
        o.add(new Hit("Reset start layout", "Personalization > Start", start));
        o.add(new Hit("Accent color", "Personalization > Colors", col));
        o.add(new Hit("Dark mode", "Personalization > Colors", col));
        o.add(new Hit("Screen times out after", "Personalization > Lock screen", lock));
        o.add(new Hit("Double tap to lock", "Personalization > Lock screen", lock));
        o.add(new Hit("Ringer", "Personalization > Sounds", snd));
        o.add(new Hit("Volume", "Personalization > Sounds", snd));
        o.add(new Hit("24-hour clock", "Time & language > Date & time", dt));
        o.add(new Hit("Time zone", "Time & language > Date & time", dt));
        o.add(new Hit("Hidden apps", "Apps > App list", open("App list", this::pageAppList)));
        String kbWhere = "Extras > Keyboard";
        for (String t : new String[]{"Autocorrect", "Keyboard height", "Key size", "Key sound", "Key vibration", "One-handed keyboard", "Keyboard cursor control"})
            o.add(new Hit(t, kbWhere, open("Keyboard", this::pageKeyboard)));
        o.add(new Hit("Gemini API key", "Extras > Cortana", open("Cortana", this::pageCortana)));
        o.add(new Hit("Permissions", "Extras > This launcher", open("This launcher", this::pageLauncher)));
        return o;
    }

    private List<Hit> search(String q) {
        ArrayList<Hit> all = new ArrayList<>();
        for (int i = 0; i < CAT_TITLES.length; i++) all.addAll(rowsFor(i));
        all.addAll(inner());
        ArrayList<Hit> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (Hit h : all) {
            if (!h.title.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (seen.add(h.title + "|" + h.where)) out.add(h);
        }
        return out;
    }

    private void resultRow(final Hit h) {
        LinearLayout r = vbox();
        r.addView(text(h.title, 17, Theme.fg, Theme.regular));
        TextView w = text(h.where, 13, Theme.subtle, Theme.regular);
        r.addView(w);
        r.setOnClickListener(v -> h.action.run());
        r.setBackground(W10Controls.pressBg());
        content.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private void openTether() {
        Intent t = new Intent();
        t.setClassName("com.android.settings", "com.android.settings.TetherSettings");
        if (t.resolveActivity(getContext().getPackageManager()) == null) t = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        start(t);
    }

    // ------------------------------------------------------------ apps

    private ArrayList<AppRef> appRefs() {
        if (appRefs != null) return appRefs;
        Context c = getContext();
        ArrayList<AppRef> out = new ArrayList<>();
        addRef(out, "Phone", SysSettings.dialerPackage(c));
        addRef(out, "Messaging", SysSettings.smsPackage(c));
        addRef(out, "Internet Explorer", SysSettings.appForCategory(c, Intent.CATEGORY_APP_BROWSER));
        addRef(out, "Camera", SysSettings.cameraPackage(c));
        addRef(out, "People", SysSettings.appForCategory(c, Intent.CATEGORY_APP_CONTACTS));
        addRef(out, "Email", SysSettings.appForCategory(c, Intent.CATEGORY_APP_EMAIL));
        addRef(out, "Calendar", SysSettings.appForCategory(c, Intent.CATEGORY_APP_CALENDAR));
        addRef(out, "Maps", SysSettings.appForCategory(c, Intent.CATEGORY_APP_MAPS));
        addRef(out, "Photos", SysSettings.appForCategory(c, Intent.CATEGORY_APP_GALLERY));
        addRef(out, "Music & videos", SysSettings.appForCategory(c, Intent.CATEGORY_APP_MUSIC));
        addRef(out, "Calculator", SysSettings.appForCategory(c, Intent.CATEGORY_APP_CALCULATOR));
        addRef(out, "Store", SysSettings.storePackage(c));
        appRefs = out;
        return out;
    }

    private void addRef(List<AppRef> out, String title, String pkg) {
        if (pkg != null) out.add(new AppRef(title, pkg));
    }

    private void pageDefaultApps() {
        final Context c = getContext();
        note("Choose which app does what. Tap an app to open it, pin it or see its settings.");
        for (final AppRef r : appRefs()) {
            comboRow(r.title, String.valueOf(SysSettings.appLabel(c, r.pkg)), true, open(r.title, () -> pageApp(r)));
        }
        link("Set WOBA as keyboard and assistant", go(new Intent(getContext(), com.woba.slauncher.KeyboardSetupActivity.class)));
        link("Change default apps in Android", go(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
    }

    private void pageApp(final AppRef r) {
        final Context c = getContext();
        note("Android app: " + SysSettings.appLabel(c, r.pkg));
        listRow("Open", () -> Actions.launchApp(c, r.pkg));
        listRow(l.isPinned(r.pkg) ? "Unpin from Start" : "Pin to Start", () -> {
            l.onTogglePin(String.valueOf(SysSettings.appLabel(c, r.pkg)), r.pkg);
            refresh();
        });
        listRow("App settings", go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.pkg))));
        listRow("Notifications", go(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, r.pkg)));
    }

    /** Qué apps salen en la lista de apps. */
    private void pageAppList() {
        final Context c = getContext();
        note("Hidden apps keep working and stay pinned if they have a tile; they just leave the app list.");
        button("Show all apps", () -> {
            Prefs.setHidden(c, new HashSet<String>());
            l.onHiddenChanged();
            refresh();
        });
        Set<String> hidden = Prefs.hidden(c);
        List<AppEntry> apps = l.allApps();
        if (apps.isEmpty()) note("Loading apps…");
        for (final AppEntry a : apps) {
            toggleRow(a.label, !hidden.contains(a.pkg), on -> {
                Set<String> h = Prefs.hidden(c);
                if (on) h.remove(a.pkg);
                else h.add(a.pkg);
                Prefs.setHidden(c, h);
                l.onHiddenChanged();
            });
        }
    }

    // ------------------------------------------------------------ system

    private void pageDisplay() {
        final Context c = getContext();
        writeBanner();
        final boolean auto = SysSettings.autoBrightness(c);
        final String[] names = {"Low", "Medium", "High"};
        final float[] vals = {0.12f, 0.45f, 0.85f};
        float cur = (float) SysSettings.brightness(c);
        final int bi = cur < 0.3f ? 0 : (cur < 0.65f ? 1 : 2);
        comboRow("Brightness level", names[bi], !auto, () ->
                flyout(null, names, bi, i -> {
                    if (ensureWrite()) SysSettings.setBrightness(c, vals[i]);
                    refresh();
                }));
        toggleRow("Automatically adjust my screen brightness when lighting changes", auto, on -> {
            if (ensureWrite()) SysSettings.setAutoBrightness(c, on);
            refresh(); // activa o desactiva el combo de brillo
        });
        toggleRow("Rotation lock", !SysSettings.autoRotate(c), on -> {
            if (!ensureWrite() || !SysSettings.setAutoRotate(c, !on)) refresh();
        });
        textSizeBlock();
        link("Connect to a wireless display", go(Settings.ACTION_CAST_SETTINGS));
        link("More display options", go(Settings.ACTION_DISPLAY_SETTINGS));
    }

    private void pageTextSize() {
        writeBanner();
        textSizeBlock();
    }

    private void textSizeBlock() {
        final Context c = getContext();
        final float[] vals = {0.85f, 1f, 1.15f, 1.3f};
        float fs = SysSettings.fontScale(c);
        int best = 0;
        for (int i = 1; i < vals.length; i++) if (Math.abs(vals[i] - fs) < Math.abs(vals[best] - fs)) best = i;
        final int[] pend = {best};
        label("Size of text, apps, and other items on this display");
        content.addView(new W10Controls.Slider(c, best / 3f, 3, true, (v, done) -> pend[0] = Math.round(v * 3)),
                new LinearLayout.LayoutParams(-1, -2));
        buttons("Apply", () -> {
            if (!ensureWrite() || !SysSettings.setFontScale(c, vals[pend[0]])) refresh();
        }, "Cancel", this::refresh);
        note("The launcher restarts to apply the new size.");
    }

    private void pageNotifications() {
        final Context c = getContext();
        heading("Quick actions");
        label("Choose your quick actions");
        final int[] quick = Prefs.quick(c);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < 4; k++) {
            final int slot = k;
            W10Controls.QuickTile t = new W10Controls.QuickTile(c, quick[k]);
            t.setOnClickListener(v -> flyout(null, QUICK_NAMES, quick[slot], i -> {
                int[] q = Prefs.quick(c);
                q[slot] = i;
                Prefs.setQuick(c, q);
                refresh();
            }));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Theme.dp(8);
            row.addView(t, lp);
        }
        content.addView(row);
        heading("Notifications");
        boolean access = Toggles.listenerEnabled(c);
        infoRow("Notification access", access ? "On" : "Off");
        link(access ? "Notification access settings" : "Allow notification access", go(Toggles.listenerSettings()));
        link("Android notification settings", go(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName())));
    }

    private void pageQuiet() {
        final Context c = getContext();
        toggleRow("Quiet hours", SysSettings.quietHours(c), on -> {
            if (!SysSettings.hasPolicyAccess(c)) {
                askPolicy();
                refresh();
            } else if (!SysSettings.setQuietHours(c, on)) {
                refresh();
            }
        });
        note("Silences calls, messages and notifications. Alarms still ring.");
        if (!SysSettings.hasPolicyAccess(c)) button("Allow", () -> start(SysSettings.policyIntent()));
    }

    private void pageBattery() {
        final Context c = getContext();
        Live live = l.live();
        content.addView(text(live.battery + "%", 56, Theme.accent, Theme.light));
        content.addView(new W10Controls.Bar(c, live.battery / 100f, 8), new LinearLayout.LayoutParams(-1, -2));
        note(live.charging ? "Charging" : "On battery");
        toggleRow("Battery saver status", SysSettings.batterySaverOn(c), on -> {
            start(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
            postDelayed(this::refresh, 400);
        });
        note("Android doesn't let apps switch battery saver directly. The switch opens the Android settings.");
        link("Battery saver settings", go(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        link("Battery usage", go(Intent.ACTION_POWER_USAGE_SUMMARY));
    }

    private void pageStorage() {
        Context c = getContext();
        long total = SysSettings.storageTotal(), free = SysSettings.storageFree();
        heading("Storage usage");
        label("Total: " + SysSettings.bytes(total));
        content.addView(new W10Controls.Bar(c, total == 0 ? 0f : (total - free) / (float) total, 8),
                new LinearLayout.LayoutParams(-1, -2));
        legend(Theme.accent, "Used: " + SysSettings.bytes(total - free));
        legend(W10Controls.trackColor(), "Remaining: " + SysSettings.bytes(free));
        long rt = SysSettings.ramTotal(c), rf = SysSettings.ramFree(c);
        heading("Memory");
        label("Total: " + SysSettings.bytes(rt));
        content.addView(new W10Controls.Bar(c, rt == 0 ? 0f : (rt - rf) / (float) rt, 8),
                new LinearLayout.LayoutParams(-1, -2));
        legend(Theme.accent, "In use: " + SysSettings.bytes(rt - rf));
        legend(W10Controls.trackColor(), "Free: " + SysSettings.bytes(rf));
        link("Manage storage", go(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
    }

    private void pageAbout() {
        Context c = getContext();
        infoRow("Device", SysSettings.model());
        infoRow("Android", android.os.Build.VERSION.RELEASE + "  (API " + android.os.Build.VERSION.SDK_INT + ")");
        if (android.os.Build.VERSION.SECURITY_PATCH != null) infoRow("Security patch", android.os.Build.VERSION.SECURITY_PATCH);
        infoRow("Build", String.valueOf(android.os.Build.DISPLAY));
        infoRow("Kernel", SysSettings.kernel());
        infoRow("Screen", SysSettings.screenInfo(c));
        infoRow("Memory", SysSettings.bytes(SysSettings.ramTotal(c)));
        String ver = "-";
        try {
            ver = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        infoRow("Launcher", "Woba S Launcher " + ver);
    }

    // ------------------------------------------------------------ red y dispositivos

    /** Página de un ajuste que Android no deja cambiar a una app: muestra el estado y abre el panel. */
    private void pageOnOff(String label, boolean on, final Intent panel) {
        toggleRow(label, on, v -> {
            start(panel);
            postDelayed(this::refresh, 400);
        });
        note("Android doesn't let apps switch this directly. The switch opens the Android panel.");
        link("Open Android settings", go(panel));
    }

    // ------------------------------------------------------------ personalización

    private void pageStart() {
        final Context c = getContext();
        final Bitmap b = Wallpaper.start;
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = Theme.dp(14);
        content.addView(new W10Controls.StartPreview(c), plp);
        final String[] bg = {"Accent color", "My picture"};
        final int sel = b != null ? 1 : 0;
        comboRow("Background", bg[sel], true, () -> flyout(null, bg, sel, i -> {
            if (i == 0) {
                Wallpaper.clear(c, true);
                l.onPrefsChanged();
                refresh();
            } else if (b == null) {
                l.pickWallpaper(true);
            }
        }));
        button("Browse", () -> l.pickWallpaper(true));
        heading("Choose a style");
        boolean tile = Prefs.flag(c, Prefs.F_TILE_TRANS, true);
        radioGroup(new String[]{"Tile picture", "Full screen picture"}, tile ? 0 : 1, i -> {
            Prefs.setFlag(c, Prefs.F_TILE_TRANS, i == 0);
            l.onPrefsChanged();
        }, false);
        note("Tile picture lets the background show through the tiles.");
        heading("Tiles");
        flagCheck("Show tile titles", Prefs.F_TILE_LABELS, true);
        flagCheck("Live tile animations", Prefs.F_LIVE_ANIM, true);
        flagCheck("Tilt tiles when pressed", Prefs.F_TILT, true);
        flagCheck("Show more tiles", Prefs.F_MORE_TILES, false);
        button("Add a widget", this::showWidgets);
        button("Reset start layout", () -> confirm("Reset Start?", "Your tiles will go back to the defaults.",
                "Reset", () -> l.onResetStart()));
        if (b != null) {
            button("Use as Android wallpaper", () -> Wallpaper.applySystem(c, b, WallpaperManager.FLAG_SYSTEM));
        }
    }

    private void pageColors() {
        final Context ctx = getContext();
        heading("Choose your mode");
        radioGroup(new String[]{"Dark", "Light"}, l.dark() ? 0 : 1, i -> l.onDark(i == 0), true);
        heading("Choose an accent color");
        for (int row = 0; row < 8; row++) {
            LinearLayout r = new LinearLayout(ctx);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 6; col++) {
                final int index = Theme.W10_FIRST + row * 6 + col;
                W10Controls.Swatch sw = new W10Controls.Swatch(ctx, Theme.ACCENTS[index], index == l.accentIndex());
                sw.setOnClickListener(v -> l.onAccent(index));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(40), Theme.dp(40));
                lp.rightMargin = Theme.dp(3);
                lp.bottomMargin = Theme.dp(3);
                r.addView(sw, lp);
            }
            content.addView(r);
        }
    }

    private void pageLock() {
        final Context c = getContext();
        writeBanner();
        final Bitmap b = Wallpaper.lock;
        final String[] bg = {"Default", "My picture"};
        final int sel = b != null ? 1 : 0;
        comboRow("Background", bg[sel], true, () -> flyout(null, bg, sel, i -> {
            if (i == 0) {
                Wallpaper.clear(c, false);
                l.onPrefsChanged();
                refresh();
            } else if (b == null) {
                l.pickWallpaper(false);
            }
        }));
        button("Browse", () -> l.pickWallpaper(false));
        int t = SysSettings.timeoutMs(c);
        int cur = -1;
        for (int i = 0; i < TIMEOUTS.length; i++) if (TIMEOUTS[i] == t) cur = i;
        final int curSel = cur;
        comboRow("Screen times out after", cur >= 0 ? TIMEOUT_NAMES[cur] : (t / 1000) + " seconds", true, () ->
                flyout(null, TIMEOUT_NAMES, curSel, i -> {
                    if (!ensureWrite() || !SysSettings.setTimeoutMs(c, TIMEOUTS[i])) refresh();
                    else refresh();
                }));
        toggleRow("Use the Windows lock screen", l.wpLock(), on -> l.onWpLock(on));
        note("Shown when the screen turns off. Android's own lock (PIN, pattern) still protects the phone.");
        toggleRow("Show status line", Prefs.flag(c, Prefs.F_LOCK_STATUS, true), on -> {
            Prefs.setFlag(c, Prefs.F_LOCK_STATUS, on);
            l.onPrefsChanged();
        });
        note("Battery, missed calls and messages at the top of the lock screen.");
        infoRow("Double tap to lock", SysSettings.accessibilityEnabled(c) ? "On" : "Off");
        link("Double tap settings", go(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        link("Preview lock screen", () -> l.onPreviewLock());
        link("Sign-in options", go(Settings.ACTION_SECURITY_SETTINGS));
        if (b != null) {
            button("Use as Android lock screen", () -> Wallpaper.applySystem(c, b, WallpaperManager.FLAG_LOCK));
        }
    }

    private void pageSound() {
        final Context c = getContext();
        writeBanner();
        final int ringer = SysSettings.ringerMode(c);
        comboRow("Ringer", RINGER[ringer], true, () -> flyout(null, RINGER, ringer, i -> {
            if (!SysSettings.setRingerMode(c, i)) askPolicy();
            refresh();
        }));
        if (!SysSettings.hasPolicyAccess(c)) {
            note("Silencing the ringer needs do-not-disturb access.");
            button("Allow", () -> start(SysSettings.policyIntent()));
        }
        sliderRow("Ringer volume", SysSettings.volume(c, SysSettings.STREAM_RING), SysSettings.STREAM_RING);
        sliderRow("Media volume", SysSettings.volume(c, SysSettings.STREAM_MEDIA), SysSettings.STREAM_MEDIA);
        sliderRow("Alarm volume", SysSettings.volume(c, SysSettings.STREAM_ALARM), SysSettings.STREAM_ALARM);
        heading("Other sounds");
        sysCheck("Key press", Settings.System.SOUND_EFFECTS_ENABLED, true);
        sysCheck("Keypad tones", Settings.System.DTMF_TONE_WHEN_DIALING, true);
        sysCheck("Lock and unlock", "lockscreen_sounds_enabled", true);
        sysCheck("Vibrate on touch", Settings.System.HAPTIC_FEEDBACK_ENABLED, true);
        sysCheck("Vibrate when ringing", "vibrate_when_ringing", false);
        link("Manage ringtone and notification sounds", go(Settings.ACTION_SOUND_SETTINGS));
    }

    private void sliderRow(String label, float value, final int stream) {
        final Context c = getContext();
        label(label);
        content.addView(new W10Controls.Slider(c, value, 0, true, (v, done) -> {
            boolean ok = SysSettings.setVolume(c, stream, v);
            if (!ok && done) {
                askPolicy();
                refresh();
            }
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    // ------------------------------------------------------------ hora e idioma

    private void pageDate() {
        final Context c = getContext();
        writeBanner();
        toggleRow("24-hour clock", SysSettings.is24h(c), on -> {
            if (!ensureWrite() || !SysSettings.set24h(c, on)) refresh();
        });
        infoRow("Time zone", TimeZone.getDefault().getID());
        link("Set date and time", go(Settings.ACTION_DATE_SETTINGS));
    }

    // ------------------------------------------------------------ extras

    private void pageCortana() {
        final Context c = getContext();
        heading("Assistant");
        String key = Prefs.getStr(c, Cortana.KEY_API, "");
        String shown = key.isEmpty() ? "Not set · tap to paste your Gemini key"
                : "Set · ending in " + key.substring(Math.max(0, key.length() - 4));
        valueRow("Gemini API key", shown, () -> askText("Gemini API key", Prefs.getStr(c, Cortana.KEY_API, ""), v -> {
            Prefs.setStr(c, Cortana.KEY_API, v.trim());
            refresh();
        }));
        valueRow("Model", Prefs.getStr(c, Cortana.KEY_MODEL, Cortana.DEFAULT_MODEL), () ->
                askText("Model name", Prefs.getStr(c, Cortana.KEY_MODEL, Cortana.DEFAULT_MODEL), v -> {
                    Prefs.setStr(c, Cortana.KEY_MODEL, v.trim().isEmpty() ? Cortana.DEFAULT_MODEL : v.trim());
                    refresh();
                }));
        toggleRow("Speak answers", !"0".equals(Prefs.getStr(c, Cortana.KEY_SPEAK, "1")),
                on -> Prefs.setStr(c, Cortana.KEY_SPEAK, on ? "1" : "0"));
        heading("Permissions");
        permRow("Microphone", Sys.has(c, "android.permission.RECORD_AUDIO"), "Allow",
                () -> start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()))));
        heading("What you can say");
        note("Open camera · set a timer for 5 minutes · alarm at 7:30 · call 600123456 · search pizza near me · what time is it. "
                + "Anything else goes to Gemini once you add a key.");
        button("Forget this conversation", () -> Cortana.clearHistory());
    }

    // ------------------------------------------------------------ teclado

    private void kbSlider(String label, final String key, int def, final String[] names) {
        final Context c = getContext();
        int cur = Math.max(0, Math.min(names.length - 1, com.woba.slauncher.ime.KbPrefs.level(c, key, def)));
        final TextView t = text(label + " · " + names[cur], 15, Theme.fg, Theme.regular);
        t.setPadding(0, Theme.dp(14), 0, Theme.dp(2));
        content.addView(t);
        final int n = names.length - 1;
        content.addView(new W10Controls.Slider(c, cur / (float) n, n, true, (v, done) -> {
            int i = Math.round(v * n);
            t.setText(Brand.t(c, label + " · " + names[i]));
            if (done) com.woba.slauncher.ime.KbPrefs.setLevel(c, key, i);
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    /** Fila que da la vuelta entre varias opciones al tocarla. */
    private void kbCycle(final String label, final String key, final String[] names) {
        final Context c = getContext();
        int cur = Math.max(0, Math.min(names.length - 1, com.woba.slauncher.ime.KbPrefs.level(c, key, 0)));
        valueRow(label, names[cur], () -> {
            com.woba.slauncher.ime.KbPrefs.setLevel(c, key, (com.woba.slauncher.ime.KbPrefs.level(c, key, 0) + 1) % names.length);
            refresh();
        });
    }

    private void kbToggle(String label, final String key) {
        final Context c = getContext();
        toggleRow(label, com.woba.slauncher.ime.KbPrefs.on(c, key), on -> com.woba.slauncher.ime.KbPrefs.set(c, key, on));
    }

    private void pageKeyboard() {
        final Context c = getContext();
        link("Set up WOBA as your keyboard", go(new Intent(c, com.woba.slauncher.KeyboardSetupActivity.class)));
        heading("Layout");
        kbSlider("Keyboard height", com.woba.slauncher.ime.KbPrefs.HEIGHT, 2, com.woba.slauncher.ime.KbPrefs.HEIGHT_NAMES);
        kbSlider("Key size", com.woba.slauncher.ime.KbPrefs.KEYSIZE, 2, com.woba.slauncher.ime.KbPrefs.KEY_NAMES);
        kbCycle("One-handed keyboard", com.woba.slauncher.ime.KbPrefs.ONEHAND, com.woba.slauncher.ime.KbPrefs.ONEHAND_NAMES);
        kbCycle("Key style", com.woba.slauncher.ime.KbPrefs.STYLE, com.woba.slauncher.ime.KbPrefs.STYLE_NAMES);
        heading("Feedback");
        kbToggle("Key sound", com.woba.slauncher.ime.KbPrefs.SOUND);
        kbToggle("Key vibration", com.woba.slauncher.ime.KbPrefs.VIBRATE);
        kbToggle("Show key preview", com.woba.slauncher.ime.KbPrefs.PREVIEW);
        kbSlider("Hold delay for accents", com.woba.slauncher.ime.KbPrefs.HOLD, 2, com.woba.slauncher.ime.KbPrefs.HOLD_NAMES);
        heading("Typing");
        kbToggle("Autocorrect", com.woba.slauncher.ime.KbPrefs.AUTOCORRECT);
        kbToggle("Text suggestions", com.woba.slauncher.ime.KbPrefs.SUGGEST);
        kbToggle("Capitalise the first letter of sentences", com.woba.slauncher.ime.KbPrefs.AUTOCAP);
        kbToggle("Double space types a period", com.woba.slauncher.ime.KbPrefs.DBLSPACE);
        kbToggle("Cursor control dot", com.woba.slauncher.ime.KbPrefs.CURSOR);
        kbToggle("Microphone key", com.woba.slauncher.ime.KbPrefs.MIC);
        kbCycle("Typing languages", com.woba.slauncher.ime.KbPrefs.LANG, com.woba.slauncher.ime.KbPrefs.LANG_NAMES);
        heading("Learned words");
        note("Words you teach the keyboard (by tapping the first suggestion, or by undoing a correction) are never corrected again. "
                + "Learned so far: " + com.woba.slauncher.ime.Dict.learnedCount(c) + ".");
        button("Forget learned words", () -> {
            com.woba.slauncher.ime.Dict.forgetAll(c);
            refresh();
        });
        button("Reset keyboard settings", () -> {
            com.woba.slauncher.ime.KbPrefs.reset(c);
            refresh();
        });
    }

    private void pageNames() {
        final Context c = getContext();
        note("Tap a word to change what the simulated apps show instead of it. Only what you see changes: "
                + "package names, web addresses and the real apps installed on your phone stay as they are. "
                + "Leave a field empty to go back to its default.");
        for (final String[] t : Brand.TERMS) {
            final String term = t[0];
            LinearLayout r = vbox();
            r.addView(rawText(term, 13, Theme.subtle, Theme.regular));
            r.addView(rawText(Brand.get(c, term), 20, Theme.fg, Theme.light));
            r.setBackground(W10Controls.pressBg());
            r.setOnClickListener(v -> askText(term, Brand.get(c, term), val -> {
                Brand.set(c, term, val);
                namesChanged();
            }));
            content.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
        button("Reset to defaults", () -> {
            Brand.resetAll(c);
            namesChanged();
        });
    }

    private void namesChanged() {
        l.onPrefsChanged();
        l.onHiddenChanged(); // vuelve a pintar la lista de apps
        refresh();
    }

    private void pageLauncher() {
        final Context c = getContext();
        heading("Permissions");
        permRow("Modify system settings", SysSettings.canWrite(c), "Allow", () -> start(SysSettings.writeSettingsIntent(c)));
        permRow("Do not disturb access", SysSettings.hasPolicyAccess(c), "Allow", () -> start(SysSettings.policyIntent()));
        permRow("Call log · missed calls tile", Sys.has(c, "android.permission.READ_CALL_LOG"), "Allow", () -> l.onRequestLive());
        permRow("Messages · unread tile", Sys.has(c, "android.permission.READ_SMS"), "Allow", () -> l.onRequestLive());
        permRow("Accessibility · double tap to lock", SysSettings.accessibilityEnabled(c), "Enable",
                () -> start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        heading("Launcher");
        link("Default home app", go(Settings.ACTION_HOME_SETTINGS));
        link("Android permissions", go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()))));
    }

    private void showWidgets() {
        final PackageManager pm = getContext().getPackageManager();
        final ArrayList<AppWidgetProviderInfo> providers = new ArrayList<>(l.widgetProviders());
        Collections.sort(providers, new Comparator<AppWidgetProviderInfo>() {
            @Override
            public int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                return a.loadLabel(pm).toLowerCase().compareTo(b.loadLabel(pm).toLowerCase());
            }
        });
        String[] labels = new String[providers.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = String.valueOf(providers.get(i).loadLabel(pm));
        flyout("Add a widget", labels, -1, i -> l.onAddWidget(providers.get(i)));
    }

    // ------------------------------------------------------------ permisos

    private void writeBanner() {
        if (SysSettings.canWrite(getContext())) return;
        note("Changing this needs permission to modify system settings.");
        button("Allow", () -> start(SysSettings.writeSettingsIntent(getContext())));
    }

    private boolean ensureWrite() {
        if (SysSettings.canWrite(getContext())) return true;
        askPermission("system settings", SysSettings.writeSettingsIntent(getContext()));
        return false;
    }

    private void askPolicy() {
        askPermission("do not disturb", SysSettings.policyIntent());
    }

    private void askPermission(String what, final Intent grant) {
        confirm("Permission needed", "To change this from here, the launcher needs access to " + what
                + ". Android will open its permission screen.", "Allow", () -> start(grant));
    }

    // ------------------------------------------------------------ piezas de UI

    private void start(Intent i) { Actions.start(getContext(), i); }

    private Runnable go(final String action) { return () -> Actions.start(getContext(), new Intent(action)); }

    private Runnable go(final Intent i) { return () -> Actions.start(getContext(), i); }

    private TextView text(String s, float sp, int color, Typeface tf) {
        return rawText(Brand.t(getContext(), s), sp, color, tf);
    }

    /** Texto sin sustituir nombres de marca (la página App names muestra las palabras originales). */
    private TextView rawText(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout vbox() {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, Theme.dp(10), 0, Theme.dp(10));
        return r;
    }

    private void heading(String s) {
        TextView t = text(s, 24, Theme.fg, Theme.light);
        t.setPadding(0, Theme.dp(20), 0, Theme.dp(6));
        content.addView(t);
    }

    private void label(String s) {
        TextView t = text(s, 15, Theme.fg, Theme.regular);
        t.setPadding(0, Theme.dp(14), 0, Theme.dp(6));
        content.addView(t);
    }

    private void note(String s) {
        TextView t = text(s, 15, Theme.alpha(Theme.fg, 0.65f), Theme.regular);
        t.setPadding(0, Theme.dp(6), 0, Theme.dp(6));
        content.addView(t);
    }

    private void link(String s, final Runnable action) {
        TextView t = text(s, 15, Theme.accent, Theme.regular);
        t.setPadding(0, Theme.dp(12), 0, Theme.dp(12));
        t.setBackground(W10Controls.pressBg());
        t.setOnClickListener(v -> action.run());
        content.addView(t, new LinearLayout.LayoutParams(-2, -2));
    }

    /** Fila de categoría: icono de acento a la izquierda, título y subtítulo. */
    private void catRow(int icon, String title, String sub, final Runnable action) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Theme.dp(64));
        r.setPadding(Theme.dp(2), Theme.dp(8), Theme.dp(2), Theme.dp(8));
        r.setBackground(W10Controls.pressBg());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Theme.dp(32), Theme.dp(32));
        ilp.rightMargin = Theme.dp(16);
        r.addView(new SettingsIcons.IconView(c, icon, Theme.accent), ilp);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(title, 17, Theme.fg, Theme.regular));
        TextView st = text(sub, 14, Theme.subtle, Theme.regular);
        st.setMaxLines(2);
        col.addView(st);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setOnClickListener(v -> action.run());
        content.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Fila de texto plano, como en las páginas de categoría de W10. */
    private void listRow(String title, final Runnable action) {
        TextView t = text(title, 17, Theme.fg, Theme.regular);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinimumHeight(Theme.dp(46));
        t.setBackground(W10Controls.pressBg());
        t.setOnClickListener(v -> action.run());
        content.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void infoRow(String label, String value) {
        LinearLayout r = vbox();
        r.addView(text(label, 14, Theme.subtle, Theme.regular));
        r.addView(text(value, 17, Theme.fg, Theme.regular));
        content.addView(r);
    }

    /** Etiqueta pequeña + valor, tocable (para campos de texto). */
    private void valueRow(String label, String value, final Runnable action) {
        LinearLayout r = vbox();
        r.addView(text(label, 14, Theme.subtle, Theme.regular));
        r.addView(text(value, 17, Theme.fg, Theme.regular));
        r.setBackground(W10Controls.pressBg());
        r.setOnClickListener(v -> action.run());
        content.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    private void permRow(String label, boolean granted, String action, final Runnable grant) {
        LinearLayout r = vbox();
        r.addView(text(label, 15, Theme.fg, Theme.regular));
        r.addView(text(granted ? "Allowed" : "Not allowed", 14, granted ? Theme.accent : Theme.subtle, Theme.regular));
        content.addView(r);
        if (!granted) button(action, grant);
    }

    private void legend(int color, String s) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Theme.dp(6), 0, Theme.dp(2));
        View sw = new View(c);
        sw.setBackgroundColor(color);
        r.addView(sw, new LinearLayout.LayoutParams(Theme.dp(14), Theme.dp(14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Theme.dp(10);
        r.addView(text(s, 15, Theme.fg, Theme.regular), lp);
        content.addView(r);
    }

    /** Etiqueta encima, interruptor en píldora debajo con "On"/"Off" a la derecha. */
    private void toggleRow(String label, boolean on, final W10Controls.Toggle.OnChange cb) {
        label(label);
        LinearLayout line = new LinearLayout(getContext());
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        final TextView st = text(on ? "On" : "Off", 15, Theme.fg, Theme.regular);
        W10Controls.Toggle sw = new W10Controls.Toggle(getContext(), on, v -> {
            st.setText(v ? "On" : "Off");
            cb.onChange(v);
        });
        line.addView(sw, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Theme.dp(12);
        line.addView(st, lp);
        content.addView(line);
    }

    private void checkRow(String label, boolean on, W10Controls.Check.OnChange cb) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Theme.dp(8), 0, Theme.dp(8));
        final W10Controls.Check ck = new W10Controls.Check(c, on, cb);
        r.addView(ck, new LinearLayout.LayoutParams(-2, -2));
        TextView t = text(label, 15, Theme.fg, Theme.regular);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = Theme.dp(12);
        r.addView(t, lp);
        t.setOnClickListener(v -> ck.toggle());
        content.addView(r);
    }

    private void flagCheck(String label, final String key, boolean def) {
        final Context c = getContext();
        checkRow(label, Prefs.flag(c, key, def), on -> {
            Prefs.setFlag(c, key, on);
            l.onPrefsChanged();
        });
    }

    private void sysCheck(String label, final String key, boolean def) {
        final Context c = getContext();
        checkRow(label, SysSettings.sysFlag(c, key, def), on -> {
            if (!ensureWrite() || !SysSettings.setSysFlag(c, key, on)) refresh();
        });
    }

    private void radioGroup(String[] names, int sel, final IntCb cb, boolean horizontal) {
        Context c = getContext();
        LinearLayout g = new LinearLayout(c);
        g.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        final W10Controls.Radio[] rs = new W10Controls.Radio[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, Theme.dp(8), Theme.dp(28), Theme.dp(8));
            rs[i] = new W10Controls.Radio(c, i == sel);
            r.addView(rs[i], new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Theme.dp(10);
            r.addView(text(names[i], 15, Theme.fg, Theme.regular), lp);
            r.setOnClickListener(v -> {
                for (int k = 0; k < rs.length; k++) rs[k].setChecked(k == idx);
                cb.on(idx);
            });
            g.addView(r);
        }
        content.addView(g);
    }

    private void comboRow(String label, String value, boolean enabled, final Runnable open) {
        label(label);
        W10Controls.Combo cb = new W10Controls.Combo(getContext(), value, enabled);
        if (enabled) cb.setOnClickListener(v -> open.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.rightMargin = Theme.dp(48);
        content.addView(cb, lp);
    }

    private TextView makeButton(String label) {
        TextView b = text(label, 15, Theme.fg, Theme.regular);
        b.setGravity(Gravity.CENTER);
        b.setMinWidth(Theme.dp(104));
        b.setPadding(Theme.dp(16), 0, Theme.dp(16), 0);
        W10Controls.styleButton(b);
        return b;
    }

    private void button(String label, final Runnable action) {
        TextView b = makeButton(label);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Theme.dp(34));
        lp.topMargin = Theme.dp(12);
        content.addView(b, lp);
    }

    private void buttons(String l1, final Runnable a1, String l2, final Runnable a2) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView b1 = makeButton(l1);
        b1.setOnClickListener(v -> a1.run());
        TextView b2 = makeButton(l2);
        b2.setOnClickListener(v -> a2.run());
        row.addView(b1, new LinearLayout.LayoutParams(-2, Theme.dp(34)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Theme.dp(34));
        lp.leftMargin = Theme.dp(8);
        row.addView(b2, lp);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = Theme.dp(14);
        content.addView(row, rlp);
    }

    // ------------------------------------------------------------ diálogos W10

    private Dialog panelDialog(String title, View body, String[] btns, final Runnable[] acts) {
        final Context c = getContext();
        final Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(W10Controls.panelColor());
        panel.setPadding(Theme.dp(16), Theme.dp(16), Theme.dp(16), Theme.dp(16));
        if (title != null) panel.addView(text(title, 20, Theme.fg, Theme.semibold));
        if (body != null) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
            blp.topMargin = Theme.dp(title != null ? 12 : 0);
            panel.addView(body, blp);
        }
        if (btns != null) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < btns.length; i++) {
                final int idx = i;
                TextView b = makeButton(btns[i]);
                b.setOnClickListener(v -> {
                    d.dismiss();
                    if (acts != null && acts[idx] != null) acts[idx].run();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(36), 1f);
                if (i > 0) lp.leftMargin = Theme.dp(8);
                row.addView(b, lp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Theme.dp(18);
            panel.addView(row, rlp);
        }
        FrameLayout wrap = new FrameLayout(c);
        wrap.setPadding(Theme.dp(12), 0, Theme.dp(12), 0);
        wrap.addView(panel, new FrameLayout.LayoutParams(-1, -2));
        d.setContentView(wrap);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(-1, -2);
            w.setGravity(Gravity.CENTER);
        }
        return d;
    }

    private void confirm(String title, String msg, String yes, final Runnable onYes) {
        TextView m = text(msg, 15, Theme.fg, Theme.regular);
        panelDialog(title, m, new String[]{yes, "Cancel"}, new Runnable[]{onYes, null}).show();
    }

    private void askText(String title, String current, final TextResult cb) {
        final EditText et = new EditText(getContext());
        et.setSingleLine(true);
        et.setText(current);
        et.setSelection(et.getText().length());
        et.setTextColor(0xFF000000);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setTypeface(Theme.regular);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFFFFFF);
        g.setStroke(Theme.dp(2), Theme.accent);
        et.setBackground(g);
        et.setPadding(Theme.dp(10), Theme.dp(8), Theme.dp(10), Theme.dp(8));
        panelDialog(title, et, new String[]{"Save", "Cancel"}, new Runnable[]{
                () -> cb.on(et.getText().toString()), null}).show();
    }

    /** Lista desplegable al estilo de los combos de W10: la opción actual va en color de acento. */
    private void flyout(String title, String[] opts, int sel, final IntCb cb) {
        Context c = getContext();
        final Dialog[] ref = new Dialog[1];
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            boolean on = i == sel;
            TextView t = text(opts[i], 17, on ? 0xFFFFFFFF : Theme.fg, Theme.regular);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setMinimumHeight(Theme.dp(46));
            t.setPadding(Theme.dp(14), 0, Theme.dp(14), 0);
            if (on) t.setBackgroundColor(Theme.accent);
            else t.setBackground(W10Controls.pressBg());
            t.setOnClickListener(v -> {
                ref[0].dismiss();
                cb.on(idx);
            });
            list.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        int max = Math.round(getResources().getDisplayMetrics().heightPixels * 0.6f);
        int want = Theme.dp(46) * opts.length;
        View body = sv;
        LinearLayout holder = new LinearLayout(c);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(sv, new LinearLayout.LayoutParams(-1, Math.min(max, want)));
        ref[0] = panelDialog(title, holder, null, null);
        ref[0].show();
    }
}
