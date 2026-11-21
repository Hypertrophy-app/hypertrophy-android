package app.hypertrophy.feature.today

import app.hypertrophy.core.data.local.ChallengeEntity
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import app.hypertrophy.core.data.sync.LocalSetRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

/** A muscle group that was trained recently, used by the recovery summary card. */
data class MuscleTraining(
    val key: String,
    val label: String,
    val lastTrainedAtMillis: Long?,
)

enum class RecoveryLevel(val label: String) {
    FATIGUED("Fatigued"),
    RECOVERING("Recovering"),
    FRESH("Fresh"),
}

data class WeekStats(val sessions: Int, val volumeKg: Double)

/**
 * Pure, JVM-testable dashboard calculations. Keeping date math out of the view
 * model makes the Today cards deterministic under test.
 */
object TodayInsights {
    private val json = Json { ignoreUnknownKeys = true }

    val muscleLabels: Map<String, String> = mapOf(
        "quadriceps" to "Quads",
        "hamstrings" to "Hamstrings",
        "glutes" to "Glutes",
        "lower_back" to "Lower Back",
        "chest" to "Chest",
        "triceps" to "Triceps",
        "front_delts" to "Front Delts",
        "side_delts" to "Side Delts",
        "rear_delts" to "Rear Delts",
        "lats" to "Lats",
        "biceps" to "Biceps",
        "core" to "Abs",
        "calves" to "Calves",
        "forearms" to "Forearms",
        "traps" to "Traps",
        "rhomboids" to "Traps",
    )

    fun epochDayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /**
     * Counts consecutive workout days ending today (or yesterday, so a streak
     * is not shown as broken before the user has trained today).
     */
    fun streakDays(sessionEpochDays: Collection<Long>, todayEpochDay: Long): Int {
        val days = sessionEpochDays.toSet()
        if (days.isEmpty()) return 0
        var cursor = when {
            todayEpochDay in days -> todayEpochDay
            todayEpochDay - 1L in days -> todayEpochDay - 1L
            else -> return 0
        }
        var count = 0
        while (cursor in days) {
            count += 1
            cursor -= 1L
        }
        return count
    }

    fun weekStats(sessions: List<WorkoutSessionEntity>, todayEpochDay: Long): WeekStats {
        val firstDay = todayEpochDay - 6L
        val week = sessions.filter { epochDayOf(it.createdAt) in firstDay..todayEpochDay }
        return WeekStats(
            sessions = week.size,
            volumeKg = week.sumOf { it.totalVolumeKg },
        )
    }

    fun sessionsInWindow(sessions: List<WorkoutSessionEntity>, days: Int, todayEpochDay: Long): Int {
        val firstDay = todayEpochDay - (days - 1L).coerceAtLeast(0L)
        return sessions.count { epochDayOf(it.createdAt) in firstDay..todayEpochDay }
    }

    /**
     * Last training instant per catalogue muscle. The coach stores
     * `LocalSetRecord` rows in `sets_json`; malformed rows are ignored so a
     * corrupted legacy session cannot break the dashboard.
     */
    fun lastTrainedByMuscle(
        sessions: List<WorkoutSessionEntity>,
        exerciseById: Map<String, ExerciseEntity>,
    ): List<MuscleTraining> {
        val lastTrained = linkedMapOf<String, Long>()
        for (session in sessions) {
            val sets = runCatching {
                json.decodeFromString<List<LocalSetRecord>>(session.setsJson)
            }.getOrDefault(emptyList())
            for (set in sets) {
                val muscle = exerciseById[set.exerciseId]?.primaryMuscle ?: continue
                val key = muscleLabels[muscle] ?: muscle.replace('_', ' ')
                val current = lastTrained[key]
                if (current == null || session.createdAt > current) {
                    lastTrained[key] = session.createdAt
                }
            }
        }
        return lastTrained.entries
            .sortedByDescending { it.value }
            .map { (key, at) -> MuscleTraining(key = key, label = key, lastTrainedAtMillis = at) }
    }

    fun recoveryLevel(lastTrainedAtMillis: Long?, nowMillis: Long): RecoveryLevel {
        if (lastTrainedAtMillis == null) return RecoveryLevel.FRESH
        val hours = TimeUnit.MILLISECONDS.toHours((nowMillis - lastTrainedAtMillis).coerceAtLeast(0))
        return when {
            hours < 24 -> RecoveryLevel.FATIGUED
            hours < 72 -> RecoveryLevel.RECOVERING
            else -> RecoveryLevel.FRESH
        }
    }

    fun formatVolume(kg: Double): String =
        Math.round(kg).toString().replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")

    fun challengeSummary(challenge: ChallengeEntity, nowMillis: Long): String {
        val target = when (challenge.metricType) {
            "total_volume" -> "${formatVolume(challenge.targetValue.toDouble())} kg"
            "sessions" -> "${challenge.targetValue} sessions"
            "streak" -> "${challenge.targetValue} day streak"
            "reps" -> "${challenge.targetValue} reps"
            else -> challenge.targetValue.toString()
        }
        val daysLeft = TimeUnit.MILLISECONDS.toDays(
            (challenge.endsAt - nowMillis).coerceAtLeast(0),
        )
        val ending = when {
            daysLeft <= 0 -> "Ends today"
            daysLeft == 1L -> "1 day left"
            else -> "$daysLeft days left"
        }
        return "$target | $ending"
    }

    fun defaultWeek(
        todayEpochDay: Long,
        nowMillis: Long,
        userId: String,
    ): List<ScheduledWorkoutEntity> = (0L..6L).map { offset ->
        val rest = offset == 2L || offset == 5L
        ScheduledWorkoutEntity(
            id = "seed-${todayEpochDay + offset}-$userId",
            userId = userId,
            templateId = null,
            title = if (rest) "Rest Day" else "Scheduled Session",
            scheduledDay = todayEpochDay + offset,
            isRestDay = rest,
            isCompleted = false,
            isKeyCompound = !rest,
            exercisesJson = null,
            createdAt = nowMillis,
        )
    }

    fun weekdayLabel(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).dayOfWeek.name
            .lowercase(Locale.US)
            .replaceFirstChar { it.titlecase(Locale.US) }
}
