package com.woba.slauncher.assist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

/** Una invocación del asistente: no dibuja nada propio, solo abre AssistActivity (Cortana) encima de la app actual. */
public class WobaVoiceInteractionSession extends VoiceInteractionSession {
    private static WobaVoiceInteractionSession current;

    public WobaVoiceInteractionSession(Context context) {
        super(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        current = this;
        setUiEnabled(false);
        Intent i = new Intent(getContext(), AssistActivity.class).setAction(Intent.ACTION_ASSIST);
        try {
            startAssistantActivity(i);
        } catch (Exception e) {
            // Si el sistema no acepta la actividad como "asistente", se abre como una actividad normal.
            try {
                getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
        }
    }

    @Override
    public void onHide() {
        super.onHide();
        if (current == this) current = null;
    }

    /** AssistActivity la llama al cerrarse para que Android dé la sesión por terminada. */
    static void dismissCurrent() {
        WobaVoiceInteractionSession s = current;
        current = null;
        if (s != null) {
            try { s.hide(); } catch (Exception ignored) { }
        }
    }
}
