package com.woba.slauncher.sim.apps;

import android.Manifest;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.woba.slauncher.sim.Net;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Tiempo real con Open-Meteo (sin clave): temperatura actual y previsión de 7 días. */
public class WeatherApp extends SimApp {
    private View view;
    private String place = SimStore.str("weather_place", "");
    private double lat = Double.parseDouble(SimStore.str("weather_lat", "NaN")), lon = Double.parseDouble(SimStore.str("weather_lon", "NaN"));
    private JSONObject data;
    private boolean loading;

    public WeatherApp(Context c, SimHost host) {
        super(c, host, "weather");
        go(new Runnable() { @Override public void run() { showMain(); } });
        String last = SimStore.str("weather_json", "");
        if (!last.isEmpty()) try { data = new JSONObject(last); } catch (Exception ignored) {}
    }

    @Override public String appTitle() { return "weather"; }

    @Override public void onShow() { if (Double.isNaN(lat)) locate(); else if (!loading) fetch(); }

    private void locate() {
        Context c = getContext();
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            host.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, new Runnable() { @Override public void run() { locate2(); } });
        } else locate2();
    }

    private void locate2() {
        Context c = getContext();
        try {
            LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            Location l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (l == null) l = lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            if (l != null) {
                lat = l.getLatitude();
                lon = l.getLongitude();
                place = "";
                SimStore.putStr("weather_lat", String.valueOf(lat));
                SimStore.putStr("weather_lon", String.valueOf(lon));
                reverse();
                fetch();
            }
        } catch (SecurityException ignored) {}
    }

    private void reverse() {
        Net.get("https://nominatim.openstreetmap.org/reverse?format=json&zoom=10&lat=" + lat + "&lon=" + lon, new Net.Text() {
            @Override public void on(String b) {
                try {
                    JSONObject o = new JSONObject(b);
                    JSONObject a = o.optJSONObject("address");
                    String n = a == null ? o.optString("name") : a.optString("city", a.optString("town", a.optString("village", a.optString("county", ""))));
                    if (!n.isEmpty()) { place = n; SimStore.putStr("weather_place", n); if (view != null) view.invalidate(); }
                } catch (Exception ignored) {}
            }
        });
    }

    private void fetch() {
        if (Double.isNaN(lat)) return;
        loading = true;
        Net.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto", new Net.Text() {
            @Override public void on(String b) {
                loading = false;
                try {
                    data = new JSONObject(b);
                    SimStore.putStr("weather_json", b);
                    SimStore.fire("weather");
                } catch (Exception ignored) {}
                if (view != null) view.invalidate();
            }
        });
    }

    public static String describe(int code) {
        if (code == 0) return "clear";
        if (code <= 2) return "partly cloudy";
        if (code == 3) return "overcast";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 57) return "drizzle";
        if (code >= 61 && code <= 67) return "rain";
        if (code >= 71 && code <= 77) return "snow";
        if (code >= 80 && code <= 82) return "showers";
        if (code >= 95) return "thunderstorm";
        return "cloudy";
    }

    public static int glyphFor(int code) { return code <= 1 ? Glyph.SUN : code <= 3 ? Glyph.CLOUD : Glyph.WEATHER; }

    /** Texto breve para el live tile (lee la última respuesta guardada). */
    public static String tileText() {
        try {
            String j = SimStore.str("weather_json", "");
            if (j.isEmpty()) return null;
            JSONObject cur = new JSONObject(j).getJSONObject("current");
            return Math.round(cur.getDouble("temperature_2m")) + "° " + describe(cur.getInt("weather_code"));
        } catch (Exception e) { return null; }
    }

    private void showMain() {
        final Context c = getContext();
        view = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas cv) {
                float w = getWidth();
                p.setTypeface(Theme.semibold);
                p.setTextSize(Theme.sp(13));
                p.setColor(Theme.fg);
                cv.drawText("WEATHER", Theme.dpf(18), Theme.dpf(30), p);
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp(34));
                cv.drawText((place.isEmpty() ? "weather" : place).toLowerCase(), Theme.dpf(18), Theme.dpf(78), p);
                if (data == null) {
                    p.setTextSize(Theme.sp(18));
                    p.setColor(Theme.subtle);
                    cv.drawText(Double.isNaN(lat) ? "tap search to choose a city" : "loading…", Theme.dpf(18), Theme.dpf(130), p);
                    return;
                }
                try {
                    JSONObject cur = data.getJSONObject("current");
                    int code = cur.getInt("weather_code");
                    p.setColor(Theme.fg);
                    p.setTextSize(Theme.sp(96));
                    cv.drawText(Math.round(cur.getDouble("temperature_2m")) + "°", Theme.dpf(18), Theme.dpf(190), p);
                    Glyph.draw(cv, glyphFor(code), w - Theme.dpf(70), Theme.dpf(150), Theme.dpf(80), Theme.accent, Theme.dpf(3));
                    p.setColor(Theme.accent);
                    p.setTextSize(Theme.sp(24));
                    cv.drawText(describe(code), Theme.dpf(18), Theme.dpf(226), p);
                    p.setColor(Theme.subtle);
                    p.setTextSize(Theme.sp(15));
                    cv.drawText("feels like " + Math.round(cur.getDouble("apparent_temperature")) + "°   humidity " + cur.getInt("relative_humidity_2m") + "%   wind " + Math.round(cur.getDouble("wind_speed_10m")) + " km/h", Theme.dpf(18), Theme.dpf(254), p);
                    JSONObject d = data.getJSONObject("daily");
                    JSONArray t = d.getJSONArray("time"), mx = d.getJSONArray("temperature_2m_max"), mn = d.getJSONArray("temperature_2m_min"), wc = d.getJSONArray("weather_code"), pr = d.optJSONArray("precipitation_probability_max");
                    p.setColor(Theme.accent);
                    p.setTypeface(Theme.semibold);
                    p.setTextSize(Theme.sp(15));
                    cv.drawText("forecast", Theme.dpf(18), Theme.dpf(300), p);
                    SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new SimpleDateFormat("EEEE", Locale.getDefault());
                    for (int i = 0; i < Math.min(7, t.length()); i++) {
                        float y = Theme.dpf(330) + i * Theme.dpf(52);
                        p.setTypeface(Theme.light);
                        p.setColor(Theme.fg);
                        p.setTextSize(Theme.sp(20));
                        String day = i == 0 ? "today" : out.format(in.parse(t.getString(i))).toLowerCase();
                        cv.drawText(day, Theme.dpf(18), y + Theme.dpf(10), p);
                        Glyph.draw(cv, glyphFor(wc.getInt(i)), w * 0.52f, y, Theme.dpf(28), Theme.fg);
                        if (pr != null) { p.setColor(Theme.subtle); p.setTextSize(Theme.sp(13)); cv.drawText(pr.optInt(i) + "%", w * 0.52f + Theme.dpf(24), y + Theme.dpf(5), p); }
                        p.setColor(Theme.fg);
                        p.setTextSize(Theme.sp(20));
                        p.setTextAlign(Paint.Align.RIGHT);
                        cv.drawText(Math.round(mx.getDouble(i)) + "°", w - Theme.dpf(70), y + Theme.dpf(10), p);
                        p.setColor(Theme.subtle);
                        cv.drawText(Math.round(mn.getDouble(i)) + "°", w - Theme.dpf(18), y + Theme.dpf(10), p);
                        p.setTextAlign(Paint.Align.LEFT);
                    }
                } catch (Exception e) {
                    p.setColor(Theme.subtle);
                    cv.drawText("no data", Theme.dpf(18), Theme.dpf(130), p);
                }
            }
        };
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.CROSSHAIR, "my location", new Runnable() { @Override public void run() { lat = Double.NaN; locate(); } }),
                new AppBar.Item(Glyph.SEARCH, "city", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showCity(); } }); } }),
                new AppBar.Item(Glyph.REFRESH, "refresh", new Runnable() { @Override public void run() { fetch(); } })
        );
        swapContent(view, ab);
    }

    private void showCity() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "weather", "city"));
        final EditText et = WpUi.textBox(c, "enter a city", "");
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Theme.dp(18);
        col.addView(et, lp);
        final WpUi.WpList res = new WpUi.WpList(c);
        col.addView(res, new LinearLayout.LayoutParams(-1, 0, 1f));
        et.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(android.widget.TextView v, int a, android.view.KeyEvent e) {
                WpUi.hideKeyboard(et);
                Net.get("https://geocoding-api.open-meteo.com/v1/search?count=8&name=" + Uri.encode(et.getText().toString()), new Net.Text() {
                    @Override public void on(String b) {
                        ArrayList<WpUi.Item> items = new ArrayList<>();
                        try {
                            JSONArray r = new JSONObject(b).optJSONArray("results");
                            for (int i = 0; r != null && i < r.length(); i++) {
                                JSONObject o = r.getJSONObject(i);
                                items.add(new WpUi.Item(o.optString("name")).sub(o.optString("admin1") + " " + o.optString("country")).tag(new Object[]{o.optString("name"), o.getDouble("latitude"), o.getDouble("longitude")}));
                            }
                        } catch (Exception ignored) {}
                        if (items.isEmpty()) items.add(new WpUi.Item(b == null ? "no connection" : "no results").dim());
                        res.setItems(items);
                    }
                });
                return true;
            }
        });
        res.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof Object[])) return;
                Object[] o = (Object[]) it.tag;
                place = (String) o[0];
                lat = (Double) o[1];
                lon = (Double) o[2];
                SimStore.putStr("weather_place", place);
                SimStore.putStr("weather_lat", String.valueOf(lat));
                SimStore.putStr("weather_lon", String.valueOf(lon));
                popPage();
                fetch();
            }
        });
        swapContent(col, null);
        et.requestFocus();
        et.post(new Runnable() { @Override public void run() { WpUi.showKeyboard(et); } });
    }
}
