package app.hypertrophy.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WorkoutSessionEntity::class, SyncOutboxEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class HypertrophyDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
