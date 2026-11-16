package app.hypertrophy.core.data.sync

import app.hypertrophy.core.data.local.SyncOutboxEntity
import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.remote.HypertrophyApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val httpClient: HttpClient,
) {
    suspend fun syncPending(): Int {
        val pending = workoutDao.pendingOutbox()
        if (pending.isEmpty()) return 0

        val entries = JSONArray()
        for (entry in pending) {
            entries.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("entityType", entry.entityType)
                    .put("entityId", entry.entityId)
                    .put("operation", entry.operation)
                    .put("payload", entry.payloadJson)
                    .put("createdAt", entry.createdAt),
            )
        }
        val body = JSONObject().put("entries", entries).toString()

        val response = httpClient.post("${HypertrophyApi.BASE_URL}/api/sync") {
            header("X-Idempotency-Key", pending.first().id)
            setBody(TextContent(body, ContentType.Application.Json))
        }

        if (response.status.isSuccess()) {
            val now = System.currentTimeMillis()
            pending.forEach { workoutDao.markSynced(it.id, now) }
            return pending.size
        } else {
            pending.forEach { workoutDao.markAttempt(it.id) }
            return 0
        }
    }
}
