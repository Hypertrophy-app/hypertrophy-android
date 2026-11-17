package app.hypertrophy.core.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormFaultDetectorTest {
    @Test
    fun kneeCaveEmitsCrimsonJointCue() {
        val detector = FormFaultDetector()
        val frame = SyntheticPose.frame(
            timestampMillis = 0L,
            kneeAngleDegrees = 95f,
            kneeInset = 0.03f,
            ankleSpread = 0.04f,
        )

        val cue = detector.evaluate(
            frame = frame,
            analyzer = SquatAnalyzer,
            phase = RepPhase.BOTTOM_INFLECTION,
            minAngleThisRep = 95f,
        )

        assertEquals(FormFault.KNEE_CAVE, cue!!.fault)
        assertTrue(cue.jointIndex == PoseLandmarks.LEFT_KNEE || cue.jointIndex == PoseLandmarks.RIGHT_KNEE)
        assertTrue(cue.severity > 0.2f)
    }

    @Test
    fun torsoLeanBeyondAnalyzerLimitIsFlagged() {
        val detector = FormFaultDetector()
        val frame = SyntheticPose.frame(timestampMillis = 0L, torsoLeanDegrees = 60f)

        val cue = detector.evaluate(frame, SquatAnalyzer, RepPhase.ECCENTRIC)

        assertEquals(FormFault.TORSO_LEAN, cue!!.fault)
    }

    @Test
    fun shallowRepFlagsInsufficientDepthOnConcentric() {
        val detector = FormFaultDetector()
        val frame = SyntheticPose.frame(timestampMillis = 0L, kneeAngleDegrees = 150f)

        val cue = detector.evaluate(
            frame = frame,
            analyzer = SquatAnalyzer,
            phase = RepPhase.CONCENTRIC,
            minAngleThisRep = 130f,
        )

        assertEquals(FormFault.INSUFFICIENT_DEPTH, cue!!.fault)
    }

    @Test
    fun asymmetricShouldersFlagBarPath() {
        val detector = FormFaultDetector()
        val frame = SyntheticPose.frame(timestampMillis = 0L, shoulderDrop = 0.06f)

        val cue = detector.evaluate(
            frame = frame,
            analyzer = SquatAnalyzer,
            phase = RepPhase.CONCENTRIC,
            minAngleThisRep = 90f,
        )

        assertEquals(FormFault.BAR_PATH_ASYMMETRY, cue!!.fault)
    }

    @Test
    fun cuesAreThrottledToOnceEveryFourSeconds() {
        val detector = FormFaultDetector()
        val first = detector.evaluate(
            frame = kneeCaveFrame(0L),
            analyzer = SquatAnalyzer,
            phase = RepPhase.BOTTOM_INFLECTION,
            minAngleThisRep = 95f,
        )
        val tooSoon = detector.evaluate(
            frame = kneeCaveFrame(1_000L),
            analyzer = SquatAnalyzer,
            phase = RepPhase.BOTTOM_INFLECTION,
            minAngleThisRep = 95f,
        )
        val later = detector.evaluate(
            frame = kneeCaveFrame(4_000L),
            analyzer = SquatAnalyzer,
            phase = RepPhase.BOTTOM_INFLECTION,
            minAngleThisRep = 95f,
        )

        assertEquals(FormFault.KNEE_CAVE, first!!.fault)
        assertNull(tooSoon)
        assertEquals(FormFault.KNEE_CAVE, later!!.fault)
        assertEquals(4_000L, later.timestampMillis)
    }

    @Test
    fun topReadyProducesNoCue() {
        val detector = FormFaultDetector()

        val cue = detector.evaluate(
            frame = kneeCaveFrame(0L),
            analyzer = SquatAnalyzer,
            phase = RepPhase.TOP_READY,
            minAngleThisRep = null,
        )

        assertNull(cue)
    }

    @Test
    fun cleanBottomProducesNoCue() {
        val detector = FormFaultDetector()
        val frame = SyntheticPose.frame(timestampMillis = 0L, kneeAngleDegrees = 95f)

        val cue = detector.evaluate(
            frame = frame,
            analyzer = SquatAnalyzer,
            phase = RepPhase.BOTTOM_INFLECTION,
            minAngleThisRep = 95f,
        )

        assertNull(cue)
    }

    private fun kneeCaveFrame(timestamp: Long): PoseFrame = SyntheticPose.frame(
        timestampMillis = timestamp,
        kneeAngleDegrees = 95f,
        kneeInset = 0.03f,
        ankleSpread = 0.04f,
    )
}
