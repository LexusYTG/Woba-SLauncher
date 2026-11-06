# Woba S Launcher

Launcher para Android que recrea la experiencia de **Windows Phone 8.1 / Windows 10 Mobile**: pantalla de inicio con
mosaicos, lista de apps, Centro de actividades, Ajustes y un simulador de apps propias (Cámara Lumia, Fotos, etc.).
Escrito en **Java puro** (Views + Canvas propios), sin Kotlin, sin Compose y sin dependencias de AndroidX.

> Estado: en desarrollo. Nada de esto se ha probado todavía en un dispositivo real; el código se revisa solo por sintaxis.

## Qué incluye
- **Launcher** estilo WP/W10M: mosaicos animados, temas claro/oscuro y color de acento.
- **Cortana** como asistente del dispositivo (mantener pulsado Home), con voz y Gemini opcional.
- **Teclado** clon del de Windows Phone (IME propio): autocorrección, sugerencias, dictado por voz, punto de control del cursor.
  Se ajusta en *Ajustes > Extras > Keyboard*.
- **Cámara** real (Camera2) con la interfaz de Lumia Camera y *Rich Capture* (HDR / noche).
- Ajustes, Centro de actividades, simulador de apps y renombrado de marcas en *Extras*.

Detalles de diseño y de lo que está o no verificado: [`MAPEO.md`](MAPEO.md).

## Compilar
Requisitos: JDK 17 y Android SDK 34.

```bash
gradle assembleDebug      # APK de prueba
gradle assembleRelease    # APK de release (firmado si hay key, ver abajo)
```

## Firmar el release
1. Crear la key (una sola vez; guardala y no la subas al repositorio):
   ```bash
   keytool -genkeypair -v -keystore woba-release.jks -alias woba -keyalg RSA -keysize 2048 -validity 10000
   ```
2. En local, crear `keystore.properties` (ignorado por git) junto a `build.gradle.kts`:
   ```
   storeFile=woba-release.jks
   storePassword=...
   keyAlias=woba
   keyPassword=...
   ```
3. En GitHub Actions: añadir los secretos `KEYSTORE_BASE64` (`base64 -w0 woba-release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` y
   `KEY_PASSWORD`. Al subir un tag `vX.Y` el workflow compila, firma y publica el APK en *Releases*.

## Fuentes
`app/src/main/assets/fonts/wp_light|regular|semibold|bold.ttf` (Selawik).
