package app.hypertrophy.feature.coach.recap

import app.hypertrophy.core.data.local.PersonalRecordEntity

/**
 * Pure personal-record arithmetic for the coach module.
 *
 * The estimate uses Epley's formula, `weightKg * (1 + reps / 30)`. Record
 * checks are strict, so an exact tie with the previous best never counts as a
 * PR. The very first logged session stores a baseline row but is not announced
 * as a new record.
 */
object PersonalRecordMath {
    /** Epley one-rep-max estimate. Non-positive reps mean the bar was not lifted. */
    fun estimateOneRepMax(weightKg: Double, reps: Int): Double {
        val effectiveReps = reps.coerceAtLeast(0)
        return weightKg * (1.0 + effectiveReps / 30.0)
    }

    /**
     * True only when the session strictly beats the previous record on top
     * weight or estimated one-rep max. Without a previous record there is
     * nothing to beat, so this stays false for the baseline session.
     */
    fun isNewRecord(
        previous: PersonalRecordEntity?,
        topWeightKg: Double,
        estimated1Rm: Double,
    ): Boolean {
        if (previous == null) return false
        return topWeightKg > previous.maxWeightKg ||
            estimated1Rm > previous.estimated1rm
    }

    /**
     * Builds the row to persist. Weight, reps, and the Epley estimate only ever
     * move upward; an existing record keeps its id so downstream reads and
     * sync do not see a replacement row.
     */
    fun merge(
        previous: PersonalRecordEntity?,
        userId: String,
        exerciseId: String,
        topWeightKg: Double,
        topReps: Int,
        estimated1Rm: Double,
        achievedAtMillis: Long,
        newId: String,
    ): PersonalRecordEntity = PersonalRecordEntity(
        id = previous?.id ?: newId,
        userId = userId,
        exerciseId = exerciseId,
        maxWeightKg = maxOf(topWeightKg, previous?.maxWeightKg ?: 0.0),
        maxReps = maxOf(topReps, previous?.maxReps ?: 0),
        estimated1rm = maxOf(estimated1Rm, previous?.estimated1rm ?: 0.0),
        achievedAt = achievedAtMillis,
    )
}
