package app.hypertrophy.core.kinematics

/** Ordered rep phases used by the HUD and the tempo tracker. */
enum class RepPhase {
    TOP_READY,
    ECCENTRIC,
    BOTTOM_INFLECTION,
    CONCENTRIC,
    REP_COMPLETE,
}

/** One completed rep with the achieved range of motion and measured tempo. */
data class RepRecord(
    val index: Int,
    val romPercent: Float,
    val deepestAngle: Float,
    val tempo: TempoSnapshot,
    val valid: Boolean,
)

/** Immutable output of one frame evaluation. */
data class RepUpdate(
    val phase: RepPhase,
    val angle: Float?,
    val romPercent: Float,
    val completedRep: RepRecord? = null,
) {
    val repCompleted: Boolean get() = completedRep != null
}

/**
 * Rep and range-of-motion counter for a single biomechanical analyzer.
 *
 * Transitions: TOP_READY -> ECCENTRIC -> BOTTOM_INFLECTION -> CONCENTRIC ->
 * REP_COMPLETE -> TOP_READY. A rep that turns around before reaching the
 * target depth band is still completed (so the athlete gets feedback) but is
 * recorded as invalid and does not increment [completedReps].
 *
 * The target depth band is `bottomAngle + depthTolerance`. The top band is
 * `topAngle - topZoneDelta`. Angles beyond the target clamp ROM at 100%.
 */
class ExerciseStateMachine(
    val analyzer: BiomechanicalAnalyzer,
    private val topZoneDelta: Float = 15f,
    private val turnaroundDelta: Float = 10f,
    private val bottomExitDelta: Float = 10f,
) {
    var phase: RepPhase = RepPhase.TOP_READY
        private set

    var completedReps: Int = 0
        private set

    var lastRep: RepRecord? = null
        private set

    /** Deepest angle reached in the rep currently being tracked. */
    var minAngleThisRep: Float? = null
        private set

    private val tempo = TempoTracker()
    private var shallowRep = false

    /** Live ROM gauge value 0..100 for the rep in progress. */
    val currentRomPercent: Float
        get() = minAngleThisRep?.let(::romPercentFor) ?: 0f

    val targetDepthAngle: Float get() = analyzer.bottomAngle + analyzer.depthTolerance

    val isTargetDepthReached: Boolean
        get() = minAngleThisRep?.let { it <= targetDepthAngle } ?: false

    fun process(frame: PoseFrame): RepUpdate {
        val angle = analyzer.primaryAngle(frame)
        val timestamp = frame.timestampMillis

        // Confidence gating: hold the current phase rather than inventing a rep.
        if (angle == null) {
            return RepUpdate(phase, null, currentRomPercent)
        }

        if (phase == RepPhase.REP_COMPLETE) {
            phase = RepPhase.TOP_READY
            minAngleThisRep = null
            shallowRep = false
        }

        var completed: RepRecord? = null

        when (phase) {
            RepPhase.TOP_READY -> {
                if (angle < analyzer.topAngle - topZoneDelta) {
                    beginRep(timestamp, angle)
                }
            }

            RepPhase.ECCENTRIC -> {
                if (angle < (minAngleThisRep ?: angle)) {
                    minAngleThisRep = angle
                }
                when {
                    angle <= targetDepthAngle -> {
                        phase = RepPhase.BOTTOM_INFLECTION
                        tempo.onBottomReached(timestamp)
                    }
                    angle > (minAngleThisRep ?: angle) + turnaroundDelta -> {
                        // Turned around above the depth band: count as a shallow rep.
                        shallowRep = true
                        phase = RepPhase.CONCENTRIC
                        tempo.onConcentricStart(timestamp)
                    }
                }
            }

            RepPhase.BOTTOM_INFLECTION -> {
                if (angle < (minAngleThisRep ?: angle)) {
                    minAngleThisRep = angle
                }
                if (angle > targetDepthAngle + bottomExitDelta) {
                    phase = RepPhase.CONCENTRIC
                    tempo.onConcentricStart(timestamp)
                }
            }

            RepPhase.CONCENTRIC -> {
                if (angle >= analyzer.topAngle - topZoneDelta) {
                    completed = finishRep(timestamp)
                }
            }

            RepPhase.REP_COMPLETE -> Unit
        }

        val rom = if (completed != null) completed.romPercent else currentRomPercent
        return RepUpdate(
            phase = phase,
            angle = angle,
            romPercent = rom,
            completedRep = completed,
        )
    }

    fun reset() {
        phase = RepPhase.TOP_READY
        completedReps = 0
        lastRep = null
        minAngleThisRep = null
        shallowRep = false
        tempo.reset()
    }

    private fun beginRep(timestamp: Long, angle: Float) {
        phase = RepPhase.ECCENTRIC
        shallowRep = false
        minAngleThisRep = angle
        tempo.onEccentricStart(timestamp)
    }

    private fun finishRep(timestamp: Long): RepRecord {
        val deepest = minAngleThisRep ?: analyzer.topAngle
        val rom = romPercentFor(deepest)
        val snapshot = tempo.onRepComplete(timestamp)
        val valid = !shallowRep && deepest <= targetDepthAngle
        val record = RepRecord(
            index = completedReps + 1,
            romPercent = rom,
            deepestAngle = deepest,
            tempo = snapshot,
            valid = valid,
        )
        lastRep = record
        if (valid) completedReps++
        phase = RepPhase.REP_COMPLETE
        minAngleThisRep = deepest
        shallowRep = false
        return record
    }

    private fun romPercentFor(angle: Float): Float {
        val span = (analyzer.topAngle - analyzer.bottomAngle).coerceAtLeast(1f)
        return ((analyzer.topAngle - angle) / span * 100f).coerceIn(0f, 100f)
    }
}
