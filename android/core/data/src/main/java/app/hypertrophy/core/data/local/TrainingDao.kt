package app.hypertrophy.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {
    @Query(
        """
        SELECT * FROM exercises
        WHERE (:muscle IS NULL OR primary_muscle = :muscle)
          AND (:equipment IS NULL OR equipment = :equipment)
          AND (:query IS NULL OR name LIKE '%' || :query || '%')
        ORDER BY name ASC
        """,
    )
    fun searchExercises(
        query: String?,
        muscle: String?,
        equipment: String?,
    ): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun findExercise(id: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun countExercises(): Int

    /**
     * 1-tap substitution: same primary muscle and kinematic profile, ordered by
     * equipment match so an available station is promoted first.
     */
    @Query(
        """
        SELECT * FROM exercises
        WHERE primary_muscle = :primaryMuscle
          AND kinematic_profile_type = :kinematicProfile
          AND id != :excludeId
        ORDER BY CASE WHEN equipment = :preferredEquipment THEN 0 ELSE 1 END, name ASC
        LIMIT :limit
        """,
    )
    suspend fun findSubstitutes(
        primaryMuscle: String,
        kinematicProfile: String,
        excludeId: String,
        preferredEquipment: String,
        limit: Int = 5,
    ): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: WorkoutTemplateEntity)

    @Query("SELECT * FROM workout_templates WHERE user_id = :userId ORDER BY updated_at DESC")
    fun observeTemplates(userId: String): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE id = :id LIMIT 1")
    suspend fun findTemplate(id: String): WorkoutTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduledOne(entry: ScheduledWorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduledAll(entries: List<ScheduledWorkoutEntity>)

    @Query("SELECT * FROM scheduled_workouts WHERE user_id = :userId ORDER BY scheduled_day ASC")
    fun observeSchedule(userId: String): Flow<List<ScheduledWorkoutEntity>>

    @Query("SELECT COUNT(*) FROM scheduled_workouts WHERE user_id = :userId")
    suspend fun countScheduled(userId: String): Int

    @Query(
        "SELECT * FROM scheduled_workouts WHERE user_id = :userId AND scheduled_day = :epochDay " +
            "ORDER BY created_at ASC",
    )
    fun observeScheduledForDay(userId: String, epochDay: Long): Flow<List<ScheduledWorkoutEntity>>

    @Query(
        "SELECT * FROM scheduled_workouts WHERE user_id = :userId " +
            "AND scheduled_day BETWEEN :fromEpochDay AND :toEpochDay ORDER BY scheduled_day ASC",
    )
    fun observeScheduledBetween(
        userId: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): Flow<List<ScheduledWorkoutEntity>>

    @Query(
        """
        SELECT * FROM scheduled_workouts
        WHERE user_id = :userId AND scheduled_day < :todayEpochDay AND is_completed = 0
        ORDER BY scheduled_day ASC
        """,
    )
    suspend fun overdueBefore(userId: String, todayEpochDay: Long): List<ScheduledWorkoutEntity>

    @Query("DELETE FROM scheduled_workouts WHERE id = :id")
    suspend fun deleteScheduled(id: String)

    /**
     * Active or upcoming challenges for every squad the user belongs to. The
     * join is deduplicated by the `(squad_id, user_id)` primary key of
     * `squad_members`, so each challenge appears exactly once.
     */
    @Query(
        """
        SELECT challenges.* FROM challenges
        INNER JOIN squad_members ON squad_members.squad_id = challenges.squad_id
        WHERE squad_members.user_id = :userId AND challenges.ends_at >= :nowMillis
        ORDER BY challenges.ends_at ASC
        """,
    )
    fun observeActiveChallenges(userId: String, nowMillis: Long): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE squad_id = :squadId ORDER BY ends_at ASC")
    fun observeChallengesForSquad(squadId: String): Flow<List<ChallengeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenges(challenges: List<ChallengeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(challenge: ChallengeEntity)

    @Query("SELECT * FROM user_badges WHERE user_id = :userId ORDER BY unlocked_at DESC")
    fun observeBadges(userId: String): Flow<List<UserBadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBadges(badges: List<UserBadgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBadge(badge: UserBadgeEntity)

    /** Exercise lookup for mapping `exerciseId` to its primary muscle. */
    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    fun observeExercisesByIds(ids: List<String>): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun findExercisesByIds(ids: List<String>): List<ExerciseEntity>
}
