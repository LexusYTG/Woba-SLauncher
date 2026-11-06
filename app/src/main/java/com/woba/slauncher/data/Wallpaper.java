package com.woba.slauncher.data;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Fondos de pantalla del Start y de la lock screen: copia propia en filesDir. */
public final class Wallpaper {
    private Wallpaper() {}

    public static Bitmap start, lock;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Rect SRC = new Rect(), DST = new Rect();

    private static File file(Context c, boolean forStart) {
        return new File(c.getFilesDir(), forStart ? "wall_start.jpg" : "wall_lock.jpg");
    }

    public static void load(Context c) {
        start = decodeFile(file(c, true));
        lock = decodeFile(file(c, false));
    }

    private static Bitmap decodeFile(File f) {
        try {
            return f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void clear(Context c, boolean forStart) {
        file(c, forStart).delete();
        if (forStart) start = null;
        else lock = null;
    }

    /** Lee la imagen elegida fuera del hilo UI, la guarda y avisa (hilo UI) al terminar. */
    public static void importAsync(Context ctx, final Uri uri, final boolean forStart, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = decodeUri(app, uri);
                boolean saved = false;
                if (b != null) {
                    try (FileOutputStream out = new FileOutputStream(file(app, forStart))) {
                        saved = b.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    } catch (Exception ignored) {
                    }
                }
                final boolean ok = saved;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            Toast.makeText(app, "Could not open that image", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (forStart) start = b;
                        else lock = b;
                        done.run();
                    }
                });
            }
        }).start();
    }

    private static Bitmap decodeUri(Context c, Uri uri) {
        try {
            DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int longSide = Math.max(dm.widthPixels, dm.heightPixels);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= longSide) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Dibuja la imagen rellenando w x h, recortando lo que sobre (center-crop). */
    public static void drawCrop(Canvas c, Bitmap b, int w, int h, Paint p) {
        float s = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        float sw = w / s, sh = h / s;
        SRC.set((int) ((b.getWidth() - sw) / 2f), (int) ((b.getHeight() - sh) / 2f),
                (int) ((b.getWidth() + sw) / 2f), (int) ((b.getHeight() + sh) / 2f));
        DST.set(0, 0, w, h);
        c.drawBitmap(b, SRC, DST, p);
    }

    /** Aplica la imagen como fondo real de Android (FLAG_SYSTEM = inicio, FLAG_LOCK = bloqueo). */
    public static void applySystem(final Context c, final Bitmap b, final int which) {
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok;
                try {
                    WallpaperManager.getInstance(app).setBitmap(b, null, true, which);
                    ok = true;
                } catch (Exception e) {
                    ok = false;
                }
                final boolean r = ok;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(app, r ? "Android wallpaper updated" : "Could not set the wallpaper",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
}
