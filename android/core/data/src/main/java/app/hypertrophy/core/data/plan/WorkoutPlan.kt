package app.hypertrophy.core.data.plan

import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.local.WorkoutTemplateEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One exercise in a reusable workout plan. The same shape is stored in
 * `workout_templates.exercises_json` and `scheduled_workouts.exercises_json`,
 * so a template can be copied onto a day without translation.
 */
@Serializable
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int = 3,
    val reps: Int = 8,
    val weightKg: Double = 0.0,
    val restSeconds: Int = 90,
)

/** A decoded workout ready for the coach. */
data class WorkoutPlan(
    val title: String,
    val exercises: List<PlannedExercise>,
    val scheduledWorkoutId: String? = null,
)

/**
 * The only encoder and decoder for plan JSON. Legacy rows written before
 * `restSeconds` existed still parse because every field has a default, and
 * malformed rows decode to an empty list instead of crashing a screen.
 */
object WorkoutPlanCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(exercises: List<PlannedExercise>): String =
        json.encodeToString(exercises)

    fun decode(raw: String?): List<PlannedExercise> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<PlannedExercise>>(raw)
                .filter { it.exerciseId.isNotBlank() }
                .map { it.sanitized() }
        }.getOrDefault(emptyList())
    }

    fun fromScheduled(entity: ScheduledWorkoutEntity): WorkoutPlan = WorkoutPlan(
        title = entity.title,
        exercises = decode(entity.exercisesJson),
        scheduledWorkoutId = entity.id,
    )

    fun fromTemplate(entity: WorkoutTemplateEntity): WorkoutPlan = WorkoutPlan(
        title = entity.title,
        exercises = decode(entity.exercisesJson),
    )

    private fun PlannedExercise.sanitized(): PlannedExercise = copy(
        sets = sets.coerceIn(MIN_SETS, MAX_SETS),
        reps = reps.coerceIn(MIN_REPS, MAX_REPS),
        weightKg = weightKg.coerceIn(0.0, MAX_WEIGHT_KG),
        restSeconds = restSeconds.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS),
    )

    private const val MIN_SETS = 1
    private const val MAX_SETS = 10
    private const val MIN_REPS = 1
    private const val MAX_REPS = 50
    private const val MAX_WEIGHT_KG = 500.0
    private const val MIN_REST_SECONDS = 15
    private const val MAX_REST_SECONDS = 600
}
