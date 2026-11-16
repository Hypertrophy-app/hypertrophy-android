package app.hypertrophy.core.data.sync

import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.remote.HypertrophyApi
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val httpClient: HttpClient,
) {
    /**
     * Drains the outbox one batched session payload at a time. Successful
     * uploads are only marked synced after the Worker response is accepted,
     * so a crash mid-sync replays safely.
     */
    suspend fun syncPending(): Int {
        val pending = workoutDao.pendingOutbox()
        var synced = 0

        for (entry in pending) {
            val response = httpClient.post("${HypertrophyApi.BASE_URL}/v1/workouts/sessions") {
                header("X-Idempotency-Key", entry.id)
                setBody(TextContent(entry.payloadJson, ContentType.Application.Json))
            }

            if (response.status.isSuccess()) {
                workoutDao.markSynced(entry.id, System.currentTimeMillis())
                synced += 1
            } else {
                workoutDao.markAttempt(entry.id)
            }
        }

        return synced
    }
}
