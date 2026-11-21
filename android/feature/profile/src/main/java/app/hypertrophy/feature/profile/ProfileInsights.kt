package app.hypertrophy.feature.profile

import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.data.local.PersonalRecordWithExercise
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import app.hypertrophy.core.data.sync.LocalSetRecord
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json

data class ProgressionPoint(
    val createdAtMillis: Long,
    val estimated1RmKg: Double,
)

data class ExerciseOption(
    val exerciseId: String,
    val name: String,
    val bestEstimated1RmKg: Double,
)

data class ProfileTotals(
    val sessions: Int,
    val volumeKg: Double,
    val streakDays: Int,
    val sessionsLast30Days: Int,
)

data class BadgeDefinition(
    val code: String,
    val title: String,
    val description: String,
    val target: Int,
)

data class BadgeState(
    val definition: BadgeDefinition,
    val unlockedAtMillis: Long?,
    val progress: Int,
) {
    val unlocked: Boolean get() = unlockedAtMillis != null
    val progressLabel: String
        get() = if (unlocked) "UNLOCKED" else "$progress / ${definition.target}"
}

/**
 * Pure profile calculations: set parsing, estimated 1RM progression, recovery
 * per muscle, totals, and badge progress. All Android-free for JVM tests.
 */
object ProfileInsights {
    private val json = Json { ignoreUnknownKeys = true }

    val badgeDefinitions: List<BadgeDefinition> = listOf(
        BadgeDefinition("first_session", "First Session", "Log one completed workout", 1),
        BadgeDefinition("ten_sessions", "Consistency Starter", "Complete 10 workouts", 10),
        BadgeDefinition("twenty_five_sessions", "Iron Habit", "Complete 25 workouts", 25),
        BadgeDefinition("volume_100k", "Six Figures", "Move 100,000 kg total", 100_000),
        BadgeDefinition("volume_500k", "Half Million", "Move 500,000 kg total", 500_000),
        BadgeDefinition("pr_club", "PR Club", "Set your first personal record", 1),
        BadgeDefinition("century_club", "Century Club", "Reach a 100 kg estimated 1RM", 100),
        BadgeDefinition("streak_7", "Week Warrior", "Train 7 days in a row", 7),
    )

    fun parseSets(setsJson: String): List<LocalSetRecord> =
        runCatching { json.decodeFromString<List<LocalSetRecord>>(setsJson) }
            .getOrDefault(emptyList())

    fun estimatedOneRepMax(weightKg: Double, reps: Int): Double {
        if (weightKg <= 0.0 || reps <= 0) return 0.0
        return weightKg * (1.0 + reps / 30.0)
    }

    fun sessionBestForExercise(session: WorkoutSessionEntity, exerciseId: String): Double =
        parseSets(session.setsJson)
            .asSequence()
            .filter { it.exerciseId == exerciseId }
            .map { estimatedOneRepMax(it.weightKg, it.reps) }
            .maxOrNull()
            ?: 0.0

    fun progressionForExercise(
        sessions: List<WorkoutSessionEntity>,
        exerciseId: String,
    ): List<ProgressionPoint> = sessions
        .sortedBy { it.createdAt }
        .mapNotNull { session ->
            val best = sessionBestForExercise(session, exerciseId)
            if (best <= 0.0) null else ProgressionPoint(session.createdAt, best)
        }

    fun exerciseOptions(
        sessions: List<WorkoutSessionEntity>,
        exerciseById: Map<String, ExerciseEntity>,
    ): List<ExerciseOption> {
        val best = linkedMapOf<String, Double>()
        for (session in sessions) {
            for (set in parseSets(session.setsJson)) {
                val estimate = estimatedOneRepMax(set.weightKg, set.reps)
                if (estimate <= 0.0) continue
                val current = best[set.exerciseId] ?: 0.0
                if (estimate > current) best[set.exerciseId] = estimate
            }
        }
        return best.entries
            .map { (id, e1rm) ->
                ExerciseOption(
                    exerciseId = id,
                    name = exerciseById[id]?.name ?: id.removePrefix("ex_").replace('_', ' '),
                    bestEstimated1RmKg = e1rm,
                )
            }
            .sortedByDescending { it.bestEstimated1RmKg }
    }

    fun defaultExerciseId(options: List<ExerciseOption>, prs: List<PersonalRecordWithExercise>): String? =
        options.firstOrNull()?.exerciseId ?: prs.firstOrNull()?.record?.exerciseId

    fun sessionEpochDays(
        sessions: List<WorkoutSessionEntity>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> = sessions.map { session ->
        Instant.ofEpochMilli(session.createdAt).atZone(zone).toLocalDate().toEpochDay()
    }

    fun totals(
        sessions: List<WorkoutSessionEntity>,
        todayEpochDay: Long,
    ): ProfileTotals {
        val days = sessionEpochDays(sessions).toSet()
        val last30 = sessions.count { session ->
            val day = Instant.ofEpochMilli(session.createdAt)
                .atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            day in (todayEpochDay - 29L)..todayEpochDay
        }
        return ProfileTotals(
            sessions = sessions.size,
            volumeKg = sessions.sumOf { it.totalVolumeKg },
            streakDays = streakDays(days, todayEpochDay),
            sessionsLast30Days = last30,
        )
    }

    fun streakDays(sessionEpochDays: Collection<Long>, todayEpochDay: Long): Int {
        val days = sessionEpochDays.toSet()
        if (days.isEmpty()) return 0
        var cursor = when {
            todayEpochDay in days -> todayEpochDay
            todayEpochDay - 1L in days -> todayEpochDay - 1L
            else -> return 0
        }
        var streak = 0
        while (cursor in days) {
            streak += 1
            cursor -= 1L
        }
        return streak
    }

    /**
     * Latest session that trained each muscle group. The catalogue drives the
     * mapping so the map matches the exercise library instead of a hard-coded
     * workout split.
     */
    fun muscleRecovery(
        sessions: List<WorkoutSessionEntity>,
        exerciseById: Map<String, ExerciseEntity>,
    ): List<MuscleRecovery> {
        val lastTrained = linkedMapOf<MuscleGroup, Long>()
        for (session in sessions.sortedBy { it.createdAt }) {
            for (set in parseSets(session.setsJson)) {
                val catalogMuscle = exerciseById[set.exerciseId]?.primaryMuscle ?: continue
                val group = MuscleGroup.fromCatalogKey(catalogMuscle) ?: continue
                val current = lastTrained[group]
                if (current == null || session.createdAt > current) {
                    lastTrained[group] = session.createdAt
                }
            }
        }
        return MuscleGroup.entries.map { group ->
            MuscleRecovery(muscle = group, lastTrainedAtMillis = lastTrained[group])
        }
    }

    fun badges(
        totals: ProfileTotals,
        prs: List<PersonalRecordWithExercise>,
        persisted: Map<String, Long>,
    ): List<BadgeState> {
        val bestE1rm = prs.maxOfOrNull { it.record.estimated1rm } ?: 0.0
        return badgeDefinitions.map { definition ->
            val progress = when (definition.code) {
                "first_session", "ten_sessions", "twenty_five_sessions" -> totals.sessions
                "volume_100k", "volume_500k" -> totals.volumeKg.toInt()
                "pr_club" -> prs.size
                "century_club" -> bestE1rm.toInt()
                "streak_7" -> totals.streakDays
                else -> 0
            }.coerceAtLeast(0)
            BadgeState(
                definition = definition,
                unlockedAtMillis = persisted[definition.code],
                progress = progress.coerceAtMost(definition.target),
            )
        }
    }

    fun newlyEarned(badges: List<BadgeState>): List<BadgeDefinition> = badges
        .filter { !it.unlocked && it.progress >= it.definition.target }
        .map { it.definition }

    fun formatVolume(kg: Double): String =
        Math.round(kg).toString().replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")

    /** Compact volume for stat tiles: 12,450 stays exact, 12.4k is shortened. */
    fun formatCompactVolume(kg: Double): String {
        val safe = if (kg.isFinite() && kg > 0.0) kg else 0.0
        return when {
            safe >= 1_000_000.0 -> String.format(Locale.US, "%.1fM", safe / 1_000_000.0)
            safe >= 10_000.0 -> String.format(Locale.US, "%.1fk", safe / 1_000.0)
            else -> formatVolume(safe)
        }
    }

    fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

    fun hoursSince(millis: Long, nowMillis: Long): Long =
        TimeUnit.MILLISECONDS.toHours((nowMillis - millis).coerceAtLeast(0))
}
