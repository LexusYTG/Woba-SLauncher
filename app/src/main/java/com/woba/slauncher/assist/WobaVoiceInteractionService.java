package com.woba.slauncher.assist;

import android.service.voice.VoiceInteractionService;

/**
 * Esto es lo que hace que WOBA aparezca en Ajustes > Apps predeterminadas > Asistente digital.
 * El sistema lo enlaza y, al mantener pulsado Home (o el botón de encendido / gesto del asistente),
 * pide una sesión (WobaVoiceInteractionSession) que abre Cortana.
 */
public class WobaVoiceInteractionService extends VoiceInteractionService {
}
