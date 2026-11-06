# Woba S Launcher

Launcher para Android que recrea la interfaz de **Windows Phone 8.1 / Windows 10 Mobile**: Start con mosaicos, lista de
apps, Centro de actividades, pantalla de bloqueo, Configuración, asistente Cortana, teclado propio y un conjunto de apps
simuladas. Escrito en Java con Views y Canvas propios, sin AndroidX ni otras dependencias.

- Paquete: `com.woba.slauncher` · minSdk 26 · targetSdk 34 · versión 1.0.1
- Licencia: ver [LICENSE](LICENSE)
- Lista completa de funciones y apps: [docs/FUNCIONES.md](docs/FUNCIONES.md)

## Compilar
Requisitos: JDK 17 y Android SDK 34.

```bash
gradle assembleDebug     # APK de prueba
gradle assembleRelease   # APK de release (firmado si hay key)
```

El APK queda en `app/build/outputs/apk/release/`.

## Firmar el release
1. Crear la key (una vez; no subirla al repositorio):
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
3. En GitHub Actions, añadir los secretos `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD`.
   Al subir un tag `vX.Y.Z`, el workflow `release.yml` compila y publica el APK en *Releases*. Sin secretos el APK sale sin firmar.

## Permisos opcionales
Se piden solo al usar la función que los necesita. Para que las acciones rápidas cambien Wi-Fi, Bluetooth, etc. sin abrir
paneles de Android:

```bash
adb shell pm grant com.woba.slauncher android.permission.WRITE_SECURE_SETTINGS
```

## Fuentes
`app/src/main/assets/fonts/wp_light|regular|semibold|bold.ttf` (Selawik, licencia SIL OFL).
