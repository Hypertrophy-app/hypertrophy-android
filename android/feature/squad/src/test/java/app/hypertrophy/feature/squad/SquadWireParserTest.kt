package app.hypertrophy.feature.squad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
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

    @Test
    fun systemMilestoneMessagesAreFlaggedByReservedUserId() {
        val event = SquadWireParser.parse(
            """{"type":"CHAT_MESSAGE","message":{"id":"m1","squadId":"s1","userId":"system","body":"Alice set a PR","createdAt":7}}""",
        )

        assertTrue(event is SquadEvent.Chat)
        assertTrue((event as SquadEvent.Chat).message.isSystem)
    }

    @Test
    fun systemFlagWinsOverUserIdWhenBothArePresent() {
        val event = SquadWireParser.parse(
            """{"type":"CHAT_MESSAGE","message":{"id":"m1","squadId":"s1","userId":"alice","body":"recap","createdAt":7,"isSystem":true}}""",
        )

        assertTrue((event as SquadEvent.Chat).message.isSystem)
    }

    @Test
    fun hostSessionCreateParsesQueueAndRest() {
        val event = SquadWireParser.parse(
            """
            {"type":"HOST_SESSION_CREATE","hostUserId":"alice","restSeconds":120,"createdAt":50,
             "queue":[
               {"exerciseId":"ex_back_squat","sets":3,"reps":8,"restSeconds":120},
               {"exerciseId":"ex_bench_press","sets":4,"reps":6,"restSeconds":90}
             ]}
            """.trimIndent(),
        )

        assertTrue(event is SquadEvent.HostSessionCreate)
        event as SquadEvent.HostSessionCreate
        assertEquals("alice", event.hostUserId)
        assertEquals(120, event.restSeconds)
        assertEquals(50L, event.createdAt)
        assertEquals(2, event.queue.size)
        assertEquals("ex_back_squat", event.queue[0].exerciseId)
        assertEquals(4, event.queue[1].sets)
        assertEquals(6, event.queue[1].reps)
    }

    @Test
    fun hostSessionQueueFallsBackToDefaultsForMissingNumbers() {
        val event = SquadWireParser.parse(
            """{"type":"HOST_SESSION_CREATE","hostUserId":"alice","restSeconds":75,"queue":[{"exerciseId":"ex_rdl"}]}""",
        )

        val exercise = (event as SquadEvent.HostSessionCreate).queue.single()
        assertEquals(3, exercise.sets)
        assertEquals(8, exercise.reps)
        assertEquals(75, exercise.restSeconds)
    }

    @Test
    fun hostSessionStartParsesQueueForLateJoiners() {
        val event = SquadWireParser.parse(
            """
            {"type":"HOST_SESSION_START","hostUserId":"bob","restSeconds":90,"createdAt":10,
             "startedAt":20,"queue":[{"exerciseId":"ex_deadlift","sets":3,"reps":5,"restSeconds":90}]}
            """.trimIndent(),
        )

        assertTrue(event is SquadEvent.HostSessionStart)
        event as SquadEvent.HostSessionStart
        assertEquals(20L, event.startedAt)
        assertEquals("bob", event.hostUserId)
        assertEquals(1, event.queue.size)
    }

    @Test
    fun errorFramesSurfaceTheServerCode() {
        val event = SquadWireParser.parse("""{"type":"error","error":"not_host"}""")

        assertEquals(SquadEvent.Error("not_host"), event)
        assertEquals("Only the host can start the group session", squadErrorMessage("not_host"))
    }

    @Test
    fun hostCreateCodecProducesTheServerContract() {
        val text = SquadWireCodec.hostSessionCreate(
            queue = listOf(
                HostExercise("ex_back_squat", sets = 3, reps = 8, restSeconds = 120),
            ),
            restSeconds = 120,
        )
        val root = Json.parseToJsonElement(text) as JsonObject

        assertEquals("HOST_SESSION_CREATE", (root["type"] as JsonPrimitive).content)
        assertEquals(120, (root["restSeconds"] as JsonPrimitive).int)
        val queue = root["queue"] as JsonArray
        assertEquals(1, queue.size)
        val entry = queue[0] as JsonObject
        assertEquals("ex_back_squat", (entry["exerciseId"] as JsonPrimitive).content)
        assertEquals(3, (entry["sets"] as JsonPrimitive).int)
    }

    @Test
    fun hostStartCodecUsesTheExpectedType() {
        assertEquals(
            "HOST_SESSION_START",
            (Json.parseToJsonElement(SquadWireCodec.hostSessionStart()) as JsonObject)
                .let { (it["type"] as JsonPrimitive).content },
        )
    }
}
