package com.woba.slauncher.ime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.woba.slauncher.data.Prefs;
import com.woba.slauncher.data.Speech;
import com.woba.slauncher.ui.Glyph;
import com.woba.slauncher.ui.Theme;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Dictado por voz del teclado (tecla del micrófono). Es una actividad translúcida y diminuta: pide el micrófono si
 * hace falta, escucha con el reconocedor de otra app y entrega el texto al teclado.
 */
public class VoiceTypeActivity extends Activity {
    private static final int REQ_MIC = 21;
    private SpeechRecognizer sr;
    private Pulse pulse;
    private boolean finished;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        Theme.apply(Prefs.loadAccent(this), Prefs.loadDark(this));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x66000000);
        root.setOnClickListener(v -> finishNow());
        pulse = new Pulse(this);
        root.addView(pulse, new FrameLayout.LayoutParams(Theme.dp(140), Theme.dp(140), Gravity.CENTER));
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        if (!Speech.available(this)) {
            finishNow();
            return;
        }
        if (checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED) start();
        else requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQ_MIC);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_MIC && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) start();
        else finishNow();
    }

    private void start() {
        try {
            sr = Speech.create(this);
            sr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) { }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { pulse.level(rms); }
                @Override public void onBufferReceived(byte[] buf) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onError(int error) { finishNow(); }
                @Override public void onPartialResults(Bundle p) { }
                @Override public void onEvent(int t, Bundle p) { }
                @Override public void onResults(Bundle r) {
                    ArrayList<String> l = r == null ? null : r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (l != null && !l.isEmpty() && !l.get(0).isEmpty()) WobaImeService.deliverVoice(l.get(0));
                    finishNow();
                }
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            sr.startListening(i);
        } catch (Exception e) {
            finishNow();
        }
    }

    private void finishNow() {
        if (finished) return;
        finished = true;
        try {
            if (sr != null) {
                sr.cancel();
                sr.destroy();
            }
        } catch (Exception ignored) { }
        sr = null;
        finish();
        overridePendingTransition(0, 0);
    }

    @Override protected void onStop() {
        super.onStop();
        finishNow();
    }

    /** Micrófono con un círculo que late con el volumen de la voz. */
    private static final class Pulse extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lv;

        Pulse(Context c) { super(c); }

        void level(float rms) {
            lv = Math.max(0f, Math.min(1f, (rms + 2f) / 12f));
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r0 = getWidth() * 0.28f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(Theme.accent, 0.35f));
            c.drawCircle(cx, cy, r0 + lv * getWidth() * 0.2f, p);
            p.setColor(Theme.accent);
            c.drawCircle(cx, cy, r0, p);
            Glyph.draw(c, Glyph.MIC, cx, cy, r0 * 1.0f, 0xFFFFFFFF);
        }
    }
}
