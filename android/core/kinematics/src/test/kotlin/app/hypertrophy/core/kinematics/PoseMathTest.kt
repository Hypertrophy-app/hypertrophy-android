package app.hypertrophy.core.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMathTest {
    private fun lm(x: Float, y: Float, z: Float = 0f, v: Float = 0.99f) =
        Landmark(x, y, z, v)

    @Test
    fun angleBetweenComputesRightAngle() {
        val angle = PoseMath.angleBetween(
            a = lm(0.5f, 0.4f),
            b = lm(0.5f, 0.5f),
            c = lm(0.6f, 0.5f),
        )

        assertEquals(90f, angle!!, 0.01f)
    }

    @Test
    fun angleBetweenComputesStraightLine() {
        val angle = PoseMath.angleBetween(
            a = lm(0.5f, 0.4f),
            b = lm(0.5f, 0.5f),
            c = lm(0.5f, 0.6f),
        )

        assertEquals(180f, angle!!, 0.01f)
    }

    @Test
    fun angleBetweenRespectsVisibilityGate() {
        val angle = PoseMath.angleBetween(
            a = lm(0.5f, 0.4f, v = 0.5f),
            b = lm(0.5f, 0.5f),
            c = lm(0.6f, 0.5f),
        )

        assertNull(angle)
    }

    @Test
    fun visibilityAboveThresholdPassesGate() {
        assertTrue(PoseMath.isVisible(lm(0.5f, 0.5f, v = 0.8f)))
        assertFalse(PoseMath.isVisible(lm(0.5f, 0.5f, v = 0.65f)))
    }

    @Test
    fun torsoInclinationIsZeroWhenUpright() {
        val frame = SyntheticPose.frame(torsoLeanDegrees = 0f)

        val inclination = PoseMath.torsoInclination(
            frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            frame.landmark(PoseLandmarks.RIGHT_SHOULDER),
            frame.landmark(PoseLandmarks.LEFT_HIP),
            frame.landmark(PoseLandmarks.RIGHT_HIP),
        )

        assertEquals(0f, inclination!!, 0.5f)
    }

    @Test
    fun torsoInclinationMatchesSyntheticLean() {
        val frame = SyntheticPose.frame(torsoLeanDegrees = 45f)

        val inclination = PoseMath.torsoInclination(
            frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            frame.landmark(PoseLandmarks.RIGHT_SHOULDER),
            frame.landmark(PoseLandmarks.LEFT_HIP),
            frame.landmark(PoseLandmarks.RIGHT_HIP),
        )

        assertEquals(45f, inclination!!, 1f)
    }

    @Test
    fun kneeValgusFlagsConvergingKnees() {
        val ratio = PoseMath.kneeValgus(
            leftKnee = lm(0.47f, 0.6f),
            rightKnee = lm(0.53f, 0.6f),
            leftAnkle = lm(0.42f, 0.9f),
            rightAnkle = lm(0.58f, 0.9f),
        )

        assertEquals(0.625f, ratio, 0.001f)
    }

    @Test
    fun kneeValgusIsNegativeWhenKneesSplayOut() {
        val ratio = PoseMath.kneeValgus(
            leftKnee = lm(0.40f, 0.6f),
            rightKnee = lm(0.60f, 0.6f),
            leftAnkle = lm(0.44f, 0.9f),
            rightAnkle = lm(0.56f, 0.9f),
        )

        assertTrue(ratio < 0f)
    }

    @Test
    fun framingAcceptsFullBodyPose() {
        val frame = SyntheticPose.frame()

        assertTrue(PoseMath.isFullyFramed(frame))
    }

    @Test
    fun framingRejectsClippedAnkle() {
        val frame = SyntheticPose.frame(hidden = setOf(PoseLandmarks.LEFT_ANKLE))

        assertFalse(PoseMath.isFullyFramed(frame))
    }

    @Test
    fun averageVisibilityUsesFramingLandmarks() {
        val frame = SyntheticPose.frame(visibility = 0.8f)

        assertEquals(0.8f, PoseMath.averageVisibility(frame), 0.001f)
    }
}
