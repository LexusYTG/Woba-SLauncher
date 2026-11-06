package com.woba.slauncher.sim.apps;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.VideoView;

import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.Thumbs;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpControls;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;

/** Películas y TV: vídeos reales del dispositivo con reproductor propio. */
public class VideoApp extends SimApp {
    static class Vid { Uri uri; String title; int dur; long size; }

    private final ArrayList<Vid> vids = new ArrayList<>();
    private boolean loaded;
    private WpUi.WpList list;
    private VideoView vv;
    private final Handler h = new Handler(Looper.getMainLooper());

    public VideoApp(Context c, SimHost host) {
        super(c, host, "video");
        go(new Runnable() { @Override public void run() { showList(); } });
    }

    @Override public String appTitle() { return "movies & tv"; }

    @Override public void onDestroy() { h.removeCallbacksAndMessages(null); if (vv != null) vv.stopPlayback(); }

    @Override public void onHide() { if (vv != null && vv.isPlaying()) vv.pause(); }

    private String perm() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE; }

    private boolean has() { return getContext().checkSelfPermission(perm()) == android.content.pm.PackageManager.PERMISSION_GRANTED; }

    private void query() {
        vids.clear();
        Cursor cur = null;
        try {
            cur = getContext().getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE},
                    null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
            while (cur != null && cur.moveToNext() && vids.size() < 3000) {
                Vid v = new Vid();
                v.uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cur.getLong(0));
                v.title = cur.getString(1);
                v.dur = cur.getInt(2);
                v.size = cur.getLong(3);
                vids.add(v);
            }
        } catch (Exception ignored) {
        } finally { if (cur != null) cur.close(); }
        loaded = true;
        fill();
    }

    private void fill() {
        if (list == null) return;
        ArrayList<WpUi.Item> items = new ArrayList<>();
        for (Vid v : vids) {
            WpUi.Item it = new WpUi.Item(v.title == null ? "video" : v.title).sub(MusicApp.dur(v.dur) + " · " + (v.size / 1048576) + " MB").tag(v);
            Bitmap b = Thumbs.get(getContext(), v.uri, 192, new Runnable() { @Override public void run() { fill(); } });
            if (b != null) it.bmp(b, Theme.accent); else it.glyph(Glyph.VIDEO, Theme.accent);
            items.add(it);
        }
        if (items.isEmpty()) items.add(new WpUi.Item(loaded ? "no videos found" : "loading…").dim());
        list.setItems(items);
    }

    private void showList() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "movies & tv", "videos"));
        if (!has()) {
            android.widget.TextView t = WpUi.label(c, "Allow access to your videos to see them here.", 19, Theme.light, Theme.fg);
            t.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(18));
            col.addView(t);
            android.widget.TextView b = WpUi.button(c, "allow");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(160), Theme.dp(46));
            lp.leftMargin = Theme.dp(18);
            col.addView(b, lp);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    host.requestPermissions(new String[]{perm()}, new Runnable() { @Override public void run() { replacePage(new Runnable() { @Override public void run() { showList(); } }); } });
                }
            });
            swapContent(col, null);
            return;
        }
        list = new WpUi.WpList(c);
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        list.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof Vid) { final Vid v = (Vid) it.tag; go(new Runnable() { @Override public void run() { showPlayer(v); } }); }
            }
        });
        AppBar ab = new AppBar(c).items(new AppBar.Item(Glyph.REFRESH, "refresh", new Runnable() { @Override public void run() { query(); } }));
        swapContent(col, ab);
        if (loaded) fill(); else query();
    }

    // ------------------------------------------------------------------ reproductor

    private void showPlayer(final Vid v) {
        final Context c = getContext();
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0xFF000000);
        vv = new VideoView(c);
        root.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        final LinearLayout ctl = new LinearLayout(c);
        ctl.setOrientation(LinearLayout.VERTICAL);
        ctl.setBackgroundColor(0xAA000000);
        ctl.setPadding(Theme.dp(18), Theme.dp(8), Theme.dp(18), Theme.dp(10));
        final android.widget.TextView time = WpUi.label(c, "0:00", 14, Theme.light, 0xFFFFFFFF);
        final WpControls.Slider seek = new WpControls.Slider(c, 0f, true, new WpControls.Slider.OnValue() {
            @Override public void onValue(float f, boolean done) { if (done && vv != null && vv.getDuration() > 0) vv.seekTo((int) (f * vv.getDuration())); }
        });
        ctl.addView(seek, new LinearLayout.LayoutParams(-1, Theme.dp(40)));
        ctl.addView(time);
        root.addView(ctl, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        final android.widget.TextView title = WpUi.label(c, v.title, 18, Theme.light, 0xFFFFFFFF);
        title.setPadding(Theme.dp(18), Theme.dp(12), Theme.dp(18), Theme.dp(12));
        title.setBackgroundColor(0xAA000000);
        root.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        root.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View x) {
                int vis = ctl.getVisibility() == VISIBLE ? GONE : VISIBLE;
                ctl.setVisibility(vis);
                title.setVisibility(vis);
            }
        });
        vv.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { if (vv.isPlaying()) vv.pause(); else vv.start(); } });
        vv.setVideoURI(v.uri);
        vv.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(android.media.MediaPlayer mp) { vv.start(); }
        });
        vv.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(android.media.MediaPlayer mp) { popPage(); }
        });
        h.removeCallbacksAndMessages(null);
        h.post(new Runnable() {
            @Override public void run() {
                if (vv == null) return;
                int d = vv.getDuration(), p = vv.getCurrentPosition();
                if (d > 0) { seek.setValue(p / (float) d); time.setText(MusicApp.dur(p) + " / " + MusicApp.dur(d)); }
                h.postDelayed(this, 500);
            }
        });
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.PLAY, "play/pause", new Runnable() { @Override public void run() { if (vv.isPlaying()) vv.pause(); else vv.start(); } }),
                new AppBar.Item(Glyph.SHARE, "share", new Runnable() { @Override public void run() {
                    Intent s = new Intent(Intent.ACTION_SEND).setType("video/*").putExtra(Intent.EXTRA_STREAM, v.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    host.launch(Intent.createChooser(s, "Share"));
                } })
        );
        swapContent(root, ab);
    }

    @Override public boolean onBack() {
        if (vv != null) { vv.stopPlayback(); vv = null; h.removeCallbacksAndMessages(null); }
        return super.onBack();
    }
}
