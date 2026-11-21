package app.hypertrophy.core.vision

import app.hypertrophy.core.kinematics.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseLandmarkerHelperTest {
    @Test
    fun gpuFailureFallsBackToCpuAndNotifiesListener() {
        val engine = FakeEngine()
        val factory = FakeEngineFactory(
            gpu = { null },
            cpu = { engine },
        )
        val listener = RecordingListener()
        val helper = PoseLandmarkerHelper(factory, listener)

        assertTrue(helper.setup())

        assertEquals(
            listOf(PoseLandmarkerHelper.PoseDelegate.GPU, PoseLandmarkerHelper.PoseDelegate.CPU),
            factory.attempts,
        )
        assertEquals(listOf(PoseLandmarkerHelper.PoseDelegate.CPU), listener.delegateChanges)
        assertEquals(PoseLandmarkerHelper.PoseDelegate.CPU, helper.activeDelegate)
        assertTrue(helper.isReady)
        assertTrue(listener.errors.isEmpty())
    }

    @Test
    fun gpuSuccessNeverAttemptsCpu() {
        val engine = FakeEngine()
        val factory = FakeEngineFactory(
            gpu = { engine },
            cpu = { FakeEngine() },
        )
        val listener = RecordingListener()
        val helper = PoseLandmarkerHelper(factory, listener)

        assertTrue(helper.setup())

        assertEquals(listOf(PoseLandmarkerHelper.PoseDelegate.GPU), factory.attempts)
        assertEquals(listOf(PoseLandmarkerHelper.PoseDelegate.GPU), listener.delegateChanges)
        assertEquals(PoseLandmarkerHelper.PoseDelegate.GPU, helper.activeDelegate)
        assertTrue(helper.isReady)
    }

    @Test
    fun bothDelegatesFailingReportsAnErrorAndReturnsFalse() {
        val factory = FakeEngineFactory(gpu = { null }, cpu = { null })
        val listener = RecordingListener()
        val helper = PoseLandmarkerHelper(factory, listener)

        assertFalse(helper.setup())

        assertEquals(
            listOf(PoseLandmarkerHelper.PoseDelegate.GPU, PoseLandmarkerHelper.PoseDelegate.CPU),
            factory.attempts,
        )
        assertEquals(listOf("Pose model could not be loaded on GPU or CPU"), listener.errors)
        assertFalse(helper.isReady)
    }

    @Test
    fun throwingFactoryIsTreatedAsFailureAndCpuFallbackStillWorks() {
        val engine = FakeEngine()
        val factory = FakeEngineFactory(
            gpu = { throw IllegalStateException("GPU delegate unavailable") },
            cpu = { engine },
        )
        val listener = RecordingListener()
        val helper = PoseLandmarkerHelper(factory, listener)

        assertTrue(helper.setup())

        assertEquals(
            listOf(PoseLandmarkerHelper.PoseDelegate.GPU, PoseLandmarkerHelper.PoseDelegate.CPU),
            factory.attempts,
        )
        assertEquals(listOf(PoseLandmarkerHelper.PoseDelegate.CPU), listener.delegateChanges)
        assertEquals(PoseLandmarkerHelper.PoseDelegate.CPU, helper.activeDelegate)
    }

    @Test
    fun setupTwiceIsIdempotent() {
        val factory = FakeEngineFactory(gpu = { FakeEngine() }, cpu = { FakeEngine() })
        val listener = RecordingListener()
        val helper = PoseLandmarkerHelper(factory, listener)

        assertTrue(helper.setup())
        assertTrue(helper.setup())

        assertEquals(listOf(PoseLandmarkerHelper.PoseDelegate.GPU), factory.attempts)
        assertEquals(1, listener.delegateChanges.size)
    }

    @Test
    fun closeClosesTheActiveEngineAndClearsReadiness() {
        val engine = FakeEngine()
        val helper = PoseLandmarkerHelper(
            engineFactory = FakeEngineFactory(gpu = { engine }),
            listener = RecordingListener(),
        )

        assertTrue(helper.setup())
        helper.close()

        assertEquals(1, engine.closeCount)
        assertFalse(helper.isReady)
    }

    @Test
    fun detectAfterCloseIsANoOp() {
        val engine = FakeEngine()
        val helper = PoseLandmarkerHelper(
            engineFactory = FakeEngineFactory(gpu = { engine }),
            listener = RecordingListener(),
        )
        helper.setup()

        helper.detectFrame(PoseImage(null), 42L)
        helper.close()
        helper.detectFrame(PoseImage(null), 43L)

        assertEquals(1, engine.detections)
        assertEquals(42L, engine.lastTimestampMs)
    }

    private class FakeEngineFactory(
        private val gpu: () -> PoseEngine? = { null },
        private val cpu: () -> PoseEngine? = { null },
    ) : PoseEngineFactory {
        val attempts = mutableListOf<PoseLandmarkerHelper.PoseDelegate>()

        override fun create(delegate: PoseLandmarkerHelper.PoseDelegate): PoseEngine? {
            attempts += delegate
            return when (delegate) {
                PoseLandmarkerHelper.PoseDelegate.GPU -> gpu()
                PoseLandmarkerHelper.PoseDelegate.CPU -> cpu()
            }
        }
    }

    private class FakeEngine : PoseEngine {
        var detections = 0
            private set
        var lastTimestampMs = 0L
            private set
        var closeCount = 0
            private set

        override fun detectAsync(image: PoseImage, timestampMs: Long) {
            detections += 1
            lastTimestampMs = timestampMs
        }

        override fun close() {
            closeCount += 1
        }
    }

    private class RecordingListener : PoseLandmarkerHelper.Listener {
        val frames = mutableListOf<PoseFrame>()
        val errors = mutableListOf<String>()
        val delegateChanges = mutableListOf<PoseLandmarkerHelper.PoseDelegate>()

        override fun onPoseFrame(frame: PoseFrame) {
            frames += frame
        }

        override fun onPoseError(message: String) {
            errors += message
        }

        override fun onDelegateChanged(delegate: PoseLandmarkerHelper.PoseDelegate) {
            delegateChanges += delegate
        }
    }
}
