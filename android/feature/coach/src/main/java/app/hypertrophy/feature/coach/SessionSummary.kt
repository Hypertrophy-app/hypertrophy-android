package app.hypertrophy.feature.coach

import app.hypertrophy.core.kinematics.TempoSnapshot
import app.hypertrophy.feature.coach.recap.PersonalRecordMath
import app.hypertrophy.feature.coach.recap.SessionMetrics
import app.hypertrophy.feature.coach.recap.SessionRecapGenerator

/** One set as recorded by the coach, carrying the exercise it belongs to. */
data class CompletedSet(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double,
    val romPercent: Float,
    val tempoLabel: String,
    val formScore: Int,
)

/** Per-exercise aggregate used for personal-record updates. */
data class ExerciseSetSummary(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val topWeightKg: Double,
    val topReps: Int,
    val estimated1RmKg: Double,
)

/**
 * Pure aggregation for a multi-exercise session. Grouping preserves the order
 * in which exercises were first recorded so PR updates and recaps stay
 * deterministic.
 */
object SessionSummaryBuilder {
    fun byExercise(sets: List<CompletedSet>): List<ExerciseSetSummary> =
        sets.groupBy { it.exerciseId }.map { (exerciseId, group) ->
            val bestSet = group.maxByOrNull {
                PersonalRecordMath.estimateOneRepMax(it.weightKg, it.reps)
            } ?: group.first()
            ExerciseSetSummary(
                exerciseId = exerciseId,
                sets = group.size,
                reps = group.sumOf { it.reps },
                topWeightKg = group.maxOf { it.weightKg },
                topReps = group.maxOf { it.reps },
                estimated1RmKg = PersonalRecordMath.estimateOneRepMax(
                    bestSet.weightKg,
                    bestSet.reps,
                ),
            )
        }

    fun metrics(
        title: String,
        sets: List<CompletedSet>,
        tempos: List<TempoSnapshot>,
        formScore: Int,
    ): SessionMetrics {
        require(sets.isNotEmpty()) { "session metrics need at least one set" }
        val bestSet = sets.maxByOrNull {
            PersonalRecordMath.estimateOneRepMax(it.weightKg, it.reps)
        } ?: sets.first()
        return SessionMetrics(
            exerciseName = title,
            sets = sets.size,
            reps = sets.sumOf { it.reps },
            avgRomPercent = sets.map { it.romPercent }.average().toFloat(),
            tempoConsistencyPercent = SessionRecapGenerator.tempoConsistency(tempos),
            formScore = formScore,
            topWeightKg = sets.maxOf { it.weightKg },
            estimated1RmKg = PersonalRecordMath.estimateOneRepMax(
                bestSet.weightKg,
                bestSet.reps,
            ),
        )
    }
}
