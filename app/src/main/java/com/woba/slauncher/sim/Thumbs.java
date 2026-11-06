package com.woba.slauncher.sim;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;

import java.io.InputStream;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Caché de miniaturas y carga de imágenes grandes fuera del hilo UI. */
public final class Thumbs {
    private Thumbs() {}

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };
    private static final ExecutorService EX = Executors.newFixedThreadPool(3);
    private static final HashSet<String> PENDING = new HashSet<>();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** Devuelve la miniatura si está en caché; si no, la carga y llama a onLoaded en el hilo UI. */
    public static Bitmap get(final Context c, final Uri uri, final int size, final Runnable onLoaded) {
        final String key = uri + "@" + size;
        Bitmap b = CACHE.get(key);
        if (b != null) return b;
        synchronized (PENDING) {
            if (!PENDING.add(key)) return null;
        }
        final Context app = c.getApplicationContext();
        EX.execute(new Runnable() {
            @Override public void run() {
                Bitmap r = null;
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 29) r = app.getContentResolver().loadThumbnail(uri, new Size(size, size), null);
                    else r = decode(app, uri, size);
                } catch (Throwable ignored) {
                    try { r = decode(app, uri, size); } catch (Throwable ignored2) {}
                }
                if (r != null) CACHE.put(key, r);
                synchronized (PENDING) { PENDING.remove(key); }
                if (r != null && onLoaded != null) UI.post(onLoaded);
            }
        });
        return null;
    }

    public interface Result { void on(Bitmap b); }

    /** Decodifica una imagen a como mucho max px de lado, respetando la rotación EXIF. */
    public static void load(final Context c, final Uri uri, final int max, final Result cb) {
        final Context app = c.getApplicationContext();
        EX.execute(new Runnable() {
            @Override public void run() {
                Bitmap b = null;
                try { b = decode(app, uri, max); } catch (Throwable ignored) {}
                final Bitmap fb = b;
                UI.post(new Runnable() { @Override public void run() { cb.on(fb); } });
            }
        });
    }

    public static Bitmap decode(Context c, Uri uri, int max) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = c.getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, o);
        if (in != null) in.close();
        int s = 1;
        while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        in = c.getContentResolver().openInputStream(uri);
        Bitmap b = BitmapFactory.decodeStream(in, null, o);
        if (in != null) in.close();
        if (b == null) return null;
        int rot = 0;
        try {
            in = c.getContentResolver().openInputStream(uri);
            android.media.ExifInterface ex = new android.media.ExifInterface(in);
            int or = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            rot = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
            if (in != null) in.close();
        } catch (Throwable ignored) {}
        int big = Math.max(b.getWidth(), b.getHeight());
        float sc = big > max ? (float) max / big : 1f;
        if (rot != 0 || sc < 1f) {
            Matrix m = new Matrix();
            m.postRotate(rot);
            m.postScale(sc, sc);
            Bitmap t = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (t != b) b.recycle();
            b = t;
        }
        return b;
    }
}
