package app.hypertrophy.feature.coach.recap

import app.hypertrophy.core.kinematics.TempoSnapshot
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Aggregate metrics for one finished coach session. */
data class SessionMetrics(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val avgRomPercent: Float,
    val tempoConsistencyPercent: Float,
    val formScore: Int,
    val topWeightKg: Double,
    val estimated1RmKg: Double,
)

data class SessionRecap(
    val headline: String,
    val body: String,
    val shareText: String,
    val formScore: Int,
    val romPercent: Float,
    val newPr: Boolean,
)

/**
 * Deterministic natural-language recap. No network call and no generative
 * model: the same metrics always produce the same summary, which keeps the
 * post-workout review instant and reproducible offline.
 */
object SessionRecapGenerator {
    /**
     * A finished quick workout with no logged sets. It still ends the session
     * and gives the user a way out, but nothing is persisted or reported as a
     * personal record.
     */
    fun empty(exerciseName: String): SessionRecap = SessionRecap(
        headline = "$exerciseName: session ended",
        body = "No sets were logged, so nothing was saved. " +
            "Start again whenever you are ready.",
        shareText = "HYPERTROPHY | $exerciseName | Quick workout ended with no sets.",
        formScore = 0,
        romPercent = 0f,
        newPr = false,
    )

    fun generate(
        current: SessionMetrics,
        previous: SessionMetrics?,
        isNewPr: Boolean,
    ): SessionRecap {
        val rom = current.avgRomPercent.coerceIn(0f, 100f)
        val form = current.formScore.coerceIn(0, 100)
        val tempo = current.tempoConsistencyPercent.coerceIn(0f, 100f)

        val headline = buildString {
            append(current.exerciseName)
            append(": ")
            append(current.reps)
            append(" reps at ")
            append(kg(current.topWeightKg))
            append(" kg")
        }

        val romLine = deltaLine("ROM", rom, previous?.avgRomPercent, "%")
        val tempoLine = deltaLine(
            "Tempo consistency",
            tempo,
            previous?.tempoConsistencyPercent,
            "%",
        )
        val formLine = deltaLine(
            "Form score",
            form.toFloat(),
            previous?.formScore?.toFloat(),
            "",
        )

        val prLine = if (isNewPr) {
            "New personal record at ${kg(current.topWeightKg)} kg."
        } else {
            val previousBest = previous?.topWeightKg
            if (previousBest == null) {
                "First logged session, baseline saved."
            } else {
                val gap = (previousBest - current.topWeightKg).coerceAtLeast(0.0)
                "Top set ${kg(current.topWeightKg)} kg, ${kg(gap)} kg under your prior best."
            }
        }

        val body = "${current.sets} sets, ${current.reps} reps. " +
            "$romLine $tempoLine $formLine $prLine"

        val prTag = if (isNewPr) " | New PR" else ""
        val shareText = "HYPERTROPHY | $headline | ROM ${rom.roundToInt()}% | " +
            "Form $form | Tempo ${tempo.roundToInt()}%$prTag"

        return SessionRecap(
            headline = headline,
            body = body,
            shareText = shareText,
            formScore = form,
            romPercent = rom,
            newPr = isNewPr,
        )
    }

    /**
     * Percentage agreement with a 3-1-1 tempo target. Exact 3-1-1 reps score
     * 100; large phase deviations pull the score toward zero.
     */
    fun tempoConsistency(
        tempos: List<TempoSnapshot>,
        targetEccentricMs: Long = 3_000L,
        targetPauseMs: Long = 1_000L,
        targetConcentricMs: Long = 1_000L,
    ): Float {
        if (tempos.isEmpty()) return 0f

        var deviation = 0.0
        for (tempo in tempos) {
            deviation += deviationRatio(tempo.eccentricMs, targetEccentricMs)
            deviation += deviationRatio(tempo.pauseMs, targetPauseMs)
            deviation += deviationRatio(tempo.concentricMs, targetConcentricMs)
        }
        val average = deviation / (tempos.size * 3)
        return (100.0 - average * 100.0).coerceIn(0.0, 100.0).toFloat()
    }

    private fun deviationRatio(actualMs: Long, targetMs: Long): Double {
        if (targetMs <= 0L) return 0.0
        return abs(actualMs - targetMs).toDouble() / targetMs.toDouble()
    }

    private fun deltaLine(
        label: String,
        current: Float,
        previous: Float?,
        suffix: String,
    ): String {
        val value = current.roundToInt()
        if (previous == null) {
            return "$label $value$suffix in this baseline session."
        }

        val delta = (current - previous).roundToInt()
        return when {
            delta > 0 -> "$label $value$suffix, up $delta from last session."
            delta < 0 -> "$label $value$suffix, down ${-delta} from last session."
            else -> "$label $value$suffix, matching last session."
        }
    }

    private fun kg(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
}
