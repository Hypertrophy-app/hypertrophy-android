package app.hypertrophy.feature.profile

import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

enum class MuscleGroup(val label: String, val isFront: Boolean) {
    CHEST("Chest", true),
    FRONT_DELTS("Front Delts", true),
    SIDE_DELTS("Side Delts", true),
    REAR_DELTS("Rear Delts", false),
    LATS("Lats", false),
    TRAPS("Traps", false),
    BICEPS("Biceps", true),
    TRICEPS("Triceps", false),
    FOREARMS("Forearms", true),
    ABS("Abs", true),
    OBLIQUES("Obliques", true),
    QUADS("Quads", true),
    HAMSTRINGS("Hamstrings", false),
    GLUTES("Glutes", false),
    CALVES("Calves", false),
    LOWER_BACK("Lower Back", false);

    companion object {
        /** Maps the exercise catalogue's `primary_muscle` values to the map. */
        fun fromCatalogKey(key: String): MuscleGroup? = when (key) {
            "chest" -> CHEST
            "front_delts" -> FRONT_DELTS
            "side_delts" -> SIDE_DELTS
            "rear_delts" -> REAR_DELTS
            "lats" -> LATS
            "traps", "rhomboids" -> TRAPS
            "biceps" -> BICEPS
            "triceps" -> TRICEPS
            "forearms" -> FOREARMS
            "core", "abs" -> ABS
            "obliques" -> OBLIQUES
            "quads", "quadriceps" -> QUADS
            "hamstrings" -> HAMSTRINGS
            "glutes" -> GLUTES
            "calves" -> CALVES
            "lower_back" -> LOWER_BACK
            else -> null
        }
    }
}

enum class RecoveryStatus { FATIGUED, RECOVERING, PRIME }

fun recoveryStatus(lastTrainedAtMillis: Long?, nowMillis: Long): RecoveryStatus {
    if (lastTrainedAtMillis == null) return RecoveryStatus.PRIME
    val hours = TimeUnit.MILLISECONDS.toHours(nowMillis - lastTrainedAtMillis).coerceAtLeast(0)
    return when {
        hours < 24 -> RecoveryStatus.FATIGUED
        hours < 72 -> RecoveryStatus.RECOVERING
        else -> RecoveryStatus.PRIME
    }
}

fun recoveryColor(status: RecoveryStatus): Color = when (status) {
    RecoveryStatus.FATIGUED -> Color(0xFFFF2A4B)
    RecoveryStatus.RECOVERING -> Color(0xFFFF6B1A)
    RecoveryStatus.PRIME -> Color(0xFFFFB800)
}

data class MuscleRecovery(
    val muscle: MuscleGroup,
    val lastTrainedAtMillis: Long?,
) {
    fun status(nowMillis: Long): RecoveryStatus = recoveryStatus(lastTrainedAtMillis, nowMillis)
}

data class HeatmapDay(val epochDay: Long, val sessionCount: Int)

fun buildHeatmap(
    sessionDays: List<Long>,
    days: Int = 90,
    nowEpochDay: Long,
): List<HeatmapDay> {
    val counts = sessionDays.groupingBy { it }.eachCount()
    return (0 until days).map { offset ->
        val day = nowEpochDay - (days - 1 - offset)
        HeatmapDay(epochDay = day, sessionCount = counts[day] ?: 0)
    }
}

fun heatmapIntensity(count: Int): Float = when {
    count <= 0 -> 0f
    count == 1 -> 0.35f
    count == 2 -> 0.65f
    else -> 1f
}
