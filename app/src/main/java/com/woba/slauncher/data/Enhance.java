package com.woba.slauncher.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * Fotografía computacional sencilla, sin dependencias (la idea de "Rich Capture" de Lumia):
 *  - HDR: varias fotos con distinta exposición se funden píxel a píxel con los pesos de Mertens
 *    (contraste, saturación y buena exposición) para recuperar luces y sombras.
 *  - NOCHE: varias fotos con la misma exposición se promedian (el ruido baja ~ raíz de N) rechazando lo que se movió.
 *  - Antes de fundir se alinean las fotos (movimiento de la mano) y se trabaja a una resolución menor que la del
 *    sensor, lo que de por sí reduce ruido (sobremuestreo, como PureView).
 *  - Después: niveles automáticos, curva suave, algo de saturación y máscara de enfoque.
 *
 * No hay pirámides ni alineación sub-píxel: es una versión modesta pero real. Es pesado en memoria: el que llama
 * debe capturar OutOfMemoryError y quedarse con una de las fotos originales.
 */
public final class Enhance {
    private Enhance() {}

    /**
     * @param jpegs     fotos de la misma escena (mismo tamaño), en orden
     * @param hdr       true = fusión de exposiciones; false = promedio para reducir ruido
     * @param maxPixels tope de píxeles del resultado (se reduce por potencias de 2)
     * @return bitmap ya girado según el EXIF, o null si no se pudo
     */
    public static Bitmap process(List<byte[]> jpegs, boolean hdr, int maxPixels) {
        int n = jpegs.size();
        if (n < 2) return null;

        BitmapFactory.Options bo = new BitmapFactory.Options();
        bo.inJustDecodeBounds = true;
        byte[] first = jpegs.get(0);
        BitmapFactory.decodeByteArray(first, 0, first.length, bo);
        if (bo.outWidth <= 0) return null;
        int sample = 1;
        while ((long) (bo.outWidth / sample) * (bo.outHeight / sample) > maxPixels) sample *= 2;

        int ref = n / 2;
        int w = 0, h = 0;
        int[][] px = new int[n][];
        for (int i = 0; i < n; i++) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            byte[] d = jpegs.get(i);
            Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length, o);
            if (b == null) return null;
            if (i == 0) { w = b.getWidth(); h = b.getHeight(); }
            else if (b.getWidth() != w || b.getHeight() != h) { b.recycle(); return null; }
            px[i] = new int[w * h];
            b.getPixels(px[i], 0, w, 0, 0, w, h);
            b.recycle();
        }

        // ---- alinear todas con la de referencia
        for (int i = 0; i < n; i++) {
            if (i == ref) continue;
            int[] s = estimateShift(px[ref], px[i], w, h);
            if (s[0] != 0 || s[1] != 0) px[i] = shift(px[i], w, h, s[0], s[1]);
        }

        // ---- fundir
        if (hdr) fuseHdr(px, w, h);
        else average(px, ref, w, h);

        int[] out = px[ref];
        for (int i = 0; i < n; i++) if (i != ref) px[i] = null;
        finish(out, w, h, hdr);

        Bitmap bmp = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
        int deg = exifDegrees(jpegs.get(ref));
        if (deg != 0) {
            Matrix m = new Matrix();
            m.postRotate(deg);
            Bitmap r = Bitmap.createBitmap(bmp, 0, 0, w, h, m, true);
            bmp.recycle();
            bmp = r;
        }
        return bmp;
    }

    private static int exifDegrees(byte[] jpeg) {
        try {
            int o = new android.media.ExifInterface(new ByteArrayInputStream(jpeg))
                    .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            if (o == android.media.ExifInterface.ORIENTATION_ROTATE_90) return 90;
            if (o == android.media.ExifInterface.ORIENTATION_ROTATE_180) return 180;
            if (o == android.media.ExifInterface.ORIENTATION_ROTATE_270) return 270;
        } catch (Exception ignored) { }
        return 0;
    }

    private static int luma(int c) {
        return (77 * ((c >> 16) & 0xFF) + 150 * ((c >> 8) & 0xFF) + 29 * (c & 0xFF)) >> 8;
    }

    // ------------------------------------------------------------------ alineación

    /** Desplazamiento (dx, dy) tal que cur(x+dx, y+dy) ≈ ref(x, y). Dos niveles: grueso y fino. */
    private static int[] estimateShift(int[] ref, int[] cur, int w, int h) {
        int fC = Math.max(4, w / 256), fF = 2;
        int gw = w / fC, gh = h / fC;
        float[] a = gray(ref, w, h, fC), b = gray(cur, w, h, fC);
        int[] c = search(a, b, gw, gh, 0, 0, 8, 1);
        int cdx = c[0] * fC / fF, cdy = c[1] * fC / fF;

        int fw = w / fF, fh = h / fF;
        float[] a2 = gray(ref, w, h, fF), b2 = gray(cur, w, h, fF);
        int r = fC / fF + 1;
        int[] f = search(a2, b2, fw, fh, cdx, cdy, r, 3);
        return new int[]{f[0] * fF, f[1] * fF};
    }

    private static float[] gray(int[] px, int w, int h, int f) {
        int gw = w / f, gh = h / f, st = Math.max(1, f / 4);
        float[] g = new float[gw * gh];
        double sum = 0;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int s = 0, cnt = 0;
                for (int yy = y * f; yy < y * f + f; yy += st)
                    for (int xx = x * f; xx < x * f + f; xx += st) { s += luma(px[yy * w + xx]); cnt++; }
                float v = (float) s / cnt;
                g[y * gw + x] = v;
                sum += v;
            }
        }
        // media 0 y desviación 1: sirve entre fotos de distinta exposición
        float mean = (float) (sum / g.length);
        double var = 0;
        for (float v : g) var += (v - mean) * (v - mean);
        float sd = (float) Math.sqrt(var / g.length) + 1e-3f;
        for (int i = 0; i < g.length; i++) g[i] = (g[i] - mean) / sd;
        return g;
    }

    private static int[] search(float[] a, float[] b, int gw, int gh, int cx, int cy, int range, int stride) {
        int m = Math.max(Math.abs(cx), Math.abs(cy)) + range + 1;
        if (gw <= 2 * m + 4 || gh <= 2 * m + 4) return new int[]{cx, cy};
        double best = Double.MAX_VALUE;
        int bx = cx, by = cy;
        for (int dy = cy - range; dy <= cy + range; dy++) {
            for (int dx = cx - range; dx <= cx + range; dx++) {
                double s = 0;
                for (int y = m; y < gh - m; y += stride) {
                    int ra = y * gw, rb = (y + dy) * gw + dx;
                    for (int x = m; x < gw - m; x += stride) s += Math.abs(a[ra + x] - b[rb + x]);
                }
                if (s < best) { best = s; bx = dx; by = dy; }
            }
        }
        return new int[]{bx, by};
    }

    private static int[] shift(int[] src, int w, int h, int dx, int dy) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = Math.max(0, Math.min(h - 1, y + dy)) * w;
            for (int x = 0; x < w; x++) out[y * w + x] = src[sy + Math.max(0, Math.min(w - 1, x + dx))];
        }
        return out;
    }

    // ------------------------------------------------------------------ NOCHE: promedio sin fantasmas

    private static void average(int[][] px, int ref, int w, int h) {
        int n = px.length, total = w * h;
        final int T = 18; // diferencia de luma que aún se considera ruido
        int[] r = px[ref];
        for (int p = 0; p < total; p++) {
            int c0 = r[p], l0 = luma(c0);
            float sr = (c0 >> 16) & 0xFF, sg = (c0 >> 8) & 0xFF, sb = c0 & 0xFF, sw = 1f;
            for (int i = 0; i < n; i++) {
                if (i == ref) continue;
                int c = px[i][p];
                int d = Math.abs(luma(c) - l0);
                if (d > 3 * T) continue;               // se movió: no se mezcla
                float wi = d <= T ? 1f : 0.5f;
                sr += wi * ((c >> 16) & 0xFF);
                sg += wi * ((c >> 8) & 0xFF);
                sb += wi * (c & 0xFF);
                sw += wi;
            }
            r[p] = 0xFF000000 | (clamp(Math.round(sr / sw)) << 16) | (clamp(Math.round(sg / sw)) << 8) | clamp(Math.round(sb / sw));
        }
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    // ------------------------------------------------------------------ HDR: fusión de exposiciones

    private static void fuseHdr(int[][] px, int w, int h) {
        int n = px.length, total = w * h;
        float[] lum = new float[total], wgt = new float[total], tmp = new float[total];
        short[][] wt = new short[n][total];
        int rb = Math.max(2, w / 150);

        for (int i = 0; i < n; i++) {
            int[] f = px[i];
            for (int p = 0; p < total; p++) lum[p] = luma(f[p]) / 255f;
            for (int y = 0; y < h; y++) {
                int up = Math.max(0, y - 1) * w, dn = Math.min(h - 1, y + 1) * w, row = y * w;
                for (int x = 0; x < w; x++) {
                    int p = row + x;
                    float l = lum[p];
                    float lap = Math.abs(4 * l - lum[up + x] - lum[dn + x] - lum[row + Math.max(0, x - 1)] - lum[row + Math.min(w - 1, x + 1)]);
                    int c = f[p];
                    float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
                    float m = (r + g + b) / 3f;
                    float sat = (float) Math.sqrt(((r - m) * (r - m) + (g - m) * (g - m) + (b - m) * (b - m)) / 3f);
                    float e = (l - 0.5f) / 0.2f;
                    float well = (float) Math.exp(-0.5f * e * e);
                    wgt[p] = (lap + 0.01f) * (sat + 0.05f) * (well + 0.01f);
                }
            }
            // suavizar el mapa de pesos para evitar costuras (dos pasadas ≈ gaussiana)
            boxBlur(wgt, tmp, w, h, rb);
            boxBlur(wgt, tmp, w, h, rb);
            short[] q = wt[i];
            for (int p = 0; p < total; p++) {
                float v = wgt[p] * 262140f;
                q[p] = (short) (v > 65535f ? 65535 : (int) v);
            }
        }

        int[] out = px[n / 2];
        // el resultado se escribe sobre la foto central: se lee cada píxel de todas antes de escribirlo
        for (int p = 0; p < total; p++) {
            float sw = 0, r = 0, g = 0, b = 0;
            for (int i = 0; i < n; i++) {
                float wi = (wt[i][p] & 0xFFFF) + 1f;
                int c = px[i][p];
                sw += wi;
                r += wi * ((c >> 16) & 0xFF);
                g += wi * ((c >> 8) & 0xFF);
                b += wi * (c & 0xFF);
            }
            out[p] = 0xFF000000 | (clamp(Math.round(r / sw)) << 16) | (clamp(Math.round(g / sw)) << 8) | clamp(Math.round(b / sw));
        }
    }

    /** Desenfoque de caja (separable) in situ; tmp es de apoyo. */
    private static void boxBlur(float[] a, float[] tmp, int w, int h, int r) {
        float inv = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += a[row + Math.max(0, Math.min(w - 1, k))];
            for (int x = 0; x < w; x++) {
                tmp[row + x] = sum * inv;
                sum += a[row + Math.min(w - 1, x + r + 1)] - a[row + Math.max(0, x - r)];
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += tmp[Math.max(0, Math.min(h - 1, k)) * w + x];
            for (int y = 0; y < h; y++) {
                a[y * w + x] = sum * inv;
                sum += tmp[Math.min(h - 1, y + r + 1) * w + x] - tmp[Math.max(0, y - r) * w + x];
            }
        }
    }

    // ------------------------------------------------------------------ acabado: tono, color y enfoque

    private static void finish(int[] px, int w, int h, boolean hdr) {
        int total = w * h;

        // niveles automáticos por percentiles de luma
        int[] hist = new int[256];
        for (int p = 0; p < total; p++) hist[luma(px[p])]++;
        int lo = 0, hi = 255, acc = 0;
        int cutLo = (int) (total * 0.004), cutHi = (int) (total * 0.004);
        for (int i = 0; i < 256; i++) { acc += hist[i]; if (acc > cutLo) { lo = i; break; } }
        acc = 0;
        for (int i = 255; i >= 0; i--) { acc += hist[i]; if (acc > cutHi) { hi = i; break; } }
        if (hi - lo < 120) { lo = Math.max(0, lo - (120 - (hi - lo)) / 2); hi = Math.min(255, lo + 120); } // no estirar de más

        float gamma = hdr ? 0.88f : 0.94f, sat = hdr ? 1.14f : 1.06f;
        float[] lut = new float[256];
        for (int i = 0; i < 256; i++) {
            float y = Math.max(0f, Math.min(1f, (i - lo) / (float) Math.max(1, hi - lo)));
            y = (float) Math.pow(y, gamma);
            float s = y * y * (3f - 2f * y);       // curva en S suave
            lut[i] = (0.82f * y + 0.18f * s) * 255f;
        }

        float[] lum = new float[total];
        for (int p = 0; p < total; p++) {
            int c = px[p];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int y = luma(c);
            float ny = lut[y];
            float k = y < 2 ? 1f : Math.min(4f, ny / y);
            float fr = r * k, fg = g * k, fb = b * k;
            float yy = 0.299f * fr + 0.587f * fg + 0.114f * fb;
            fr = yy + (fr - yy) * sat;
            fg = yy + (fg - yy) * sat;
            fb = yy + (fb - yy) * sat;
            int rr = clamp(Math.round(fr)), gg = clamp(Math.round(fg)), bb = clamp(Math.round(fb));
            px[p] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
            lum[p] = 0.299f * rr + 0.587f * gg + 0.114f * bb;
        }

        // máscara de enfoque sobre la luma (con umbral para no realzar el ruido)
        float[] blur = lum.clone(), tmp = new float[total];
        boxBlur(blur, tmp, w, h, 1);
        boxBlur(blur, tmp, w, h, 1);
        float amount = hdr ? 0.55f : 0.4f;
        for (int p = 0; p < total; p++) {
            float d = lum[p] - blur[p];
            if (Math.abs(d) < 2.5f) continue;
            float add = amount * d;
            int c = px[p];
            int r = clamp(Math.round(((c >> 16) & 0xFF) + add));
            int g = clamp(Math.round(((c >> 8) & 0xFF) + add));
            int b = clamp(Math.round((c & 0xFF) + add));
            px[p] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }
}
