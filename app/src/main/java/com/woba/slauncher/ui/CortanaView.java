package com.woba.slauncher.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.woba.slauncher.data.AppEntry;
import com.woba.slauncher.data.Cortana;
import com.woba.slauncher.data.HostBridge;
import com.woba.slauncher.data.Prefs;
import com.woba.slauncher.data.Speech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cortana: anillo animado, voz (SpeechRecognizer + TextToSpeech), texto y respuestas del cerebro. */
public class CortanaView extends LinearLayout {

    public interface Listener {
        void onClose();
        List<AppEntry> apps();
    }

    private enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final HostBridge host;
    private final Listener listener;
    private final Stage stage;
    private final EditText input;
    private final MicButton mic;

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;

    private State state = State.IDLE;
    private float rms;
    private String userText = "", replyText = "", partial = "";

    public CortanaView(final Context ctx, HostBridge host, Listener listener) {
        super(ctx);
        this.host = host;
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(0xFF000000);
        setClickable(true); // se traga los toques: nada pasa al Start de abajo

        stage = new Stage(ctx);
        addView(stage, new LayoutParams(-1, 0, 1f));

        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setBackgroundColor(0xFFDADADA);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        input = new EditText(ctx);
        input.setBackground(null);
        input.setHint("ask me anything");
        input.setHintTextColor(0xFF7A7A7A);
        input.setTextColor(0xFF000000);
        input.setTypeface(Theme.regular);
        input.setTextSize(16f);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setPadding(Theme.dp(12), 0, Theme.dp(8), 0);
        input.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                String t = input.getText().toString().trim();
                input.setText("");
                hideKeyboard();
                if (!t.isEmpty()) submit(t);
                return true;
            }
            return false;
        });
        bar.addView(input, new LayoutParams(0, Theme.dp(48), 1f));

        mic = new MicButton(ctx);
        mic.setOnClickListener(v -> onMicTap());
        bar.addView(mic, new LayoutParams(Theme.dp(48), Theme.dp(48)));
        addView(bar, new LayoutParams(-1, -2));

        tts = new TextToSpeech(ctx.getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                try { tts.setLanguage(Locale.getDefault()); } catch (Exception ignored) { }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { post(() -> { if (state == State.SPEAKING) state = State.IDLE; }); }
                    @Override public void onError(String id) { post(() -> { if (state == State.SPEAKING) state = State.IDLE; }); }
                });
            }
        });
    }

    /** Se llama al mostrarla: empieza a escuchar. */
    public void open() {
        post(this::startListening);
    }

    public void close() {
        release();
        listener.onClose();
    }

    // ------------------------------------------------------------ teclado y márgenes

    @Override
    @SuppressWarnings("deprecation")
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        int b;
        if (Build.VERSION.SDK_INT >= 30) b = in.getInsets(WindowInsets.Type.ime()).bottom;
        else b = in.getSystemWindowInsetBottom();
        setPadding(0, 0, 0, b > Theme.dp(80) ? b : 0); // solo si es el teclado, no la barra de navegación
        return super.onApplyWindowInsets(in);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && input.getWindowToken() != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ voz

    private void onMicTap() {
        if (state == State.LISTENING) {
            if (recognizer != null) recognizer.stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        hideKeyboard();
        stopSpeaking();
        host.requestMic(granted -> {
            if (!granted) {
                show("", "I need the microphone to hear you. You can also type below.");
                return;
            }
            beginRecognition();
        });
    }

    private void beginRecognition() {
        Context c = getContext();
        if (!Speech.available(c)) {
            show("", "Speech recognition isn't available on this device. You can type below.");
            return;
        }
        try {
            if (recognizer == null) {
                recognizer = Speech.create(c); // nunca el reconocedor de WOBA (sería llamarse a sí misma)
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle p) { }
                    @Override public void onBeginningOfSpeech() { }
                    @Override public void onRmsChanged(float db) {
                        float t = Math.max(0f, Math.min(1f, (db + 2f) / 12f));
                        rms = rms * 0.6f + t * 0.4f;
                    }
                    @Override public void onBufferReceived(byte[] b) { }
                    @Override public void onEndOfSpeech() { }
                    @Override public void onEvent(int t, Bundle p) { }
                    @Override public void onPartialResults(Bundle b) {
                        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (l != null && !l.isEmpty()) partial = l.get(0);
                    }
                    @Override public void onResults(Bundle b) {
                        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        partial = "";
                        if (l != null && !l.isEmpty()) submit(l.get(0));
                        else { state = State.IDLE; }
                    }
                    @Override public void onError(int e) {
                        partial = "";
                        state = State.IDLE;
                        if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                            show("", "I didn't catch that. Tap the mic to try again.");
                        else if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                            show("", "I need the microphone to hear you.");
                        else if (e == SpeechRecognizer.ERROR_NETWORK || e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
                            show("", "Speech recognition needs a connection right now.");
                        else if (e != SpeechRecognizer.ERROR_CLIENT)
                            show("", "Speech recognition failed (code " + e + "). You can type below.");
                    }
                });
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            partial = "";
            state = State.LISTENING;
            recognizer.startListening(i);
        } catch (Exception e) {
            state = State.IDLE;
            show("", "Speech recognition couldn't start. You can type below.");
        }
    }

    private void stopSpeaking() {
        try { if (tts != null) tts.stop(); } catch (Exception ignored) { }
        if (state == State.SPEAKING) state = State.IDLE;
    }

    // ------------------------------------------------------------ conversación

    private void show(String user, String reply) {
        userText = user;
        replyText = reply;
        stage.resetScroll();
    }

    private void submit(String q) {
        userText = q;
        replyText = "";
        state = State.THINKING;
        stage.resetScroll();
        Cortana.ask(getContext(), q, listener.apps(), (text, action) -> {
            replyText = text;
            boolean speak = !"0".equals(Prefs.getStr(getContext(), Cortana.KEY_SPEAK, "1"));
            if (speak && ttsReady) {
                state = State.SPEAKING;
                try {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cortana");
                } catch (Exception e) {
                    state = State.IDLE;
                }
            } else {
                state = State.IDLE;
            }
            if (action != null) postDelayed(action, 1100);
        });
    }

    private void release() {
        try { if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); } } catch (Exception ignored) { }
        recognizer = null;
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception ignored) { }
        tts = null;
        state = State.IDLE;
        hideKeyboard();
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------ botón del micrófono

    private final class MicButton extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        MicButton(Context c) {
            super(c);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(state == State.LISTENING ? Theme.accent : 0xFF404040);
            c.drawRect(0, 0, w, h, p);
            float cx = w / 2f, cy = h / 2f;
            p.setColor(0xFFFFFFFF);
            r.set(cx - Theme.dpf(4f), cy - Theme.dpf(11f), cx + Theme.dpf(4f), cy + Theme.dpf(3f));
            c.drawRoundRect(r, Theme.dpf(4f), Theme.dpf(4f), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Theme.dpf(1.8f));
            r.set(cx - Theme.dpf(8f), cy - Theme.dpf(7f), cx + Theme.dpf(8f), cy + Theme.dpf(9f));
            c.drawArc(r, 0f, 180f, false, p);
            c.drawLine(cx, cy + Theme.dpf(9f), cx, cy + Theme.dpf(13f), p);
            postInvalidateDelayed(120);
        }
    }

    // ------------------------------------------------------------ escenario

    private final class Stage extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final TextPaint greet = Theme.textPaint(Theme.light, 28f, Theme.accent);
        private final TextPaint hint = Theme.textPaint(Theme.light, 14f, 0xFF9E9E9E);
        private final TextPaint userP = Theme.textPaint(Theme.light, 17f, 0xFF9E9E9E);
        private final TextPaint replyP = Theme.textPaint(Theme.light, 24f, 0xFFFFFFFF);
        private float ct;          // 0 = anillo centrado, 1 = anillo pequeño arriba
        private float scroll, maxScroll, lastY, downY;
        private boolean dragging;
        private final int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();

        Stage(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
        }

        void resetScroll() { scroll = 0f; }

        private StaticLayout lay(String s, TextPaint p, int w, Layout.Alignment a) {
            return StaticLayout.Builder.obtain(s, 0, s.length(), p, w).setAlignment(a).build();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float t = SystemClock.uptimeMillis() / 1000f;
            boolean convo = !userText.isEmpty() || !replyText.isEmpty() || !partial.isEmpty() || state == State.THINKING;
            float target = convo ? 1f : 0f;
            ct += (target - ct) * 0.18f;
            if (Math.abs(target - ct) < 0.01f) ct = target;

            // ---- anillo
            float fullR = Theme.dpf(52f), smallR = Theme.dpf(16f);
            float R = fullR + (smallR - fullR) * ct;
            float cx = w / 2f + (Theme.dpf(36f) - w / 2f) * ct;
            float cy = h * 0.30f + (Theme.dpf(40f) - h * 0.30f) * ct;
            ring.setColor(Theme.accent);
            ring.setStrokeWidth(R * 0.14f);
            rms *= 0.96f;
            switch (state) {
                case LISTENING: {
                    float r = R * (1f + 0.32f * rms);
                    ring.setAlpha(255);
                    c.drawCircle(cx, cy, r, ring);
                    ring.setAlpha(90);
                    c.drawCircle(cx, cy, R * (1.25f + 0.5f * rms), ring);
                    break;
                }
                case THINKING: {
                    oval.set(cx - R, cy - R, cx + R, cy + R);
                    ring.setAlpha(255);
                    for (int i = 0; i < 3; i++) c.drawArc(oval, t * 260f + i * 120f, 70f, false, ring);
                    break;
                }
                case SPEAKING:
                    ring.setAlpha(255);
                    c.drawCircle(cx, cy, R * (1f + 0.07f * (float) Math.sin(t * 9f)), ring);
                    break;
                default:
                    ring.setAlpha(255);
                    c.drawCircle(cx, cy, R * (1f + 0.025f * (float) Math.sin(t * 1.6f)), ring);
                    break;
            }

            // ---- texto
            int tw = w - Theme.dp(32);
            if (ct < 0.5f) {
                greet.setAlpha(Math.round(255 * (1f - ct * 2f)));
                StaticLayout g = lay("Hi! How can I help you?", greet, tw, Layout.Alignment.ALIGN_CENTER);
                c.save();
                c.translate(Theme.dpf(16f), cy + fullR + Theme.dpf(36f));
                g.draw(c);
                c.restore();
                hint.setAlpha(Math.round(255 * (1f - ct * 2f)));
                String ex = "try \"open camera\" or \"set a timer for 5 minutes\"";
                StaticLayout hl = lay(ex, hint, tw, Layout.Alignment.ALIGN_CENTER);
                c.save();
                c.translate(Theme.dpf(16f), cy + fullR + Theme.dpf(36f) + g.getHeight() + Theme.dpf(10f));
                hl.draw(c);
                c.restore();
            } else {
                float y = Theme.dpf(80f) - scroll;
                String u = state == State.LISTENING && !partial.isEmpty() ? partial : userText;
                float total = 0f;
                c.save();
                c.clipRect(0, Theme.dpf(70f), w, h);
                if (!u.isEmpty()) {
                    StaticLayout ul = lay(u, userP, tw, Layout.Alignment.ALIGN_NORMAL);
                    c.save();
                    c.translate(Theme.dpf(16f), y);
                    ul.draw(c);
                    c.restore();
                    y += ul.getHeight() + Theme.dpf(16f);
                    total += ul.getHeight() + Theme.dpf(16f);
                }
                String rep = replyText.isEmpty() && state == State.THINKING ? "…" : replyText;
                if (!rep.isEmpty()) {
                    StaticLayout rl = lay(rep, replyP, tw, Layout.Alignment.ALIGN_NORMAL);
                    c.save();
                    c.translate(Theme.dpf(16f), y);
                    rl.draw(c);
                    c.restore();
                    total += rl.getHeight();
                }
                c.restore();
                maxScroll = Math.max(0f, total + Theme.dpf(100f) - h);
                scroll = Math.max(0f, Math.min(maxScroll, scroll));
            }
            postInvalidateOnAnimation();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = lastY = e.getY();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getY() - downY) > slop) dragging = true;
                    if (dragging) {
                        scroll = Math.max(0f, Math.min(maxScroll, scroll - (e.getY() - lastY)));
                        lastY = e.getY();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) {
                        hideKeyboard();
                        if (ct < 0.5f) onMicTap(); // tocar el anillo grande = escuchar
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
