package app.hypertrophy.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeSessions(userId: String): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun findSession(id: String): WorkoutSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueOutbox(entry: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox WHERE synced_at IS NULL ORDER BY created_at ASC LIMIT :limit")
    suspend fun pendingOutbox(limit: Int = 50): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET synced_at = :syncedAt, attempts = attempts + 1 WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun markAttempt(id: String)
}
