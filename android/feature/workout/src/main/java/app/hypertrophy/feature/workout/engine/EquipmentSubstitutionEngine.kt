package app.hypertrophy.feature.workout.engine

/** Lightweight exercise view model for substitution matching. */
data class ExerciseOption(
    val id: String,
    val name: String,
    val primaryMuscle: String,
    val kinematicProfileType: String,
    val equipment: String,
)

enum class SubstitutionReason {
    NONE,
    SAME_MUSCLE_AND_KINEMATIC,
}

/**
 * One-tap equipment substitution. Matches on primary muscle and kinematic
 * profile so biomechanics stay comparable, then promotes equipment the athlete
 * can actually use. Set history is untouched: callers swap only the exercise
 * reference for upcoming sets.
 */
object EquipmentSubstitutionEngine {
    fun findSubstitutes(
        target: ExerciseOption,
        candidates: List<ExerciseOption>,
        availableEquipment: Set<String>,
        limit: Int = 5,
    ): List<ExerciseOption> =
        candidates.asSequence()
            .filter { it.id != target.id }
            .filter { it.primaryMuscle == target.primaryMuscle }
            .filter { it.kinematicProfileType == target.kinematicProfileType }
            .sortedWith(
                compareBy(
                    { if (it.equipment in availableEquipment) 0 else 1 },
                    { it.name },
                ),
            )
            .take(limit)
            .toList()
}
