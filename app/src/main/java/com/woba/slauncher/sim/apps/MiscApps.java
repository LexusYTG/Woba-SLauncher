package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/** Apps pequeñas: Xbox, Help + Tips y OneDrive. */
public final class MiscApps {
    private MiscApps() {}

    // ------------------------------------------------------------------ Xbox

    public static class XboxApp extends SimApp {
        private static final String[][] ACH = {
                {"First steps", "Open the Xbox app", "10"}, {"Tile master", "Pin 5 tiles to Start", "20"}, {"Night owl", "Use the phone after midnight", "15"},
                {"Social butterfly", "Send 3 messages", "25"}, {"Explorer", "Open every app", "50"}, {"Photographer", "Open Photos", "10"}
        };
        private WpUi.WpList list;

        public XboxApp(Context c, SimHost host) {
            super(c, host, "xbox");
            go(new Runnable() { @Override public void run() { showMain(); } });
        }

        @Override public String appTitle() { return "xbox"; }

        private void showMain() {
            final Context c = getContext();
            list = new WpUi.WpList(c);
            list.setOnItem(new WpUi.OnItem() {
                @Override public void on(WpUi.Item it, int pos, float x, float y) {
                    if (it.tag instanceof Integer) {
                        int i = (Integer) it.tag;
                        String key = "ach" + i;
                        if (!SimStore.flag(key, false)) { SimStore.putFlag(key, true); Toast.makeText(getContext(), "Achievement unlocked: " + ACH[i][0], Toast.LENGTH_SHORT).show(); fill(); }
                    }
                }
            });
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(WpUi.pageHeader(c, "xbox", "achievements"));
            col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
            swapContent(col, null);
            fill();
        }

        private void fill() {
            ArrayList<WpUi.Item> items = new ArrayList<>();
            int score = 0;
            for (int i = 0; i < ACH.length; i++) if (SimStore.flag("ach" + i, false)) score += Integer.parseInt(ACH[i][2]);
            items.add(new WpUi.Item(android.os.Build.MODEL + " · " + score + " G").sub("gamerscore (tap an achievement to unlock it)").glyph(Glyph.XBOX, 0xFF107C10));
            items.add(WpUi.Item.section("achievements"));
            for (int i = 0; i < ACH.length; i++) {
                boolean on = SimStore.flag("ach" + i, false);
                WpUi.Item it = new WpUi.Item(ACH[i][0]).sub(ACH[i][1]).right(ACH[i][2] + " G").tag(i);
                it.glyph(on ? Glyph.CHECK : Glyph.GAMEPAD, on ? 0xFF107C10 : 0xFF555555);
                items.add(it);
            }
            list.setItems(items);
        }
    }

    // ------------------------------------------------------------------ Help + Tips

    public static class HelpApp extends SimApp {
        private static final String[][] TIPS = {
                {"Move tiles", "Touch and hold a tile on Start, then drag it. Tap the corner buttons to unpin or resize."},
                {"All apps", "Swipe left on Start to see every app. Tap a letter to jump to a section."},
                {"Action Center", "Swipe down from the top edge to see notifications and quick actions."},
                {"Recent apps", "Touch and hold the back button to switch between running apps. Swipe a card up to close it."},
                {"Lock the phone", "Double-tap an empty area of Start (needs the accessibility service for a real lock)."},
                {"Cortana", "Tap the Cortana tile or the search button. Ask for an alarm, a timer, the weather or to open an app."},
                {"Personalize", "Settings > start+theme: accent color, background, transparent tiles and more."},
                {"Kid's Corner", "Settings > kid's corner: choose the apps your child can use."},
                {"Continuum", "Settings > continuum: shows a desktop-style interface for a big screen."},
                {"Battery Saver", "Settings > battery saver turns off live tile animations and background refresh."}
        };

        public HelpApp(Context c, SimHost host) {
            super(c, host, "help");
            go(new Runnable() { @Override public void run() { showMain(); } });
        }

        @Override public String appTitle() { return "help + tips"; }

        private void showMain() {
            final Context c = getContext();
            ScrollView sv = new ScrollView(c);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            sv.addView(col, new ScrollView.LayoutParams(-1, -2));
            col.addView(WpUi.pageHeader(c, "help + tips", "tips"));
            for (String[] t : TIPS) {
                TextView h = WpUi.label(c, t[0].toLowerCase(), 22, Theme.light, Theme.accent);
                h.setPadding(Theme.dp(18), Theme.dp(14), Theme.dp(18), Theme.dp(2));
                col.addView(h);
                TextView b = WpUi.label(c, t[1], 16, Theme.light, Theme.fg);
                b.setPadding(Theme.dp(18), 0, Theme.dp(18), Theme.dp(4));
                col.addView(b);
            }
            swapContent(sv, null);
        }
    }

    // ------------------------------------------------------------------ OneDrive

    public static class OneDriveApp extends SimApp {
        private WpUi.WpList list;
        private File root;

        public OneDriveApp(Context c, SimHost host) {
            super(c, host, "onedrive");
            root = new File(c.getFilesDir(), "onedrive");
            root.mkdirs();
            go(new Runnable() { @Override public void run() { showMain(); } });
        }

        @Override public String appTitle() { return "onedrive"; }

        private void showMain() {
            final Context c = getContext();
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(WpUi.pageHeader(c, "onedrive", "files"));
            long used = 0;
            File[] fs = root.listFiles();
            if (fs != null) for (File f : fs) used += f.length();
            TextView t = WpUi.label(c, (used / 1024) + " KB used of 15 GB (local simulation)", 14, Theme.light, Theme.subtle);
            t.setPadding(Theme.dp(18), 0, Theme.dp(18), Theme.dp(8));
            col.addView(t);
            list = new WpUi.WpList(c);
            col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
            list.setOnItem(new WpUi.OnItem() {
                @Override public void on(WpUi.Item it, int pos, float x, float y) {
                    if (it.tag instanceof File) {
                        final File f = (File) it.tag;
                        showMenu(x, y + Theme.dpf(100), new String[]{"delete"}, new Choice() { @Override public void on(int i) { f.delete(); showMain(); } });
                    }
                }
            });
            AppBar ab = new AppBar(c).items(new AppBar.Item(Glyph.ADD, "upload photo", new Runnable() { @Override public void run() { upload(); } }));
            swapContent(col, ab);
            ArrayList<WpUi.Item> items = new ArrayList<>();
            if (fs != null) for (File f : fs) items.add(new WpUi.Item(f.getName()).sub((f.length() / 1024) + " KB").glyph(Glyph.PHOTOS, Theme.accent).tag(f));
            if (items.isEmpty()) items.add(new WpUi.Item("nothing here yet — upload a photo").dim());
            list.setItems(items);
        }

        private void upload() {
            host.bridge().pickImage(new HostBridge.ImageCallback() {
                @Override public void onImage(Uri uri) {
                    try {
                        InputStream in = getContext().getContentResolver().openInputStream(uri);
                        File out = new File(root, "photo_" + System.currentTimeMillis() + ".jpg");
                        FileOutputStream o = new FileOutputStream(out);
                        byte[] b = new byte[16384];
                        int n;
                        while (in != null && (n = in.read(b)) > 0) o.write(b, 0, n);
                        o.close();
                        if (in != null) in.close();
                        showMain();
                    } catch (Exception e) { Toast.makeText(getContext(), "Couldn't upload", Toast.LENGTH_SHORT).show(); }
                }
            });
        }
    }
}
