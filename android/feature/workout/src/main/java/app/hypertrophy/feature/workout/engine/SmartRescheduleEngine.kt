package app.hypertrophy.feature.workout.engine

/** Planner entry used by the smart rescheduling engine. Days are epoch days. */
data class PlannedWorkout(
    val id: String,
    val dayEpoch: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val isRestDay: Boolean = false,
    val isKeyCompound: Boolean = false,
    val exerciseIds: List<String> = emptyList(),
)

enum class RescheduleAction {
    NONE,
    SHIFT_WEEK_PLUS_ONE_DAY,
    MERGE_KEY_COMPOUND_TODAY,
}

data class RescheduleResult(
    val action: RescheduleAction,
    val plans: List<PlannedWorkout>,
    val overdueIds: List<String>,
    val mergedExerciseIds: List<String>,
)

/**
 * Detects uncompleted sessions from previous days and produces an actionable
 * recovery plan. Pure functions keep the behavior unit-testable.
 */
object SmartRescheduleEngine {
    fun overdue(plans: List<PlannedWorkout>, todayEpoch: Long): List<PlannedWorkout> =
        plans.filter { !it.isCompleted && !it.isRestDay && it.dayEpoch < todayEpoch }
            .sortedBy { it.dayEpoch }

    /** Shifts every uncompleted plan forward by one day, preserving rest days. */
    fun shiftWeekPlusOneDay(plans: List<PlannedWorkout>, todayEpoch: Long): RescheduleResult {
        val late = overdue(plans, todayEpoch)
        if (late.isEmpty()) {
            return RescheduleResult(RescheduleAction.NONE, plans, emptyList(), emptyList())
        }
        val lateIds = late.map { it.id }.toSet()
        val shifted = plans.map { plan ->
            if (plan.id in lateIds) plan.copy(dayEpoch = plan.dayEpoch + 1) else plan
        }
        return RescheduleResult(
            action = RescheduleAction.SHIFT_WEEK_PLUS_ONE_DAY,
            plans = shifted,
            overdueIds = late.map { it.id },
            mergedExerciseIds = emptyList(),
        )
    }

    /**
     * Merges key compound lifts from overdue sessions into today's session and
     * drops the remainder of those overdue plans.
     */
    fun mergeKeyCompoundLiftsToday(plans: List<PlannedWorkout>, todayEpoch: Long): RescheduleResult {
        val late = overdue(plans, todayEpoch)
        if (late.isEmpty()) {
            return RescheduleResult(RescheduleAction.NONE, plans, emptyList(), emptyList())
        }

        val lateIds = late.map { it.id }.toSet()
        val compounds = late.filter { it.isKeyCompound }
        val mergedExerciseIds = compounds.flatMap { it.exerciseIds }.distinct()

        val today = plans.firstOrNull { it.dayEpoch == todayEpoch && !it.isRestDay }
        val updatedPlans = plans.mapNotNull { plan ->
            when {
                plan.id in lateIds -> null
                today != null && plan.id == today.id -> plan.copy(
                    exerciseIds = (plan.exerciseIds + mergedExerciseIds).distinct(),
                    isKeyCompound = true,
                )
                else -> plan
            }
        }

        return RescheduleResult(
            action = RescheduleAction.MERGE_KEY_COMPOUND_TODAY,
            plans = updatedPlans,
            overdueIds = late.map { it.id },
            mergedExerciseIds = mergedExerciseIds,
        )
    }
}
