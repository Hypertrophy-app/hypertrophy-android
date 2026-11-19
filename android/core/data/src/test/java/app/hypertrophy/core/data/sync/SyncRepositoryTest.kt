package app.hypertrophy.core.data.sync

import app.hypertrophy.core.data.local.PersonalRecordEntity
import app.hypertrophy.core.data.local.SyncOutboxEntity
import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeWorkoutDao(initial: List<SyncOutboxEntity> = emptyList()) : WorkoutDao {
    val outbox = initial.toMutableList()
    var sessionUpserts = 0

    override fun observeSessions(userId: String): Flow<List<WorkoutSessionEntity>> = flowOf(emptyList())

    override suspend fun findSession(id: String): WorkoutSessionEntity? = null

    override suspend fun findPersonalRecord(userId: String, exerciseId: String): PersonalRecordEntity? = null

    override suspend fun upsertPersonalRecord(record: PersonalRecordEntity) = Unit

    override suspend fun upsertSession(session: WorkoutSessionEntity) {
        sessionUpserts++
    }

    override suspend fun enqueueOutbox(entry: SyncOutboxEntity) {
        outbox.removeAll { it.id == entry.id }
        outbox += entry
    }

    override suspend fun pendingOutbox(limit: Int): List<SyncOutboxEntity> =
        outbox.filter { it.syncedAt == null }.sortedBy { it.createdAt }.take(limit)

    override suspend fun markSynced(id: String, syncedAt: Long) {
        val index = outbox.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = outbox[index]
            outbox[index] = old.copy(syncedAt = syncedAt, attempts = old.attempts + 1)
        }
    }

    override suspend fun markAttempt(id: String) {
        val index = outbox.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = outbox[index]
            outbox[index] = old.copy(attempts = old.attempts + 1)
        }
    }
}

private fun entry(
    id: String,
    createdAt: Long,
    payloadJson: String = """{"id":"s1","title":"Back Squat","startedAt":1,"endedAt":2,"sets":[]}""",
) = SyncOutboxEntity(
    id = id,
    userId = "local-user",
    entityType = "workout_session",
    entityId = "s1",
    operation = "create",
    payloadJson = payloadJson,
    createdAt = createdAt,
)

class SyncRepositoryTest {
    private fun repository(
        dao: FakeWorkoutDao,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ): SyncRepository {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = """{"synced":["x"],"failed":[]}""",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return SyncRepository(dao, HttpClient(engine))
    }

    @Test
    fun successfulFlushMarksAllPendingSynced() = runBlocking {
        val dao = FakeWorkoutDao(listOf(entry("a", 1L), entry("b", 2L)))
        val repo = repository(dao)

        assertEquals(2, repo.syncPending())
        assertTrue(dao.outbox.all { it.syncedAt != null })
        // Idempotent: nothing left to send.
        assertEquals(0, repo.syncPending())
    }

    @Test
    fun secondFlushSendsNoHttpRequest() = runBlocking {
        val dao = FakeWorkoutDao(listOf(entry("a", 1L)))
        var requests = 0
        val repo = repository(dao, onRequest = { requests++ })

        repo.syncPending()
        repo.syncPending()
        assertEquals(1, requests)
    }

    @Test
    fun failureKeepsRowsPendingAndCountsAttempts() = runBlocking {
        val dao = FakeWorkoutDao(listOf(entry("a", 1L)))
        val repo = repository(dao, status = HttpStatusCode.InternalServerError)

        assertEquals(0, repo.syncPending())
        assertNull(dao.outbox.single().syncedAt)
        assertEquals(1, dao.outbox.single().attempts)

        // A later run with a healthy endpoint still delivers the same row.
        val recovered = repository(dao)
        assertEquals(1, recovered.syncPending())
        assertNotNull(dao.outbox.single().syncedAt)
    }

    @Test
    fun payloadIsSentAsJsonObjectNotString() = runBlocking {
        val payload = """{"id":"11111111-2222-3333-4444-555555555555","title":"Back Squat","startedAt":1,"endedAt":2,"sets":[{"exerciseId":"ex_back_squat","setIndex":0,"reps":8,"weightKg":100.0}]}"""
        val dao = FakeWorkoutDao(listOf(entry("a", 1L, payload)))
        var body: JSONObject? = null
        val repo = repository(dao, onRequest = { request -> body = JSONObject((request.body as io.ktor.http.content.TextContent).text) })

        repo.syncPending()

        assertNotNull(body)
        val sent = body!!
        val first = sent.getJSONArray("entries").getJSONObject(0)
        // Must be a JSON object: the backend's sessionPayloadSchema rejects strings.
        val sentPayload = first.get("payload")
        assertTrue(sentPayload is JSONObject)
        assertEquals("11111111-2222-3333-4444-555555555555", (sentPayload as JSONObject).getString("id"))
        assertEquals(1, sentPayload.getJSONArray("sets").length())
    }

    @Test
    fun idempotencyKeyIsFirstPendingEntryId() = runBlocking {
        val dao = FakeWorkoutDao(listOf(entry("first", 1L), entry("second", 2L)))
        var key: String? = null
        val repo = repository(dao, onRequest = { request -> key = request.headers["X-Idempotency-Key"] })

        repo.syncPending()
        assertEquals("first", key)
    }

    @Test
    fun entriesAreOrderedOldestFirstAndBatchedInOneRequest() = runBlocking {
        val dao = FakeWorkoutDao(listOf(entry("newer", 5L), entry("older", 1L), entry("middle", 3L)))
        var body: JSONObject? = null
        val repo = repository(dao, onRequest = { request -> body = JSONObject((request.body as io.ktor.http.content.TextContent).text) })

        assertEquals(3, repo.syncPending())

        val order = body!!.getJSONArray("entries").let { array ->
            (0 until array.length()).map { array.getJSONObject(it).getString("id") }
        }
        assertEquals(listOf("older", "middle", "newer"), order)
        assertTrue(dao.outbox.all { it.syncedAt != null })
    }
}
