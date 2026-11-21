package app.hypertrophy.feature.profile

import app.hypertrophy.core.data.local.WorkoutSessionEntity

/** One workout translated into the records the app exports to Health Connect. */
data class SessionExport(
    val sessionId: String,
    val exerciseClientRecordId: String,
    val caloriesClientRecordId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val activeCalories: Double,
) {
    val durationMinutes: Long get() = (endMillis - startMillis) / 60_000L
}

/**
 * Pure mapping from local sessions to Health Connect records. The exported
 * `clientRecordId` is derived from the session id so re-exporting the same
 * workout updates one record instead of creating duplicates.
 */
object HealthConnectExport {
    /** Moderate resistance training burns roughly 5 kcal per minute. */
    const val CALORIES_PER_MINUTE = 5.0

    /** Health Connect caps `clientRecordId` at 100 characters; titles stay short too. */
    const val MAX_TITLE_LENGTH = 100

    fun drafts(sessions: List<WorkoutSessionEntity>): List<SessionExport> =
        sessions.mapNotNull { session ->
            if (session.startedAt <= 0L || session.endedAt <= session.startedAt) {
                return@mapNotNull null
            }
            val minutes = (session.endedAt - session.startedAt) / 60_000.0
            val calories = (minutes * CALORIES_PER_MINUTE).coerceAtLeast(1.0)
            SessionExport(
                sessionId = session.id,
                exerciseClientRecordId = "hypertrophy-${session.id}",
                caloriesClientRecordId = "hypertrophy-${session.id}-cal",
                title = session.title.trim().ifBlank { "Strength training" }.take(MAX_TITLE_LENGTH),
                startMillis = session.startedAt,
                endMillis = session.endedAt,
                activeCalories = Math.round(calories * 10.0) / 10.0,
            )
        }
}
