package com.woba.slauncher.data;

import java.util.Date;

/** Datos en vivo compartidos por los live tiles y la pantalla de bloqueo (solo hilo UI). */
public class Live {
    public Date now = new Date();
    public int battery = 0;
    public boolean charging = false;
    public int missed = 0;
    public int sms = 0;
}
