package com.woba.slauncher.sim;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

/** Base de toda app simulada: una página a pantalla completa con diálogos, menús contextuales y saltos. */
public abstract class SimApp extends FrameLayout {
    public final String id;
    protected final SimHost host;
    private View overlay;

    public interface Choice { void on(int index); }

    public SimApp(Context c, SimHost host, String id) {
        super(c);
        this.host = host;
        this.id = id;
        setBackgroundColor(Theme.bg);
        setClickable(true); // traga los toques para que no lleguen al Start de debajo
    }

    /** Nombre de la app en mayúsculas pequeñas, arriba a la izquierda. */
    public abstract String appTitle();

    /** true si consumió el botón atrás (por defecto: retrocede una página interna). */
    public boolean onBack() { return popPage(); }

    /** Se llama al abrir (arg opcional: id de contacto, número, url...). */
    public void onOpen(String arg) {}

    public void onShow() {}

    public void onHide() {}

    public void onThemeChanged() { setBackgroundColor(Theme.bg); invalidate(); }

    public void onDestroy() {}

    /** La Activity pasa a segundo plano (otra app o diálogo del sistema encima). */
    public void onHostPause() {}

    /** La Activity vuelve a primer plano. */
    public void onHostResume() {}

    /** Tecla física (volumen, cámara) con esta app arriba. true si la consumió. */
    public boolean onHardwareKey(int keyCode) { return false; }

    public void close() { host.closeSim(this); }

    /** Cierra el overlay (diálogo/menú) si lo hay. Devuelve true si había uno. */
    public boolean dismissOverlay() {
        if (overlay == null) return false;
        removeView(overlay);
        overlay = null;
        return true;
    }

    protected boolean hasOverlay() { return overlay != null; }

    /** Procesa atrás: overlay primero, barra de app desplegada, luego la app. */
    public final boolean handleBack() {
        if (dismissOverlay()) return true;
        if (bar != null && bar.isOpen()) { bar.setOpen(false); return true; }
        return onBack();
    }

    // ------------------------------------------------------------------ contenido + barra de app

    protected AppBar bar;
    private View scrimView, contentView;
    private final java.util.ArrayList<Runnable> pageStack = new java.util.ArrayList<>();
    private Runnable currentPage;

    /** Navega a una página nueva (la actual queda en la pila interna de la app). */
    protected void go(Runnable page) {
        if (currentPage != null) pageStack.add(currentPage);
        currentPage = page;
        page.run();
    }

    /** Sustituye la página actual sin apilar. */
    protected void replacePage(Runnable page) {
        currentPage = page;
        page.run();
    }

    /** Vuelve a la página anterior; false si no hay. */
    protected boolean popPage() {
        if (pageStack.isEmpty()) return false;
        currentPage = pageStack.remove(pageStack.size() - 1);
        currentPage.run();
        return true;
    }

    protected boolean hasPages() { return !pageStack.isEmpty(); }

    /** Cambia contenido y barra con un fundido corto (para páginas internas). */
    protected void swapContent(View content, AppBar appBar) {
        if (contentView != null) removeView(contentView);
        if (scrimView != null) removeView(scrimView);
        if (bar != null) removeView(bar);
        bar = null;
        scrimView = null;
        setContent(content, appBar);
        content.setAlpha(0f);
        content.setTranslationX(Theme.dpf(30));
        content.animate().alpha(1f).translationX(0f).setDuration(170).start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    @Override public android.view.WindowInsets onApplyWindowInsets(android.view.WindowInsets in) {
        int ime = 0;
        if (android.os.Build.VERSION.SDK_INT >= 30) ime = in.getInsets(android.view.WindowInsets.Type.ime()).bottom;
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int overlap = ime > 0 ? Math.max(0, loc[1] + getHeight() - getPaddingBottom() - (screenH - ime)) : 0;
        if (overlap != getPaddingBottom()) setPadding(0, 0, 0, overlap);
        return super.onApplyWindowInsets(in);
    }

    /** Coloca el contenido y, opcionalmente, la barra de aplicación inferior (con velo al desplegarla). */
    protected void setContent(View content, AppBar appBar) {
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -1);
        contentView = content;
        addView(content, 0, cp);
        bar = appBar;
        if (appBar != null) {
            scrimView = new View(getContext());
            scrimView.setBackgroundColor(0x88000000);
            scrimView.setVisibility(GONE);
            scrimView.setClickable(true);
            scrimView.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { bar.setOpen(false); }
            });
            addView(scrimView, 1, new FrameLayout.LayoutParams(-1, -1));
            addView(appBar, 2, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            appBar.setOnExpand(new AppBar.OnExpand() {
                @Override public void onExpand(boolean open) { scrimView.setVisibility(open ? VISIBLE : GONE); }
            });
            // el contenido deja sitio a la barra
            ((FrameLayout.LayoutParams) content.getLayoutParams()).bottomMargin = Theme.dp(56);
        }
    }

    private void setOverlay(View v) {
        dismissOverlay();
        overlay = v;
        addView(v, new LayoutParams(-1, -1));
    }

    // ------------------------------------------------------------------ menú contextual

    public void showMenu(float x, float y, final String[] labels, final Choice cb) {
        final Context c = getContext();
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(0x99000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { dismissOverlay(); }
        });
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(WpUi.barBg());
        panel.setPadding(0, Theme.dp(8), 0, Theme.dp(8));
        panel.setClickable(true);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = WpUi.label(c, labels[i], 20, Theme.light, Theme.fg);
            t.setPadding(Theme.dp(18), Theme.dp(11), Theme.dp(18), Theme.dp(11));
            t.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    dismissOverlay();
                    if (cb != null) cb.on(idx);
                }
            });
            WpUi.tilt(t);
            panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.leftMargin = Theme.dp(24);
        lp.rightMargin = Theme.dp(24);
        scrim.addView(panel, lp);
        setOverlay(scrim);
        final float yy = y;
        scrim.post(new Runnable() {
            @Override public void run() {
                int h = panel.getHeight(), H = getHeight();
                float top = Math.max(Theme.dpf(16), Math.min(yy - h / 2f, H - h - Theme.dpf(16)));
                panel.setTranslationY(top);
            }
        });
    }

    // ------------------------------------------------------------------ diálogo de mensaje

    public void showDialog(String title, String message, final String[] buttons, final Choice cb) {
        final Context c = getContext();
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(0x99000000);
        scrim.setClickable(true);
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(WpUi.barBg());
        panel.setPadding(Theme.dp(18), Theme.dp(22), Theme.dp(18), Theme.dp(12));
        if (title != null && !title.isEmpty()) {
            TextView t = WpUi.label(c, title.toUpperCase(), 20, Theme.semibold, Theme.fg);
            panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        if (message != null && !message.isEmpty()) {
            TextView t = WpUi.label(c, message, 17, Theme.light, Theme.fg);
            t.setPadding(0, Theme.dp(10), 0, Theme.dp(18));
            panel.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < buttons.length; i++) {
            final int idx = i;
            TextView b = WpUi.button(c, buttons[i]);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    dismissOverlay();
                    if (cb != null) cb.on(idx);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Theme.dp(44), 1f);
            if (i > 0) lp.leftMargin = Theme.dp(8);
            row.addView(b, lp);
        }
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        scrim.addView(panel, plp);
        setOverlay(scrim);
    }

    // ------------------------------------------------------------------ cuadro de texto modal

    public interface TextResult { void on(String text); }

    /** Diálogo con un campo de texto (renombrar, nueva carpeta, nueva nota...). */
    public void showPrompt(String title, String hint, String initial, final TextResult cb) {
        final Context c = getContext();
        FrameLayout scrim = new FrameLayout(c);
        scrim.setBackgroundColor(0x99000000);
        scrim.setClickable(true);
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(WpUi.barBg());
        panel.setPadding(Theme.dp(18), Theme.dp(22), Theme.dp(18), Theme.dp(12));
        panel.addView(WpUi.label(c, title.toUpperCase(), 20, Theme.semibold, Theme.fg), new LinearLayout.LayoutParams(-1, -2));
        final android.widget.EditText et = WpUi.textBox(c, hint, initial);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2);
        elp.topMargin = Theme.dp(14);
        elp.bottomMargin = Theme.dp(14);
        panel.addView(et, elp);
        LinearLayout row = new LinearLayout(c);
        TextView ok = WpUi.button(c, "ok");
        TextView no = WpUi.button(c, "cancel");
        ok.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                String s = et.getText().toString();
                WpUi.hideKeyboard(et);
                dismissOverlay();
                if (cb != null) cb.on(s);
            }
        });
        no.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                WpUi.hideKeyboard(et);
                dismissOverlay();
            }
        });
        row.addView(ok, new LinearLayout.LayoutParams(0, Theme.dp(44), 1f));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, Theme.dp(44), 1f);
        nlp.leftMargin = Theme.dp(8);
        row.addView(no, nlp);
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        scrim.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        setOverlay(scrim);
        et.requestFocus();
        et.post(new Runnable() {
            @Override public void run() { WpUi.showKeyboard(et); }
        });
    }

    // ------------------------------------------------------------------ salto alfabético

    public interface Pick { void on(String letter); }

    /** Cuadrícula de letras (jump list de WP): las disponibles en acento, el resto apagadas. */
    public void showJump(final String[] letters, final boolean[] enabled, final Pick cb) {
        final Context c = getContext();
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            final RectF r = new RectF();

            @Override protected void onDraw(Canvas cv) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xE6000000);
                cv.drawRect(0, 0, getWidth(), getHeight(), p);
                int cols = 4, n = letters.length, rows = (n + cols - 1) / cols;
                float gap = Theme.dpf(6), w = getWidth() - Theme.dpf(36);
                float cell = (w - gap * (cols - 1)) / cols;
                float x0 = Theme.dpf(18), y0 = Theme.dpf(40);
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp(30));
                p.setTextAlign(Paint.Align.CENTER);
                for (int i = 0; i < n; i++) {
                    float x = x0 + (i % cols) * (cell + gap), y = y0 + (i / cols) * (cell * 0.8f + gap);
                    p.setColor(enabled[i] ? Theme.accent : 0xFF2B2B2B);
                    cv.drawRect(x, y, x + cell, y + cell * 0.8f, p);
                    p.setColor(enabled[i] ? 0xFFFFFFFF : 0xFF666666);
                    cv.drawText(letters[i], x + cell / 2f, Theme.centerBaseline(p, y + cell * 0.4f), p);
                }
                p.setTextAlign(Paint.Align.LEFT);
            }

            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
                int cols = 4;
                float gap = Theme.dpf(6), w = getWidth() - Theme.dpf(36);
                float cell = (w - gap * (cols - 1)) / cols;
                float x0 = Theme.dpf(18), y0 = Theme.dpf(40);
                int col = (int) ((e.getX() - x0) / (cell + gap));
                int row = (int) ((e.getY() - y0) / (cell * 0.8f + gap));
                int i = row * cols + col;
                dismissOverlay();
                if (col >= 0 && col < cols && row >= 0 && i >= 0 && i < letters.length && enabled[i] && cb != null) cb.on(letters[i]);
                return true;
            }
        };
        v.setClickable(true);
        setOverlay(v);
    }

    protected static TextView ellipsized(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    protected void runUi(Runnable r) { post(r); }

    protected int sw() { return ViewGroup.LayoutParams.MATCH_PARENT; }
}
