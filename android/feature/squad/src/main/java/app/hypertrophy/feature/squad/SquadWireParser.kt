package app.hypertrophy.feature.squad

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * One decoded frame from the SquadRoomDO socket. Keeping the wire format in
 * a pure, testable parser means the view model only deals in typed events.
 */
sealed interface SquadEvent {
    data class History(val messages: List<SquadChatMessage>) : SquadEvent

    data class Presence(
        val userId: String,
        val status: PresenceStatus,
        val at: Long,
    ) : SquadEvent

    data class Chat(val message: SquadChatMessage) : SquadEvent

    data class LiveSetProgress(
        val userId: String,
        val exerciseId: String,
        val setIndex: Int,
        val reps: Int,
        val weightKg: Double,
        val isPr: Boolean,
        val at: Long,
    ) : SquadEvent

    data class HostSessionCreate(
        val hostUserId: String,
        val queue: List<HostExercise>,
        val restSeconds: Int,
        val createdAt: Long,
    ) : SquadEvent

    data class HostSessionStart(
        val startedAt: Long,
        val hostUserId: String,
        val queue: List<HostExercise>,
        val restSeconds: Int,
        val createdAt: Long,
    ) : SquadEvent

    data class Error(val code: String) : SquadEvent

    data object Unknown : SquadEvent
}

object SquadWireParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): SquadEvent {
        val root = runCatching { json.parseToJsonElement(text) }
            .getOrNull() as? JsonObject ?: return SquadEvent.Unknown
        val type = root.string("type") ?: return SquadEvent.Unknown

        return when (type) {
            "history" -> SquadEvent.History(
                root.array("messages")
                    ?.mapNotNull { element -> (element as? JsonObject)?.let(::toChat) }
                    ?: emptyList(),
            )

            "PRESENCE_UPDATE" -> SquadEvent.Presence(
                userId = root.string("userId") ?: return SquadEvent.Unknown,
                status = PresenceStatus.fromWire(root.string("status")),
                at = root.long("at") ?: 0L,
            )

            "CHAT_MESSAGE" -> {
                val message = root.obj("message")?.let(::toChat) ?: return SquadEvent.Unknown
                SquadEvent.Chat(message)
            }

            "LIVE_SET_PROGRESS" -> SquadEvent.LiveSetProgress(
                userId = root.string("userId") ?: return SquadEvent.Unknown,
                exerciseId = root.string("exerciseId") ?: return SquadEvent.Unknown,
                setIndex = root.int("setIndex") ?: return SquadEvent.Unknown,
                reps = root.int("reps") ?: return SquadEvent.Unknown,
                weightKg = root.double("weightKg") ?: return SquadEvent.Unknown,
                isPr = root.bool("isPr") ?: false,
                at = root.long("at") ?: 0L,
            )

            "HOST_SESSION_CREATE" -> {
                val restSeconds = root.int("restSeconds") ?: 90
                SquadEvent.HostSessionCreate(
                    hostUserId = root.string("hostUserId") ?: return SquadEvent.Unknown,
                    queue = root.queue(restSeconds),
                    restSeconds = restSeconds,
                    createdAt = root.long("createdAt") ?: 0L,
                )
            }

            "HOST_SESSION_START" -> {
                val restSeconds = root.int("restSeconds") ?: 90
                SquadEvent.HostSessionStart(
                    startedAt = root.long("startedAt") ?: return SquadEvent.Unknown,
                    hostUserId = root.string("hostUserId") ?: "",
                    queue = root.queue(restSeconds),
                    restSeconds = restSeconds,
                    createdAt = root.long("createdAt") ?: 0L,
                )
            }

            "error" -> SquadEvent.Error(root.string("error") ?: "unknown")

            else -> SquadEvent.Unknown
        }
    }

    private fun toChat(obj: JsonObject): SquadChatMessage? {
        val id = obj.string("id") ?: return null
        val userId = obj.string("userId") ?: ""
        return SquadChatMessage(
            id = id,
            squadId = obj.string("squadId") ?: "",
            userId = userId,
            body = obj.string("body") ?: "",
            createdAtMillis = obj.long("createdAt") ?: 0L,
            isSystem = obj.bool("isSystem") ?: (userId == SYSTEM_USER_ID),
        )
    }

    private fun JsonObject.queue(defaultRestSeconds: Int): List<HostExercise> =
        array("queue")
            ?.mapNotNull { element -> (element as? JsonObject)?.toHostExercise(defaultRestSeconds) }
            ?: emptyList()

    private fun JsonObject.toHostExercise(defaultRestSeconds: Int): HostExercise? {
        val exerciseId = string("exerciseId")?.takeIf { it.isNotBlank() } ?: return null
        return HostExercise(
            exerciseId = exerciseId,
            sets = int("sets")?.coerceAtLeast(1) ?: 3,
            reps = int("reps")?.coerceAtLeast(1) ?: 8,
            restSeconds = int("restSeconds")?.coerceAtLeast(0) ?: defaultRestSeconds,
        )
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (get(key) as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? =
        (get(key) as? JsonPrimitive)?.intOrNull

    private fun JsonObject.double(key: String): Double? =
        (get(key) as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (get(key) as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray

    const val SYSTEM_USER_ID = "system"
}

/** Outbound frames. Kept next to the parser so both sides of the wire stay in sync. */
object SquadWireCodec {
    fun hostSessionCreate(queue: List<HostExercise>, restSeconds: Int): String =
        buildJsonObject {
            put("type", "HOST_SESSION_CREATE")
            put("restSeconds", restSeconds)
            putJsonArray("queue") {
                for (exercise in queue) {
                    addJsonObject {
                        put("exerciseId", exercise.exerciseId)
                        put("sets", exercise.sets)
                        put("reps", exercise.reps)
                        put("restSeconds", exercise.restSeconds)
                    }
                }
            }
        }.toString()

    fun hostSessionStart(): String = """{"type":"HOST_SESSION_START"}"""
}
