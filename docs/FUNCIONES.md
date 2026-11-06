# Funciones de Woba S Launcher

## Launcher

**Start**
- Rejilla de 4 columnas con tiles de tamaño pequeño (1x1), mediano (2x2), ancho (4x2) y grande (4x4); los pequeños se agrupan en bloques de 2x2.
- Tipos de tile: app, teléfono (llamadas perdidas), mensajes (no leídos), reloj, batería, ajustes, bloqueo, widget de Android y Cortana.
- Pulsación larga para editar: arrastrar para reordenar (con auto-scroll), cambiar tamaño, anclar y desanclar.
- Animaciones de tiles (live tile, flip, inclinación al pulsar), títulos opcionales y fondo propio o imagen a pantalla completa.
- Añadir widgets de Android y restablecer el diseño de Start.

**Lista de apps**: cabeceras por letra, salto por letras, búsqueda y menú de pulsación larga (anclar/desanclar y ajustes de la app). Se pueden mostrar u ocultar apps.

**Centro de actividades**: cuatro acciones rápidas elegibles entre Wi-Fi, Bluetooth, modo avión, bloqueo de rotación, horas de silencio, ubicación, ahorro de batería y compartir internet; notificaciones agrupadas por app, "clear all" y acceso a todos los ajustes. Cambia el ajuste real cuando Android lo permite y, si no, abre el panel oficial.

**Pantalla de bloqueo**: hora grande, deslizar hacia arriba para entrar, línea de estado, doble toque para bloquear (servicio de accesibilidad), vista previa y fondo propio.

**Barra de estado y de navegación**: señal, red, hora y batería; atrás, Windows (inicio) y buscar. Pulsación larga en atrás abre las apps recientes.

**Apps recientes**: tarjetas con captura; tocar abre, deslizar hacia arriba cierra.

**Personalización**: colores de acento de WP8, tema claro/oscuro, fondos de Start y bloqueo (también como fondo y pantalla de bloqueo de Android), fuentes Selawik, renombrado visual de marcas de terceros (*App names*).

**Notificaciones y recordatorios**: captura las notificaciones del sistema para el Centro de actividades y muestra recordatorios programados como notificación.

## Configuración (estilo Windows 10 Mobile)
Buscador y 11 categorías. Cada ajuste usa un control propio o abre el panel equivalente de Android.

| Categoría | Contenido |
|---|---|
| System | Display (brillo, rotación, tamaño de texto/elementos), Notifications & actions, Quiet hours, Phone, Messaging, Battery saver, Storage, Device encryption, About |
| Devices | Bluetooth, Typing, Printers & scanners, Project my screen |
| Network & wireless | Cellular & SIM, Wi-Fi, Airplane mode, Data usage, Mobile hotspot |
| Personalization | Start, Colors, Lock screen, Sounds |
| Apps | Apps & features, Default apps, App list |
| Accounts | Email & app accounts, Sign-in options |
| Time & language | Date & time, Region & language, Keyboard & assistant, Keyboard, Speech |
| Ease of Access | Text size, Narrator, Magnifier, High contrast, Closed captions, Other options |
| Privacy | Location, App permissions |
| Update & recovery | Phone update, Backup, For developers |
| Extras | Cortana, Keyboard, App names, This launcher, All Android settings |

## Cortana (asistente)
- Se abre manteniendo pulsado Home (WOBA se registra como asistente digital) o desde su tile; también responde a ASSIST y VOICE_COMMAND.
- Entrada por voz (reconocedor de otra app, normalmente el de Google) o texto; puede leer las respuestas en voz alta.
- Comandos locales, en español e inglés: abrir apps, llamar (por nombre o número), buscar en internet, crear alarmas, crear temporizadores, decir la hora, la fecha y el nivel de batería, abrir Wi-Fi, Bluetooth y ajustes.
- Lo que no es un comando se envía a Gemini con la API key y el modelo que configure el usuario (Ajustes > Extras > Cortana). Se puede olvidar la conversación.

## Teclado
Teclado propio estilo Windows Phone (activar en *Keyboard & assistant*).
- Teclas planas con burbuja de vista previa, tema y acento del launcher, páginas de símbolos (&123), emoji, acentos al mantener pulsada una tecla, borrado con repetición.
- Autocorrección con diccionarios español e inglés (borrar justo después la deshace), sugerencias, mayúscula automática, doble espacio para punto, palabras aprendidas.
- Dictado por voz, punto de control del cursor, teclado a una mano.
- Ajustes: altura, tamaño de tecla, estilo, sonido, vibración, vista previa, retardo de acentos, idiomas, olvidar palabras aprendidas y restablecer.

## Apps simuladas
Las apps tienen aspecto de Windows Phone. Los datos propios se guardan en JSON dentro de la app. En la lista de apps aparecen 23 (la pantalla de llamada no se lista).

| App | Qué hace |
|---|---|
| Phone | Historial (real y simulado), marcación rápida, buzón de voz y teclado de marcación |
| Call | Pantalla de llamada entrante, saliente y activa, con tono, vibración y registro |
| People | Lista alfabética con salto por letras, favoritos, novedades y ficha de contacto editable |
| Messaging | Conversaciones con burbujas; envío real por SMS si hay permiso o simulado |
| Camera | Cámara real (Camera2): foto, vídeo, flash, zoom con pellizco, frontal y trasera, cuadrículas, relación de aspecto, calidad de vídeo y *Rich Capture* (HDR y noche). Guarda en DCIM/Camera |
| Photos | Colección y álbumes reales, visor con zoom, favoritas, compartir y usar como fondo |
| Music | Canciones, álbumes y artistas del dispositivo con reproductor *Now playing* que sigue sonando al cerrar la app |
| Movies & TV | Vídeos del dispositivo con reproductor propio |
| FM Radio | Radio simulada con dial, presets, ruido entre emisoras y melodías sintetizadas (Android no tiene receptor FM) |
| Microsoft Edge | Navegador sobre WebView: pestañas, favoritos, historial, lista de lectura y vista de lectura |
| Maps | Mapas de OpenStreetMap: arrastrar, pellizcar, ubicación, búsqueda y rutas (se abren en Google Maps) |
| Weather | Tiempo actual y previsión de 7 días con Open-Meteo |
| Outlook Mail | Bandeja, enviados, borradores, eliminados y marcados; redactar y leer |
| Calendar | Vistas de día, agenda y mes; eventos propios con recordatorio real; importa el calendario de Android |
| Alarms & Clock | Alarmas reales (vía el reloj de Android), reloj mundial, temporizador y cronómetro |
| Calculator | Estándar y científica |
| OneNote | Notas con cuadernos, búsqueda, compartir y autoguardado |
| Office | Word (texto), Excel (hoja con fórmulas básicas) y PowerPoint (diapositivas con presentación) |
| Files | Explorador real del almacenamiento: copiar, mover, renombrar, borrar y compartir |
| Wallet | Tarjetas y pases locales y "toca para pagar" simulado (números ficticios, no salen del teléfono) |
| Store | Catálogo estilo Microsoft Store; "instalar" abre la ficha real en Google Play |
| Xbox | Logros |
| Help + Tips | Consejos de uso |
| OneDrive | Pantalla de almacenamiento simulada, local |
