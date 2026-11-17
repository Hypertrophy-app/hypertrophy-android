package app.hypertrophy.core.kinematics

/** Emitted once when a set is considered finished by the rest detector. */
data class SetCompletedEvent(
    val reps: Int,
    val setDurationMs: Long,
    val restHoldMs: Long,
    val timestampMillis: Long,
)

/**
 * Detects the transition from exercising to resting.
 *
 * After at least one valid rep, the detector captures an anchor pose and then
 * watches for a neutral standing posture whose landmark drift stays below
 * [driftThreshold] for [restHoldMs] (default 3.5 seconds). Comparing against an
 * anchor rather than frame-to-frame speed keeps camera jitter from looking like
 * movement. Whenever the athlete moves more than the threshold the anchor
 * follows them, so the hold re-arms instead of firing mid-rep.
 *
 * The detector fires exactly once per set; call [reset] when the next set
 * starts. Leaving the frame entirely also counts as leaving the exercise
 * posture, so walking away still closes the set.
 */
class AutoSetRestDetector(
    private val restHoldMs: Long = 3_500L,
    private val driftThreshold: Float = 0.06f,
    private val neutralTorsoDegrees: Float = 22f,
) {
    var restProgress: Float = 0f
        private set

    var isResting: Boolean = false
        private set

    private var anchorFrame: PoseFrame? = null
    private var firstFrameAtMs: Long? = null
    private var restStartedAtMs: Long? = null
    private var fired = false

    /**
     * @param repsCompleted the validated rep count for the current set.
     * @return the completion event on the frame the rest hold elapses, else null.
     */
    fun update(frame: PoseFrame, repsCompleted: Int): SetCompletedEvent? {
        if (firstFrameAtMs == null) firstFrameAtMs = frame.timestampMillis

        if (fired || repsCompleted < 1) {
            anchorFrame = null
            restStartedAtMs = null
            restProgress = 0f
            isResting = false
            return null
        }

        val anchor = anchorFrame
        if (anchor == null) {
            // First frame of the set only arms the anchor; the hold starts on
            // the next frame so a single noisy sample cannot finish a set.
            anchorFrame = frame
            restStartedAtMs = null
            restProgress = 0f
            isResting = false
            return null
        }

        val drift = drift(anchor, frame)
        val neutral = isNeutralPosture(frame)
        val restingNow = neutral && drift <= driftThreshold
        isResting = restingNow

        if (!restingNow) {
            anchorFrame = frame
            restStartedAtMs = null
            restProgress = 0f
            return null
        }

        val start = restStartedAtMs ?: frame.timestampMillis.also { restStartedAtMs = it }
        val heldMs = (frame.timestampMillis - start).coerceAtLeast(0L)
        restProgress = (heldMs.toFloat() / restHoldMs.toFloat()).coerceIn(0f, 1f)

        if (heldMs < restHoldMs) return null

        fired = true
        val setStart = firstFrameAtMs ?: start
        return SetCompletedEvent(
            reps = repsCompleted,
            setDurationMs = (start - setStart).coerceAtLeast(0L),
            restHoldMs = heldMs,
            timestampMillis = frame.timestampMillis,
        )
    }

    fun reset() {
        anchorFrame = null
        firstFrameAtMs = null
        restStartedAtMs = null
        restProgress = 0f
        isResting = false
        fired = false
    }

    /**
     * Largest landmark displacement from the anchor in normalized units. Only
     * landmarks visible in both frames are considered, which keeps a dropped
     * wrist from looking like movement. The maximum, rather than the average,
     * makes a single moving limb (for example shins during a squat) re-arm the
     * hold even when the torso stays still.
     */
    private fun drift(anchor: PoseFrame, current: PoseFrame): Float {
        var largest = 0f
        var count = 0

        for (index in PoseLandmarks.FRAMING_LANDMARKS) {
            val a = anchor.landmark(index) ?: continue
            val b = current.landmark(index) ?: continue
            if (!PoseMath.isVisible(a) || !PoseMath.isVisible(b)) continue
            largest = maxOf(largest, PoseMath.distance(a, b))
            count++
        }

        if (count == 0) return Float.MAX_VALUE
        return largest
    }

    private fun isNeutralPosture(frame: PoseFrame): Boolean {
        if (frame.landmarks.size < PoseLandmarks.COUNT) return true

        val inclination = PoseMath.torsoInclination(
            leftShoulder = frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            rightShoulder = frame.landmark(PoseLandmarks.RIGHT_SHOULDER),
            leftHip = frame.landmark(PoseLandmarks.LEFT_HIP),
            rightHip = frame.landmark(PoseLandmarks.RIGHT_HIP),
        ) ?: return true

        if (inclination > neutralTorsoDegrees) return false
        return !armsRaised(frame)
    }

    /** Arms at or above shoulder height mean the athlete is still pressing or pulling. */
    private fun armsRaised(frame: PoseFrame): Boolean {
        val shoulder = PoseMath.midpoint(
            frame.landmark(PoseLandmarks.LEFT_SHOULDER) ?: return false,
            frame.landmark(PoseLandmarks.RIGHT_SHOULDER) ?: return false,
        )
        val wrist = PoseMath.midpoint(
            frame.landmark(PoseLandmarks.LEFT_WRIST) ?: return false,
            frame.landmark(PoseLandmarks.RIGHT_WRIST) ?: return false,
        )
        return wrist.y < shoulder.y + 0.03f
    }
}
