package app.hypertrophy.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable outbox. Every local mutation writes here first; [SyncWorker] drains
 * it and Room remains the single source of truth for the UI.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["user_id", "created_at"]),
        Index(value = ["synced_at", "created_at"]),
    ],
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val operation: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "synced_at") val syncedAt: Long? = null,
)
