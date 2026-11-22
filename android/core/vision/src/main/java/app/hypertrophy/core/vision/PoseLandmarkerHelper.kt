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
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PoseLandmarkerHelper"

/**
 * On-device MediaPipe PoseLandmarker wrapper in LIVE_STREAM mode.
 *
 * Frames arrive from CameraX, are rotated in memory, and are submitted to the
 * GPU delegate first. If the GPU delegate cannot initialize (older devices,
 * missing GL support) the helper rebuilds the landmarker on CPU. No frame is
 * ever written to disk or sent over the network.
 *
 * MediaPipe construction sits behind [PoseEngineFactory] so setup, fallback,
 * and close behavior can be unit tested without loading native libraries.
 */
class PoseLandmarkerHelper internal constructor(
    private val engineFactory: PoseEngineFactory,
    private val listener: Listener,
) {
    constructor(
        context: Context,
        listener: Listener,
        minPoseDetectionConfidence: Float = 0.5f,
        minPosePresenceConfidence: Float = 0.5f,
        minTrackingConfidence: Float = 0.5f,
    ) : this(
        engineFactory = MediaPipePoseEngineFactory(
            context = context,
            listener = listener,
            minPoseDetectionConfidence = minPoseDetectionConfidence,
            minPosePresenceConfidence = minPosePresenceConfidence,
            minTrackingConfidence = minTrackingConfidence,
        ),
        listener = listener,
    )

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

    private var engine: PoseEngine? = null
    private val closed = AtomicBoolean(false)

    val isReady: Boolean get() = engine != null

    /**
     * Builds the engine, preferring the GPU delegate. Safe to call more than
     * once; the first successful setup wins.
     */
    fun setup(): Boolean {
        if (engine != null) return true

        val failures = mutableListOf<String>()

        val gpu = attemptEngine(PoseDelegate.GPU, failures)
        if (gpu != null) {
            engine = gpu
            activeDelegate = PoseDelegate.GPU
            listener.onDelegateChanged(PoseDelegate.GPU)
            return true
        }

        val cpu = attemptEngine(PoseDelegate.CPU, failures)
        if (cpu != null) {
            engine = cpu
            activeDelegate = PoseDelegate.CPU
            listener.onDelegateChanged(PoseDelegate.CPU)
            return true
        }

        // The underlying cause (missing/compressed asset, unsupported delegate,
        // native library failure) is far more useful than a generic message.
        val cause = failures.firstOrNull { it.isNotBlank() }
        listener.onPoseError(
            if (cause == null) {
                "Pose model could not be loaded on GPU or CPU"
            } else {
                "Pose model could not be loaded on GPU or CPU: $cause"
            },
        )
        return false
    }

    /** Submits an already rotated frame. The timestamp drives MediaPipe tracking. */
    fun detect(bitmap: Bitmap, timestampMs: Long = SystemClock.uptimeMillis()) {
        detectFrame(PoseImage(bitmap), timestampMs)
    }

    /** Internal seam so JVM tests can exercise the closed/no-engine guards. */
    internal fun detectFrame(image: PoseImage, timestampMs: Long) {
        if (closed.get()) return
        val active = engine ?: return
        runCatching { active.detectAsync(image, timestampMs) }
            .onFailure { listener.onPoseError(it.message ?: "Pose detection failed") }
    }

    fun close() {
        closed.set(true)
        runCatching { engine?.close() }
        engine = null
    }

    /** A factory failure marks the delegate unavailable; CPU fallback still runs. */
    private fun attemptEngine(
        delegate: PoseDelegate,
        failures: MutableList<String>,
    ): PoseEngine? = try {
        engineFactory.create(delegate)
    } catch (error: Throwable) {
        Log.w(TAG, "PoseLandmarker $delegate delegate unavailable", error)
        failures += error.message?.takeIf { it.isNotBlank() }
            ?: error::class.java.simpleName
        null
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"

        /** Rotates a camera frame upright before inference. */
        fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}

/** Frame handle for a [PoseEngine]. Tests omit the bitmap to stay off Android graphics. */
internal class PoseImage(val bitmap: Bitmap?)

internal interface PoseEngine {
    fun detectAsync(image: PoseImage, timestampMs: Long)
    fun close()
}

internal interface PoseEngineFactory {
    fun create(delegate: PoseLandmarkerHelper.PoseDelegate): PoseEngine?
}

/** Builds live-stream MediaPipe landmarkers; the helper owns delegate fallback. */
internal class MediaPipePoseEngineFactory(
    private val context: Context,
    private val listener: PoseLandmarkerHelper.Listener,
    private val minPoseDetectionConfidence: Float,
    private val minPosePresenceConfidence: Float,
    private val minTrackingConfidence: Float,
) : PoseEngineFactory {
    override fun create(delegate: PoseLandmarkerHelper.PoseDelegate): PoseEngine? = runCatching {
        // MediaPipe reads the model via AssetManager.openFd(), which cannot open
        // an asset that aapt compressed. Marking .task files noCompress in the
        // app module fixes it; this check makes any regression actionable.
        runCatching { context.assets.openFd(PoseLandmarkerHelper.MODEL_ASSET).close() }
            .onFailure {
                throw IllegalStateException(
                    "Pose model asset is missing or compressed: " +
                        PoseLandmarkerHelper.MODEL_ASSET,
                    it,
                )
            }

        val lastTimestamp = AtomicLong(0L)
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(PoseLandmarkerHelper.MODEL_ASSET)
            .setDelegate(
                if (delegate == PoseLandmarkerHelper.PoseDelegate.GPU) {
                    Delegate.GPU
                } else {
                    Delegate.CPU
                },
            )
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
            .setMinPosePresenceConfidence(minPosePresenceConfidence)
            .setMinTrackingConfidence(minTrackingConfidence)
            .setResultListener { result, _ ->
                deliver(result, lastTimestamp.get(), listener)
            }
            .setErrorListener { error ->
                listener.onPoseError(error.message ?: "Pose landmarker error")
            }
            .build()

        MediaPipePoseEngine(
            landmarker = PoseLandmarker.createFromOptions(context, options),
            lastTimestamp = lastTimestamp,
        )
    }.onFailure { error ->
        Log.w(TAG, "PoseLandmarker ${delegate.name} delegate unavailable", error)
    }.getOrNull()
}

private class MediaPipePoseEngine(
    private val landmarker: PoseLandmarker,
    private val lastTimestamp: AtomicLong,
) : PoseEngine {
    override fun detectAsync(image: PoseImage, timestampMs: Long) {
        val bitmap = image.bitmap ?: return
        lastTimestamp.set(timestampMs)
        val mediaPipeImage = BitmapImageBuilder(bitmap).build()
        landmarker.detectAsync(mediaPipeImage, timestampMs)
    }

    override fun close() {
        landmarker.close()
    }
}

private fun deliver(
    result: PoseLandmarkerResult,
    timestampMs: Long,
    listener: PoseLandmarkerHelper.Listener,
) {
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
            timestampMillis = timestampMs.takeIf { it > 0L } ?: SystemClock.uptimeMillis(),
        ),
    )
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
