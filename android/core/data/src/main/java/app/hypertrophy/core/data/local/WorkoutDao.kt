package app.hypertrophy.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A personal record joined with the display name and muscle of its exercise. */
data class PersonalRecordWithExercise(
    @Embedded val record: PersonalRecordEntity,
    @ColumnInfo(name = "exercise_name") val exerciseName: String,
    @ColumnInfo(name = "primary_muscle") val primaryMuscle: String,
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeSessions(userId: String): Flow<List<WorkoutSessionEntity>>

    /** Completion timestamps for the current streak, independent of history windows. */
    @Query("SELECT created_at FROM workout_sessions WHERE user_id = :userId")
    fun observeSessionTimestamps(userId: String): Flow<List<Long>>

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

    /**
     * PR board with exercise names. Uses an INNER JOIN so an unseeded catalogue
     * cannot produce a row without a label; the exercise catalogue is seeded on
     * first launch, but workout plans and schedules are never fabricated.
     */
    @Query(
        """
        SELECT personal_records.*, exercises.name AS exercise_name,
               exercises.primary_muscle AS primary_muscle
        FROM personal_records
        INNER JOIN exercises ON exercises.id = personal_records.exercise_id
        WHERE personal_records.user_id = :userId
        ORDER BY personal_records.achieved_at DESC
        LIMIT :limit
        """,
    )
    fun observePersonalRecordBoard(
        userId: String,
        limit: Int,
    ): Flow<List<PersonalRecordWithExercise>>

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

    @Query(
        "SELECT * FROM sync_outbox WHERE user_id = :userId AND synced_at IS NULL " +
            "ORDER BY created_at ASC LIMIT :limit",
    )
    suspend fun pendingOutboxForUser(userId: String, limit: Int = 50): List<SyncOutboxEntity>

    @Query("UPDATE sync_outbox SET synced_at = :syncedAt, attempts = attempts + 1 WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun markAttempt(id: String)
}
