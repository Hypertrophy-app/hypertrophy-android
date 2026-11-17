package app.hypertrophy.core.kinematics

import kotlin.math.abs

/** Result of a Quick Workout auto-classification pass. */
data class ExerciseClassification(
    val kind: ExerciseKind,
    val confidence: Float,
    val reason: String,
)

/**
 * Sliding-window exercise classifier for Quick Workout mode.
 *
 * The classifier keeps the latest [windowSize] frames (60 frames by default,
 * roughly two seconds at 30 fps) and compares the variance of joint
 * velocities. Lower-body dominance separates squats from hinges using average
 * torso inclination. Upper-body dominance uses elbow excursion plus wrist
 * travel to separate pushes, pulls, and isolation curls/raises.
 *
 * Everything runs on-device and statelessly per call, so it can be restarted
 * between sets without leaking history.
 */
class ExerciseAutoClassifier(
    private val windowSize: Int = 60,
) {
    private val window = ArrayDeque<PoseFrame>()

    val frameCount: Int get() = window.size
    val isReady: Boolean get() = window.size >= windowSize

    fun addFrame(frame: PoseFrame) {
        window.addLast(frame)
        while (window.size > windowSize) window.removeFirst()
    }

    fun reset() = window.clear()

    fun classify(): ExerciseClassification? {
        if (!isReady) return null
        val frames = window.toList()

        val kneeAngles = frames.mapNotNull { kneeAngle(it) }
        val elbowAngles = frames.mapNotNull { elbowAngle(it) }
        if (kneeAngles.size < 10 || elbowAngles.size < 10) return null

        val kneeRange = range(kneeAngles)
        val elbowRange = range(elbowAngles)
        // Knees and ankles both swing in a squat; ankles catch the shin travel
        // when the knee itself stays under the hip.
        val kneeVelocityVariance = maxOf(
            velocityVariance(frames, PoseLandmarks.LEFT_KNEE),
            velocityVariance(frames, PoseLandmarks.LEFT_ANKLE),
        )
        // The wrist catches forearm rotation while a fixed wrist catches
        // elbow-driven pushes, so take the stronger upper-body signal.
        val upperVelocityVariance = maxOf(
            velocityVariance(frames, PoseLandmarks.LEFT_ELBOW),
            velocityVariance(frames, PoseLandmarks.LEFT_WRIST),
        )
        if (kneeVelocityVariance + upperVelocityVariance < MIN_MOVEMENT) {
            return ExerciseClassification(ExerciseKind.UNKNOWN, 0f, "Not enough movement")
        }

        val lowerDominant = kneeVelocityVariance > upperVelocityVariance * 1.2f
        val upperDominant = upperVelocityVariance > kneeVelocityVariance * 1.2f

        if (lowerDominant && kneeRange >= 20f) {
            val lean = averageTorsoLean(frames)
            val kind = if (lean >= HINGE_LEAN_DEGREES) ExerciseKind.HINGE else ExerciseKind.SQUAT
            val confidence = confidenceOf(kneeVelocityVariance, upperVelocityVariance)
            return ExerciseClassification(
                kind = kind,
                confidence = confidence,
                reason = "Knee excursion ${kneeRange.toInt()} deg, torso lean ${lean.toInt()} deg",
            )
        }

        if (upperDominant && elbowRange >= 25f) {
            val shoulderYRange = coordinateRange(frames, PoseLandmarks.LEFT_SHOULDER) { it.y }
            val wristYRange = coordinateRange(frames, PoseLandmarks.LEFT_WRIST) { it.y }
            val wristXRange = coordinateRange(frames, PoseLandmarks.LEFT_WRIST) { it.x }
            val lean = averageTorsoLean(frames)

            val verticalTravel = wristYRange > wristXRange * 1.4f
            val kind = when {
                verticalTravel && shoulderYRange < 0.06f && lean < CURL_LEAN_DEGREES ->
                    ExerciseKind.CURL_RAISE

                verticalTravel -> ExerciseKind.PULL
                else -> ExerciseKind.PUSH
            }
            val confidence = confidenceOf(upperVelocityVariance, kneeVelocityVariance)
            return ExerciseClassification(
                kind = kind,
                confidence = confidence,
                reason = "Elbow excursion ${elbowRange.toInt()} deg, wrist travel " +
                    "${(wristYRange * 100).toInt()}/${(wristXRange * 100).toInt()}",
            )
        }

        return ExerciseClassification(ExerciseKind.UNKNOWN, 0.2f, "Ambiguous movement")
    }

    private fun kneeAngle(frame: PoseFrame): Float? = PoseMath.angleBetween(
        frame.landmark(PoseLandmarks.LEFT_HIP),
        frame.landmark(PoseLandmarks.LEFT_KNEE),
        frame.landmark(PoseLandmarks.LEFT_ANKLE),
    )

    private fun elbowAngle(frame: PoseFrame): Float? = PoseMath.angleBetween(
        frame.landmark(PoseLandmarks.LEFT_SHOULDER),
        frame.landmark(PoseLandmarks.LEFT_ELBOW),
        frame.landmark(PoseLandmarks.LEFT_WRIST),
    )

    private fun averageTorsoLean(frames: List<PoseFrame>): Float {
        val samples = frames.mapNotNull {
            PoseMath.torsoInclination(
                it.landmark(PoseLandmarks.LEFT_SHOULDER),
                it.landmark(PoseLandmarks.RIGHT_SHOULDER),
                it.landmark(PoseLandmarks.LEFT_HIP),
                it.landmark(PoseLandmarks.RIGHT_HIP),
            )
        }
        return if (samples.isEmpty()) 0f else samples.average().toFloat()
    }

    /**
     * Variance of per-axis joint velocities over the window. Per-axis terms
     * matter because a constant-speed rotation has near-zero variance in speed
     * magnitude while each axis still accelerates and decelerates.
     */
    private fun velocityVariance(frames: List<PoseFrame>, index: Int): Float {
        val vx = ArrayList<Float>(frames.size)
        val vy = ArrayList<Float>(frames.size)
        val vz = ArrayList<Float>(frames.size)

        for (i in 1 until frames.size) {
            val previous = frames[i - 1]
            val current = frames[i]
            val a = previous.landmark(index) ?: continue
            val b = current.landmark(index) ?: continue
            if (!PoseMath.isVisible(a) || !PoseMath.isVisible(b)) continue
            val dt = (current.timestampMillis - previous.timestampMillis).coerceAtLeast(1L)
            val seconds = dt / 1000f
            vx += (b.x - a.x) / seconds
            vy += (b.y - a.y) / seconds
            vz += (b.z - a.z) / seconds
        }
        if (vx.size < 2) return 0f

        return variance(vx) + variance(vy) + variance(vz)
    }

    private fun variance(values: List<Float>): Float {
        val mean = values.average().toFloat()
        return values.sumOf { v ->
            val delta = (v - mean).toDouble()
            delta * delta
        }.div(values.size).toFloat()
    }

    private fun coordinateRange(
        frames: List<PoseFrame>,
        index: Int,
        selector: (Landmark) -> Float,
    ): Float {
        val values = frames.mapNotNull { pose ->
            pose.landmark(index)?.takeIf { PoseMath.isVisible(it) }?.let(selector)
        }
        if (values.isEmpty()) return 0f
        return (values.maxOrNull()!! - values.minOrNull()!!).let(::abs)
    }

    private fun range(values: List<Float>): Float =
        values.maxOrNull()!! - values.minOrNull()!!

    private fun confidenceOf(dominant: Float, other: Float): Float {
        val total = dominant + other
        if (total <= 1e-6f) return 0.5f
        return (dominant / total).coerceIn(0.5f, 0.99f)
    }

    private companion object {
        const val MIN_MOVEMENT = 0.0005f
        const val HINGE_LEAN_DEGREES = 40f
        const val CURL_LEAN_DEGREES = 30f
    }
}
