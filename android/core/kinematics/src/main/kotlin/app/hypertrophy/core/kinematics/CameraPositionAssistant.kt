package app.hypertrophy.core.kinematics

import kotlin.math.roundToInt

enum class CameraAimState {
    ALIGNING,
    LOCKED_READY,
}

/**
 * Guidance for placing the phone so the full body stays in frame.
 *
 * [progress] is the fraction of the required 1.5 second hold. The assistant
 * locks only while framing and visibility stay healthy, and drops back to
 * ALIGNING as soon as the athlete leaves the frame.
 */
data class CameraAim(
    val state: CameraAimState,
    val framingOk: Boolean,
    val visibility: Float,
    val progress: Float,
    val message: String,
) {
    val isLocked: Boolean get() = state == CameraAimState.LOCKED_READY
    val progressPercent: Int get() = (progress * 100f).roundToInt().coerceIn(0, 100)
}

/**
 * Validates head-to-ankle landmark framing plus lighting/visibility confidence.
 * A full-body view held for [requiredHoldMs] promotes ALIGNING -> LOCKED_READY.
 */
class CameraPositionAssistant(
    private val requiredHoldMs: Long = 1_500L,
    private val minVisibility: Float = PoseMath.MIN_VISIBILITY,
) {
    var state: CameraAimState = CameraAimState.ALIGNING
        private set

    var progress: Float = 0f
        private set

    private var stableSinceMs: Long? = null

    fun update(frame: PoseFrame): CameraAim {
        val visibility = PoseMath.averageVisibility(frame)
        val framingOk = PoseMath.isFullyFramed(frame) && visibility > minVisibility

        if (!framingOk) {
            stableSinceMs = null
            progress = 0f
            state = CameraAimState.ALIGNING
            return CameraAim(
                state = state,
                framingOk = false,
                visibility = visibility,
                progress = 0f,
                message = guidance(frame, visibility),
            )
        }

        val start = stableSinceMs ?: frame.timestampMillis.also { stableSinceMs = it }
        val heldMs = (frame.timestampMillis - start).coerceAtLeast(0L)
        progress = (heldMs.toFloat() / requiredHoldMs.toFloat()).coerceIn(0f, 1f)

        state = if (heldMs >= requiredHoldMs) {
            CameraAimState.LOCKED_READY
        } else {
            CameraAimState.ALIGNING
        }

        val message = when (state) {
            CameraAimState.LOCKED_READY -> "Locked in. Start your set."
            else -> "Hold still for ${((requiredHoldMs - heldMs) / 1000f).coerceAtLeast(0.1f)}s"
        }

        return CameraAim(
            state = state,
            framingOk = true,
            visibility = visibility,
            progress = progress,
            message = message,
        )
    }

    fun reset() {
        state = CameraAimState.ALIGNING
        progress = 0f
        stableSinceMs = null
    }

    private fun guidance(frame: PoseFrame, visibility: Float): String {
        if (visibility <= minVisibility) {
            return "Add light or face the camera"
        }
        if (frame.landmarks.size < PoseLandmarks.COUNT) {
            return "Step back until your whole body is visible"
        }
        val head = frame.landmark(PoseLandmarks.NOSE)
        if (head == null || !PoseMath.isVisible(head) || head.y < 0.02f) {
            return "Tilt the phone up or step back - head is clipped"
        }
        val ankles = listOf(
            frame.landmark(PoseLandmarks.LEFT_ANKLE),
            frame.landmark(PoseLandmarks.RIGHT_ANKLE),
        )
        if (ankles.any { it == null || !PoseMath.isVisible(it) || it.y > 0.98f }) {
            return "Step back - feet are clipped"
        }
        return "Center yourself in the frame"
    }
}
