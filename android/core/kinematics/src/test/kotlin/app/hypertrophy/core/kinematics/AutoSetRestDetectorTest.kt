package app.hypertrophy.core.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSetRestDetectorTest {
    @Test
    fun requiresAtLeastOneRep() {
        val detector = AutoSetRestDetector()
        var event: SetCompletedEvent? = null

        for (timestamp in 0L..5_000L step 250L) {
            event = detector.update(neutral(timestamp), repsCompleted = 0) ?: event
        }

        assertNull(event)
    }

    @Test
    fun neutralHoldAfterRepsLogsTheSet() {
        val detector = AutoSetRestDetector()
        var event: SetCompletedEvent? = null

        for (timestamp in 0L..4_000L step 250L) {
            event = detector.update(neutral(timestamp), repsCompleted = 4) ?: event
        }

        assertNotNull(event)
        assertEquals(4, event!!.reps)
        assertEquals(3_750L, event.timestampMillis)
        assertEquals(3_500L, event.restHoldMs)
    }

    @Test
    fun movementResetsTheRestHold() {
        val detector = AutoSetRestDetector()
        var event: SetCompletedEvent? = null

        for (timestamp in 0L..1_000L step 250L) {
            event = detector.update(neutral(timestamp), repsCompleted = 3) ?: event
        }
        detector.update(
            SyntheticPose.frame(timestampMillis = 1_250L, kneeAngleDegrees = 120f),
            repsCompleted = 3,
        )
        detector.update(neutral(1_500L), repsCompleted = 3)
        detector.update(neutral(1_750L), repsCompleted = 3)
        val tooEarly = detector.update(neutral(4_750L), repsCompleted = 3)
        event = detector.update(neutral(5_250L), repsCompleted = 3) ?: event

        assertNull(tooEarly)
        assertNotNull(event)
        assertEquals(5_250L, event!!.timestampMillis)
    }

    @Test
    fun firesOnlyOncePerSet() {
        val detector = AutoSetRestDetector()
        var first: SetCompletedEvent? = null

        for (timestamp in 0L..4_000L step 250L) {
            val update = detector.update(neutral(timestamp), repsCompleted = 2)
            if (first == null && update != null) first = update
        }
        val after = detector.update(neutral(4_250L), repsCompleted = 2)

        assertNotNull(first)
        assertNull(after)

        detector.reset()
        var second: SetCompletedEvent? = null
        for (timestamp in 5_000L..9_000L step 250L) {
            second = detector.update(neutral(timestamp), repsCompleted = 2) ?: second
        }
        assertNotNull(second)
    }

    @Test
    fun raisedArmsKeepSetActive() {
        val detector = AutoSetRestDetector()
        var event: SetCompletedEvent? = null
        val frame = SyntheticPose.frame(
            timestampMillis = 0L,
            leftWristOverride = 0.40f to 0.10f,
        )

        for (timestamp in 0L..4_000L step 250L) {
            event = detector.update(
                frame.copy(timestampMillis = timestamp),
                repsCompleted = 5,
            ) ?: event
        }

        assertNull(event)
    }

    @Test
    fun restProgressRisesBeforeEvent() {
        val detector = AutoSetRestDetector()
        detector.update(neutral(0L), repsCompleted = 1)
        detector.update(neutral(1_000L), repsCompleted = 1)
        detector.update(neutral(4_000L), repsCompleted = 1)

        assertEquals(0.857f, detector.restProgress, 0.01f)
        assertEquals(true, detector.isResting)

        val event = detector.update(neutral(4_500L), repsCompleted = 1)

        assertNotNull(event)
        assertEquals(1f, detector.restProgress, 0.001f)
    }

    @Test
    fun cameraJitterDoesNotBlockRestDetection() {
        val detector = AutoSetRestDetector()
        var event: SetCompletedEvent? = null

        for (index in 0..20) {
            val jitter = if (index % 2 == 0) 0.004f else -0.004f
            val frame = SyntheticPose.frame(
                timestampMillis = index * 200L,
                kneeInset = jitter,
            )
            event = detector.update(frame, repsCompleted = 3) ?: event
        }

        assertNotNull(event)
        assertEquals(3, event!!.reps)
    }

    private fun neutral(timestamp: Long): PoseFrame =
        SyntheticPose.frame(timestampMillis = timestamp)
}
