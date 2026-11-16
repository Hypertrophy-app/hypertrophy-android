package app.hypertrophy.core.data

import app.hypertrophy.core.data.local.SyncOutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncOutboxEntityTest {
    @Test
    fun newOutboxEntryStartsUnsynced() {
        val entry = SyncOutboxEntity(
            id = "outbox-1",
            userId = "user-1",
            entityType = "workout_session",
            entityId = "session-1",
            operation = "create",
            payloadJson = "{}",
            createdAt = 1_700_000_000_000,
        )

        assertNull(entry.syncedAt)
        assertEquals(0, entry.attempts)
    }
}
