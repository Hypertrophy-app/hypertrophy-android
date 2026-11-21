package app.hypertrophy.feature.profile

import app.hypertrophy.core.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectExportTest {
    @Test
    fun draftsUseSessionDerivedClientRecordIdsForIdempotentExports() {
        val session = session(id = "session-1", start = 1_000L, end = 1_000L + 30L * 60_000L)

        val drafts = HealthConnectExport.drafts(listOf(session))

        assertEquals(1, drafts.size)
        assertEquals("hypertrophy-session-1", drafts.first().exerciseClientRecordId)
        assertEquals("hypertrophy-session-1-cal", drafts.first().caloriesClientRecordId)
    }

    @Test
    fun caloriesScaleWithDurationAndHaveAMinimum() {
        val short = HealthConnectExport.drafts(
            listOf(session(id = "short", start = 1_000L, end = 1_000L + 1_000L)),
        ).first()
        val long = HealthConnectExport.drafts(
            listOf(session(id = "long", start = 1_000L, end = 1_000L + 40L * 60_000L)),
        ).first()

        assertEquals(1.0, short.activeCalories, 0.001)
        assertEquals(200.0, long.activeCalories, 0.001)
        assertEquals(40L, long.durationMinutes)
    }

    @Test
    fun draftsSkipCorruptTimestampsAndFallBackToADefaultTitle() {
        val drafts = HealthConnectExport.drafts(
            listOf(
                session(id = "bad-end", start = 5_000L, end = 4_000L),
                session(id = "bad-start", start = 0L, end = 10_000L),
                session(id = "blank-title", start = 1_000L, end = 2_000L, title = "   "),
            ),
        )

        assertEquals(1, drafts.size)
        assertEquals("Strength training", drafts.first().title)
        assertTrue(drafts.none { it.sessionId == "bad-end" })
        assertTrue(drafts.none { it.sessionId == "bad-start" })
    }

    @Test
    fun longTitlesAreTruncatedToTheClientRecordLimit() {
        val title = "A".repeat(150)

        val draft = HealthConnectExport.drafts(
            listOf(session(id = "title", start = 1_000L, end = 2_000L, title = title)),
        ).first()

        assertEquals(HealthConnectExport.MAX_TITLE_LENGTH, draft.title.length)
    }

    private fun session(
        id: String,
        start: Long,
        end: Long,
        title: String = "Hypertrophy session",
    ): WorkoutSessionEntity = WorkoutSessionEntity(
        id = id,
        userId = "local-user",
        squadId = null,
        title = title,
        startedAt = start,
        endedAt = end,
        totalVolumeKg = 0.0,
        avgFormScore = 100.0,
        aiRecapText = null,
        setsJson = "[]",
        createdAt = start,
    )
}
