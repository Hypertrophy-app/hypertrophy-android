package app.hypertrophy.feature.coach.recap

import app.hypertrophy.core.data.local.PersonalRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordMathTest {
    @Test
    fun epleyEstimateSupportsFractionalWeights() {
        val estimate = PersonalRecordMath.estimateOneRepMax(weightKg = 102.5, reps = 5)

        assertEquals(102.5 * (1.0 + 5.0 / 30.0), estimate, 0.000001)
        assertEquals(119.583333, estimate, 0.000001)
    }

    @Test
    fun epleyEstimateTreatsNonPositiveRepsAsZero() {
        assertEquals(100.0, PersonalRecordMath.estimateOneRepMax(100.0, 0), 0.000001)
        assertEquals(100.0, PersonalRecordMath.estimateOneRepMax(100.0, -3), 0.000001)
    }

    @Test
    fun firstRecordIsAStoredBaselineButNotAnnouncedAsAPr() {
        val merged = PersonalRecordMath.merge(
            previous = null,
            userId = "user-1",
            exerciseId = "ex_back_squat",
            topWeightKg = 100.0,
            topReps = 5,
            estimated1Rm = 116.666666,
            achievedAtMillis = 1_000L,
            newId = "pr-new",
        )

        assertFalse(PersonalRecordMath.isNewRecord(null, 100.0, 116.666666))
        assertEquals("pr-new", merged.id)
        assertEquals(100.0, merged.maxWeightKg, 0.000001)
        assertEquals(5, merged.maxReps)
        assertEquals(116.666666, merged.estimated1rm, 0.000001)
        assertEquals(1_000L, merged.achievedAt)
    }

    @Test
    fun strictlyHeavierTopSetIsANewRecord() {
        val previous = record(maxWeightKg = 100.0, estimated1rm = 120.0)

        assertTrue(PersonalRecordMath.isNewRecord(previous, 102.5, 118.0))
    }

    @Test
    fun strictlyHigherEstimateAtLowerWeightIsANewRecord() {
        val previous = record(maxWeightKg = 100.0, estimated1rm = 120.0)

        assertTrue(PersonalRecordMath.isNewRecord(previous, 95.0, 125.0))
    }

    @Test
    fun exactTieIsNotANewRecord() {
        val previous = record(maxWeightKg = 100.0, estimated1rm = 120.0)

        assertFalse(PersonalRecordMath.isNewRecord(previous, 100.0, 120.0))
    }

    @Test
    fun mergeKeepsTheExistingRecordId() {
        val previous = record(id = "pr-existing")

        val merged = PersonalRecordMath.merge(
            previous = previous,
            userId = "user-1",
            exerciseId = "ex_back_squat",
            topWeightKg = 105.0,
            topReps = 4,
            estimated1Rm = 119.0,
            achievedAtMillis = 2_000L,
            newId = "pr-replacement",
        )

        assertEquals("pr-existing", merged.id)
    }

    @Test
    fun mergeKeepsRunningMaximaWhenTheSessionIsLower() {
        val previous = record(maxWeightKg = 100.0, maxReps = 5, estimated1rm = 120.0)

        val merged = PersonalRecordMath.merge(
            previous = previous,
            userId = "user-1",
            exerciseId = "ex_back_squat",
            topWeightKg = 90.0,
            topReps = 3,
            estimated1Rm = 117.0,
            achievedAtMillis = 2_000L,
            newId = "pr-replacement",
        )

        assertEquals(100.0, merged.maxWeightKg, 0.000001)
        assertEquals(5, merged.maxReps)
        assertEquals(120.0, merged.estimated1rm, 0.000001)
        assertEquals(2_000L, merged.achievedAt)
    }

    @Test
    fun mergeAdvancesEachMaximumIndependently() {
        val previous = record(maxWeightKg = 100.0, maxReps = 3, estimated1rm = 110.0)

        val merged = PersonalRecordMath.merge(
            previous = previous,
            userId = "user-1",
            exerciseId = "ex_back_squat",
            topWeightKg = 95.0,
            topReps = 8,
            estimated1Rm = 120.333333,
            achievedAtMillis = 2_000L,
            newId = "pr-replacement",
        )

        assertEquals(100.0, merged.maxWeightKg, 0.000001)
        assertEquals(8, merged.maxReps)
        assertEquals(120.333333, merged.estimated1rm, 0.000001)
    }

    @Test
    fun mergedRecordValuesNeverRegressAcrossSessions() {
        var current: PersonalRecordEntity? = null
        val sessions = listOf(
            Triple(100.0, 5, 116.666666),
            Triple(102.5, 3, 112.75),
            Triple(95.0, 8, 120.333333),
            Triple(100.0, 6, 120.0),
        )

        for ((index, session) in sessions.withIndex()) {
            val (weight, reps, estimate) = session
            val before = current
            current = PersonalRecordMath.merge(
                previous = before,
                userId = "user-1",
                exerciseId = "ex_back_squat",
                topWeightKg = weight,
                topReps = reps,
                estimated1Rm = estimate,
                achievedAtMillis = 1_000L + index,
                newId = "pr-$index",
            )
            if (before != null) {
                assertTrue(current.maxWeightKg >= before.maxWeightKg)
                assertTrue(current.maxReps >= before.maxReps)
                assertTrue(current.estimated1rm >= before.estimated1rm)
            }
        }
    }

    private fun record(
        id: String = "pr-existing",
        maxWeightKg: Double = 100.0,
        maxReps: Int = 5,
        estimated1rm: Double = 120.0,
    ): PersonalRecordEntity = PersonalRecordEntity(
        id = id,
        userId = "user-1",
        exerciseId = "ex_back_squat",
        maxWeightKg = maxWeightKg,
        maxReps = maxReps,
        estimated1rm = estimated1rm,
        achievedAt = 1_000L,
    )
}
