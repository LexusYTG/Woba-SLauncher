package com.woba.slauncher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import java.util.List;

/**
 * Elige el servicio de reconocimiento de voz real (normalmente el de Google).
 *
 * Cuando WOBA es el asistente digital, Android pone el reconocedor de WOBA como el predeterminado del
 * sistema (WobaRecognitionService). Si Cortana lo usara se llamaría a sí misma, así que siempre se
 * busca otro servicio y se crea el SpeechRecognizer apuntando a él.
 */
public final class Speech {
    private Speech() {}

    private static final String GOOGLE = "com.google.android.googlequicksearchbox";

    /** El reconocedor de otra app: primero el que eligió el usuario, luego Google, luego cualquiera. null si no hay. */
    public static ComponentName findRecognizer(Context c) {
        String me = c.getPackageName();
        try {
            String def = Settings.Secure.getString(c.getContentResolver(), "voice_recognition_service");
            ComponentName d = def == null ? null : ComponentName.unflattenFromString(def);
            if (d != null && !me.equals(d.getPackageName())) return d;
        } catch (Exception ignored) { }
        ComponentName google = null, other = null;
        try {
            List<ResolveInfo> l = c.getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
            for (ResolveInfo ri : l) {
                if (ri.serviceInfo == null || me.equals(ri.serviceInfo.packageName)) continue;
                ComponentName cn = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
                if (GOOGLE.equals(ri.serviceInfo.packageName)) google = cn;
                else if (other == null) other = cn;
            }
        } catch (Exception ignored) { }
        return google != null ? google : other;
    }

    public static boolean available(Context c) {
        return findRecognizer(c) != null || SpeechRecognizer.isRecognitionAvailable(c);
    }

    public static SpeechRecognizer create(Context c) {
        ComponentName cn = findRecognizer(c);
        return cn != null ? SpeechRecognizer.createSpeechRecognizer(c, cn) : SpeechRecognizer.createSpeechRecognizer(c);
    }
}
