package app.hypertrophy.feature.coach.music

import android.service.notification.NotificationListenerService

/**
 * Bound by the system only after the user grants notification access. It
 * exists so [MusicController] can call MediaSessionManager.getActiveSessions
 * with a valid listener component.
 */
class HypertrophyNotificationListener : NotificationListenerService()
