package app.hypertrophy.feature.coach.recap

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tiny offline store for the previous session per exercise. The recap compares
 * against this snapshot, so the feature never needs a network round trip to
 * produce a meaningful post-workout summary.
 */
@Singleton
class SessionHistoryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("coach_history", Context.MODE_PRIVATE)

    fun previous(userId: String, exerciseId: String): SessionMetrics? {
        val raw = prefs.getString(key(userId, exerciseId), null) ?: return null
        val parts = raw.split(SEPARATOR)
        if (parts.size != 8) return null
        return runCatching {
            SessionMetrics(
                exerciseName = parts[0],
                sets = parts[1].toInt(),
                reps = parts[2].toInt(),
                avgRomPercent = parts[3].toFloat(),
                tempoConsistencyPercent = parts[4].toFloat(),
                formScore = parts[5].toInt(),
                topWeightKg = parts[6].toDouble(),
                estimated1RmKg = parts[7].toDouble(),
            )
        }.getOrNull()
    }

    fun save(userId: String, exerciseId: String, metrics: SessionMetrics) {
        val encoded = listOf(
            metrics.exerciseName,
            metrics.sets.toString(),
            metrics.reps.toString(),
            metrics.avgRomPercent.toString(),
            metrics.tempoConsistencyPercent.toString(),
            metrics.formScore.toString(),
            metrics.topWeightKg.toString(),
            metrics.estimated1RmKg.toString(),
        ).joinToString(SEPARATOR)

        prefs.edit().putString(key(userId, exerciseId), encoded).apply()
    }

    private fun key(userId: String, exerciseId: String): String = "$userId|$exerciseId"

    private companion object {
        const val SEPARATOR = "|"
    }
}
