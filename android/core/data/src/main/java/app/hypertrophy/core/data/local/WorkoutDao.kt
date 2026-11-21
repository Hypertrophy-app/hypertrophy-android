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

    /**
     * History window for the Today dashboard and Profile feed. [sinceMillis] is
     * compared against `created_at` (the completion timestamp) to stay on the
     * existing `(user_id, created_at)` index.
     */
    @Query(
        "SELECT * FROM workout_sessions WHERE user_id = :userId AND created_at >= :sinceMillis " +
            "ORDER BY created_at DESC",
    )
    fun observeSessionsSince(userId: String, sinceMillis: Long): Flow<List<WorkoutSessionEntity>>

    /** Inclusive `[fromMillis, toMillis]` window ordered oldest first. */
    @Query(
        "SELECT * FROM workout_sessions WHERE user_id = :userId " +
            "AND created_at BETWEEN :fromMillis AND :toMillis ORDER BY created_at ASC",
    )
    fun observeSessionsBetween(
        userId: String,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<WorkoutSessionEntity>>

    @Query(
        "SELECT COUNT(*) FROM workout_sessions WHERE user_id = :userId AND created_at >= :sinceMillis",
    )
    fun observeSessionCountSince(userId: String, sinceMillis: Long): Flow<Int>

    /** Nullable-safe lifetime/window volume; an empty window emits 0.0. */
    @Query(
        "SELECT COALESCE(SUM(total_volume_kg), 0.0) FROM workout_sessions " +
            "WHERE user_id = :userId AND created_at >= :sinceMillis",
    )
    fun observeTotalVolumeSince(userId: String, sinceMillis: Long): Flow<Double>

    @Query("SELECT * FROM workout_sessions WHERE user_id = :userId ORDER BY created_at DESC LIMIT 1")
    suspend fun latestSession(userId: String): WorkoutSessionEntity?

    @Query(
        "SELECT * FROM workout_sessions WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit",
    )
    fun observeRecentSessions(userId: String, limit: Int): Flow<List<WorkoutSessionEntity>>

    @Query(
        "SELECT * FROM workout_sessions WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit",
    )
    suspend fun recentSessions(userId: String, limit: Int): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE id = :id LIMIT 1")
    suspend fun findSession(id: String): WorkoutSessionEntity?

    /** All PR rows for the profile PR board, newest achievement first. */
    @Query("SELECT * FROM personal_records WHERE user_id = :userId ORDER BY achieved_at DESC")
    fun observePersonalRecords(userId: String): Flow<List<PersonalRecordEntity>>

    @Query(
        "SELECT * FROM personal_records WHERE user_id = :userId AND exercise_id = :exerciseId " +
            "ORDER BY achieved_at DESC LIMIT 1",
    )
    suspend fun findPersonalRecord(userId: String, exerciseId: String): PersonalRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPersonalRecord(record: PersonalRecordEntity)

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
