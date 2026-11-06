package com.woba.slauncher.data;

public class AppEntry {
    public final String label;
    public final String pkg;
    /** Primera instalación (ms); 0 si no se conoce (apps simuladas). */
    public long installed;

    public AppEntry(String label, String pkg) {
        this.label = label;
        this.pkg = pkg;
    }
}
