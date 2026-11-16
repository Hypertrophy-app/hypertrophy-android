package app.hypertrophy.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String? = null,
    @ColumnInfo(name = "streak_count") val streakCount: Int = 0,
    @ColumnInfo(name = "last_workout_date") val lastWorkoutDate: Long? = null,
    @ColumnInfo(name = "privacy_stealth_mode") val privacyStealthMode: Boolean = false,
    @ColumnInfo(name = "dashboard_layout_json") val dashboardLayoutJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "exercises",
    indices = [Index(value = ["primary_muscle"]), Index(value = ["kinematic_profile_type"])],
)
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "primary_muscle") val primaryMuscle: String,
    @ColumnInfo(name = "secondary_muscles_json") val secondaryMusclesJson: String? = null,
    val equipment: String,
    val difficulty: String = "intermediate",
    @ColumnInfo(name = "instructions_json") val instructionsJson: String? = null,
    @ColumnInfo(name = "glb_asset_key") val glbAssetKey: String? = null,
    @ColumnInfo(name = "kinematic_profile_type") val kinematicProfileType: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "workout_templates", indices = [Index(value = ["user_id", "updated_at"])])
data class WorkoutTemplateEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val title: String,
    @ColumnInfo(name = "exercises_json") val exercisesJson: String,
    @ColumnInfo(name = "is_public") val isPublic: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "personal_records",
    indices = [Index(value = ["user_id", "exercise_id"]), Index(value = ["achieved_at"])],
)
data class PersonalRecordEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "max_weight_kg") val maxWeightKg: Double = 0.0,
    @ColumnInfo(name = "max_reps") val maxReps: Int = 0,
    @ColumnInfo(name = "estimated_1rm") val estimated1rm: Double = 0.0,
    @ColumnInfo(name = "achieved_at") val achievedAt: Long,
)

@Entity(tableName = "squads")
data class SquadEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "invite_code") val inviteCode: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "squad_members",
    primaryKeys = ["squad_id", "user_id"],
    indices = [Index(value = ["squad_id", "joined_at"]), Index(value = ["user_id", "joined_at"])],
)
data class SquadMemberEntity(
    @ColumnInfo(name = "squad_id") val squadId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val role: String = "athlete",
    @ColumnInfo(name = "joined_at") val joinedAt: Long,
)

@Entity(tableName = "challenges", indices = [Index(value = ["squad_id", "ends_at"])])
data class ChallengeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "squad_id") val squadId: String,
    val title: String,
    @ColumnInfo(name = "metric_type") val metricType: String,
    @ColumnInfo(name = "target_value") val targetValue: Long,
    @ColumnInfo(name = "starts_at") val startsAt: Long,
    @ColumnInfo(name = "ends_at") val endsAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "user_badges", primaryKeys = ["user_id", "badge_code"])
data class UserBadgeEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "badge_code") val badgeCode: String,
    @ColumnInfo(name = "unlocked_at") val unlockedAt: Long,
)
