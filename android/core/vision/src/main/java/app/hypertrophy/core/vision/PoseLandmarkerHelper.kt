package app.hypertrophy.core.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.hypertrophy.core.kinematics.Landmark
import app.hypertrophy.core.kinematics.PoseFrame
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device MediaPipe PoseLandmarker wrapper in LIVE_STREAM mode.
 *
 * Frames arrive from CameraX, are rotated in memory, and are submitted to the
 * GPU delegate first. If the GPU delegate cannot initialize (older devices,
 * missing GL support) the helper rebuilds the landmarker on CPU. No frame is
 * ever written to disk or sent over the network.
 */
class PoseLandmarkerHelper(
    private val context: Context,
    private val listener: Listener,
    private val minPoseDetectionConfidence: Float = 0.5f,
    private val minPosePresenceConfidence: Float = 0.5f,
    private val minTrackingConfidence: Float = 0.5f,
) {
    enum class PoseDelegate {
        GPU,
        CPU,
    }

    interface Listener {
        fun onPoseFrame(frame: PoseFrame)
        fun onPoseError(message: String)
        fun onDelegateChanged(delegate: PoseDelegate)
    }

    var activeDelegate: PoseDelegate = PoseDelegate.CPU
        private set

    private var landmarker: PoseLandmarker? = null
    private val lastTimestamp = java.util.concurrent.atomic.AtomicLong(0L)
    private val closed = AtomicBoolean(false)

    val isReady: Boolean get() = landmarker != null

    /**
     * Builds the landmarker, preferring the GPU delegate. Safe to call more
     * than once; the first successful setup wins.
     */
    fun setup(): Boolean {
        if (landmarker != null) return true

        val gpu = attempt(PoseDelegate.GPU)
        if (gpu != null) {
            landmarker = gpu
            activeDelegate = PoseDelegate.GPU
            listener.onDelegateChanged(PoseDelegate.GPU)
            return true
        }

        val cpu = attempt(PoseDelegate.CPU)
        if (cpu != null) {
            landmarker = cpu
            activeDelegate = PoseDelegate.CPU
            listener.onDelegateChanged(PoseDelegate.CPU)
            return true
        }

        listener.onPoseError("Pose model could not be loaded on GPU or CPU")
        return false
    }

    /** Submits an already rotated frame. The timestamp drives MediaPipe tracking. */
    fun detect(bitmap: Bitmap, timestampMs: Long = SystemClock.uptimeMillis()) {
        if (closed.get()) return
        val active = landmarker ?: return
        lastTimestamp.set(timestampMs)
        val image = BitmapImageBuilder(bitmap).build()
        runCatching { active.detectAsync(image, timestampMs) }
            .onFailure { listener.onPoseError(it.message ?: "Pose detection failed") }
    }

    fun close() {
        closed.set(true)
        runCatching { landmarker?.close() }
        landmarker = null
    }

    private fun attempt(delegate: PoseDelegate): PoseLandmarker? = runCatching {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(if (delegate == PoseDelegate.GPU) Delegate.GPU else Delegate.CPU)
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setResultListener { result, _ -> deliver(result) }
            .setErrorListener { error ->
                listener.onPoseError(error.message ?: "Pose landmarker error")
            }
            .build()

        PoseLandmarker.createFromOptions(context, options)
    }.onFailure { error ->
        Log.w(TAG, "PoseLandmarker ${delegate.name} delegate unavailable", error)
    }.getOrNull()

    private fun deliver(result: PoseLandmarkerResult) {
        val pose = result.landmarks().firstOrNull()
        val landmarks = pose?.map { landmark ->
            Landmark(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0f),
            )
        } ?: emptyList()

        listener.onPoseFrame(
            PoseFrame(
                landmarks = landmarks,
                timestampMillis = lastTimestamp.get().takeIf { it > 0L }
                    ?: SystemClock.uptimeMillis(),
            ),
        )
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
        private const val TAG = "PoseLandmarkerHelper"

        /** Rotates a camera frame upright before inference. */
        fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}

/**
 * CameraX analyzer with STRATEGY_KEEP_ONLY_LATEST upstream. Every ImageProxy is
 * closed in a finally block; only the derived landmark list escapes.
 */
class PoseImageAnalyzer(
    private val helper: PoseLandmarkerHelper,
) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            if (!helper.isReady) return
            val bitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val upright = PoseLandmarkerHelper.rotate(bitmap, rotation)
            helper.detect(upright, SystemClock.uptimeMillis())
        } catch (error: Throwable) {
            Log.w("PoseImageAnalyzer", "Frame analysis failed", error)
        } finally {
            image.close()
        }
    }
}
