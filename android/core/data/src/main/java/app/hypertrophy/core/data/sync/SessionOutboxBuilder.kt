package app.hypertrophy.core.data.sync

import app.hypertrophy.core.data.local.SyncOutboxEntity
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One completed set as recorded by the coach HUD. */
data class SessionSetInput(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double,
    val romPercent: Float = 0f,
    val tempoLabel: String = "3-1-1",
    val formScore: Int = 100,
)

data class BuiltSession(
    val session: WorkoutSessionEntity,
    val outboxEntry: SyncOutboxEntity,
)

/**
 * Payload shapes for POST /api/sync. Field names and bounds mirror
 * `sessionPayloadSchema` / `setSchema` in backend/src/routes/api.ts; the
 * backend rejects anything else with `invalid_session_payload`.
 */
@Serializable
data class SyncSetPayload(
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
)

@Serializable
data class SyncSessionPayload(
    val id: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val sets: List<SyncSetPayload>,
    val squadId: String? = null,
)

/** Local mirror of a set row stored in workout_sessions.sets_json. */
@Serializable
data class LocalSetRecord(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double,
    val romPercent: Float = 0f,
    val tempoLabel: String = "",
    val formScore: Int = 100,
)

/**
 * Builds the Room session row and its single durable outbox entry in one
 * place so the offline-first write path (Room first, outbox drained by
 * SyncWorker) can never drift from the backend contract.
 */
object SessionOutboxBuilder {
    const val ENTITY_TYPE = "workout_session"
    const val OPERATION = "create"

    private val json = Json {
        ignoreUnknownKeys = true
        // Coaching defaults are real payload values, not optional wire noise:
        // keep them so legacy parsing and local recaps see the full set row.
        encodeDefaults = true
    }

    fun build(
        userId: String,
        sessionId: String,
        title: String,
        startedAt: Long,
        endedAt: Long,
        sets: List<SessionSetInput>,
        avgFormScore: Double?,
        aiRecapText: String?,
        createdAt: Long,
        squadId: String? = null,
    ): BuiltSession {
        require(sets.isNotEmpty()) { "a session needs at least one set" }
        require(endedAt >= startedAt) { "endedAt must not precede startedAt" }

        val totalVolume = sets.sumOf { it.reps * it.weightKg }
        val localRecords = sets.map {
            LocalSetRecord(
                exerciseId = it.exerciseId,
                reps = it.reps,
                weightKg = it.weightKg,
                romPercent = it.romPercent,
                tempoLabel = it.tempoLabel,
                formScore = it.formScore,
            )
        }
        val syncPayload = SyncSessionPayload(
            id = sessionId,
            title = title.take(120),
            startedAt = startedAt,
            endedAt = endedAt,
            sets = sets.mapIndexed { index, set ->
                SyncSetPayload(
                    exerciseId = set.exerciseId,
                    setIndex = index,
                    reps = set.reps,
                    weightKg = set.weightKg,
                )
            },
            squadId = squadId,
        )

        val session = WorkoutSessionEntity(
            id = sessionId,
            userId = userId,
            squadId = squadId,
            title = title.take(120),
            startedAt = startedAt,
            endedAt = endedAt,
            totalVolumeKg = totalVolume,
            avgFormScore = avgFormScore,
            aiRecapText = aiRecapText,
            setsJson = json.encodeToString(localRecords),
            createdAt = createdAt,
        )
        val outboxEntry = SyncOutboxEntity(
            id = "$ENTITY_TYPE:$sessionId",
            userId = userId,
            entityType = ENTITY_TYPE,
            entityId = sessionId,
            operation = OPERATION,
            payloadJson = json.encodeToString(syncPayload),
            createdAt = createdAt,
        )
        return BuiltSession(session = session, outboxEntry = outboxEntry)
    }
}
