package app.hypertrophy.core.kinematics

import kotlin.math.roundToLong

/** Milliseconds spent in each rep phase. */
data class TempoSnapshot(
    val eccentricMs: Long,
    val pauseMs: Long,
    val concentricMs: Long,
) {
    /** Compact 3-1-1 style label, rounded to whole seconds. */
    fun label(): String {
        fun seconds(ms: Long): Long = (ms / 1000.0).roundToLong().coerceAtLeast(0)
        return "${seconds(eccentricMs)}-${seconds(pauseMs)}-${seconds(concentricMs)}"
    }

    val totalMs: Long get() = eccentricMs + pauseMs + concentricMs
}

/** Records phase transitions for one rep at millisecond precision. */
class TempoTracker {
    private var eccentricStart: Long? = null
    private var bottomEnter: Long? = null
    private var concentricStart: Long? = null

    fun onEccentricStart(timestampMillis: Long) {
        eccentricStart = timestampMillis
        bottomEnter = null
        concentricStart = null
    }

    fun onBottomReached(timestampMillis: Long) {
        bottomEnter = timestampMillis
    }

    fun onConcentricStart(timestampMillis: Long) {
        concentricStart = timestampMillis
    }

    fun onRepComplete(timestampMillis: Long): TempoSnapshot {
        val start = eccentricStart ?: timestampMillis
        val bottom = bottomEnter ?: start
        val concentric = concentricStart ?: bottom
        val snapshot = TempoSnapshot(
            eccentricMs = (bottom - start).coerceAtLeast(0),
            pauseMs = (concentric - bottom).coerceAtLeast(0),
            concentricMs = (timestampMillis - concentric).coerceAtLeast(0),
        )
        eccentricStart = null
        bottomEnter = null
        concentricStart = null
        return snapshot
    }

    fun reset() {
        eccentricStart = null
        bottomEnter = null
        concentricStart = null
    }
}
