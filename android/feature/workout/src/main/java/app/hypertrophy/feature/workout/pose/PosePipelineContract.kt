package app.hypertrophy.feature.workout.pose

/**
 * Contract for the on-device pose pipeline. CameraX feeds [android.media.ImageProxy]
 * frames to MediaPipe PoseLandmarker in memory, the landmark frame is scored, and
 * the ImageProxy is closed immediately in a finally block.
 *
 * Privacy invariant: no Bitmap, frame, or raw landmark stream is ever written to
 * disk, Room, R2, or the network. Only compact rep ticks and the aggregate set
 * summary leave the device.
 */
data class RepTick(
    val exerciseId: String,
    val repIndex: Int,
    val timestampMillis: Long,
    val peakConfidence: Float,
)

object PosePipelineContract {
    const val MIN_LANDMARK_VISIBILITY = 0.5f
}
