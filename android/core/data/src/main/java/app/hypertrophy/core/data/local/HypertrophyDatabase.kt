package app.hypertrophy.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ExerciseEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutSessionEntity::class,
        PersonalRecordEntity::class,
        SquadEntity::class,
        SquadMemberEntity::class,
        ChallengeEntity::class,
        UserBadgeEntity::class,
        SyncOutboxEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class HypertrophyDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
