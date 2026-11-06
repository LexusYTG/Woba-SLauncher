package com.woba.slauncher.data;

public class Tile {
    public enum Kind { APP, PHONE, MESSAGES, CLOCK, BATTERY, SETTINGS, LOCK, WIDGET, CORTANA }

    /** Tamaños de Windows 10 Mobile sobre rejilla de 4 columnas: pequeño 1x1, mediano 2x2, ancho 4x2, grande 4x4. */
    public enum Size {
        SMALL(1, 1), MEDIUM(2, 2), WIDE(4, 2), LARGE(4, 4);

        public final int cols;
        public final int rows;

        Size(int cols, int rows) {
            this.cols = cols;
            this.rows = rows;
        }

        public Size next() {
            switch (this) {
                case SMALL: return MEDIUM;
                case MEDIUM: return WIDE;
                case WIDE: return LARGE;
                default: return SMALL;
            }
        }
    }

    public final String id;
    public final Kind kind;
    public String title;
    public String pkg;
    public Size size;
    public int widgetId;

    public Tile(String id, Kind kind, String title, String pkg, Size size, int widgetId) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.pkg = pkg == null ? "" : pkg;
        this.size = size;
        this.widgetId = widgetId;
    }

    public Tile(String id, Kind kind, String title, Size size) {
        this(id, kind, title, "", size, -1);
    }
}
