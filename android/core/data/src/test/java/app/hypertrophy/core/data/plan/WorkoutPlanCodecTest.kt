package app.hypertrophy.core.data.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanCodecTest {
    @Test
    fun roundTripPreservesEveryField() {
        val plan = listOf(
            PlannedExercise("ex_back_squat", sets = 5, reps = 3, weightKg = 102.5, restSeconds = 180),
            PlannedExercise("ex_bench_press", sets = 3, reps = 8, weightKg = 80.0),
        )

        val decoded = WorkoutPlanCodec.decode(WorkoutPlanCodec.encode(plan))

        assertEquals(plan, decoded)
    }

    @Test
    fun legacyJsonWithoutRestSecondsUsesTheDefault() {
        val legacy = """[{"exerciseId":"ex_back_squat","sets":4,"reps":12,"weightKg":60.0}]"""

        val decoded = WorkoutPlanCodec.decode(legacy)

        assertEquals(1, decoded.size)
        assertEquals(4, decoded.single().sets)
        assertEquals(12, decoded.single().reps)
        assertEquals(60.0, decoded.single().weightKg, 0.000001)
        assertEquals(90, decoded.single().restSeconds)
    }

    @Test
    fun valuesAreClampedToSafeRanges() {
        val raw = """[{"exerciseId":"ex_back_squat","sets":99,"reps":0,"weightKg":-10.0,"restSeconds":1}]"""

        val decoded = WorkoutPlanCodec.decode(raw).single()

        assertEquals(10, decoded.sets)
        assertEquals(1, decoded.reps)
        assertEquals(0.0, decoded.weightKg, 0.000001)
        assertEquals(15, decoded.restSeconds)
    }

    @Test
    fun malformedJsonDecodesToEmpty() {
        assertTrue(WorkoutPlanCodec.decode("not json").isEmpty())
        assertTrue(WorkoutPlanCodec.decode("""{"exerciseId":"ex_back_squat"}""").isEmpty())
        assertTrue(WorkoutPlanCodec.decode(null).isEmpty())
        assertTrue(WorkoutPlanCodec.decode("  ").isEmpty())
    }

    @Test
    fun blankExerciseIdsAreDropped() {
        val raw = """[{"exerciseId":"","sets":3},{"exerciseId":"ex_rdl","sets":3}]"""

        val decoded = WorkoutPlanCodec.decode(raw)

        assertEquals(listOf("ex_rdl"), decoded.map { it.exerciseId })
    }

    @Test
    fun unknownKeysAreIgnored() {
        val raw = """[{"exerciseId":"ex_rdl","sets":3,"reps":8,"weightKg":100.0,"note":"heavy"}]"""

        val decoded = WorkoutPlanCodec.decode(raw).single()

        assertEquals("ex_rdl", decoded.exerciseId)
        assertEquals(100.0, decoded.weightKg, 0.000001)
    }
}
