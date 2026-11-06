package com.woba.slauncher.data;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;

import android.net.Uri;

import java.util.List;

/** Lo que solo puede hacer la Activity: widgets, bloqueo y permisos. */
public interface HostBridge {
    interface WidgetCallback {
        void onWidget(int widgetId);
    }

    interface ImageCallback {
        void onImage(Uri uri);
    }

    /** Abre el selector de fotos de Android. */
    void pickImage(ImageCallback callback);

    List<AppWidgetProviderInfo> widgetProviders();

    void addWidget(AppWidgetProviderInfo provider, WidgetCallback callback);

    AppWidgetHostView createWidgetView(int widgetId);

    void deleteWidget(int widgetId);

    /** Intenta bloquear de verdad (accesibilidad). false si no es posible. */
    boolean lockDevice();

    void requestLivePermissions(String[] permissions);

    /** Pide permisos y ejecuta done cuando el usuario decide. */
    void requestPermissions(String[] permissions, Runnable done);

    /** Pide el micrófono y llama a onResult(true/false) al decidir el usuario. */
    void requestMic(MicCallback callback);

    interface MicCallback {
        void onResult(boolean granted);
    }
}
