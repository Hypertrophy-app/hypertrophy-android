package app.hypertrophy.feature.coach

import app.hypertrophy.core.kinematics.TempoSnapshot
import app.hypertrophy.feature.coach.recap.PersonalRecordMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryTest {
    @Test
    fun byExerciseGroupsSetsAndKeepsFirstSeenOrder() {
        val sets = listOf(
            set("squat", reps = 8, weight = 100.0),
            set("row", reps = 10, weight = 70.0),
            set("squat", reps = 6, weight = 110.0),
        )

        val summaries = SessionSummaryBuilder.byExercise(sets)

        assertEquals(listOf("squat", "row"), summaries.map { it.exerciseId })
        val squat = summaries.first()
        assertEquals(2, squat.sets)
        assertEquals(14, squat.reps)
        assertEquals(110.0, squat.topWeightKg, 0.000001)
        assertEquals(8, squat.topReps)
        val bestEstimate = PersonalRecordMath.estimateOneRepMax(110.0, 6)
        assertEquals(bestEstimate, squat.estimated1RmKg, 0.000001)
        assertTrue(bestEstimate > PersonalRecordMath.estimateOneRepMax(100.0, 8))
    }

    @Test
    fun byExerciseUsesBestEstimatedSetEvenWhenItIsNotTheHeaviest() {
        val sets = listOf(
            set("bench", reps = 5, weight = 100.0),
            set("bench", reps = 15, weight = 80.0),
        )

        val summary = SessionSummaryBuilder.byExercise(sets).single()

        assertEquals(100.0, summary.topWeightKg, 0.000001)
        assertEquals(15, summary.topReps)
        assertEquals(
            PersonalRecordMath.estimateOneRepMax(80.0, 15),
            summary.estimated1RmKg,
            0.000001,
        )
    }

    @Test
    fun metricsAggregateTheWholeSessionAcrossExercises() {
        val sets = listOf(
            set("squat", reps = 8, weight = 100.0, rom = 90f),
            set("row", reps = 10, weight = 70.0, rom = 70f),
        )

        val metrics = SessionSummaryBuilder.metrics(
            title = "Push Day",
            sets = sets,
            tempos = listOf(TempoSnapshot(3_000L, 1_000L, 1_000L)),
            formScore = 92,
        )

        assertEquals("Push Day", metrics.exerciseName)
        assertEquals(2, metrics.sets)
        assertEquals(18, metrics.reps)
        assertEquals(80f, metrics.avgRomPercent, 0.000001f)
        assertEquals(100f, metrics.tempoConsistencyPercent, 0.000001f)
        assertEquals(92, metrics.formScore)
        assertEquals(100.0, metrics.topWeightKg, 0.000001)
        assertEquals(
            PersonalRecordMath.estimateOneRepMax(100.0, 8),
            metrics.estimated1RmKg,
            0.000001,
        )
    }

    private fun set(
        exerciseId: String,
        reps: Int,
        weight: Double,
        rom: Float = 80f,
    ) = CompletedSet(
        exerciseId = exerciseId,
        reps = reps,
        weightKg = weight,
        romPercent = rom,
        tempoLabel = "3-1-1",
        formScore = 90,
    )
}
