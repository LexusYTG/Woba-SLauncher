package com.woba.slauncher.sim.apps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.sim.SimStore;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.util.ArrayList;

/** Microsoft Edge simulado sobre WebView: pestañas, favoritos, historial, lista de lectura y vista de lectura. */
public class EdgeApp extends SimApp {
    private static final String SEARCH = "https://www.bing.com/search?q=";

    static class Tab {
        WebView web;
        String title = "New tab", url = "";
        int progress;
        Bitmap snap;
    }

    private final ArrayList<Tab> tabs = new ArrayList<>();
    private Tab cur;
    private FrameLayout host0;
    private EditText bar0;
    private StartPage startPage;
    private View progress;
    private boolean desktop;

    public EdgeApp(Context c, SimHost host) {
        super(c, host, "edge");
        go(new Runnable() { @Override public void run() { showBrowser(); } });
    }

    @Override public String appTitle() { return "edge"; }

    @Override public void onOpen(String arg) {
        if (arg == null) return;
        String u = arg.startsWith("url|") ? arg.substring(4) : arg;
        if (cur == null) newTab();
        if (!u.isEmpty()) load(u);
    }

    @Override public void onShow() { if (cur != null && cur.web != null) cur.web.onResume(); }

    @Override public void onHide() { if (cur != null && cur.web != null) cur.web.onPause(); }

    @Override public void onDestroy() {
        for (Tab t : tabs) if (t.web != null) { t.web.stopLoading(); t.web.destroy(); }
        tabs.clear();
    }

    // ------------------------------------------------------------------ navegador

    @SuppressLint({"SetJavaScriptEnabled"})
    private Tab newTab() {
        Tab t = new Tab();
        WebView w = new WebView(getContext());
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        w.setBackgroundColor(Theme.bg);
        t.web = w;
        final Tab tab = t;
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { tab.progress = p; if (tab == cur) updateProgress(); }
            @Override public void onReceivedTitle(WebView v, String title) { tab.title = title == null ? tab.url : title; }
        });
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:") || u.startsWith("file:")) return false;
                try { host.launch(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) {}
                return true;
            }

            @Override public void onPageStarted(WebView v, String url, Bitmap f) {
                tab.url = url;
                if (tab == cur && bar0 != null && !bar0.hasFocus()) bar0.setText(url);
            }

            @Override public void onPageFinished(WebView v, String url) {
                tab.url = url;
                tab.progress = 100;
                if (tab == cur) { updateProgress(); if (bar0 != null && !bar0.hasFocus()) bar0.setText(url); }
                if (url != null && url.startsWith("http") && !SimStore.flag("edge_private", false)) {
                    SimStore.Mark m = new SimStore.Mark();
                    m.title = tab.title;
                    m.url = url;
                    m.time = System.currentTimeMillis();
                    if (SimStore.history.isEmpty() || !SimStore.history.get(0).url.equals(url)) {
                        SimStore.history.add(0, m);
                        while (SimStore.history.size() > 300) SimStore.history.remove(SimStore.history.size() - 1);
                        SimStore.save();
                    }
                }
            }
        });
        w.setDownloadListener(new android.webkit.DownloadListener() {
            @Override public void onDownloadStart(String u, String ua, String cd, String mime, long len) {
                try { host.launch(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) {}
            }
        });
        tabs.add(t);
        switchTo(t);
        return t;
    }

    private void switchTo(Tab t) {
        if (cur != null && cur.web != null && cur.web.getParent() != null) {
            cur.snap = shot(cur.web);
            ((android.view.ViewGroup) cur.web.getParent()).removeView(cur.web);
        }
        cur = t;
        if (host0 != null) attach();
    }

    private Bitmap shot(View v) {
        try {
            if (v.getWidth() <= 0) return null;
            Bitmap b = Bitmap.createBitmap(v.getWidth() / 3, v.getHeight() / 3, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.scale(1f / 3f, 1f / 3f);
            v.draw(c);
            return b;
        } catch (Throwable e) { return null; }
    }

    private void attach() {
        host0.removeAllViews();
        boolean empty = cur.url == null || cur.url.isEmpty();
        if (empty) {
            startPage = new StartPage(getContext());
            host0.addView(startPage, new FrameLayout.LayoutParams(-1, -1));
        } else host0.addView(cur.web, new FrameLayout.LayoutParams(-1, -1));
        host0.addView(progress, new FrameLayout.LayoutParams(-1, Theme.dp(3)));
        if (bar0 != null) bar0.setText(cur.url == null ? "" : cur.url);
        updateProgress();
    }

    private void updateProgress() {
        if (progress == null) return;
        int w = host0.getWidth();
        progress.setVisibility(cur.progress >= 100 || cur.progress <= 0 ? View.GONE : View.VISIBLE);
        progress.getLayoutParams().width = Math.max(1, w * cur.progress / 100);
        progress.requestLayout();
    }

    private void load(String input) {
        String u = input.trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http://") && !u.startsWith("https://") && !u.startsWith("file:") && !u.startsWith("about:")) {
            if (!u.contains(" ") && u.contains(".")) u = "https://" + u;
            else u = SEARCH + Uri.encode(u);
        }
        cur.url = u;
        if (host0 != null && cur.web.getParent() == null) attach();
        cur.web.loadUrl(u);
    }

    private void showBrowser() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        host0 = new FrameLayout(c);
        col.addView(host0, new LinearLayout.LayoutParams(-1, 0, 1f));
        progress = new View(c);
        progress.setBackgroundColor(Theme.accent);
        LinearLayout row = new LinearLayout(c);
        row.setBackgroundColor(WpUi.barBg());
        row.setPadding(Theme.dp(8), Theme.dp(6), Theme.dp(8), Theme.dp(6));
        bar0 = WpUi.textBox(c, "search or type a web address", "");
        bar0.setImeOptions(EditorInfo.IME_ACTION_GO);
        bar0.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI | android.text.InputType.TYPE_CLASS_TEXT);
        bar0.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (id == EditorInfo.IME_ACTION_GO || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    WpUi.hideKeyboard(bar0);
                    bar0.clearFocus();
                    load(bar0.getText().toString());
                    return true;
                }
                return false;
            }
        });
        bar0.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) {
                if (f) bar0.post(new Runnable() { @Override public void run() { bar0.selectAll(); } });
            }
        });
        row.addView(bar0, new LinearLayout.LayoutParams(-1, Theme.dp(42)));
        col.addView(row, new LinearLayout.LayoutParams(-1, -2));
        AppBar ab = new AppBar(c).items(
                new AppBar.Item(Glyph.LEFT, "back", new Runnable() { @Override public void run() {
                    if (cur.web.canGoBack()) cur.web.goBack(); else if (!cur.url.isEmpty()) { cur.url = ""; attach(); }
                } }),
                new AppBar.Item(Glyph.RIGHT, "forward", new Runnable() { @Override public void run() { if (cur.web.canGoForward()) cur.web.goForward(); } }),
                new AppBar.Item(Glyph.REFRESH, "refresh", new Runnable() { @Override public void run() { if (cur.url != null && !cur.url.isEmpty()) cur.web.reload(); } }),
                new AppBar.Item(Glyph.TABS, "tabs", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showTabs(); } }); } })
        ).menu(
                new AppBar.Item(0, "new tab", new Runnable() { @Override public void run() { newTab(); } }),
                new AppBar.Item(0, "add to favorites", new Runnable() { @Override public void run() { addFav(); } }),
                new AppBar.Item(0, "add to reading list", new Runnable() { @Override public void run() { addReading(); } }),
                new AppBar.Item(0, "reading view", new Runnable() { @Override public void run() { readerView(); } }),
                new AppBar.Item(0, "favorites", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showMarks("favorites", SimStore.favorites); } }); } }),
                new AppBar.Item(0, "history", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showMarks("history", SimStore.history); } }); } }),
                new AppBar.Item(0, "reading list", new Runnable() { @Override public void run() { go(new Runnable() { @Override public void run() { showMarks("reading list", SimStore.readingList); } }); } }),
                new AppBar.Item(0, "share", new Runnable() { @Override public void run() {
                    if (cur.url.isEmpty()) return;
                    Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cur.url);
                    host.launch(Intent.createChooser(s, "Share"));
                } }),
                new AppBar.Item(0, desktop ? "request mobile site" : "request desktop site", new Runnable() { @Override public void run() { toggleDesktop(); } }),
                new AppBar.Item(0, SimStore.flag("edge_private", false) ? "stop InPrivate" : "new InPrivate mode", new Runnable() { @Override public void run() {
                    SimStore.putFlag("edge_private", !SimStore.flag("edge_private", false));
                    Toast.makeText(getContext(), SimStore.flag("edge_private", false) ? "InPrivate: history is not saved" : "InPrivate off", Toast.LENGTH_SHORT).show();
                } }),
                new AppBar.Item(0, "clear history", new Runnable() { @Override public void run() { SimStore.history.clear(); SimStore.save(); Toast.makeText(getContext(), "History cleared", Toast.LENGTH_SHORT).show(); } })
        );
        swapContent(col, ab);
        if (cur == null) newTab(); else attach();
    }

    private void toggleDesktop() {
        desktop = !desktop;
        WebSettings s = cur.web.getSettings();
        s.setUserAgentString(desktop ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 Edg/120.0" : null);
        if (!cur.url.isEmpty()) cur.web.reload();
        showBrowser();
    }

    private void addFav() {
        if (cur.url == null || cur.url.isEmpty()) return;
        SimStore.Mark m = new SimStore.Mark();
        m.title = cur.title;
        m.url = cur.url;
        m.time = System.currentTimeMillis();
        SimStore.favorites.add(m);
        SimStore.save();
        Toast.makeText(getContext(), "Added to favorites", Toast.LENGTH_SHORT).show();
    }

    private void addReading() {
        if (cur.url == null || cur.url.isEmpty()) return;
        SimStore.Mark m = new SimStore.Mark();
        m.title = cur.title;
        m.url = cur.url;
        m.time = System.currentTimeMillis();
        SimStore.readingList.add(0, m);
        SimStore.save();
        Toast.makeText(getContext(), "Added to reading list", Toast.LENGTH_SHORT).show();
    }

    private void readerView() {
        if (cur.url == null || cur.url.isEmpty()) return;
        final String title = cur.title;
        cur.web.evaluateJavascript("(function(){var a=document.querySelector('article')||document.body;var p=a.querySelectorAll('p');var t=[];for(var i=0;i<p.length;i++){var x=p[i].innerText.trim();if(x.length>40)t.push(x);}return t.join('\\n\\n');})()", new android.webkit.ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                String text = v == null ? "" : v;
                if (text.startsWith("\"") && text.length() > 1) text = text.substring(1, text.length() - 1);
                text = text.replace("\\n", "\n").replace("\\\"", "\"").replace("\\u003C", "<").replace("\\\\", "\\");
                final String body = text.isEmpty() ? "This page can't be shown in reading view." : text;
                go(new Runnable() { @Override public void run() { showReader(title, body); } });
            }
        });
    }

    private void showReader(String title, String body) {
        Context c = getContext();
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Theme.dp(20), Theme.dp(12), Theme.dp(20), Theme.dp(30));
        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(WpUi.label(c, "READING VIEW", 13, Theme.semibold, Theme.accent));
        TextView t = WpUi.label(c, title, 28, Theme.light, Theme.fg);
        t.setPadding(0, Theme.dp(8), 0, Theme.dp(12));
        col.addView(t);
        TextView b = WpUi.label(c, body, 19, Theme.light, Theme.fg);
        b.setLineSpacing(0, 1.25f);
        col.addView(b);
        swapContent(sv, null);
    }

    // ------------------------------------------------------------------ páginas auxiliares

    private void showMarks(String title, final ArrayList<SimStore.Mark> list) {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "edge", title));
        final WpUi.WpList l = new WpUi.WpList(c);
        col.addView(l, new LinearLayout.LayoutParams(-1, 0, 1f));
        final Runnable fill = new Runnable() {
            @Override public void run() {
                ArrayList<WpUi.Item> items = new ArrayList<>();
                for (SimStore.Mark m : list) items.add(new WpUi.Item(m.title == null || m.title.isEmpty() ? m.url : m.title).sub(m.url).tag(m));
                if (items.isEmpty()) items.add(new WpUi.Item("nothing here").dim());
                l.setItems(items);
            }
        };
        fill.run();
        l.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (it.tag instanceof SimStore.Mark) { final String u = ((SimStore.Mark) it.tag).url; popPage(); load(u); }
            }
        });
        l.setOnLong(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof SimStore.Mark)) return;
                final SimStore.Mark m = (SimStore.Mark) it.tag;
                showMenu(x, y + Theme.dpf(120), new String[]{"open in new tab", "remove"}, new Choice() {
                    @Override public void on(int i) {
                        if (i == 0) { popPage(); newTab(); load(m.url); } else { list.remove(m); SimStore.save(); fill.run(); }
                    }
                });
            }
        });
        swapContent(col, null);
    }

    private void showTabs() {
        final Context c = getContext();
        if (cur != null && cur.web.getParent() != null) cur.snap = shot(cur.web);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(WpUi.pageHeader(c, "edge", "tabs"));
        TabGrid g = new TabGrid(c);
        col.addView(g, new LinearLayout.LayoutParams(-1, 0, 1f));
        AppBar ab = new AppBar(c).items(new AppBar.Item(Glyph.NEW_TAB, "new tab", new Runnable() { @Override public void run() {
            popPage(); newTab();
        } }));
        swapContent(col, ab);
    }

    private class TabGrid extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        TabGrid(Context c) { super(c); }

        float cw() { return (getWidth() - Theme.dpf(18) * 3) / 2f; }

        float ch() { return cw() * 1.3f + Theme.dpf(30); }

        @Override protected void onDraw(Canvas c) {
            float w = cw(), h = ch();
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                float x = Theme.dpf(18) + (i % 2) * (w + Theme.dpf(18)), y = Theme.dpf(8) + (i / 2) * (h + Theme.dpf(12));
                p.setStyle(Paint.Style.FILL);
                p.setColor(WpUi.mix(Theme.bg, Theme.fg, 0.18f));
                c.drawRect(x, y + Theme.dpf(28), x + w, y + h, p);
                if (t.snap != null) c.drawBitmap(t.snap, null, new android.graphics.RectF(x, y + Theme.dpf(28), x + w, y + h), p);
                p.setColor(t == cur ? Theme.accent : Theme.fg);
                p.setTypeface(Theme.regular);
                p.setTextSize(Theme.sp(13));
                String s = android.text.TextUtils.ellipsize(t.title, new android.text.TextPaint(p), w - Theme.dpf(30), android.text.TextUtils.TruncateAt.END).toString();
                c.drawText(s, x, y + Theme.dpf(18), p);
                Glyph.draw(c, Glyph.CLOSE, x + w - Theme.dpf(10), y + Theme.dpf(12), Theme.dpf(14), Theme.fg);
                if (t == cur) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Theme.dpf(2));
                    p.setColor(Theme.accent);
                    c.drawRect(x, y + Theme.dpf(28), x + w, y + h, p);
                }
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float w = cw(), h = ch();
            for (int i = 0; i < tabs.size(); i++) {
                float x = Theme.dpf(18) + (i % 2) * (w + Theme.dpf(18)), y = Theme.dpf(8) + (i / 2) * (h + Theme.dpf(12));
                if (e.getX() >= x && e.getX() <= x + w && e.getY() >= y && e.getY() <= y + h) {
                    Tab t = tabs.get(i);
                    if (e.getX() > x + w - Theme.dpf(28) && e.getY() < y + Theme.dpf(28)) {
                        closeTab(t);
                        if (tabs.isEmpty()) { popPage(); newTab(); } else invalidate();
                    } else { popPage(); switchTo(t); }
                    return true;
                }
            }
            return true;
        }
    }

    private void closeTab(Tab t) {
        int i = tabs.indexOf(t);
        tabs.remove(t);
        if (t.web != null) { if (t.web.getParent() != null) ((android.view.ViewGroup) t.web.getParent()).removeView(t.web); t.web.destroy(); }
        if (t == cur) cur = tabs.isEmpty() ? null : tabs.get(Math.max(0, i - 1));
    }

    /** Página de inicio de pestaña nueva: favoritos como mosaicos. */
    private class StartPage extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        StartPage(Context c) { super(c); }

        @Override protected void onDraw(Canvas c) {
            p.setTypeface(Theme.light);
            p.setTextSize(Theme.sp(40));
            p.setColor(Theme.fg);
            c.drawText("new tab", Theme.dpf(18), Theme.dpf(60), p);
            p.setTextSize(Theme.sp(15));
            p.setColor(Theme.accent);
            p.setTypeface(Theme.semibold);
            c.drawText("favorites", Theme.dpf(18), Theme.dpf(100), p);
            float w = (getWidth() - Theme.dpf(18) * 2 - Theme.dpf(8) * 2) / 3f;
            for (int i = 0; i < SimStore.favorites.size() && i < 12; i++) {
                SimStore.Mark m = SimStore.favorites.get(i);
                float x = Theme.dpf(18) + (i % 3) * (w + Theme.dpf(8)), y = Theme.dpf(112) + (i / 3) * (w + Theme.dpf(8));
                p.setStyle(Paint.Style.FILL);
                p.setColor(WpUi.colorFor(m.url));
                c.drawRect(x, y, x + w, y + w, p);
                p.setColor(0xFFFFFFFF);
                p.setTypeface(Theme.light);
                p.setTextSize(Theme.sp(36));
                p.setTextAlign(Paint.Align.CENTER);
                String t = m.title == null || m.title.isEmpty() ? m.url : m.title;
                c.drawText(t.substring(0, 1).toUpperCase(), x + w / 2f, Theme.centerBaseline(p, y + w * 0.42f), p);
                p.setTextSize(Theme.sp(12));
                p.setTypeface(Theme.regular);
                c.drawText(android.text.TextUtils.ellipsize(t, new android.text.TextPaint(p), w - Theme.dpf(10), android.text.TextUtils.TruncateAt.END).toString(), x + w / 2f, y + w - Theme.dpf(10), p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
            float w = (getWidth() - Theme.dpf(18) * 2 - Theme.dpf(8) * 2) / 3f;
            int col = (int) ((e.getX() - Theme.dpf(18)) / (w + Theme.dpf(8))), row = (int) ((e.getY() - Theme.dpf(112)) / (w + Theme.dpf(8)));
            int i = row * 3 + col;
            if (col >= 0 && col < 3 && row >= 0 && i >= 0 && i < SimStore.favorites.size()) load(SimStore.favorites.get(i).url);
            return true;
        }
    }

    @Override public boolean onBack() {
        if (popPage()) return true;
        if (cur != null && cur.web != null && cur.web.getParent() != null && cur.web.canGoBack()) { cur.web.goBack(); return true; }
        if (cur != null && cur.url != null && !cur.url.isEmpty()) { cur.url = ""; attach(); return true; }
        return false;
    }
}
