package app.hypertrophy.feature.coach.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline coach voice. Android TextToSpeech runs entirely on-device.
 *
 * Every utterance takes AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK so corrections are
 * audible over the athlete's music without stopping it. Focus is released when
 * the utterance finishes or errors.
 */
@Singleton
class VoiceCoach @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile
    private var ready = false

    private var tts: TextToSpeech? = null
    private var lastSpokenAtMs = 0L

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()

    val isReady: Boolean get() = ready

    fun initialize() {
        if (tts != null) return

        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.apply {
                    language = Locale.US
                    setSpeechRate(1.02f)
                    setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                abandonFocus()
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                abandonFocus()
                            }
                        },
                    )
                }
                ready = true
            }
        }
        tts = engine
    }

    /**
     * Speaks [text] with a small debounce. Repeated cues inside
     * [minIntervalMs] are dropped so the coach never stacks corrections.
     */
    fun announce(text: String, minIntervalMs: Long = 1_200L) {
        if (!ready || text.isBlank()) return
        val engine = tts ?: return

        val now = SystemClock.elapsedRealtime()
        if (now - lastSpokenAtMs < minIntervalMs) return

        val granted = audioManager.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return

        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            abandonFocus()
            return
        }
        lastSpokenAtMs = now
    }

    fun shutdown() {
        abandonFocus()
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    private fun abandonFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
    }

    private companion object {
        const val UTTERANCE_ID = "hypertrophy-coach"
    }
}
