package app.hypertrophy.core.data.sync

import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOutboxBuilderTest {
    private val now = 1_760_000_000_000L

    private fun sets() = listOf(
        SessionSetInput(exerciseId = "ex_back_squat", reps = 8, weightKg = 100.0, romPercent = 92f),
        SessionSetInput(exerciseId = "ex_back_squat", reps = 6, weightKg = 100.0, romPercent = 88f),
    )

    @Test
    fun sessionAggregatesVolumeAndTimeline() {
        val sessionId = UUID.randomUUID().toString()
        val built = SessionOutboxBuilder.build(
            userId = "user-1",
            sessionId = sessionId,
            title = "Back Squat",
            startedAt = now - 3_600_000L,
            endedAt = now,
            sets = sets(),
            avgFormScore = 95.0,
            aiRecapText = "Solid session.",
            createdAt = now,
        )

        assertEquals(sessionId, built.session.id)
        assertEquals(14 * 100.0, built.session.totalVolumeKg, 0.001)
        assertEquals(now - 3_600_000L, built.session.startedAt)
        assertEquals(now, built.session.endedAt)
        assertEquals(95.0, built.session.avgFormScore!!, 0.001)
        assertTrue(built.session.setsJson.contains("tempoLabel"))
    }

    @Test
    fun outboxEntryTargetsTheSessionEntity() {
        val sessionId = UUID.randomUUID().toString()
        val built = SessionOutboxBuilder.build(
            userId = "user-1",
            sessionId = sessionId,
            title = "Back Squat",
            startedAt = now,
            endedAt = now,
            sets = sets(),
            avgFormScore = null,
            aiRecapText = null,
            createdAt = now,
        )

        val entry = built.outboxEntry
        assertEquals("workout_session", entry.entityType)
        assertEquals(sessionId, entry.entityId)
        assertEquals("create", entry.operation)
        assertEquals(null, entry.syncedAt)
        assertEquals(0, entry.attempts)
        assertTrue(entry.id.length <= 120)
    }

    @Test
    fun payloadMatchesBackendSessionSchema() {
        val sessionId = UUID.randomUUID().toString()
        val built = SessionOutboxBuilder.build(
            userId = "user-1",
            sessionId = sessionId,
            title = "Back Squat",
            startedAt = now,
            endedAt = now + 1_000L,
            sets = sets(),
            avgFormScore = null,
            aiRecapText = null,
            createdAt = now,
        )

        // Mirrors sessionPayloadSchema in backend/src/routes/api.ts.
        val payload = JSONObject(built.outboxEntry.payloadJson)
        assertEquals(sessionId, payload.getString("id"))
        assertTrue(payload.getString("id").let { UUID.fromString(it).toString() == it })
        assertEquals("Back Squat", payload.getString("title"))
        assertTrue(payload.getLong("startedAt") > 0L)
        assertTrue(payload.getLong("endedAt") > 0L)

        val syncSets = payload.getJSONArray("sets")
        assertEquals(2, syncSets.length())
        for (i in 0 until syncSets.length()) {
            val set = syncSets.getJSONObject(i)
            assertEquals("ex_back_squat", set.getString("exerciseId"))
            assertEquals(i, set.getInt("setIndex"))
            assertTrue(set.getInt("reps") > 0)
            assertTrue(set.getDouble("weightKg") > 0.0)
        }
    }

    @Test
    fun localSetsJsonKeepsCoachingFields() {
        val built = SessionOutboxBuilder.build(
            userId = "user-1",
            sessionId = UUID.randomUUID().toString(),
            title = "Back Squat",
            startedAt = now,
            endedAt = now,
            sets = sets(),
            avgFormScore = null,
            aiRecapText = null,
            createdAt = now,
        )

        val local = org.json.JSONArray(built.session.setsJson)
        assertEquals(2, local.length())
        val first = local.getJSONObject(0)
        assertEquals(92.0, first.getDouble("romPercent"), 0.001)
        assertEquals("3-1-1", first.getString("tempoLabel"))
        assertEquals(100, first.getInt("formScore"))
    }

    @Test
    fun longTitlesAreTruncatedToBackendLimit() {
        val built = SessionOutboxBuilder.build(
            userId = "user-1",
            sessionId = UUID.randomUUID().toString(),
            title = "x".repeat(300),
            startedAt = now,
            endedAt = now,
            sets = sets(),
            avgFormScore = null,
            aiRecapText = null,
            createdAt = now,
        )
        assertEquals(120, built.session.title.length)
        assertEquals(120, JSONObject(built.outboxEntry.payloadJson).getString("title").length)
    }

    @Test
    fun emptySetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionOutboxBuilder.build(
                userId = "user-1",
                sessionId = UUID.randomUUID().toString(),
                title = "Back Squat",
                startedAt = now,
                endedAt = now,
                sets = emptyList(),
                avgFormScore = null,
                aiRecapText = null,
                createdAt = now,
            )
        }
    }

    @Test
    fun reversedTimelineIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionOutboxBuilder.build(
                userId = "user-1",
                sessionId = UUID.randomUUID().toString(),
                title = "Back Squat",
                startedAt = now,
                endedAt = now - 1,
                sets = sets(),
                avgFormScore = null,
                aiRecapText = null,
                createdAt = now,
            )
        }
    }

    @Test
    fun sameInputsProduceIdenticalPayload() {
        fun payload() = SessionOutboxBuilder.build(
            userId = "user-1",
            sessionId = "fixed-session-id",
            title = "Back Squat",
            startedAt = now,
            endedAt = now + 5,
            sets = sets(),
            avgFormScore = 90.0,
            aiRecapText = "text",
            createdAt = now,
        ).outboxEntry.payloadJson

        assertEquals(payload(), payload())
    }
}
