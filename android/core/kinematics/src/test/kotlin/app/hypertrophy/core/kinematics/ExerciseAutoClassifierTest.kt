package app.hypertrophy.core.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class ExerciseAutoClassifierTest {
    @Test
    fun squatIsClassifiedFromKneeVelocityVariance() {
        val classifier = ExerciseAutoClassifier()

        repeat(60) { index ->
            val progress = ease(index / 59f)
            classifier.addFrame(
                SyntheticPose.frame(
                    timestampMillis = index * 33L,
                    kneeAngleDegrees = 172f - 77f * progress,
                    torsoLeanDegrees = 10f,
                ),
            )
        }

        val result = classifier.classify()!!

        assertEquals(ExerciseKind.SQUAT, result.kind)
        assertTrue(result.confidence > 0.5f)
        assertTrue(result.reason.contains("Knee"))
    }

    @Test
    fun hingeIsSeparatedByTorsoLean() {
        val classifier = ExerciseAutoClassifier()

        repeat(60) { index ->
            val progress = ease(index / 59f)
            classifier.addFrame(
                SyntheticPose.frame(
                    timestampMillis = index * 33L,
                    kneeAngleDegrees = 172f - 42f * progress,
                    torsoLeanDegrees = 65f,
                ),
            )
        }

        val result = classifier.classify()!!

        assertEquals(ExerciseKind.HINGE, result.kind)
    }

    @Test
    fun curlIsClassifiedFromVerticalWristTravel() {
        val classifier = ExerciseAutoClassifier()

        repeat(60) { index ->
            val progress = ease(index / 59f)
            classifier.addFrame(
                SyntheticPose.frame(
                    timestampMillis = index * 33L,
                    elbowAngleDegrees = 165f - 120f * progress,
                    kneeAngleDegrees = 172f,
                ),
            )
        }

        val result = classifier.classify()!!

        assertEquals(ExerciseKind.CURL_RAISE, result.kind)
    }

    @Test
    fun pushIsClassifiedWhenWristStaysPut() {
        val classifier = ExerciseAutoClassifier()

        repeat(60) { index ->
            val progress = ease(index / 59f)
            classifier.addFrame(
                SyntheticPose.frame(
                    timestampMillis = index * 33L,
                    elbowAngleDegrees = 120f,
                    leftElbowOverride = 0.42f to (0.30f + 0.12f * progress),
                    leftWristOverride = 0.52f to 0.34f,
                    kneeAngleDegrees = 172f,
                ),
            )
        }

        val result = classifier.classify()!!

        assertEquals(ExerciseKind.PUSH, result.kind)
    }

    @Test
    fun pullIsClassifiedWhenShouldersRiseWithTheBody() {
        val classifier = ExerciseAutoClassifier()

        repeat(60) { index ->
            val progress = ease(index / 59f)
            classifier.addFrame(
                SyntheticPose.frame(
                    timestampMillis = index * 33L,
                    shoulderLift = 0.12f * progress,
                    leftElbowOverride = 0.46f to (0.42f - 0.12f * progress),
                    leftWristOverride = (0.40f + 0.04f * progress) to (0.54f - 0.40f * progress),
                    kneeAngleDegrees = 172f,
                ),
            )
        }

        val result = classifier.classify()!!

        assertEquals(ExerciseKind.PULL, result.kind)
    }

    @Test
    fun classifierNeedsFullSixtyFrameWindow() {
        val classifier = ExerciseAutoClassifier()

        repeat(30) { index ->
            classifier.addFrame(
                SyntheticPose.frame(
                    timestampMillis = index * 33L,
                    kneeAngleDegrees = 172f - index.toFloat(),
                ),
            )
        }

        assertTrue(!classifier.isReady)
        assertNull(classifier.classify())
    }

    @Test
    fun stillWindowReturnsUnknown() {
        val classifier = ExerciseAutoClassifier()

        repeat(60) { index ->
            classifier.addFrame(SyntheticPose.frame(timestampMillis = index * 33L))
        }

        val result = classifier.classify()!!

        assertEquals(ExerciseKind.UNKNOWN, result.kind)
        assertEquals(0f, result.confidence, 0.01f)
    }

    /** Real reps ease in and out of the turnaround instead of moving linearly. */
    private fun ease(progress: Float): Float =
        (0.5 - 0.5 * cos(Math.PI * progress)).toFloat()
}
