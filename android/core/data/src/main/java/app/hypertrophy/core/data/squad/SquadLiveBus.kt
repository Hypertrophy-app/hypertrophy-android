package app.hypertrophy.core.data.squad

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-feature bus for teammate progress. `feature:squad` publishes frames
 * received from the SquadRoomDO socket; the coach HUD observes them to render
 * the live teammate ticker without either feature depending on the other.
 */
data class TeammateSetProgress(
    val userId: String,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val isPr: Boolean,
    val at: Long,
)

object SquadLiveBus {
    private val lock = Any()
    private val latest = linkedMapOf<String, TeammateSetProgress>()

    private val _teammateProgress = MutableStateFlow<Map<String, TeammateSetProgress>>(emptyMap())
    val teammateProgress: StateFlow<Map<String, TeammateSetProgress>>
        get() = _teammateProgress

    /** Entries older than [MAX_AGE_MILLIS] are dropped so stale sets never show. */
    fun publish(progress: TeammateSetProgress, nowMillis: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            latest[progress.userId] = progress
            latest.entries.removeAll { nowMillis - it.value.at > MAX_AGE_MILLIS }
            _teammateProgress.value = latest.toMap()
        }
    }

    fun clear() {
        synchronized(lock) {
            latest.clear()
            _teammateProgress.value = emptyMap()
        }
    }

    const val MAX_AGE_MILLIS = 30_000L
}
