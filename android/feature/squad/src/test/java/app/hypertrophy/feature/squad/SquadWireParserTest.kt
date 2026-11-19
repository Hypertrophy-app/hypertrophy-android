package app.hypertrophy.feature.squad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SquadWireParserTest {
    @Test
    fun historyFrameParsesMessages() {
        val frame = """
            {"type":"history","messages":[
              {"id":"m1","squadId":"s1","userId":"alice","body":"morning","createdAt":1760000000000},
              {"id":"m2","squadId":"s1","userId":"bob","body":"lets go","createdAt":1760000001000}
            ]}
        """.trimIndent()

        val event = SquadWireParser.parse(frame)
        assertTrue(event is SquadEvent.History)
        val messages = (event as SquadEvent.History).messages
        assertEquals(2, messages.size)
        assertEquals("alice", messages[0].userId)
        assertEquals("morning", messages[0].body)
        assertEquals(1_760_000_000_000L, messages[0].createdAtMillis)
    }

    @Test
    fun presenceFrameParsesStatus() {
        val event = SquadWireParser.parse(
            """{"type":"PRESENCE_UPDATE","userId":"alice","status":"RESTING","at":42}""",
        )
        assertTrue(event is SquadEvent.Presence)
        event as SquadEvent.Presence
        assertEquals("alice", event.userId)
        assertEquals(PresenceStatus.RESTING, event.status)
        assertEquals(42L, event.at)
    }

    @Test
    fun unknownPresenceStatusFallsBackToIdle() {
        val event = SquadWireParser.parse(
            """{"type":"PRESENCE_UPDATE","userId":"alice","status":" teleporting","at":1}""",
        )
        assertTrue(event is SquadEvent.Presence)
        assertEquals(PresenceStatus.IDLE, (event as SquadEvent.Presence).status)
    }

    @Test
    fun chatFrameParsesNestedMessage() {
        val event = SquadWireParser.parse(
            """{"type":"CHAT_MESSAGE","message":{"id":"m9","squadId":"s1","userId":"bob","body":"pr!","createdAt":7}}""",
        )
        assertTrue(event is SquadEvent.Chat)
        val message = (event as SquadEvent.Chat).message
        assertEquals("m9", message.id)
        assertEquals("bob", message.userId)
        assertEquals("pr!", message.body)
    }

    @Test
    fun liveSetProgressParsesAllFields() {
        val event = SquadWireParser.parse(
            """{"type":"LIVE_SET_PROGRESS","userId":"alice","exerciseId":"ex_back_squat","setIndex":2,"reps":8,"weightKg":100.5,"isPr":true,"at":99}""",
        )
        assertTrue(event is SquadEvent.LiveSetProgress)
        event as SquadEvent.LiveSetProgress
        assertEquals("alice", event.userId)
        assertEquals("ex_back_squat", event.exerciseId)
        assertEquals(2, event.setIndex)
        assertEquals(8, event.reps)
        assertEquals(100.5, event.weightKg, 0.001)
        assertTrue(event.isPr)
        assertEquals(99L, event.at)
    }

    @Test
    fun malformedAndUnknownFramesAreIgnored() {
        assertEquals(SquadEvent.Unknown, SquadWireParser.parse("not json at all"))
        assertEquals(SquadEvent.Unknown, SquadWireParser.parse("""{"type":"pong","at":1}"""))
        assertEquals(SquadEvent.Unknown, SquadWireParser.parse("""{"nope":true}"""))
        assertEquals(
            SquadEvent.Unknown,
            SquadWireParser.parse("""{"type":"CHAT_MESSAGE"}"""),
        )
    }

    @Test
    fun unknownKeysAreTolerated() {
        // Forward compatibility: the durable object may add fields later.
        val event = SquadWireParser.parse(
            """{"type":"PRESENCE_UPDATE","userId":"alice","status":"WORKING_OUT","at":5,"futureField":{"x":1}}""",
        )
        assertTrue(event is SquadEvent.Presence)
        assertEquals(PresenceStatus.WORKING_OUT, (event as SquadEvent.Presence).status)
    }
}
