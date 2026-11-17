package app.hypertrophy.core.kinematics

import kotlin.math.abs

/** Real-time form rules evaluated against the current pose frame. */
enum class FormFault {
    KNEE_CAVE,
    TORSO_LEAN,
    INSUFFICIENT_DEPTH,
    BAR_PATH_ASYMMETRY,
}

/**
 * A spoken/visual correction. [jointIndex] points at the MediaPipe landmark to
 * pulse crimson in the skeleton overlay.
 */
data class FormCue(
    val fault: FormFault,
    val message: String,
    val jointIndex: Int,
    val severity: Float,
    val timestampMillis: Long,
)

/**
 * Evaluates real-time rules and emits at most one cue every [throttleMs]
 * (default 4 seconds) so the voice coach never talks over itself.
 *
 * Depth is judged from the deepest angle reached in the current rep, supplied
 * by [ExerciseStateMachine.minAngleThisRep]. During CONCENTRIC a deepest angle
 * above the target depth band means the athlete cut the rep short.
 */
class FormFaultDetector(
    private val throttleMs: Long = 4_000L,
    private val kneeCaveRatio: Float = 0.45f,
    private val shoulderAsymmetry: Float = 0.035f,
) {
    var lastCue: FormCue? = null
        private set

    private var lastEmittedAtMs: Long? = null

    fun evaluate(
        frame: PoseFrame,
        analyzer: BiomechanicalAnalyzer,
        phase: RepPhase,
        minAngleThisRep: Float? = null,
    ): FormCue? {
        if (phase == RepPhase.TOP_READY || phase == RepPhase.REP_COMPLETE) return null

        val candidate = when {
            phase == RepPhase.ECCENTRIC || phase == RepPhase.BOTTOM_INFLECTION -> {
                kneeCaveCue(frame) ?: torsoLeanCue(frame, analyzer)
            }

            phase == RepPhase.CONCENTRIC -> {
                depthCue(analyzer, minAngleThisRep)
                    ?: torsoLeanCue(frame, analyzer)
                    ?: asymmetryCue(frame)
            }

            else -> null
        } ?: return null

        val timestamp = frame.timestampMillis
        val last = lastEmittedAtMs
        if (last != null && timestamp - last < throttleMs) return null

        lastEmittedAtMs = timestamp
        val timed = candidate.copy(timestampMillis = timestamp)
        lastCue = timed
        return timed
    }

    fun reset() {
        lastCue = null
        lastEmittedAtMs = null
    }

    private fun kneeCaveCue(frame: PoseFrame): FormCue? {
        val ratio = PoseMath.kneeValgus(
            leftKnee = frame.landmark(PoseLandmarks.LEFT_KNEE),
            rightKnee = frame.landmark(PoseLandmarks.RIGHT_KNEE),
            leftAnkle = frame.landmark(PoseLandmarks.LEFT_ANKLE),
            rightAnkle = frame.landmark(PoseLandmarks.RIGHT_ANKLE),
        )
        if (ratio <= kneeCaveRatio) return null

        val leftKnee = frame.landmark(PoseLandmarks.LEFT_KNEE)
        val rightKnee = frame.landmark(PoseLandmarks.RIGHT_KNEE)
        val joint = if (
            leftKnee != null && rightKnee != null && leftKnee.x > rightKnee.x
        ) {
            PoseLandmarks.RIGHT_KNEE
        } else {
            PoseLandmarks.LEFT_KNEE
        }

        return FormCue(
            fault = FormFault.KNEE_CAVE,
            message = "Knees caving in. Push them out over your toes.",
            jointIndex = joint,
            severity = ((ratio - kneeCaveRatio) / 0.4f).coerceIn(0.2f, 1f),
            timestampMillis = 0L,
        )
    }

    private fun torsoLeanCue(frame: PoseFrame, analyzer: BiomechanicalAnalyzer): FormCue? {
        val inclination = PoseMath.torsoInclination(
            leftShoulder = frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            rightShoulder = frame.landmark(PoseLandmarks.RIGHT_SHOULDER),
            leftHip = frame.landmark(PoseLandmarks.LEFT_HIP),
            rightHip = frame.landmark(PoseLandmarks.RIGHT_HIP),
        ) ?: return null

        if (inclination <= analyzer.maxTorsoInclination) return null

        return FormCue(
            fault = FormFault.TORSO_LEAN,
            message = "Chest up. Reduce the torso lean.",
            jointIndex = PoseLandmarks.LEFT_SHOULDER,
            severity = (
                (inclination - analyzer.maxTorsoInclination) /
                    analyzer.maxTorsoInclination.coerceAtLeast(1f)
                ).coerceIn(0.2f, 1f),
            timestampMillis = 0L,
        )
    }

    private fun depthCue(analyzer: BiomechanicalAnalyzer, minAngleThisRep: Float?): FormCue? {
        if (minAngleThisRep == null) return null
        val target = analyzer.bottomAngle + analyzer.depthTolerance
        if (minAngleThisRep <= target) return null

        return FormCue(
            fault = FormFault.INSUFFICIENT_DEPTH,
            message = "Go deeper. Hit full depth next rep.",
            jointIndex = PoseLandmarks.LEFT_KNEE,
            severity = ((minAngleThisRep - target) / 45f).coerceIn(0.2f, 1f),
            timestampMillis = 0L,
        )
    }

    private fun asymmetryCue(frame: PoseFrame): FormCue? {
        val left = frame.landmark(PoseLandmarks.LEFT_SHOULDER) ?: return null
        val right = frame.landmark(PoseLandmarks.RIGHT_SHOULDER) ?: return null
        if (!PoseMath.isVisible(left) || !PoseMath.isVisible(right)) return null

        val drop = abs(left.y - right.y)
        if (drop <= shoulderAsymmetry) return null

        return FormCue(
            fault = FormFault.BAR_PATH_ASYMMETRY,
            message = "Keep the bar level. One side is drifting.",
            jointIndex = if (left.y > right.y) {
                PoseLandmarks.LEFT_SHOULDER
            } else {
                PoseLandmarks.RIGHT_SHOULDER
            },
            severity = (drop / 0.1f).coerceIn(0.2f, 1f),
            timestampMillis = 0L,
        )
    }
}
