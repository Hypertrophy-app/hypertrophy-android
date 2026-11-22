package app.hypertrophy.feature.coach.recap

import app.hypertrophy.core.kinematics.TempoSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecapGeneratorTest {
    @Test
    fun emptySessionRecapDoesNotClaimAnySavedWork() {
        val recap = SessionRecapGenerator.empty("Quick Workout")

        assertEquals("Quick Workout: session ended", recap.headline)
        assertTrue(recap.body.contains("No sets were logged"))
        assertTrue(recap.shareText.contains("no sets"))
        assertFalse(recap.newPr)
        assertEquals(0, recap.formScore)
        assertEquals(0f, recap.romPercent, 0.001f)
    }

    @Test
    fun firstSessionSavesBaselineWithoutClaimingAPr() {
        val recap = SessionRecapGenerator.generate(
            current = metrics(),
            previous = null,
            isNewPr = false,
        )

        assertFalse(recap.newPr)
        assertTrue(recap.body.contains("First logged session, baseline saved."))
        assertTrue(recap.headline.startsWith("Back Squat: 24 reps"))
        assertTrue(recap.headline.contains("100 kg"))
    }

    @Test
    fun improvementsReportUpwardDeltas() {
        val recap = SessionRecapGenerator.generate(
            current = metrics(rom = 92f, tempo = 88f, form = 90),
            previous = metrics(rom = 80f, tempo = 84f, form = 85),
            isNewPr = false,
        )

        assertTrue(recap.body.contains("ROM 92%, up 12 from last session."))
        assertTrue(recap.body.contains("Tempo consistency 88%, up 4 from last session."))
        assertTrue(recap.body.contains("Form score 90, up 5 from last session."))
    }

    @Test
    fun regressionsReportDownwardDeltas() {
        val recap = SessionRecapGenerator.generate(
            current = metrics(rom = 75f, tempo = 70f, form = 80),
            previous = metrics(rom = 90f, tempo = 85f, form = 90),
            isNewPr = false,
        )

        assertTrue(recap.body.contains("ROM 75%, down 15 from last session."))
        assertTrue(recap.body.contains("Tempo consistency 70%, down 15 from last session."))
        assertTrue(recap.body.contains("Form score 80, down 10 from last session."))
    }

    @Test
    fun matchingMetricsSaySo() {
        val recap = SessionRecapGenerator.generate(
            current = metrics(),
            previous = metrics(),
            isNewPr = false,
        )

        assertTrue(recap.body.contains("ROM 92%, matching last session."))
        assertTrue(recap.body.contains("Form score 90, matching last session."))
    }

    @Test
    fun newPrIsCalledOutInBodyAndShareText() {
        val recap = SessionRecapGenerator.generate(
            current = metrics(topWeight = 102.5),
            previous = metrics(topWeight = 100.0),
            isNewPr = true,
        )

        assertTrue(recap.newPr)
        assertTrue(recap.body.contains("New personal record at 102.5 kg."))
        assertTrue(recap.shareText.contains("New PR"))
        assertTrue(recap.shareText.contains("102.5 kg"))
    }

    @Test
    fun shareTextCarriesEveryHeadlineStat() {
        val recap = SessionRecapGenerator.generate(
            current = metrics(),
            previous = metrics(),
            isNewPr = false,
        )

        assertTrue(recap.shareText.startsWith("HYPERTROPHY | Back Squat: 24 reps at 100 kg"))
        assertTrue(recap.shareText.contains("ROM 92%"))
        assertTrue(recap.shareText.contains("Form 90"))
        assertTrue(recap.shareText.contains("Tempo 88%"))
        assertFalse(recap.shareText.contains("New PR"))
    }

    @Test
    fun exactTargetTempoScoresOneHundred() {
        val score = SessionRecapGenerator.tempoConsistency(
            listOf(
                TempoSnapshot(3_000L, 1_000L, 1_000L),
                TempoSnapshot(3_000L, 1_000L, 1_000L),
            ),
        )

        assertEquals(100f, score, 0.01f)
    }

    @Test
    fun doubledEccentricLosesOneThirdOfTheScore() {
        val score = SessionRecapGenerator.tempoConsistency(
            listOf(TempoSnapshot(6_000L, 1_000L, 1_000L)),
        )

        assertEquals(66.67f, score, 0.1f)
    }

    @Test
    fun emptyTempoListScoresZero() {
        assertEquals(0f, SessionRecapGenerator.tempoConsistency(emptyList()), 0.001f)
    }

    @Test
    fun recapClampsOutOfRangeMetrics() {
        val recap = SessionRecapGenerator.generate(
            current = metrics(rom = 140f, tempo = -20f, form = 240),
            previous = null,
            isNewPr = false,
        )

        assertEquals(100f, recap.romPercent, 0.001f)
        assertEquals(100, recap.formScore)
        assertTrue(recap.shareText.contains("Form 100"))
        assertTrue(recap.shareText.contains("Tempo 0%"))
    }

    private fun metrics(
        reps: Int = 24,
        topWeight: Double = 100.0,
        rom: Float = 92f,
        tempo: Float = 88f,
        form: Int = 90,
        sets: Int = 3,
    ): SessionMetrics = SessionMetrics(
        exerciseName = "Back Squat",
        sets = sets,
        reps = reps,
        avgRomPercent = rom,
        tempoConsistencyPercent = tempo,
        formScore = form,
        topWeightKg = topWeight,
        estimated1RmKg = topWeight * 1.2,
    )
}
