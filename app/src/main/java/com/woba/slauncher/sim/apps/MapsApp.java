package com.woba.slauncher.sim.apps;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.woba.slauncher.sim.Net;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;

/** Mapas con teselas de OpenStreetMap: arrastrar, pellizcar, ubicación, búsqueda y rutas (abre Google Maps). */
public class MapsApp extends SimApp {
    private MapView map;
    private double pinLat = Double.NaN, pinLon = Double.NaN;
    private String pinName = "";
    private WpUi.WpList results;

    public MapsApp(Context c, SimHost host) {
        super(c, host, "maps");
        go(new Runnable() { @Override public void run() { showMap(); } });
    }

    @Override public String appTitle() { return "maps"; }

    @Override public void onOpen(String arg) {
        if (arg != null && arg.startsWith("q|")) search(arg.substring(2), true);
    }

    @Override public void onDestroy() { stopLocation(); }

    private void showMap() {
        final Context c = getContext();
        map = new MapView(c);
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.CROSSHAIR, "my location", new Runnable() { @Override public void run() { locate(); } }),
                new AppBar.Item(Glyph.SEARCH, "search", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showSearch(); } }); } }),
                new AppBar.Item(Glyph.RIGHT, "directions", new Runnable() { @Override public void run() { directions(); } })
        ).menu(
                new AppBar.Item(0, "share location", new Runnable() { @Override public void run() { shareLoc(); } }),
                new AppBar.Item(0, "open in google maps", new Runnable() { @Override public void run() {
                    host.launch(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + map.lat + "," + map.lon + "?z=" + (int) map.zoom)));
                } })
        );
        swapContent(map, ab);
    }

    private void directions() {
        if (Double.isNaN(pinLat)) { Toast.makeText(getContext(), "Search for a place or long-press the map first", Toast.LENGTH_SHORT).show(); return; }
        host.launch(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + pinLat + "," + pinLon)));
    }

    private void shareLoc() {
        double la = Double.isNaN(pinLat) ? map.lat : pinLat, lo = Double.isNaN(pinLat) ? map.lon : pinLon;
        Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://www.openstreetmap.org/?mlat=" + la + "&mlon=" + lo + "#map=16/" + la + "/" + lo);
        host.launch(Intent.createChooser(s, "Share"));
    }

    // ------------------------------------------------------------------ ubicación

    private LocationManager lm;
    private LocationListener ll;
    double myLat = Double.NaN, myLon = Double.NaN;

    private void locate() {
        final Context c = getContext();
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, new Runnable() {
                @Override public void run() { if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) locate(); }
            });
            return;
        }
        lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        try {
            Location l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l != null) { myLat = l.getLatitude(); myLon = l.getLongitude(); map.goTo(myLat, myLon, 15); }
            stopLocation();
            ll = new LocationListener() {
                @Override public void onLocationChanged(Location x) {
                    boolean first = Double.isNaN(myLat);
                    myLat = x.getLatitude(); myLon = x.getLongitude();
                    if (first) map.goTo(myLat, myLon, 15); else map.invalidate();
                }
                @Override public void onStatusChanged(String p, int s, Bundle b) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            };
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 5, ll);
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, ll);
            if (l == null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                Toast.makeText(c, "Turn on location in the Action Center", Toast.LENGTH_SHORT).show();
        } catch (SecurityException ignored) {
        }
    }

    private void stopLocation() {
        try { if (lm != null && ll != null) lm.removeUpdates(ll); } catch (Exception ignored) {}
        ll = null;
    }

    // ------------------------------------------------------------------ búsqueda

    private void showSearch() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "maps", "search"));
        final EditText et = WpUi.textBox(c, "search for a place or address", "");
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(et, lp);
        results = new WpUi.WpList(c);
        col.addView(results, new LinearLayout.LayoutParams(-1, 0, 1f));
        et.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(android.widget.TextView v, int a, android.view.KeyEvent e) {
                WpUi.hideKeyboard(et);
                query(et.getText().toString());
                return true;
            }
        });
        results.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof double[]) {
                    double[] d = (double[]) it.tag;
                    pinLat = d[0]; pinLon = d[1]; pinName = it.title;
                    popPage();
                    map.goTo(pinLat, pinLon, 15);
                }
            }
        });
        swapContent(col, null);
        et.requestFocus();
        et.post(new Runnable() { @Override public void run() { WpUi.showKeyboard(et); } });
    }

    private void query(String q) {
        final ArrayList<WpUi.Item> wait = new ArrayList<>();
        wait.add(new WpUi.Item("searching…").dim());
        results.setItems(wait);
        Net.get("https://nominatim.openstreetmap.org/search?format=json&limit=8&q=" + Uri.encode(q), new Net.Text() {
            @Override public void on(String body) {
                ArrayList<WpUi.Item> items = new ArrayList<>();
                try {
                    JSONArray a = new JSONArray(body);
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.getJSONObject(i);
                        String name = o.optString("display_name");
                        String[] parts = name.split(", ", 2);
                        items.add(new WpUi.Item(parts[0]).sub(parts.length > 1 ? parts[1] : "").glyph(Glyph.PIN, Theme.accent)
                                .tag(new double[]{o.getDouble("lat"), o.getDouble("lon")}));
                    }
                } catch (Exception ignored) {}
                if (items.isEmpty()) items.add(new WpUi.Item(body == null ? "no connection" : "no results").dim());
                if (results != null) results.setItems(items);
            }
        });
    }

    private void search(String q, final boolean jump) {
        Net.get("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + Uri.encode(q), new Net.Text() {
            @Override public void on(String body) {
                try {
                    JSONArray a = new JSONArray(body);
                    if (a.length() > 0) {
                        JSONObject o = a.getJSONObject(0);
                        pinLat = o.getDouble("lat"); pinLon = o.getDouble("lon");
                        pinName = o.optString("display_name").split(", ")[0];
                        if (map != null && jump) map.goTo(pinLat, pinLon, 15);
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    // ------------------------------------------------------------------ vista del mapa

    private class MapView extends View {
        double lat = 40.4168, lon = -3.7038, zoom = 5;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(40 * 1024 * 1024) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };
        final HashSet<String> pending = new HashSet<>();
        final ScaleGestureDetector sg;
        final GestureDetector gd;
        final File dir;

        MapView(Context c) {
            super(c);
            dir = new File(c.getCacheDir(), "tiles");
            dir.mkdirs();
            setBackgroundColor(0xFFE8E4DC);
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    zoom = Math.max(2, Math.min(18.5, zoom + Math.log(d.getScaleFactor()) / Math.log(2)));
                    invalidate();
                    return true;
                }
            });
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    double ts = tilePx() * Math.pow(2, 0);
                    double n = Math.pow(2, Math.floor(zoom));
                    double[] t = toTile(lat, lon, Math.floor(zoom));
                    double scale = tilePx() * Math.pow(2, zoom - Math.floor(zoom));
                    t[0] += dx / scale;
                    t[1] += dy / scale;
                    double[] ll2 = fromTile(t[0], t[1], Math.floor(zoom));
                    lat = Math.max(-85, Math.min(85, ll2[0]));
                    lon = ll2[1];
                    invalidate();
                    return true;
                }

                @Override public boolean onDoubleTap(MotionEvent e) { zoom = Math.min(18.5, zoom + 1); invalidate(); return true; }

                @Override public void onLongPress(MotionEvent e) {
                    double[] ll2 = pointToLatLon(e.getX(), e.getY());
                    pinLat = ll2[0]; pinLon = ll2[1]; pinName = String.format(java.util.Locale.US, "%.5f, %.5f", pinLat, pinLon);
                    invalidate();
                }
            });
        }

        void goTo(double la, double lo, double z) { lat = la; lon = lo; zoom = z; invalidate(); }

        float tilePx() { return Theme.dpf(256) * 0.8f; }

        double[] toTile(double la, double lo, double z) {
            double n = Math.pow(2, z);
            double x = (lo + 180) / 360 * n;
            double lr = Math.toRadians(la);
            double y = (1 - Math.log(Math.tan(lr) + 1 / Math.cos(lr)) / Math.PI) / 2 * n;
            return new double[]{x, y};
        }

        double[] fromTile(double x, double y, double z) {
            double n = Math.pow(2, z);
            double lo = x / n * 360 - 180;
            double la = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n))));
            return new double[]{la, lo};
        }

        double[] pointToLatLon(float px, float py) {
            double z = Math.floor(zoom);
            double scale = tilePx() * Math.pow(2, zoom - z);
            double[] c0 = toTile(lat, lon, z);
            return fromTile(c0[0] + (px - getWidth() / 2f) / scale, c0[1] + (py - getHeight() / 2f) / scale, z);
        }

        float[] latLonToPoint(double la, double lo) {
            double z = Math.floor(zoom);
            double scale = tilePx() * Math.pow(2, zoom - z);
            double[] c0 = toTile(lat, lon, z), t = toTile(la, lo, z);
            return new float[]{(float) (getWidth() / 2f + (t[0] - c0[0]) * scale), (float) (getHeight() / 2f + (t[1] - c0[1]) * scale)};
        }

        @Override protected void onDraw(Canvas c) {
            int z = (int) Math.floor(zoom);
            float scale = tilePx() * (float) Math.pow(2, zoom - z);
            double[] c0 = toTile(lat, lon, z);
            int n = 1 << z;
            int x0 = (int) Math.floor(c0[0] - getWidth() / 2f / scale), x1 = (int) Math.floor(c0[0] + getWidth() / 2f / scale);
            int y0 = (int) Math.floor(c0[1] - getHeight() / 2f / scale), y1 = (int) Math.floor(c0[1] + getHeight() / 2f / scale);
            for (int tx = x0; tx <= x1; tx++) {
                for (int ty = y0; ty <= y1; ty++) {
                    if (ty < 0 || ty >= n) continue;
                    int wx = ((tx % n) + n) % n;
                    float sx = (float) (getWidth() / 2f + (tx - c0[0]) * scale), sy = (float) (getHeight() / 2f + (ty - c0[1]) * scale);
                    Bitmap b = tile(z, wx, ty);
                    if (b != null) c.drawBitmap(b, null, new android.graphics.RectF(sx, sy, sx + scale + 1, sy + scale + 1), p);
                }
            }
            // marcador
            if (!Double.isNaN(pinLat)) {
                float[] pt = latLonToPoint(pinLat, pinLon);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.accent);
                c.drawCircle(pt[0], pt[1] - Theme.dpf(16), Theme.dpf(11), p);
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(pt[0] - Theme.dpf(8), pt[1] - Theme.dpf(10));
                path.lineTo(pt[0] + Theme.dpf(8), pt[1] - Theme.dpf(10));
                path.lineTo(pt[0], pt[1]);
                path.close();
                c.drawPath(path, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(pt[0], pt[1] - Theme.dpf(16), Theme.dpf(4), p);
                p.setTypeface(Theme.semibold);
                p.setTextSize(Theme.sp(14));
                float tw = p.measureText(pinName);
                p.setColor(0xCC000000);
                c.drawRect(pt[0] - tw / 2f - Theme.dpf(6), pt[1] - Theme.dpf(58), pt[0] + tw / 2f + Theme.dpf(6), pt[1] - Theme.dpf(36), p);
                p.setColor(0xFFFFFFFF);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(pinName, pt[0], pt[1] - Theme.dpf(42), p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            if (!Double.isNaN(myLat)) {
                float[] pt = latLonToPoint(myLat, myLon);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x55000000 | (Theme.accent & 0xFFFFFF));
                c.drawCircle(pt[0], pt[1], Theme.dpf(22), p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(pt[0], pt[1], Theme.dpf(9), p);
                p.setColor(Theme.accent);
                c.drawCircle(pt[0], pt[1], Theme.dpf(6.5f), p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xAAFFFFFF);
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(10));
            c.drawRect(0, getHeight() - Theme.dpf(16), Theme.dpf(170), getHeight(), p);
            p.setColor(0xFF000000);
            c.drawText("© OpenStreetMap contributors", Theme.dpf(4), getHeight() - Theme.dpf(4), p);
        }

        Bitmap tile(final int z, final int x, final int y) {
            final String key = z + "/" + x + "/" + y;
            Bitmap b = mem.get(key);
            if (b != null) return b;
            synchronized (pending) { if (!pending.add(key)) return null; }
            Net.EX.execute(new Runnable() {
                @Override public void run() {
                    Bitmap r = null;
                    try {
                        File f = new File(dir, z + "_" + x + "_" + y + ".png");
                        byte[] data;
                        if (f.exists()) data = java.nio.file.Files.readAllBytes(f.toPath());
                        else {
                            data = Net.getBytes("https://tile.openstreetmap.org/" + key + ".png");
                            FileOutputStream o = new FileOutputStream(f);
                            o.write(data);
                            o.close();
                        }
                        r = BitmapFactory.decodeByteArray(data, 0, data.length);
                    } catch (Throwable ignored) {}
                    synchronized (pending) { pending.remove(key); }
                    if (r != null) { mem.put(key, r); postInvalidate(); }
                }
            });
            return null;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            sg.onTouchEvent(e);
            if (!sg.isInProgress()) gd.onTouchEvent(e);
            return true;
        }
    }
}
