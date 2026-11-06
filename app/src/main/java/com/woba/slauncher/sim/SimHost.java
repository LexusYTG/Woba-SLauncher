package com.woba.slauncher.sim;

import android.content.Intent;

import com.woba.slauncher.data.HostBridge;

/** Lo que una app simulada puede pedirle al launcher. */
public interface SimHost {
    /** Abre otra app simulada por id (la actual queda en la pila). */
    void openSim(String id, String arg);

    /** Cierra la app simulada que está arriba (vuelve a la anterior o al Start). */
    void closeSim(SimApp app);

    /** Vuelve al Start sin cerrar las apps (quedan en el selector de recientes). */
    void goStart();

    /** Lanza un intent de Android. */
    void launch(Intent intent);

    /** Pide permisos en tiempo de ejecución y avisa al decidir. */
    void requestPermissions(String[] perms, Runnable done);

    /** Puente con la Activity (imágenes, micrófono...). */
    HostBridge bridge();

    /** Abre Cortana. */
    void openCortana();

    /** Publica una notificación simulada en el Action Center. */
    void postNotification(String appId, String title, String text, String openId, String openArg);

    /** Muestra el diálogo de llamada entrante / activa. */
    void startCall(String name, String number, boolean incoming);
}
