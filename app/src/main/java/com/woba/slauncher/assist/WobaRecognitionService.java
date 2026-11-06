package com.woba.slauncher.assist;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import com.woba.slauncher.data.Speech;

/**
 * Android exige que un asistente digital declare un servicio de reconocimiento. Este no reconoce nada
 * por sí mismo: reenvía a otro reconocedor instalado (el de Google) y devuelve sus resultados.
 */
public class WobaRecognitionService extends RecognitionService {
    private SpeechRecognizer inner;

    @Override
    protected void onStartListening(Intent intent, final Callback cb) {
        destroyInner();
        ComponentName cn = Speech.findRecognizer(this);
        if (cn == null) {
            try { cb.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); } catch (RemoteException ignored) { }
            return;
        }
        try {
            inner = SpeechRecognizer.createSpeechRecognizer(this, cn);
            inner.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) { try { cb.readyForSpeech(p); } catch (RemoteException ignored) { } }
                @Override public void onBeginningOfSpeech() { try { cb.beginningOfSpeech(); } catch (RemoteException ignored) { } }
                @Override public void onRmsChanged(float v) { try { cb.rmsChanged(v); } catch (RemoteException ignored) { } }
                @Override public void onBufferReceived(byte[] b) { try { cb.bufferReceived(b); } catch (RemoteException ignored) { } }
                @Override public void onEndOfSpeech() { try { cb.endOfSpeech(); } catch (RemoteException ignored) { } }
                @Override public void onError(int e) { try { cb.error(e); } catch (RemoteException ignored) { } }
                @Override public void onResults(Bundle b) { try { cb.results(b); } catch (RemoteException ignored) { } }
                @Override public void onPartialResults(Bundle b) { try { cb.partialResults(b); } catch (RemoteException ignored) { } }
                @Override public void onEvent(int t, Bundle b) { }
            });
            inner.startListening(intent);
        } catch (Exception e) {
            try { cb.error(SpeechRecognizer.ERROR_CLIENT); } catch (RemoteException ignored) { }
        }
    }

    @Override protected void onStopListening(Callback cb) {
        if (inner != null) inner.stopListening();
    }

    @Override protected void onCancel(Callback cb) { destroyInner(); }

    @Override public void onDestroy() {
        destroyInner();
        super.onDestroy();
    }

    private void destroyInner() {
        if (inner == null) return;
        try { inner.cancel(); inner.destroy(); } catch (Exception ignored) { }
        inner = null;
    }
}
