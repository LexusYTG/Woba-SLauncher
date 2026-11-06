package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;
import java.util.Locale;

/** Tienda: catálogo con aspecto de Microsoft Store; "instalar" abre la ficha real en Google Play. */
public class StoreApp extends SimApp {
    static class Entry {
        final String name, pkg, publisher, cat, desc;
        final float rating;
        Entry(String n, String p, String pub, String c, float r, String d) { name = n; pkg = p; publisher = pub; cat = c; rating = r; desc = d; }
    }

    private static final Entry[] CATALOG = {
            new Entry("WhatsApp Messenger", "com.whatsapp", "WhatsApp LLC", "apps", 4.4f, "Simple, reliable, private messaging and calling."),
            new Entry("Telegram", "org.telegram.messenger", "Telegram FZ-LLC", "apps", 4.5f, "Fast, secure messaging with channels and bots."),
            new Entry("Skype", "com.skype.raider", "Skype", "apps", 4.0f, "Free video calls and messages."),
            new Entry("Microsoft Outlook", "com.microsoft.office.outlook", "Microsoft Corporation", "apps", 4.6f, "Email, calendar and contacts in one place."),
            new Entry("Microsoft Word", "com.microsoft.office.word", "Microsoft Corporation", "apps", 4.5f, "Create, edit and share documents."),
            new Entry("Microsoft Excel", "com.microsoft.office.excel", "Microsoft Corporation", "apps", 4.5f, "Spreadsheets and charts on the go."),
            new Entry("Microsoft PowerPoint", "com.microsoft.office.powerpoint", "Microsoft Corporation", "apps", 4.5f, "Build and present slides."),
            new Entry("OneNote", "com.microsoft.office.onenote", "Microsoft Corporation", "apps", 4.5f, "Capture notes, ideas and sketches."),
            new Entry("OneDrive", "com.microsoft.skydrive", "Microsoft Corporation", "apps", 4.6f, "Store photos and files in the cloud."),
            new Entry("Microsoft Edge", "com.microsoft.emmx", "Microsoft Corporation", "apps", 4.6f, "Fast and secure web browser."),
            new Entry("Bing Search", "com.microsoft.bing", "Microsoft Corporation", "apps", 4.5f, "Search the web, images and news."),
            new Entry("Google Maps", "com.google.android.apps.maps", "Google LLC", "apps", 4.3f, "Navigation and real-time traffic."),
            new Entry("Waze", "com.waze", "Waze", "apps", 4.5f, "Community-driven navigation."),
            new Entry("Instagram", "com.instagram.android", "Instagram", "apps", 4.0f, "Share photos and videos."),
            new Entry("Duolingo", "com.duolingo", "Duolingo", "apps", 4.7f, "Learn languages for free."),
            new Entry("Minecraft", "com.mojang.minecraftpe", "Mojang", "games", 4.5f, "Build, explore and survive."),
            new Entry("Subway Surfers", "com.kiloo.subwaysurf", "Kiloo", "games", 4.5f, "Endless runner."),
            new Entry("Candy Crush Saga", "com.king.candycrushsaga", "King", "games", 4.4f, "Match-three puzzle."),
            new Entry("Asphalt 9", "com.gameloft.android.ANMP.GloftA9HM", "Gameloft", "games", 4.6f, "Arcade racing."),
            new Entry("Chess", "com.chess", "Chess.com", "games", 4.7f, "Play chess online."),
            new Entry("Spotify", "com.spotify.music", "Spotify AB", "music", 4.3f, "Music and podcasts."),
            new Entry("Shazam", "com.shazam.android", "Apple", "music", 4.5f, "Identify songs."),
            new Entry("VLC", "org.videolan.vlc", "VideoLAN", "music", 4.4f, "Plays any media file."),
            new Entry("SoundCloud", "com.soundcloud.android", "SoundCloud", "music", 4.2f, "Discover new music."),
            new Entry("YouTube Music", "com.google.android.apps.youtube.music", "Google LLC", "music", 4.3f, "Songs and videos."),
    };

    public StoreApp(Context c, SimHost host) {
        super(c, host, "store");
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "store"; }

    private boolean installed(String pkg) {
        try { getContext().getPackageManager().getPackageInfo(pkg, 0); return true; } catch (Exception e) { return false; }
    }

    private WpUi.WpList listFor(final String cat) {
        final Context c = getContext();
        WpUi.WpList l = new WpUi.WpList(c);
        ArrayList<WpUi.Item> items = new ArrayList<>();
        for (Entry e : CATALOG) if (cat == null || e.cat.equals(cat)) {
            items.add(new WpUi.Item(e.name).sub(e.publisher + "  ·  " + stars(e.rating) + (installed(e.pkg) ? "  ·  installed" : "")).avatar(e.name.substring(0, 1), WpUi.colorFor(e.pkg)).tag(e));
        }
        l.setItems(items);
        l.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof Entry) { final Entry e = (Entry) it.tag; go(new Runnable() { @Override public void run() { showEntry(e); } }); }
            }
        });
        return l;
    }

    private String stars(float r) {
        int n = Math.round(r);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < n ? "★" : "☆");
        return sb.toString();
    }

    private void showMain() {
        final Context c = getContext();
        WpUi.WpList apps = listFor("apps"), games = listFor("games"), music = listFor("music");
        WpUi.WpList mine = new WpUi.WpList(c);
        ArrayList<WpUi.Item> mi = new ArrayList<>();
        int n = 0;
        for (Entry e : CATALOG) if (installed(e.pkg)) { mi.add(new WpUi.Item(e.name).sub("installed").avatar(e.name.substring(0, 1), WpUi.colorFor(e.pkg)).tag(e)); n++; }
        if (mi.isEmpty()) mi.add(new WpUi.Item("no apps from the catalog installed").dim());
        mine.setItems(mi);
        mine.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof Entry) { final Entry e = (Entry) it.tag; go(new Runnable() { @Override public void run() { showEntry(e); } }); }
            }
        });
        Pivot pv = new Pivot(c, "store", new String[]{"apps", "games", "music", "my apps"}, new View[]{apps, games, music, mine});
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.SEARCH, "search", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showSearch(); } }); } }),
                new AppBar.Item(Glyph.STORE, "play store", new Runnable() { @Override public void run() { openPlay(null); } })
        );
        swapContent(pv, ab);
    }

    private void openPlay(String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(pkg == null ? "market://search?q=apps" : "market://details?id=" + pkg));
        try { host.launch(i); } catch (Exception e) {
            host.launch(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps" + (pkg == null ? "" : "/details?id=" + pkg))));
        }
    }

    private void showSearch() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "store", "search"));
        final EditText et = WpUi.textBox(c, "search the store", "");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(et, lp);
        final WpUi.WpList res = new WpUi.WpList(c);
        col.addView(res, new LinearLayout.LayoutParams(-1, 0, 1f));
        res.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof Entry) { final Entry e = (Entry) it.tag; WpUi.hideKeyboard(et); go(new Runnable() { @Override public void run() { showEntry(e); } }); }
                else if (it.tag instanceof String) { WpUi.hideKeyboard(et); host.launch(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode((String) it.tag)))); }
            }
        });
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.getDefault());
                ArrayList<WpUi.Item> out = new ArrayList<>();
                if (!q.isEmpty()) {
                    for (Entry e : CATALOG) if (e.name.toLowerCase().contains(q) || e.publisher.toLowerCase().contains(q))
                        out.add(new WpUi.Item(e.name).sub(e.publisher).avatar(e.name.substring(0, 1), WpUi.colorFor(e.pkg)).tag(e));
                    out.add(new WpUi.Item("search \"" + s + "\" in Google Play").glyph(Glyph.STORE, Theme.accent).tag(s.toString()));
                }
                res.setItems(out);
            }
        });
        swapContent(col, null);
        et.requestFocus();
        et.post(new Runnable() { @Override public void run() { WpUi.showKeyboard(et); } });
    }

    private void showEntry(final Entry e) {
        final Context c = getContext();
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(20));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(WpUi.label(c, "STORE", 13, Theme.semibold, Theme.fg));
        LinearLayout head = new LinearLayout(c);
        head.setPadding(0, Theme.dp(12), 0, Theme.dp(12));
        View icon = new View(c) {
            @Override protected void onDraw(Canvas cv) { WpUi.drawAvatar(cv, 0, 0, getWidth(), e.name.substring(0, 1), 0, WpUi.colorFor(e.pkg), null); }
        };
        head.addView(icon, new LinearLayout.LayoutParams(Theme.dp(96), Theme.dp(96)));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(Theme.dp(14), 0, 0, 0);
        t.addView(WpUi.label(c, e.name, 26, Theme.light, Theme.fg));
        t.addView(WpUi.label(c, e.publisher, 15, Theme.light, Theme.subtle));
        t.addView(WpUi.label(c, stars(e.rating) + "  " + e.rating, 15, Theme.light, Theme.accent));
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(head);
        final boolean inst = installed(e.pkg);
        TextView b = WpUi.button(c, inst ? "open" : "install");
        b.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (inst) {
                    Intent li = getContext().getPackageManager().getLaunchIntentForPackage(e.pkg);
                    if (li != null) host.launch(li);
                } else openPlay(e.pkg);
            }
        });
        col.addView(b, new LinearLayout.LayoutParams(-1, Theme.dp(46)));
        TextView d = WpUi.label(c, e.desc + "\n\nThis opens the app's page in Google Play, where Android installs it.", 17, Theme.light, Theme.fg);
        d.setPadding(0, Theme.dp(16), 0, 0);
        col.addView(d);
        swapContent(sv, null);
    }
}
