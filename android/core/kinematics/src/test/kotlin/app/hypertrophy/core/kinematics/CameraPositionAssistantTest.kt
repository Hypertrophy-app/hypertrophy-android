package app.hypertrophy.core.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPositionAssistantTest {
    @Test
    fun locksAfterOnePointFiveSecondsOfFullBodyVisibility() {
        val assistant = CameraPositionAssistant()

        val early = assistant.update(SyntheticPose.frame(timestampMillis = 0L))
        val almost = assistant.update(SyntheticPose.frame(timestampMillis = 1_499L))
        val locked = assistant.update(SyntheticPose.frame(timestampMillis = 1_500L))

        assertEquals(CameraAimState.ALIGNING, early.state)
        assertEquals(CameraAimState.ALIGNING, almost.state)
        assertTrue(almost.progress > 0.99f)
        assertEquals(CameraAimState.LOCKED_READY, locked.state)
        assertTrue(locked.isLocked)
        assertEquals(100, locked.progressPercent)
    }

    @Test
    fun clippedFootKeepsAssistantAligning() {
        val assistant = CameraPositionAssistant()

        val aim = assistant.update(
            SyntheticPose.frame(
                timestampMillis = 500L,
                hidden = setOf(PoseLandmarks.RIGHT_ANKLE),
            ),
        )

        assertEquals(CameraAimState.ALIGNING, aim.state)
        assertEquals(0f, aim.progress, 0.001f)
        assertFalse(aim.framingOk)
        assertTrue(aim.message.contains("feet") || aim.message.contains("body"))
    }

    @Test
    fun lowLightingPreventsLock() {
        val assistant = CameraPositionAssistant()

        val aim = assistant.update(SyntheticPose.frame(timestampMillis = 0L, visibility = 0.4f))

        assertEquals(CameraAimState.ALIGNING, aim.state)
        assertFalse(aim.framingOk)
        assertTrue(aim.message.contains("light") || aim.message.contains("face"))
    }

    @Test
    fun leavingFrameResetsHoldProgress() {
        val assistant = CameraPositionAssistant()
        assistant.update(SyntheticPose.frame(timestampMillis = 0L))
        val halfway = assistant.update(SyntheticPose.frame(timestampMillis = 750L))
        assistant.update(
            SyntheticPose.frame(
                timestampMillis = 800L,
                hidden = setOf(PoseLandmarks.LEFT_ANKLE),
            ),
        )
        val restarted = assistant.update(SyntheticPose.frame(timestampMillis = 900L))

        assertTrue(halfway.progress > 0.45f)
        assertEquals(0f, restarted.progress, 0.001f)
        assertEquals(CameraAimState.ALIGNING, restarted.state)
    }

    @Test
    fun resetReturnsToAligning() {
        val assistant = CameraPositionAssistant()
        assistant.update(SyntheticPose.frame(timestampMillis = 0L))
        assistant.update(SyntheticPose.frame(timestampMillis = 2_000L))
        assertEquals(CameraAimState.LOCKED_READY, assistant.state)

        assistant.reset()

        assertEquals(CameraAimState.ALIGNING, assistant.state)
        assertEquals(0f, assistant.progress, 0.001f)
    }
}
