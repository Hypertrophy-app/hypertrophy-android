package app.hypertrophy.feature.squad

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
