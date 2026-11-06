package com.woba.slauncher.sim.apps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.woba.slauncher.sim.Net;
import com.woba.slauncher.sim.Phone;
import com.woba.slauncher.sim.SimApp;
import com.woba.slauncher.sim.SimHost;
import com.woba.slauncher.ui.AppBar;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;
import com.woba.slauncher.ui.WpUi;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

/** Archivos: explorador real del almacenamiento (con acceso a todos los archivos): copiar, mover, renombrar, borrar, compartir. */
public class FilesApp extends SimApp {
    private File dir;
    private WpUi.WpList list;
    private final HashSet<String> sel = new HashSet<>();
    private final ArrayList<File> clip = new ArrayList<>();
    private boolean clipMove;
    private int sort; // 0 nombre, 1 fecha, 2 tamaño
    private TextView pathLabel;

    public FilesApp(Context c, SimHost host) {
        super(c, host, "files");
        go(new Runnable() { @Override public void run() { showMain(); } });
    }

    @Override public String appTitle() { return "files"; }

    private boolean allowed() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return getContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onShow() { if (dir != null && list != null) refresh(); }

    private void ask() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getContext().getPackageName()));
            host.launch(i);
        } else host.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, new Runnable() {
            @Override public void run() { showMain(); }
        });
    }

    private void showMain() {
        final Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hdr = WpUi.pageHeader(c, "files", dir == null ? "this device" : dir.getName().isEmpty() ? "this device" : dir.getName());
        pathLabel = WpUi.label(c, dir == null ? "" : dir.getAbsolutePath(), 12, Theme.light, Theme.subtle);
        hdr.addView(pathLabel);
        col.addView(hdr);
        if (!allowed()) {
            TextView t = WpUi.label(c, "Allow access to all files to browse your storage.", 19, Theme.light, Theme.fg);
            t.setPadding(Theme.dp(18), Theme.dp(10), Theme.dp(18), Theme.dp(18));
            col.addView(t);
            TextView b = WpUi.button(c, "allow");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Theme.dp(160), Theme.dp(46));
            lp.leftMargin = Theme.dp(18);
            col.addView(b, lp);
            b.setOnClickListener(new OnClickListener() { @Override public void onClick(android.view.View v) { ask(); } });
            swapContent(col, null);
            return;
        }
        list = new WpUi.WpList(c);
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        list.setOnItem(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof File)) return;
                File f = (File) it.tag;
                if (!sel.isEmpty()) { toggle(f); return; }
                if (f.isDirectory()) { dir = f; showMain(); } else open(f);
            }
        });
        list.setOnLong(new WpUi.OnItem() {
            @Override public void on(WpUi.Item it, int pos, float x, float y) {
                if (!(it.tag instanceof File)) return;
                final File f = (File) it.tag;
                if (!sel.isEmpty()) { toggle(f); return; }
                showMenu(x, y + Theme.dpf(120), new String[]{"select", "open", "rename", "details", "delete"}, new Choice() {
                    @Override public void on(int i) {
                        if (i == 0) toggle(f);
                        else if (i == 1) { if (f.isDirectory()) { dir = f; showMain(); } else open(f); }
                        else if (i == 2) rename(f);
                        else if (i == 3) details(f);
                        else confirmDelete(java.util.Collections.singletonList(f));
                    }
                });
            }
        });
        swapContent(col, bar());
        refresh();
    }

    private AppBar bar() {
        final Context c = getContext();
        if (!sel.isEmpty()) {
            return new AppBar(c).items(
                    new AppBar.Item(Glyph.SAVE, "copy", new Runnable() { @Override public void run() { setClip(false); } }),
                    new AppBar.Item(Glyph.RIGHT, "move", new Runnable() { @Override public void run() { setClip(true); } }),
                    new AppBar.Item(Glyph.SHARE, "share", new Runnable() { @Override public void run() { shareSel(); } }),
                    new AppBar.Item(Glyph.DELETE, "delete", new Runnable() { @Override public void run() { confirmDelete(selected()); } }),
                    new AppBar.Item(Glyph.CLOSE, "cancel", new Runnable() { @Override public void run() { sel.clear(); showMain(); } })
            );
        }
        return new AppBar(c).items(
                new AppBar.Item(Glyph.UP, "up", new Runnable() { @Override public void run() { up(); } }),
                new AppBar.Item(Glyph.ADD, "new folder", new Runnable() { @Override public void run() { newFolder(); } }),
                new AppBar.Item(Glyph.CHECK, "paste", new Runnable() { @Override public void run() { paste(); } })
        ).menu(
                new AppBar.Item(0, "sort by name", new Runnable() { @Override public void run() { sort = 0; refresh(); } }),
                new AppBar.Item(0, "sort by date", new Runnable() { @Override public void run() { sort = 1; refresh(); } }),
                new AppBar.Item(0, "sort by size", new Runnable() { @Override public void run() { sort = 2; refresh(); } }),
                new AppBar.Item(0, "storage", new Runnable() { @Override public void run() { dir = null; showMain(); } })
        );
    }

    private boolean up() {
        if (dir == null) return false;
        File p = dir.getParentFile();
        if (dir.equals(Environment.getExternalStorageDirectory()) || p == null || !p.canRead()) dir = null; else dir = p;
        showMain();
        return true;
    }

    private ArrayList<File> selected() {
        ArrayList<File> l = new ArrayList<>();
        for (String s : sel) l.add(new File(s));
        return l;
    }

    private void toggle(File f) {
        boolean wasEmpty = sel.isEmpty();
        if (!sel.remove(f.getAbsolutePath())) sel.add(f.getAbsolutePath());
        if (wasEmpty != sel.isEmpty()) showMain(); else refresh();
    }

    private void refresh() {
        if (list == null) return;
        ArrayList<WpUi.Item> items = new ArrayList<>();
        if (dir == null) {
            File root = Environment.getExternalStorageDirectory();
            items.add(storageItem("Internal storage", root));
            for (File f : getContext().getExternalFilesDirs(null)) {
                if (f == null) continue;
                String p = f.getAbsolutePath();
                int i = p.indexOf("/Android/data");
                if (i > 0) { File v = new File(p.substring(0, i)); if (!v.equals(root) && v.exists()) items.add(storageItem("SD card", v)); }
            }
            String[][] quick = {{"Downloads", Environment.DIRECTORY_DOWNLOADS}, {"Documents", Environment.DIRECTORY_DOCUMENTS}, {"Pictures", Environment.DIRECTORY_PICTURES},
                    {"Music", Environment.DIRECTORY_MUSIC}, {"Movies", Environment.DIRECTORY_MOVIES}, {"DCIM", Environment.DIRECTORY_DCIM}};
            items.add(WpUi.Item.section("folders"));
            for (String[] q : quick) {
                File f = Environment.getExternalStoragePublicDirectory(q[1]);
                items.add(new WpUi.Item(q[0]).glyph(Glyph.FOLDER, Theme.accent).tag(f));
            }
            list.setItems(items);
            return;
        }
        File[] fs = dir.listFiles();
        if (fs == null) fs = new File[0];
        final int s = sort;
        Arrays.sort(fs, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                if (s == 1) return Long.compare(b.lastModified(), a.lastModified());
                if (s == 2) return Long.compare(b.length(), a.length());
                return a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
            }
        });
        for (File f : fs) {
            if (f.isHidden() && false) continue;
            WpUi.Item it = new WpUi.Item(f.getName()).tag(f);
            it.sub(f.isDirectory() ? (f.list() == null ? "" : f.list().length + " items") : size(f.length()) + " · " + Phone.when(f.lastModified()));
            it.glyph(f.isDirectory() ? Glyph.FOLDER : iconFor(f), f.isDirectory() ? Theme.accent : WpUi.colorFor(ext(f)));
            if (!sel.isEmpty()) { it.showCheck = true; it.checked = sel.contains(f.getAbsolutePath()); }
            items.add(it);
        }
        if (items.isEmpty()) items.add(new WpUi.Item("this folder is empty").dim());
        list.setItems(items);
    }

    private WpUi.Item storageItem(String name, File root) {
        String sub = "";
        try {
            StatFs sf = new StatFs(root.getAbsolutePath());
            sub = size(sf.getAvailableBytes()) + " free of " + size(sf.getTotalBytes());
        } catch (Exception ignored) {}
        return new WpUi.Item(name).sub(sub).glyph(Glyph.STORAGE, Theme.accent).tag(root);
    }

    private static String ext(File f) { String n = f.getName(); int i = n.lastIndexOf('.'); return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT); }

    private int iconFor(File f) {
        String e = ext(f);
        if ("jpg jpeg png gif webp bmp heic".contains(e) && !e.isEmpty()) return Glyph.PHOTOS;
        if ("mp3 m4a flac wav ogg aac".contains(e) && !e.isEmpty()) return Glyph.MUSIC;
        if ("mp4 mkv avi mov webm 3gp".contains(e) && !e.isEmpty()) return Glyph.VIDEO;
        if ("doc docx".contains(e) && !e.isEmpty()) return Glyph.WORD;
        if ("xls xlsx csv".contains(e) && !e.isEmpty()) return Glyph.EXCEL;
        if ("ppt pptx".contains(e) && !e.isEmpty()) return Glyph.PPT;
        return Glyph.PAGE;
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576) return (b / 1024) + " KB";
        if (b < 1073741824L) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    // ------------------------------------------------------------------ operaciones

    private Uri uriFor(File f) {
        try {
            android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder().build());
        } catch (Exception ignored) {}
        return Uri.fromFile(f);
    }

    private void open(File f) {
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(f));
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(f), mime == null ? "*/*" : mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { host.launch(i); } catch (Exception e) { Toast.makeText(getContext(), "No app can open this file", Toast.LENGTH_SHORT).show(); }
    }

    private void shareSel() {
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : selected()) if (f.isFile()) uris.add(uriFor(f));
        if (uris.isEmpty()) return;
        Intent s = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        host.launch(Intent.createChooser(s, "Share"));
    }

    private void setClip(boolean move) {
        clip.clear();
        clip.addAll(selected());
        clipMove = move;
        sel.clear();
        Toast.makeText(getContext(), clip.size() + " item(s) ready to paste", Toast.LENGTH_SHORT).show();
        showMain();
    }

    private void paste() {
        if (clip.isEmpty() || dir == null) { Toast.makeText(getContext(), "Nothing to paste", Toast.LENGTH_SHORT).show(); return; }
        final ArrayList<File> items = new ArrayList<>(clip);
        final boolean mv = clipMove;
        final File to = dir;
        clip.clear();
        Net.EX.execute(new Runnable() {
            @Override public void run() {
                int ok = 0;
                for (File f : items) {
                    try {
                        File dst = unique(new File(to, f.getName()));
                        if (mv && f.renameTo(dst)) { ok++; continue; }
                        copy(f, dst);
                        if (mv) delete(f);
                        ok++;
                    } catch (Exception ignored) {}
                }
                final int n = ok;
                post(new Runnable() { @Override public void run() { Toast.makeText(getContext(), n + " item(s) done", Toast.LENGTH_SHORT).show(); refresh(); } });
            }
        });
    }

    private File unique(File f) {
        if (!f.exists()) return f;
        String n = f.getName();
        int i = n.lastIndexOf('.');
        String base = i > 0 ? n.substring(0, i) : n, e = i > 0 ? n.substring(i) : "";
        for (int k = 1; k < 1000; k++) { File g = new File(f.getParentFile(), base + " (" + k + ")" + e); if (!g.exists()) return g; }
        return f;
    }

    private void copy(File a, File b) throws Exception {
        if (a.isDirectory()) {
            b.mkdirs();
            File[] fs = a.listFiles();
            if (fs != null) for (File f : fs) copy(f, new File(b, f.getName()));
        } else Files.copy(a.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void delete(File f) {
        if (f.isDirectory()) { File[] fs = f.listFiles(); if (fs != null) for (File c : fs) delete(c); }
        f.delete();
    }

    private void confirmDelete(final java.util.List<File> fs) {
        showDialog("delete", "Delete " + fs.size() + " item(s)? This can't be undone.", new String[]{"delete", "cancel"}, new Choice() {
            @Override public void on(int i) {
                if (i != 0) return;
                final ArrayList<File> l = new ArrayList<>(fs);
                Net.EX.execute(new Runnable() {
                    @Override public void run() {
                        for (File f : l) delete(f);
                        post(new Runnable() { @Override public void run() { sel.clear(); showMain(); } });
                    }
                });
            }
        });
    }

    private void rename(final File f) {
        showPrompt("rename", "name", f.getName(), new TextResult() {
            @Override public void on(String t) {
                if (t.trim().isEmpty()) return;
                if (!f.renameTo(new File(f.getParentFile(), t.trim()))) Toast.makeText(getContext(), "Couldn't rename", Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
    }

    private void newFolder() {
        if (dir == null) return;
        showPrompt("new folder", "name", "", new TextResult() {
            @Override public void on(String t) {
                if (t.trim().isEmpty()) return;
                if (!new File(dir, t.trim()).mkdirs()) Toast.makeText(getContext(), "Couldn't create the folder", Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
    }

    private void details(File f) {
        showDialog("details", f.getName() + "\n" + f.getAbsolutePath() + "\n" + (f.isDirectory() ? "folder" : size(f.length())) + "\nmodified " + Phone.full(f.lastModified()), new String[]{"ok"}, null);
    }

    @Override public boolean onBack() {
        if (!sel.isEmpty()) { sel.clear(); showMain(); return true; }
        if (dir != null) return up();
        return super.onBack();
    }
}
