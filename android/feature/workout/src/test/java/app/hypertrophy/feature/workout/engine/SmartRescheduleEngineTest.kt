package app.hypertrophy.feature.workout.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRescheduleEngineTest {
    private val today = 20_000L

    @Test
    fun overdueIgnoresCompletedAndRestDays() {
        val plans = listOf(
            plan("a", today - 2),
            plan("b", today - 1, completed = true),
            plan("c", today - 1, rest = true),
            plan("d", today),
        )

        val overdue = SmartRescheduleEngine.overdue(plans, today)

        assertEquals(listOf("a"), overdue.map { it.id })
    }

    @Test
    fun shiftWeekPlusOneDayMovesOnlyOverduePlans() {
        val plans = listOf(
            plan("a", today - 2),
            plan("b", today - 1, completed = true),
            plan("c", today),
            plan("rest", today + 1, rest = true),
        )

        val result = SmartRescheduleEngine.shiftWeekPlusOneDay(plans, today)

        assertEquals(RescheduleAction.SHIFT_WEEK_PLUS_ONE_DAY, result.action)
        assertEquals(today - 1, result.plans.first { it.id == "a" }.dayEpoch)
        assertEquals(today - 1, result.plans.first { it.id == "b" }.dayEpoch)
        assertEquals(today, result.plans.first { it.id == "c" }.dayEpoch)
        assertEquals(today + 1, result.plans.first { it.id == "rest" }.dayEpoch)
        assertEquals(listOf("a"), result.overdueIds)
    }

    @Test
    fun mergeKeyCompoundMovesCompoundsIntoTodaysPlan() {
        val plans = listOf(
            plan("overdue", today - 3, keyCompound = true, exerciseIds = listOf("ex_back_squat", "ex_rdl")),
            plan("accessory", today - 1, exerciseIds = listOf("ex_bicep_curl")),
            plan("today", today, exerciseIds = listOf("ex_bench_press")),
        )

        val result = SmartRescheduleEngine.mergeKeyCompoundLiftsToday(plans, today)

        assertEquals(RescheduleAction.MERGE_KEY_COMPOUND_TODAY, result.action)
        assertEquals(listOf("ex_back_squat", "ex_rdl"), result.mergedExerciseIds)
        assertTrue(result.plans.none { it.id == "overdue" })
        assertTrue(result.plans.none { it.id == "accessory" })
        val todayPlan = result.plans.first { it.id == "today" }
        assertEquals(listOf("ex_bench_press", "ex_back_squat", "ex_rdl"), todayPlan.exerciseIds)
        assertTrue(todayPlan.isKeyCompound)
    }

    @Test
    fun noOverdueReturnsNone() {
        val plans = listOf(plan("today", today), plan("tomorrow", today + 1))

        val shift = SmartRescheduleEngine.shiftWeekPlusOneDay(plans, today)
        val merge = SmartRescheduleEngine.mergeKeyCompoundLiftsToday(plans, today)

        assertEquals(RescheduleAction.NONE, shift.action)
        assertEquals(RescheduleAction.NONE, merge.action)
        assertEquals(plans, shift.plans)
        assertEquals(plans, merge.plans)
    }

    private fun plan(
        id: String,
        day: Long,
        completed: Boolean = false,
        rest: Boolean = false,
        keyCompound: Boolean = false,
        exerciseIds: List<String> = emptyList(),
    ) = PlannedWorkout(
        id = id,
        dayEpoch = day,
        title = id,
        isCompleted = completed,
        isRestDay = rest,
        isKeyCompound = keyCompound,
        exerciseIds = exerciseIds,
    )
}
