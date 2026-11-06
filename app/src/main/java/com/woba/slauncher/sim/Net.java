package com.woba.slauncher.sim;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP mínimo fuera del hilo UI (mapas, tiempo, geocodificación). */
public final class Net {
    private Net() {}

    public static final ExecutorService EX = Executors.newFixedThreadPool(4);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    public static final String UA = "WindowsPhoneLauncher/2.0 (personal use)";

    public interface Text { void on(String body); }

    public static byte[] getBytes(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", UA);
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return out.toByteArray();
    }

    public static void get(final String url, final Text cb) {
        EX.execute(new Runnable() {
            @Override public void run() {
                String r = null;
                try { r = new String(getBytes(url), "UTF-8"); } catch (Throwable ignored) {}
                final String fr = r;
                UI.post(new Runnable() { @Override public void run() { cb.on(fr); } });
            }
        });
    }
}
