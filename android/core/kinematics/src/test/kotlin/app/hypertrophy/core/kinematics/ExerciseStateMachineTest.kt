package app.hypertrophy.core.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseStateMachineTest {
    @Test
    fun fullDepthRepWalksEveryPhase() {
        val machine = ExerciseStateMachine(SquatAnalyzer)
        val angles = listOf(170f, 150f, 120f, 90f, 90f, 120f, 150f, 170f)
        val phases = mutableListOf<RepPhase>()
        var completed: RepRecord? = null

        angles.forEachIndexed { index, angle ->
            val update = machine.process(frame(angle, index * 100L))
            phases += update.phase
            update.completedRep?.let { completed = it }
        }

        assertEquals(
            listOf(
                RepPhase.TOP_READY,
                RepPhase.ECCENTRIC,
                RepPhase.ECCENTRIC,
                RepPhase.BOTTOM_INFLECTION,
                RepPhase.BOTTOM_INFLECTION,
                RepPhase.CONCENTRIC,
                RepPhase.CONCENTRIC,
                RepPhase.REP_COMPLETE,
            ),
            phases,
        )
        assertNotNull(completed)
        assertTrue(completed!!.valid)
        assertEquals(100f, completed!!.romPercent, 0.01f)
        assertEquals(1, machine.completedReps)
        assertEquals(1, completed!!.index)
    }

    @Test
    fun secondRepContinuesCounting() {
        val machine = ExerciseStateMachine(SquatAnalyzer)
        val cycle = listOf(170f, 150f, 120f, 90f, 120f, 170f)

        repeat(2) { rep ->
            cycle.forEachIndexed { index, angle ->
                machine.process(frame(angle, rep * 1_000L + index * 100L))
            }
        }

        assertEquals(2, machine.completedReps)
        assertEquals(2, machine.lastRep!!.index)
        assertEquals(RepPhase.REP_COMPLETE, machine.phase)
    }

    @Test
    fun shallowRepIsRecordedButNotCounted() {
        val machine = ExerciseStateMachine(SquatAnalyzer)
        var completed: RepRecord? = null

        listOf(170f, 150f, 130f, 118f, 130f, 150f, 170f).forEachIndexed { index, angle ->
            val update = machine.process(frame(angle, index * 100L))
            update.completedRep?.let { completed = it }
        }

        assertNotNull(completed)
        assertFalse(completed!!.valid)
        assertEquals(65f, completed!!.romPercent, 0.5f)
        assertEquals(118f, completed!!.deepestAngle, 0.01f)
        assertEquals(0, machine.completedReps)
    }

    @Test
    fun tempoTrackerMeasuresEccentricPauseConcentric() {
        val machine = ExerciseStateMachine(SquatAnalyzer)
        val frames = listOf(
            170f to 0L,
            150f to 500L,
            90f to 3_500L,
            90f to 4_500L,
            120f to 4_600L,
            170f to 5_600L,
        )

        var completed: RepRecord? = null
        frames.forEach { (angle, timestamp) ->
            val update = machine.process(frame(angle, timestamp))
            update.completedRep?.let { completed = it }
        }

        val tempo = completed!!.tempo
        assertEquals(3_000L, tempo.eccentricMs)
        assertEquals(1_100L, tempo.pauseMs)
        assertEquals(1_000L, tempo.concentricMs)
        assertEquals("3-1-1", tempo.label())
    }

    @Test
    fun lostVisibilityHoldsPhaseWithoutInventingReps() {
        val machine = ExerciseStateMachine(SquatAnalyzer)
        machine.process(frame(170f, 0L))
        val eccentric = machine.process(frame(150f, 100L))

        val lost = machine.process(
            SyntheticPose.frame(
                timestampMillis = 200L,
                kneeAngleDegrees = 90f,
                hidden = setOf(PoseLandmarks.LEFT_KNEE),
            ),
        )

        assertEquals(RepPhase.ECCENTRIC, eccentric.phase)
        assertEquals(RepPhase.ECCENTRIC, lost.phase)
        assertNull(lost.angle)
        assertFalse(lost.repCompleted)
        assertEquals(0, machine.completedReps)

        val bottom = machine.process(frame(90f, 300L))
        assertEquals(RepPhase.BOTTOM_INFLECTION, bottom.phase)
    }

    @Test
    fun resetClearsRepsAndPhase() {
        val machine = ExerciseStateMachine(SquatAnalyzer)
        listOf(170f, 150f, 90f, 120f, 170f).forEachIndexed { index, angle ->
            machine.process(frame(angle, index * 100L))
        }

        machine.reset()

        assertEquals(0, machine.completedReps)
        assertEquals(RepPhase.TOP_READY, machine.phase)
        assertNull(machine.lastRep)
    }

    private fun frame(angle: Float, timestamp: Long): PoseFrame =
        SyntheticPose.frame(timestampMillis = timestamp, kneeAngleDegrees = angle)
}
