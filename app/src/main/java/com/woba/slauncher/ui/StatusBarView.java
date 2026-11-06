package com.woba.slauncher.ui;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextPaint;
import android.view.View;

import com.woba.slauncher.data.Live;
import com.woba.slauncher.data.Notif;
import com.woba.slauncher.data.Sys;
import com.woba.slauncher.data.SysSettings;

/** Barra de estado estilo Windows Phone 8.1: señal, red, estados a la izquierda; hora y batería a la derecha. */
public class StatusBarView extends View {
    private final Live live;
    private final TextPaint text = Theme.textPaint(Theme.semibold, 11f, Theme.fg);

    private String operator = "";
    private int level = 0;
    private boolean simReady, wifi, bt, airplane, quiet, hasNotifs;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback netCb;

    public StatusBarView(Context ctx, Live live) {
        super(ctx);
        this.live = live;
        setClickable(false);
        refresh();
    }

    /** Altura de la barra: la del sistema (cubre también el notch), mínimo 24 dp. */
    public static int heightFor(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int h = id > 0 ? c.getResources().getDimensionPixelSize(id) : 0;
        return Math.max(h, Theme.dp(24));
    }

    /** Relee los estados baratos del sistema. Se llama en cada tick y al cambiar la red. */
    public void refresh() {
        Context c = getContext();
        try {
            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            simReady = tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY;
            operator = tm == null ? "" : String.valueOf(tm.getNetworkOperatorName());
            level = 0;
            if (simReady && Build.VERSION.SDK_INT >= 28) {
                SignalStrength ss = tm.getSignalStrength();
                if (ss != null) level = Math.max(0, Math.min(4, ss.getLevel()));
            }
        } catch (Exception e) {
            simReady = false;
        }
        wifi = SysSettings.wifiOn(c);
        bt = SysSettings.bluetoothOn(c);
        airplane = SysSettings.airplaneOn(c);
        quiet = SysSettings.quietHours(c);
        hasNotifs = !Notif.all().isEmpty();
        invalidate();
    }

    private final Notif.Listener notifListener = new Notif.Listener() {
        @Override public void onNotifsChanged() { refresh(); }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Notif.addListener(notifListener);
        try {
            cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            netCb = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network n) { post(refreshRun); }
                @Override public void onLost(Network n) { post(refreshRun); }
            };
            cm.registerDefaultNetworkCallback(netCb);
        } catch (Exception ignored) {
            netCb = null;
        }
    }

    private final Runnable refreshRun = new Runnable() {
        @Override public void run() { refresh(); }
    };

    @Override
    protected void onDetachedFromWindow() {
        Notif.removeListener(notifListener);
        try {
            if (cm != null && netCb != null) cm.unregisterNetworkCallback(netCb);
        } catch (Exception ignored) {
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float pad = Theme.dpf(8f);
        float icon = Theme.dpf(14f);
        float cy = h - Theme.dpf(12f);
        int fg = Theme.fg;
        text.setColor(fg);
        text.setTextSize(Theme.sp(11f));

        // ---- izquierda: señal, operador, wifi, estados
        float x = pad + icon / 2f;
        if (airplane) {
            StatusIcons.airplane(c, x, cy, icon, fg);
            x += icon + Theme.dpf(4f);
        } else if (simReady) {
            StatusIcons.signal(c, x, cy, icon, level, fg);
            x += icon + Theme.dpf(2f);
            if (operator.length() > 0) {
                c.drawText(operator, x, Theme.centerBaseline(text, cy), text);
                x += text.measureText(operator) + Theme.dpf(6f);
            }
        } else {
            String s = "No SIM";
            c.drawText(s, pad, Theme.centerBaseline(text, cy), text);
            x = pad + text.measureText(s) + Theme.dpf(6f) + icon / 2f;
        }
        if (wifi) {
            StatusIcons.wifi(c, x, cy, icon, fg);
            x += icon + Theme.dpf(2f);
        }
        if (bt) {
            StatusIcons.bluetooth(c, x, cy, icon, fg);
            x += icon + Theme.dpf(2f);
        }
        if (quiet) {
            StatusIcons.moon(c, x, cy, icon, fg);
            x += icon + Theme.dpf(2f);
        }

        // ---- derecha: batería y hora (la hora es lo último, como en WP)
        String time = Sys.timeText(getContext(), live.now);
        float right = w - pad;
        float tw = text.measureText(time);
        c.drawText(time, right - tw, Theme.centerBaseline(text, cy), text);
        right -= tw + Theme.dpf(8f);
        float bs = Theme.dpf(20f);
        StatusIcons.battery(c, right - bs / 2f, cy, bs, live.battery, live.charging, fg);
        right -= bs + Theme.dpf(4f);
        String pct = live.battery + "%";
        float pw = text.measureText(pct);
        c.drawText(pct, right - pw, Theme.centerBaseline(text, cy), text);
        right -= pw + Theme.dpf(8f);
        if (hasNotifs) StatusIcons.alert(c, right - icon / 2f, cy, icon, fg);
    }
}
