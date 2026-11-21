package app.hypertrophy.feature.squad

import app.hypertrophy.core.data.local.SquadEntity
import app.hypertrophy.core.data.local.SquadMemberEntity
import java.util.Locale

enum class PresenceStatus(val wire: String) {
    WORKING_OUT("WORKING_OUT"),
    RESTING("RESTING"),
    IDLE("IDLE"),
    OFFLINE("OFFLINE");

    companion object {
        fun fromWire(value: String?): PresenceStatus =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: IDLE
    }
}

data class SquadMemberStatus(
    val userId: String,
    val status: PresenceStatus,
    val detail: String = "",
    val updatedAtMillis: Long = 0L,
)

data class SquadChatMessage(
    val id: String,
    val squadId: String,
    val userId: String,
    val body: String,
    val createdAtMillis: Long,
    val isSystem: Boolean = false,
)

/** One exercise in a hosted group session queue. */
data class HostExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
)

/**
 * A "workout together" lobby owned by one squad member. The host builds the
 * queue, then starts it for everyone connected to the room.
 */
data class HostSessionUi(
    val hostUserId: String,
    val queue: List<HostExercise>,
    val restSeconds: Int,
    val createdAtMillis: Long,
    val startedAtMillis: Long? = null,
    val isHost: Boolean = false,
) {
    val started: Boolean get() = startedAtMillis != null
}

object InviteCode {
    private val pattern = Regex("^[A-Z0-9]{6}$")

    fun normalize(raw: String): String = raw.trim().uppercase(Locale.US)

    fun isValid(raw: String): Boolean = pattern.matches(normalize(raw))

    fun deepLink(code: String): String = "hypertrophy://join/${normalize(code)}"
}

fun presenceLabel(status: SquadMemberStatus): String = when (status.status) {
    PresenceStatus.WORKING_OUT -> "Working out"
    PresenceStatus.RESTING -> "Resting"
    PresenceStatus.IDLE -> "Idle"
    PresenceStatus.OFFLINE -> "Offline"
}

/** Maps raw socket error codes to copy that is safe to show in chat UI. */
fun squadErrorMessage(code: String): String = when (code) {
    "no_session" -> "No group session to start"
    "not_host" -> "Only the host can start the group session"
    "session_exists" -> "A teammate is already hosting. Wait or ask them to start."
    "invalid_body" -> "Message cannot be empty"
    "invalid_json" -> "Could not read the squad message"
    "unknown_type" -> "Unsupported squad action"
    else -> "Squad error: $code"
}

/** Room cache mapping. Keeping it pure makes the offline snapshot rules testable. */
fun SquadEntity.toDto(role: String): SquadDto = SquadDto(
    id = id,
    name = name,
    inviteCode = inviteCode,
    ownerId = ownerId,
    createdAt = createdAt,
    role = role,
)

fun SquadDto.toEntity(): SquadEntity = SquadEntity(
    id = id,
    name = name,
    inviteCode = inviteCode,
    ownerId = ownerId,
    createdAt = createdAt,
)

fun SquadDto.toMembership(userId: String, joinedAt: Long): SquadMemberEntity = SquadMemberEntity(
    squadId = id,
    userId = userId,
    role = role ?: "athlete",
    joinedAt = joinedAt,
)

