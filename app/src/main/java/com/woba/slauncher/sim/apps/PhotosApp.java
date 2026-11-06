package com.woba.slauncher.sim.apps;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.sim.Phone;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.sim.Thumbs;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Pivot;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fotos: colección y álbumes reales (MediaStore), visor con zoom, favoritas, compartir y fondo de pantalla. */
public class PhotosApp extends SimApp {
    static class Img {
        long id;
        Uri uri;
        long date;
        String bucket, name;
    }

    private final ArrayList<Img> imgs = new ArrayList<>();
    private boolean loaded, denied;
    private WpUi.WpList albumList;
    private Grid collection, favGrid, albumGrid;
    private ArrayList<Img> shown = new ArrayList<>();
    private String albumName;

    public PhotosApp(Context c, SimHost host) {
        super(c, host, "photos");
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "photos"; }

    private String perm() { return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE; }

    private boolean has() { return getContext().checkSelfPermission(perm()) == android.content.pm.PackageManager.PERMISSION_GRANTED; }

    @Override public void onShow() { if (!loaded && has()) query(); }

    private void query() {
        imgs.clear();
        Cursor cur = null;
        try {
            cur = getContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DISPLAY_NAME},
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
            while (cur != null && cur.moveToNext() && imgs.size() < 6000) {
                Img i = new Img();
                i.id = cur.getLong(0);
                i.uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, i.id);
                i.bucket = cur.getString(1) == null ? "Other" : cur.getString(1);
                i.date = cur.getLong(2) * 1000L;
                i.name = cur.getString(3);
                imgs.add(i);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        loaded = true;
        fill();
    }

    private boolean isFav(Img i) { return SimStore.str("photo_favs", "").contains("," + i.id + ","); }

    private void toggleFav(Img i) {
        String s = SimStore.str("photo_favs", ",");
        String k = "," + i.id + ",";
        if (s.contains(k)) s = s.replace(k, ","); else s = s + i.id + ",";
        SimStore.putStr("photo_favs", s);
    }

    private void fill() {
        if (collection == null) return;
        collection.set(imgs);
        ArrayList<Img> f = new ArrayList<>();
        for (Img i : imgs) if (isFav(i)) f.add(i);
        favGrid.set(f);
        LinkedHashMap<String, ArrayList<Img>> al = albums();
        ArrayList<WpUi.Item> items = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Img>> e : al.entrySet()) {
            WpUi.Item it = new WpUi.Item(e.getKey()).sub(e.getValue().size() + " items").tag(e.getKey());
            it.bmp = Thumbs.get(getContext(), e.getValue().get(0).uri, 160, new Runnable() { @Override public void run() { fill(); } });
            it.avatarColor = Theme.accent;
            if (it.bmp == null) it.glyph = Glyph.PHOTOS;
            items.add(it);
        }
        if (items.isEmpty()) items.add(new WpUi.Item("no albums").dim());
        albumList.setItems(items);
    }

    private LinkedHashMap<String, ArrayList<Img>> albums() {
        LinkedHashMap<String, ArrayList<Img>> m = new LinkedHashMap<>();
        for (Img i : imgs) {
            ArrayList<Img> l = m.get(i.bucket);
            if (l == null) { l = new ArrayList<>(); m.put(i.bucket, l); }
            l.add(i);
        }
        return m;
    }

    private void showMain() {
        final Context c = getContext();
        if (!has()) { showPermission(); return; }
        collection = new Grid(c);
        favGrid = new Grid(c);
        albumList = new WpUi.WpList(c);
        albumList.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof String)) return;
                albumName = (String) it.tag;
                go(new Runnable() { @Override public void run() { showAlbum(); } });
            }
        });
        Pivot pv = new Pivot(c, "photos", new String[]{"collection", "albums", "favorites"}, new View[]{collection, albumList, favGrid});
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.CAMERA, "camera", new Runnable() { @Override public void run() { openCamera(); } }),
                new AppBar.Item(Glyph.REFRESH, "refresh", new Runnable() { @Override public void run() { query(); } })
        );
        swapContent(pv, ab);
        if (loaded) fill(); else query();
    }

    private void openCamera() {
        host.openSim("camera", null);
    }

    private void showPermission() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "photos", "photos"));
        android.widget.TextView t = WpUi.label(c, "Allow access to your photos to see them here.", 19, Theme.light, Theme.fg);
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
    }

    private void showAlbum() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "photos", albumName));
        albumGrid = new Grid(c);
        col.addView(albumGrid, new LinearLayout.LayoutParams(-1, 0, 1f));
        ArrayList<Img> l = albums().get(albumName);
        albumGrid.set(l == null ? new ArrayList<Img>() : l);
        swapContent(col, null);
    }

    // ------------------------------------------------------------------ rejilla

    private class Grid extends WpUi.WpList {
        ArrayList<Img> data = new ArrayList<>();

        Grid(Context c) {
            super(c);
            setOnItem(new WpUi.OnItem() {
                @Override public void on(WpUi.Item it, int pos, float x, float y) {
                    if (!(it.tag instanceof int[])) return;
                    int col = (int) (x / (getWidth() / 3f));
                    int[] t = (int[]) it.tag;
                    int idx = t[0] + Math.max(0, Math.min(2, col));
                    if (idx < data.size()) {
                        final ArrayList<Img> snapshot = data;
                        final int at = idx;
                        shown = snapshot;
                        go(new Runnable() { @Override public void run() { showViewer(at); } });
                    }
                }
            });
        }

        void set(ArrayList<Img> list) {
            data = list;
            ArrayList<WpUi.Item> items = new ArrayList<>();
            if (list.isEmpty()) items.add(new WpUi.Item(loaded ? "no photos" : "loading…").dim());
            for (int i = 0; i < list.size(); i += 3) {
                WpUi.Item it = new WpUi.Item("");
                it.type = WpUi.T_SPACER;
                it.tag = new int[]{i};
                it.heightDp = -1;
                items.add(it);
            }
            setItems(items);
        }

        @Override public void setItems(java.util.List<WpUi.Item> list) {
            super.setItems(list);
            setAdapter(adapterFor());
        }

        private android.widget.BaseAdapter adapterFor() {
            final java.util.List<WpUi.Item> its = getItems();
            return new android.widget.BaseAdapter() {
                @Override public int getCount() { return its.size(); }
                @Override public Object getItem(int p) { return its.get(p); }
                @Override public long getItemId(int p) { return p; }
                @Override public View getView(int p, View cv, android.view.ViewGroup parent) {
                    ThumbRow r = cv instanceof ThumbRow ? (ThumbRow) cv : new ThumbRow(parent.getContext());
                    r.bind(its.get(p));
                    return r;
                }
            };
        }

        class ThumbRow extends View {
            WpUi.Item it;
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            ThumbRow(Context c) { super(c); }
            void bind(WpUi.Item i) { it = i; requestLayout(); invalidate(); }

            @Override protected void onMeasure(int w, int h) {
                int width = MeasureSpec.getSize(w);
                boolean grid = it != null && it.tag instanceof int[];
                setMeasuredDimension(width, grid ? width / 3 : Theme.dp(60));
            }

            @Override protected void onDraw(Canvas c) {
                if (it == null) return;
                if (!(it.tag instanceof int[])) {
                    p.setColor(Theme.subtle);
                    p.setTypeface(Theme.light);
                    p.setTextSize(Theme.sp(20));
                    c.drawText(it.title, Theme.dpf(18), Theme.centerBaseline(p, getHeight() / 2f), p);
                    return;
                }
                int base = ((int[]) it.tag)[0];
                float s = getWidth() / 3f;
                for (int k = 0; k < 3; k++) {
                    int idx = base + k;
                    if (idx >= data.size()) break;
                    Img im = data.get(idx);
                    Bitmap b = Thumbs.get(getContext(), im.uri, 256, new Runnable() { @Override public void run() { notifyChanged(); } });
                    float x = k * s;
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(WpUi.mix(Theme.bg, Theme.fg, 0.18f));
                    c.drawRect(x + 1, 1, x + s - 1, s - 1, p);
                    if (b != null) {
                        int sq = Math.min(b.getWidth(), b.getHeight());
                        android.graphics.Rect src = new android.graphics.Rect((b.getWidth() - sq) / 2, (b.getHeight() - sq) / 2, (b.getWidth() + sq) / 2, (b.getHeight() + sq) / 2);
                        c.drawBitmap(b, src, new RectF(x + 1, 1, x + s - 1, s - 1), p);
                    }
                    if (isFav(im)) Glyph.draw(c, Glyph.STAR_FILL, x + s - Theme.dpf(14), Theme.dpf(14), Theme.dpf(14), Theme.accent);
                }
            }
        }
    }

    // ------------------------------------------------------------------ visor

    private int viewIndex;
    private Zoom zoom;

    private void showViewer(int idx) {
        final Context c = getContext();
        viewIndex = idx;
        zoom = new Zoom(c);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.STAR, "favorite", new Runnable() { @Override public void run() {
                    Img i = cur(); if (i == null) return; toggleFav(i); Toast.makeText(getContext(), isFav(i) ? "Added to favorites" : "Removed from favorites", Toast.LENGTH_SHORT).show(); fill();
                } }),
                new AppBar.Item(Glyph.SHARE, "share", new Runnable() { @Override public void run() { share(); } }),
                new AppBar.Item(Glyph.DELETE, "delete", new Runnable() { @Override public void run() { delete(); } })
        ).menu(
                new AppBar.Item(0, "set as wallpaper", new Runnable() { @Override public void run() { setWallpaper(); } }),
                new AppBar.Item(0, "set as lock screen background", new Runnable() { @Override public void run() { setLockBg(); } }),
                new AppBar.Item(0, "info", new Runnable() { @Override public void run() { info(); } })
        );
        swapContent(zoom, ab);
        zoom.show(cur());
    }

    private Img cur() { return viewIndex >= 0 && viewIndex < shown.size() ? shown.get(viewIndex) : null; }

    private void share() {
        Img i = cur();
        if (i == null) return;
        Intent s = new Intent(Intent.ACTION_SEND);
        s.setType("image/*");
        s.putExtra(Intent.EXTRA_STREAM, i.uri);
        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        host.launch(Intent.createChooser(s, "Share"));
    }

    private void delete() {
        final Img i = cur();
        if (i == null) return;
        showDialog("delete", "Delete this photo?", new String[]{"delete", "cancel"}, new Choice() {
            @Override public void on(int k) {
                if (k != 0) return;
                try {
                    int n = getContext().getContentResolver().delete(i.uri, null, null);
                    if (n > 0) {
                        imgs.remove(i);
                        shown.remove(i);
                        fill();
                        if (shown.isEmpty()) popPage(); else { viewIndex = Math.min(viewIndex, shown.size() - 1); zoom.show(cur()); }
                    } else Toast.makeText(getContext(), "Couldn't delete", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Android won't let this app delete that photo", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void setWallpaper() {
        Img i = cur();
        if (i == null) return;
        com.woba.slauncher.data.Wallpaper.importAsync(getContext(), i.uri, true, new Runnable() {
            @Override public void run() { Toast.makeText(getContext(), "Start background updated", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void setLockBg() {
        Img i = cur();
        if (i == null) return;
        com.woba.slauncher.data.Wallpaper.importAsync(getContext(), i.uri, false, new Runnable() {
            @Override public void run() { Toast.makeText(getContext(), "Lock screen background updated", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void info() {
        Img i = cur();
        if (i == null) return;
        showDialog("info", i.name + "\n" + i.bucket + "\n" + Phone.full(i.date), new String[]{"ok"}, null);
    }

    private class Zoom extends View {
        Bitmap bmp;
        final Matrix m = new Matrix();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float scale = 1f, minScale = 1f;
        final ScaleGestureDetector sg;
        final GestureDetector gd;
        float tx, ty;
        String loadingFor;

        Zoom(Context c) {
            super(c);
            setBackgroundColor(0xFF000000);
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    float f = d.getScaleFactor();
                    float ns = Math.max(minScale, Math.min(minScale * 8, scale * f));
                    f = ns / scale;
                    scale = ns;
                    tx = d.getFocusX() - (d.getFocusX() - tx) * f;
                    ty = d.getFocusY() - (d.getFocusY() - ty) * f;
                    clamp();
                    invalidate();
                    return true;
                }
            });
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDoubleTap(MotionEvent e) {
                    if (scale > minScale * 1.05f) fit(); else {
                        float ns = minScale * 3f, f = ns / scale;
                        scale = ns;
                        tx = e.getX() - (e.getX() - tx) * f;
                        ty = e.getY() - (e.getY() - ty) * f;
                        clamp();
                    }
                    invalidate();
                    return true;
                }

                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    if (scale > minScale * 1.02f) { tx -= dx; ty -= dy; clamp(); invalidate(); return true; }
                    return false;
                }

                @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                    if (scale <= minScale * 1.02f && Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > 900) { step(vx < 0 ? 1 : -1); return true; }
                    return false;
                }
            });
        }

        void step(int d) {
            int n = viewIndex + d;
            if (n < 0 || n >= shown.size()) return;
            viewIndex = n;
            final int dir = d;
            animate().translationX(-dir * getWidth() * 0.3f).alpha(0f).setDuration(110).withEndAction(new Runnable() {
                @Override public void run() {
                    show(cur());
                    setTranslationX(dir * getWidth() * 0.3f);
                    animate().translationX(0).alpha(1f).setDuration(140).start();
                }
            }).start();
        }

        void show(Img i) {
            bmp = null;
            invalidate();
            if (i == null) return;
            final String key = String.valueOf(i.id);
            loadingFor = key;
            Thumbs.load(getContext(), i.uri, 2048, new Thumbs.Result() {
                @Override public void on(Bitmap b) {
                    if (!key.equals(loadingFor)) return;
                    bmp = b;
                    fit();
                    invalidate();
                }
            });
        }

        void fit() {
            if (bmp == null || getWidth() == 0) return;
            minScale = Math.min((float) getWidth() / bmp.getWidth(), (float) getHeight() / bmp.getHeight());
            scale = minScale;
            tx = (getWidth() - bmp.getWidth() * scale) / 2f;
            ty = (getHeight() - bmp.getHeight() * scale) / 2f;
        }

        void clamp() {
            if (bmp == null) return;
            float w = bmp.getWidth() * scale, h = bmp.getHeight() * scale;
            tx = w <= getWidth() ? (getWidth() - w) / 2f : Math.max(getWidth() - w, Math.min(0, tx));
            ty = h <= getHeight() ? (getHeight() - h) / 2f : Math.max(getHeight() - h, Math.min(0, ty));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { fit(); }

        @Override protected void onDraw(Canvas c) {
            if (bmp == null) {
                p.setColor(Theme.subtle);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(Theme.sp(16));
                c.drawText(loadingFor == null ? "" : "loading…", getWidth() / 2f, getHeight() / 2f, p);
                p.setTextAlign(Paint.Align.LEFT);
                return;
            }
            m.reset();
            m.postScale(scale, scale);
            m.postTranslate(tx, ty);
            c.drawBitmap(bmp, m, p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            sg.onTouchEvent(e);
            if (!sg.isInProgress()) gd.onTouchEvent(e);
            return true;
        }
    }
}
