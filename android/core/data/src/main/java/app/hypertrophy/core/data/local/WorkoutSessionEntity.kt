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
    @ColumnInfo(name = "ended_at") val endedAt: Long,
    @ColumnInfo(name = "total_volume_kg") val totalVolumeKg: Double,
    @ColumnInfo(name = "avg_form_score") val avgFormScore: Double? = null,
    @ColumnInfo(name = "ai_recap_text") val aiRecapText: String? = null,
    @ColumnInfo(name = "sets_json") val setsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
