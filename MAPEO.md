# Mapeo Windows Phone 8 → Android

Tipos: **NATIVO** = control propio que cambia el ajuste real · **PANEL** = abre el panel/pantalla de Android (Android no deja a una app cambiarlo) · **INFO** = solo lectura.

## Permisos que se piden (solo al tocar algo que los necesita)
| Permiso | Para qué | Cómo se concede |
|---|---|---|
| WRITE_SETTINGS | brillo, timeout, rotación, reloj 24 h | pantalla "Modificar ajustes del sistema" |
| ACCESS_NOTIFICATION_POLICY | silenciar timbre, quiet hours | pantalla "Acceso a No molestar" |
| READ_CALL_LOG / READ_SMS | live tiles de llamadas y mensajes | diálogo normal |
| Servicio de accesibilidad | doble toque = bloquear | Ajustes > Accesibilidad |

## system
| WP8 | Android | Tipo |
|---|---|---|
| ringtones+sounds: ringer | AudioManager.setRingerMode | NATIVO (silencio pide No molestar) |
| ringtones+sounds: volúmenes | setStreamVolume ring/music/alarm | NATIVO |
| ringtones+sounds: tonos | ACTION_SOUND_SETTINGS | PANEL |
| start+theme: fondo, acento, widgets, reset | Prefs del launcher | NATIVO |
| lock screen: timeout | Settings.System.SCREEN_OFF_TIMEOUT | NATIVO |
| lock screen: lock screen WP | SCREEN_OFF + overlay propio | NATIVO (visual) |
| lock screen: doble toque | AccessibilityService LOCK_SCREEN | NATIVO + permiso |
| display: bloqueo de rotación | ACCELEROMETER_ROTATION | NATIVO |
| display: brillo auto / nivel | SCREEN_BRIGHTNESS(_MODE) | NATIVO |
| wi-fi | Settings.Panel.ACTION_WIFI (API 30+) | PANEL + estado |
| bluetooth | ACTION_BLUETOOTH_SETTINGS | PANEL + estado |
| airplane mode | ACTION_AIRPLANE_MODE_SETTINGS | PANEL + estado |
| cellular+sim | Panel Internet (API 29+) | PANEL |
| nfc | Panel NFC (API 29+) | PANEL + estado |
| location | ACTION_LOCATION_SOURCE_SETTINGS | PANEL + estado |
| battery saver | ACTION_BATTERY_SAVER_SETTINGS | PANEL + estado |
| quiet hours | NotificationManager.setInterruptionFilter(ALARMS) | NATIVO + permiso |
| storage sense | StatFs + ActivityManager.MemoryInfo | INFO |
| date+time | TIME_12_24 / ACTION_DATE_SETTINGS | NATIVO + PANEL |
| language, keyboard, ease of access | ACTION_LOCALE / INPUT_METHOD / ACCESSIBILITY | PANEL |
| phone update | android.settings.SYSTEM_UPDATE_SETTINGS | PANEL |
| about | Build.*, kernel, pantalla, RAM | INFO |

## Añadido en la ronda 2
| WP8 | Android / launcher | Tipo |
|---|---|---|
| start+theme > start background | foto propia (se copia a filesDir) + opción de aplicarla con WallpaperManager (FLAG_SYSTEM) | NATIVO |
| start+theme > tiles | transparencia, títulos, animaciones live, inclinación | NATIVO (launcher) |
| lock screen > wallpaper | foto propia + opción WallpaperManager FLAG_LOCK | NATIVO |
| lock screen > status line | batería / llamadas / SMS en la lock WP | NATIVO |
| display > text size | Settings.System.FONT_SCALE | NATIVO (WRITE_SETTINGS) |
| sounds > touch/keypad/lock sounds, vibrate | Settings.System SOUND_EFFECTS, DTMF, HAPTIC, vibrate_when_ringing | NATIVO (WRITE_SETTINGS) |
| internet sharing, vpn, cast, data sense, accounts, speech, privacy, printers, developer | pantallas de Android | PANEL |
| applications > app list | oculta/muestra apps en la lista (Prefs) | NATIVO |

## applications
La lista se arma con las apps **predeterminadas reales** de Android por categoría
(phone=dialer, messaging=SMS, internet explorer=navegador, camera, people=contactos, email,
calendar, maps, photos=galería, music+videos, calculator, store). Cada una ofrece:
open · pin to start · app settings · notifications. Más: this launcher (permisos), default apps, all apps.

## Límites de Android (no son del launcher)
- Desde Android 10 una app no puede encender/apagar Wi-Fi, Bluetooth, modo avión ni datos: por eso abren el panel de Android.
- Silenciar el timbre exige acceso a No molestar.
- El bloqueo real de Android sigue existiendo; la lock screen WP va encima.

## Ronda 3: barra de estado, Action Center y notificaciones
| WP8.1 | Android / launcher | Tipo |
|---|---|---|
| status bar (señal, operador, Wi-Fi, BT, luna, avión, batería, hora, icono alert) | `StatusBarView` + TelephonyManager, ConnectivityManager callback, BatteryManager | NATIVO (propia) |
| action center: bajar desde arriba | gesto en `LauncherRoot` -> `ActionCenterView` (arrastre, fling, barra inferior para cerrar) | NATIVO |
| notificaciones agrupadas por app, tocar = abrir, deslizar = descartar, clear all | `WpNotificationService` (NotificationListenerService) + `Notif` | NATIVO + acceso a notificaciones |
| quick actions: Wi-Fi, Bluetooth, avión, rotación | `Toggles` | ver tabla de abajo |
| all settings | ACTION_SETTINGS | PANEL |

### Qué cambia de verdad y qué abre panel
| Acción | Sin permisos extra | Con WRITE_SECURE_SETTINGS (adb) |
|---|---|---|
| Rotación | cambia (WRITE_SETTINGS) | igual |
| Quiet hours | cambia (acceso a No molestar) | igual |
| Bluetooth | intenta enable()/disable(); en Android 13+ falla -> panel | igual |
| Wi-Fi | setWifiEnabled falla con targetSdk 34 -> panel | igual |
| Avión | panel | panel (Android no apaga radios solo con ese permiso) |
| Ubicación | panel | cambia `location_mode` |
| Ahorro de batería | panel | cambia `low_power` |
| Acceso a notificaciones | pantalla de Android (1 toque en el Action Center) | se activa solo al abrir el launcher |

Para el modo "con permiso":
`adb shell pm grant com.woba.slauncher android.permission.WRITE_SECURE_SETTINGS`

### Limitaciones conocidas
- Al deslizar desde el borde superior, Android puede enseñar su propia barra unos segundos (modo inmersivo). No se ha probado cuánto molesta.
- Barra de estado propia: la señal usa `SignalStrength.getLevel()` (API 28+); sin SIM muestra "No SIM".
- Sin probar en dispositivo ni compilado.

## Ronda 4: Cortana
| WP8.1 | Android / launcher | Tipo |
|---|---|---|
| Cortana (tile en Start, anillo animado, "Hi! How can I help you?") | `CortanaView` | NATIVO |
| escuchar | `SpeechRecognizer` (necesita RECORD_AUDIO y un servicio de voz, normalmente Google) | NATIVO + permiso |
| hablar | `TextToSpeech` (idioma del sistema) | NATIVO |
| abrir apps, alarmas, temporizadores, llamar a un número, buscar, hora, fecha, batería | `Cortana` (comandos locales; alarmas y temporizadores sin pantalla) | NATIVO |
| preguntas libres | Gemini REST `generateContent` con tu API key (ajustes > cortana) | RED |

- La key se guarda en SharedPreferences del launcher, sin cifrar. Es de uso personal.
- Modelo por defecto `gemini-3.5-flash`; se cambia en ajustes > cortana.
- Llamar por nombre aún no busca contactos (haría falta READ_CONTACTS): solo abre el marcador.
- Sin probar en dispositivo ni compilado.

## Nombres de marca (Ajustes > system > app names)
Todo texto de terceros (Microsoft, Cortana, Xbox, OneDrive, OneNote, Outlook, Skype, Bing, Edge, Word, Excel,
PowerPoint, Office, Lumia, Nokia, Windows Phone) pasa por `data/Brand.java` antes de mostrarse. Por defecto
**Microsoft se muestra como UOVA**; el resto se muestra igual y se puede cambiar desde la pantalla *app names*.

- Solo cambia lo que se ve: nombres de paquete (`com.microsoft.*`), URL, correos y las apps reales instaladas no se tocan.
- Se aplica en `WpUi.label`, en los textos de Ajustes, en los tiles y la lista de apps (solo apps simuladas y Cortana)
  y en el prompt de Gemini.
- Para añadir otra palabra, agrégala a `Brand.TERMS` (`{palabra en el código, nombre por defecto}`).
- Un campo vacío vuelve al valor por defecto. "reset to defaults" restaura todos.

## Ronda 5: más cerca de Windows 10 Mobile
| W10M | Launcher | Tipo |
|---|---|---|
| tile grande 4x4 | `Tile.Size.LARGE` (el tamaño cicla pequeño, mediano, ancho, grande) | NATIVO |
| "show more tiles" | 6 columnas en Start (Ajustes > personalization > start) | NATIVO |
| fondo de Start con parallax | `WallView.setScroll` | NATIVO |
| insignia con el nº de notificaciones en tiles de apps | `TileView` + `Notif` | NATIVO + acceso a notificaciones |
| "recently added" en la lista de apps (últimos 7 días, máx. 4) | `AppListView` + `AppEntry.installed` | NATIVO |
| notificaciones (3) en la lock screen | `LockView.drawNotifs` | NATIVO + acceso a notificaciones |
| vibración y clic en los botones de navegación | `NavBarView` | NATIVO |
| Cortana: llamar por nombre | `Cortana.contactLookup` (pide READ_CONTACTS desde los permisos de la app) | NATIVO + permiso |
| acento por defecto 0078D7, backup desactivado | `Prefs.DEFAULT_ACCENT`, manifest | NATIVO |

Sin compilar ni probar en dispositivo.

## Ronda 6: asistente, teclado y cámara propios
| Qué | Dónde | Notas |
|---|---|---|
| Asistente digital (mantener Home / gesto del asistente) | `assist/WobaVoiceInteractionService`, `...SessionService`, `...Session`, `AssistActivity` | Cortana sube como panel sobre la app actual. También responde a ACTION_ASSIST / VOICE_COMMAND |
| Reconocedor "proxy" | `assist/WobaRecognitionService`, `data/Speech` | Android obliga a declararlo; reenvía al reconocedor de Google. `CortanaView` nunca usa el de WOBA |
| Teclado WP | `ime/WobaImeService`, `ime/WpKeyboardView` | letras, &123 (2 páginas), emoji, numérico/teléfono, acentos al mantener, sugerencias (corrector del sistema), tema/acento del launcher |
| Activar teclado y asistente | `KeyboardSetupActivity` (también en Ajustes > Keyboard & assistant) | Android exige que el usuario los active |
| Cámara real | `sim/apps/CameraApp` (Camera2) | foto, vídeo, flash, zoom pellizco, frontal/trasera, volumen = disparador, guarda en DCIM/Camera |

Sin compilar ni probar en dispositivo (solo revisión de sintaxis).

### Cámara: interfaz de Lumia Camera 5
Obturador (anillo r=33) a la derecha, botón de exposición a su izquierda, WB / enfoque / ISO / velocidad sobre anillos
concéntricos centrados en el borde derecho (radios 0.394, 0.616, 0.85, 1.07, 1.28 × alto), valor ("auto") a la izquierda de cada
anillo. Tocar un botón despliega los anillos; arrastrar sobre un anillo fija el valor manual; tocar otra vez el botón vuelve a auto.
Arriba: modo foto/vídeo, miniatura redonda; flash, girar y ajustes. Ajustes: general | photo | video.
Medido de una captura; los dibujos de los iconos son aproximados. Sin compilar ni probar.

### Cámara: Rich Capture (data/Enhance.java)
Ajustes > photo > Rich capture: auto / hdr / night / off.
- HDR: ráfaga de 3 fotos (−2, 0, +2 EV; exposición manual si el sensor lo permite, si no compensación AE) fundidas con pesos de Mertens.
- Noche: ráfaga de 5 fotos iguales promediadas, rechazando lo que se movió.
- Ambas: alineación en dos niveles, trabajo a ≤8,5 MP (sobremuestreo), niveles automáticos, curva S, saturación y máscara de enfoque.
- Auto elige noche si ISO ≥ 640 o exposición > 40 ms; si no, HDR. No se usa con flash encendido ni exposición manual.
- `largeHeap` activado en el manifest. Si falta memoria se guarda una de las fotos sin procesar.
Sin compilar ni probar.

### Teclado: detalles 1:1, ajustes y autocorrección (ime/)
- `WpKeyboardView`: micrófono a la izquierda de la barra de sugerencias (abre `VoiceTypeActivity`, que dicta con otro reconocedor y escribe el
  texto en el campo), punto de acento de control del cursor entre las dos filas de abajo (a 0.25 del ancho; arrastrarlo manda flechas al campo),
  tecla Enter clara con flecha fina oscura (lupa para buscar, → para ir/siguiente, ✓ para listo, avión para enviar), flecha fina para mayúsculas
  (con barra en bloqueo), burbuja de la tecla pulsada rellena de acento con letra blanca, tecla ".com" en URL y correo, colores pizarra/gris sobre fondo
  casi negro, estilo opcional teñido de acento.
- Ajustes (Ajustes > Extras > Keyboard, `KbPrefs`): altura, tamaño de teclas (letras), una mano izq./der., estilo, sonido, vibración, burbuja,
  retardo de acentos, autocorrección, sugerencias, mayúscula automática, doble espacio = punto, punto de cursor, micrófono, idiomas, olvidar
  palabras aprendidas, restablecer.
- Autocorrección (`Dict` + `WobaImeService`): al pulsar espacio/puntuación/Intro la palabra se cambia por la corrección: primero la del corrector del
  sistema (si ya llegó), si no la local (distancia 1 + tildes). Se salta palabras de <3 letras, SIGLAS, mayúsculas en mitad de oración y palabras
  aprendidas. Borrar justo después la deshace y aprende la palabra. Tocar la palabra de la barra la acepta y aprende.
- Diccionarios: `assets/dict_es.txt` (~1000 palabras) y `assets/dict_en.txt` (~680), escritos a mano porque no había acceso a listas grandes.
  Son pequeños: el corrector local es conservador (no corrige lo que parece plural/conjugación de una palabra conocida). Para mejorarlo basta
  sustituir los archivos por listas grandes (una palabra por línea, la más frecuente primero, o "palabra<TAB>frecuencia").
Sin compilar ni probar en un dispositivo; la lógica de `Dict` sí se probó en Java normal con stubs.
