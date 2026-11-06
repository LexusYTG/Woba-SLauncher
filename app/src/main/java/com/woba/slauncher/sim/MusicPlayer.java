package com.woba.slauncher.sim;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/** Reproductor de música único del simulador (sigue sonando al cerrar la app Música). */
public final class MusicPlayer {
    private MusicPlayer() {}

    public static class Track {
        public long id, albumId;
        public String title, artist, album;
        public int duration;
        public Uri uri;
    }

    public interface Listener { void onPlayerChanged(); }

    public static final ArrayList<Track> queue = new ArrayList<>();
    private static final ArrayList<Listener> L = new ArrayList<>();
    public static int index = -1;
    public static boolean shuffle;
    public static int repeat; // 0 no, 1 todas, 2 una
    private static MediaPlayer mp;
    private static boolean prepared;
    private static Context app;
    private static AudioManager am;
    private static AudioFocusRequest focus;

    public static void addListener(Listener l) { if (!L.contains(l)) L.add(l); }

    public static void removeListener(Listener l) { L.remove(l); }

    private static void fire() { for (Listener l : new ArrayList<>(L)) l.onPlayerChanged(); }

    public static Track current() { return index >= 0 && index < queue.size() ? queue.get(index) : null; }

    public static boolean isPlaying() { try { return mp != null && prepared && mp.isPlaying(); } catch (Exception e) { return false; } }

    public static int position() { try { return mp != null && prepared ? mp.getCurrentPosition() : 0; } catch (Exception e) { return 0; } }

    public static int duration() { try { return mp != null && prepared ? mp.getDuration() : (current() != null ? current().duration : 0); } catch (Exception e) { return 0; } }

    public static void play(Context c, java.util.List<Track> list, int at) {
        app = c.getApplicationContext();
        queue.clear();
        queue.addAll(list);
        index = at;
        start();
    }

    private static void start() {
        Track t = current();
        if (t == null) return;
        try {
            if (mp != null) { mp.release(); }
            prepared = false;
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build());
            mp.setDataSource(app, t.uri);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer m) {
                    prepared = true;
                    requestFocus();
                    m.start();
                    fire();
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) { onEnd(); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int w, int e) { prepared = false; fire(); return true; }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            prepared = false;
        }
        fire();
    }

    private static void requestFocus() {
        try {
            am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                        @Override public void onAudioFocusChange(int c) {
                            if (c == AudioManager.AUDIOFOCUS_LOSS || c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { if (isPlaying()) toggle(); }
                        }
                    }).build();
            am.requestAudioFocus(focus);
        } catch (Exception ignored) {}
    }

    private static void onEnd() {
        if (repeat == 2) { start(); return; }
        if (!advance(1, true)) { prepared = true; fire(); }
    }

    private static boolean advance(int d, boolean auto) {
        if (queue.isEmpty()) return false;
        int n;
        if (shuffle && d > 0) n = new Random().nextInt(queue.size());
        else n = index + d;
        if (n >= queue.size()) { if (repeat == 1 || !auto) n = 0; else return false; }
        if (n < 0) n = queue.size() - 1;
        index = n;
        start();
        return true;
    }

    public static void next() { advance(1, false); }

    public static void prev() {
        if (position() > 3000) { seek(0); return; }
        advance(-1, false);
    }

    public static void toggle() {
        try {
            if (mp == null || !prepared) { if (current() != null) start(); return; }
            if (mp.isPlaying()) mp.pause(); else { requestFocus(); mp.start(); }
        } catch (Exception ignored) {}
        fire();
    }

    public static void seek(int ms) { try { if (mp != null && prepared) mp.seekTo(ms); } catch (Exception ignored) {} }

    public static void stop() {
        try { if (mp != null) { mp.release(); } } catch (Exception ignored) {}
        mp = null;
        prepared = false;
        index = -1;
        fire();
    }

    public static void shuffleQueue() { Collections.shuffle(queue); }
}
