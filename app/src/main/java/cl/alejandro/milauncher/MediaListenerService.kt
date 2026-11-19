package cl.alejandro.milauncher

import android.service.notification.NotificationListenerService

/**
 * Servicio vacío. Android solo deja leer las sesiones de música (qué canción suena)
 * a apps que tienen "acceso a notificaciones", y ese acceso se concede a un servicio como este.
 * No lee ni guarda ninguna notificación.
 */
class MediaListenerService : NotificationListenerService()
