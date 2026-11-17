package app.hypertrophy.core.kinematics

/**
 * One normalized MediaPipe pose landmark. Coordinates are image-normalized
 * (0..1) with y growing downward; z is relative depth. Visibility is the
 * model's confidence that the landmark is present and unoccluded.
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f,
)

data class PoseFrame(
    val landmarks: List<Landmark>,
    val timestampMillis: Long,
) {
    fun landmark(index: Int): Landmark? = landmarks.getOrNull(index)

    val isComplete: Boolean get() = landmarks.size >= PoseLandmarks.COUNT
}

object PoseLandmarks {
    const val COUNT = 33
    const val NOSE = 0
    const val LEFT_EYE = 2
    const val RIGHT_EYE = 5
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    /** Head through ankles; used by the camera framing assistant. */
    val FRAMING_LANDMARKS = listOf(
        NOSE,
        LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE,
        LEFT_ANKLE, RIGHT_ANKLE,
    )
}
