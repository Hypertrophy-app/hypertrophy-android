package app.hypertrophy.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["user_id", "created_at"]),
        Index(value = ["squad_id", "created_at"]),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "squad_id") val squadId: String?,
    val title: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "total_volume_kg") val totalVolumeKg: Double,
    @ColumnInfo(name = "sets_json") val setsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
