package app.hypertrophy.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-only weekly planner entry. The schedule never round-trips to D1 until
 * the user starts a session, so it stays editable while offline.
 */
@Entity(
    tableName = "scheduled_workouts",
    indices = [Index(value = ["user_id", "scheduled_day"]), Index(value = ["template_id"])],
)
data class ScheduledWorkoutEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "template_id") val templateId: String?,
    val title: String,
    /** ISO epoch day (days since 1970-01-01). */
    @ColumnInfo(name = "scheduled_day") val scheduledDay: Long,
    @ColumnInfo(name = "is_rest_day") val isRestDay: Boolean = false,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "is_key_compound") val isKeyCompound: Boolean = false,
    @ColumnInfo(name = "exercises_json") val exercisesJson: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
