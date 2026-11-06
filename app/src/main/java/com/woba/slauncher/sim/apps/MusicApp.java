package com.woba.slauncher.sim.apps;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.LinearLayout;

import com.woba.slauncher.sim.MusicPlayer;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.Thumbs;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpControls;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Groove Música: canciones, álbumes y artistas reales (MediaStore) con reproductor "Now playing". */
public class MusicApp extends SimApp implements MusicPlayer.Listener {
    private final ArrayList<MusicPlayer.Track> tracks = new ArrayList<>();
    private boolean loaded;
    private WpUi.WpList songs, albums, artists;
    private NowPlaying np;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String filterKey, filterVal;

    public MusicApp(Context c, SimHost host) {
        super(c, host, "music");
        MusicPlayer.addListener(this);
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "music"; }

    @Override public void onDestroy() { MusicPlayer.removeListener(this); h.removeCallbacksAndMessages(null); }

    @Override public void onPlayerChanged() { if (np != null) np.invalidate(); }

    @Override public void onShow() { if (!loaded && has()) query(); }

    private String perm() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE; }

    private boolean has() { return getContext().checkSelfPermission(perm()) == android.content.pm.PackageManager.PERMISSION_GRANTED; }

    private void query() {
        tracks.clear();
        Cursor cur = null;
        try {
            cur = getContext().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID},
                    MediaStore.Audio.Media.IS_MUSIC + "!=0 AND " + MediaStore.Audio.Media.DURATION + ">10000", null, MediaStore.Audio.Media.TITLE + " COLLATE LOCALIZED ASC");
            while (cur != null && cur.moveToNext() && tracks.size() < 8000) {
                MusicPlayer.Track t = new MusicPlayer.Track();
                t.id = cur.getLong(0);
                t.title = cur.getString(1);
                t.artist = cur.getString(2);
                t.album = cur.getString(3);
                t.duration = cur.getInt(4);
                t.albumId = cur.getLong(5);
                t.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, t.id);
                if ("<unknown>".equals(t.artist)) t.artist = "Unknown artist";
                tracks.add(t);
            }
        } catch (Exception ignored) {
        } finally { if (cur != null) cur.close(); }
        loaded = true;
        fill();
    }

    private void fill() {
        if (songs == null) return;
        ArrayList<WpUi.Item> si = new ArrayList<>();
        String last = "";
        for (MusicPlayer.Track t : tracks) {
            String l = t.title == null || t.title.isEmpty() ? "#" : t.title.substring(0, 1).toUpperCase();
            if (!Character.isLetter(l.charAt(0))) l = "#";
            if (!l.equals(last)) { si.add(WpUi.Item.section(l)); last = l; }
            si.add(new WpUi.Item(t.title).sub(t.artist + " · " + t.album).right(dur(t.duration)).tag(t));
        }
        if (tracks.isEmpty()) si.add(new WpUi.Item(loaded ? "no music found" : "loading…").dim());
        songs.setItems(si);
        LinkedHashMap<String, ArrayList<MusicPlayer.Track>> al = new LinkedHashMap<>(), ar = new LinkedHashMap<>();
        for (MusicPlayer.Track t : tracks) {
            put(al, t.album == null ? "Unknown album" : t.album, t);
            put(ar, t.artist == null ? "Unknown artist" : t.artist, t);
        }
        albums.setItems(group(al, "album"));
        artists.setItems(group(ar, "artist"));
    }

    private void put(LinkedHashMap<String, ArrayList<MusicPlayer.Track>> m, String k, MusicPlayer.Track t) {
        ArrayList<MusicPlayer.Track> l = m.get(k);
        if (l == null) { l = new ArrayList<>(); m.put(k, l); }
        l.add(t);
    }

    private ArrayList<WpUi.Item> group(LinkedHashMap<String, ArrayList<MusicPlayer.Track>> m, final String kind) {
        ArrayList<WpUi.Item> out = new ArrayList<>();
        for (Map.Entry<String, ArrayList<MusicPlayer.Track>> e : m.entrySet()) {
            WpUi.Item it = new WpUi.Item(e.getKey()).sub(e.getValue().size() + " songs").tag(new String[]{kind, e.getKey()});
            Bitmap b = Thumbs.get(getContext(), e.getValue().get(0).uri, 192, new Runnable() { @Override public void run() { fill(); } });
            if (b != null) it.bmp(b, Theme.accent); else it.glyph(Glyph.MUSIC, Theme.accent);
            out.add(it);
        }
        if (out.isEmpty()) out.add(new WpUi.Item("nothing here").dim());
        return out;
    }

    static String dur(int ms) { int s = ms / 1000; return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60); }

    private void showMain() {
        final Context c = getContext();
        if (!has()) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(WpUi.pageHeader(c, "music", "music"));
            android.widget.TextView t = WpUi.label(c, "Allow access to your music to see it here.", 19, Theme.light, Theme.fg);
            t.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(18));
            col.addView(t);
            android.widget.TextView b = WpUi.button(c, "allow");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(160), Theme.dp(46));
            lp.leftMargin = Theme.dp(18);
            col.addView(b, lp);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    host.requestPermissions(new String[]{perm()}, new Runnable() { @Override public void run() { replacePage(new Runnable() { @Override public void run() { showMain(); } }); } });
                }
            });
            swapContent(col, null);
            return;
        }
        songs = new WpUi.WpList(c);
        albums = new WpUi.WpList(c);
        artists = new WpUi.WpList(c);
        songs.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof MusicPlayer.Track) { MusicPlayer.play(getContext(), tracks, tracks.indexOf(it.tag)); openNow(); }
            }
        });
        WpUi.OnItem grp = new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof String[]) {
                    String[] t = (String[]) it.tag;
                    filterKey = t[0]; filterVal = t[1];
                    go(new Runnable() { @Override public void run() { showGroup(); } });
                }
            }
        };
        albums.setOnItem(grp); artists.setOnItem(grp);
        Pivot pv = new Pivot(c, "music", new String[]{"songs", "albums", "artists"}, new View[]{songs, albums, artists});
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.PLAY, "now playing", new Runnable() { @Override public void run() { openNow(); } }),
                new AppBar.Item(Glyph.SHUFFLE, "shuffle all", new Runnable() {
                    @Override public void run() { if (!tracks.isEmpty()) { MusicPlayer.shuffle = true; MusicPlayer.play(getContext(), tracks, (int) (Math.random() * tracks.size())); openNow(); } } }),
                new AppBar.Item(Glyph.REFRESH, "refresh", new Runnable() { @Override public void run() { query(); } })
        );
        swapContent(pv, ab);
        if (loaded) fill(); else query();
    }

    private void showGroup() {
        Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "music", filterVal));
        WpUi.WpList l = new WpUi.WpList(c);
        final ArrayList<MusicPlayer.Track> sel = new ArrayList<>();
        ArrayList<WpUi.Item> items = new ArrayList<>();
        for (MusicPlayer.Track t : tracks) if ((filterKey.equals("album") ? t.album : t.artist) != null && (filterKey.equals("album") ? t.album : t.artist).equals(filterVal)) {
            sel.add(t);
            items.add(new WpUi.Item(t.title).sub(t.artist).right(dur(t.duration)).tag(t));
        }
        l.setItems(items);
        l.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof MusicPlayer.Track) { MusicPlayer.play(getContext(), sel, sel.indexOf(it.tag)); openNow(); }
            }
        });
        col.addView(l, new LinearLayout.LayoutParams(-1, 0, 1f));
        swapContent(col, null);
    }

    private void openNow() {
        if (np != null && hasPages() && false) return;
        go(new Runnable() { @Override public void run() { showNow(); } });
    }

    // ------------------------------------------------------------------ reproduciendo

    private void showNow() {
        final Context c = getContext();
        np = new NowPlaying(c);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.PREV, "previous", new Runnable() { @Override public void run() { MusicPlayer.prev(); } }),
                new AppBar.Item(MusicPlayer.isPlaying() ? Glyph.PAUSE : Glyph.PLAY, "play", new Runnable() { @Override public void run() { MusicPlayer.toggle(); } }),
                new AppBar.Item(Glyph.NEXT, "next", new Runnable() { @Override public void run() { MusicPlayer.next(); } })
        ).menu(
                new AppBar.Item(0, "shuffle: " + (MusicPlayer.shuffle ? "on" : "off"), new Runnable() { @Override public void run() { MusicPlayer.shuffle = !MusicPlayer.shuffle; showNow(); } }),
                new AppBar.Item(0, "repeat: " + (MusicPlayer.repeat == 0 ? "off" : MusicPlayer.repeat == 1 ? "all" : "one"), new Runnable() { @Override public void run() { MusicPlayer.repeat = (MusicPlayer.repeat + 1) % 3; showNow(); } }),
                new AppBar.Item(0, "stop", new Runnable() { @Override public void run() { MusicPlayer.stop(); popPage(); } })
        );
        swapContent(np, ab);
        h.removeCallbacksAndMessages(null);
        h.post(new Runnable() { @Override public void run() { if (np != null) { np.invalidate(); h.postDelayed(this, 500); } } });
    }

    private class NowPlaying extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean dragging;
        Bitmap art;
        long artFor = -1;

        NowPlaying(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight();
            MusicPlayer.Track t = MusicPlayer.current();
            p.setTypeface(Theme.semibold);
            p.setTextSize(Theme.sp(13));
            p.setColor(Theme.fg);
            c.drawText("MUSIC", Theme.dpf(18), Theme.dpf(30), p);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(34));
            c.drawText("now playing", Theme.dpf(18), Theme.dpf(76), p);
            float s = Math.min(w - Theme.dpf(60), hh * 0.42f);
            float x = (w - s) / 2f, y = Theme.dpf(96);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.accent);
            c.drawRect(x, y, x + s, y + s, p);
            if (t != null && artFor != t.id) {
                artFor = t.id;
                art = Thumbs.get(getContext(), t.uri, 512, new Runnable() { @Override public void run() { art = Thumbs.get(getContext(), MusicPlayer.current().uri, 512, null); invalidate(); } });
            }
            if (art != null) c.drawBitmap(art, null, new android.graphics.RectF(x, y, x + s, y + s), p);
            else Glyph.draw(c, Glyph.MUSIC, x + s / 2f, y + s / 2f, s * 0.4f, 0xFFFFFFFF, Theme.dpf(3));
            float ty = y + s + Theme.dpf(34);
            p.setColor(Theme.fg);
            p.setTextSize(Theme.sp(24));
            String title = t == null ? "nothing playing" : t.title;
            c.drawText(android.text.TextUtils.ellipsize(title, new android.text.TextPaint(p), w - Theme.dpf(36), android.text.TextUtils.TruncateAt.END).toString(), Theme.dpf(18), ty, p);
            p.setColor(Theme.subtle);
            p.setTextSize(Theme.sp(16));
            if (t != null) c.drawText(android.text.TextUtils.ellipsize(t.artist + " — " + t.album, new android.text.TextPaint(p), w - Theme.dpf(36), android.text.TextUtils.TruncateAt.END).toString(), Theme.dpf(18), ty + Theme.dpf(26), p);
            // barra de progreso
            float by = ty + Theme.dpf(56), bx0 = Theme.dpf(18), bx1 = w - Theme.dpf(18);
            int dur = MusicPlayer.duration(), pos = MusicPlayer.position();
            float f = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;
            p.setColor(Theme.alpha(Theme.fg, 0.3f));
            c.drawRect(bx0, by - Theme.dpf(1.5f), bx1, by + Theme.dpf(1.5f), p);
            p.setColor(Theme.accent);
            c.drawRect(bx0, by - Theme.dpf(3), bx0 + (bx1 - bx0) * f, by + Theme.dpf(3), p);
            p.setColor(Theme.fg);
            c.drawRect(bx0 + (bx1 - bx0) * f - Theme.dpf(5), by - Theme.dpf(13), bx0 + (bx1 - bx0) * f + Theme.dpf(5), by + Theme.dpf(13), p);
            p.setColor(Theme.subtle);
            p.setTextSize(Theme.sp(13));
            c.drawText(dur(pos), bx0, by + Theme.dpf(32), p);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(dur(dur), bx1, by + Theme.dpf(32), p);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText((MusicPlayer.shuffle ? "shuffle  " : "") + (MusicPlayer.repeat == 1 ? "repeat all" : MusicPlayer.repeat == 2 ? "repeat one" : ""), bx0 + Theme.dpf(70), by + Theme.dpf(32), p);
        }

        float barY() { return 0; }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            float w = getWidth();
            float s = Math.min(w - Theme.dpf(60), getHeight() * 0.42f);
            float by = Theme.dpf(96) + s + Theme.dpf(34) + Theme.dpf(56);
            if (Math.abs(e.getY() - by) < Theme.dpf(34) || dragging) {
                float f = Math.max(0f, Math.min(1f, (e.getX() - Theme.dpf(18)) / (w - Theme.dpf(36))));
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) { dragging = false; MusicPlayer.seek((int) (f * MusicPlayer.duration())); }
                else { dragging = true; if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); }
                invalidate();
            }
            return true;
        }
    }
}
