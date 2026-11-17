package app.hypertrophy.feature.coach.music

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class MusicState(
    val available: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
)

/**
 * Reads the user's active media session so the HUD can show the current track
 * and drive Play/Pause/Next without leaving the workout.
 *
 * Active-session discovery requires notification-listener access, which the
 * user grants in system settings. Without it the controller reports
 * `available = false` and the HUD offers an enable shortcut.
 */
@Singleton
class MusicController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val listenerComponent =
        ComponentName(context, HypertrophyNotificationListener::class.java)

    @Volatile
    private var controller: MediaController? = null

    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(':').any { flattened ->
            ComponentName.unflattenFromString(flattened)?.packageName == context.packageName
        }
    }

    fun state(): MusicState {
        if (!hasNotificationAccess()) return MusicState(available = false)

        val sessions = runCatching {
            mediaSessionManager.getActiveSessions(listenerComponent)
        }.getOrElse { return MusicState(available = false) }

        val active = sessions.firstOrNull() ?: return MusicState(available = false)
        controller = active

        val metadata = active.metadata
        return MusicState(
            available = true,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            isPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun togglePlayPause() {
        val active = controller ?: return
        val isPlaying = active.playbackState?.state == PlaybackState.STATE_PLAYING
        runCatching {
            if (isPlaying) {
                active.transportControls.pause()
            } else {
                active.transportControls.play()
            }
        }
    }

    fun next() {
        runCatching { controller?.transportControls?.skipToNext() }
    }

    companion object {
        fun settingsIntent(): android.content.Intent =
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
