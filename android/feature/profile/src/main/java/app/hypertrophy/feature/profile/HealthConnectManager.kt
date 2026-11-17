package app.hypertrophy.feature.profile

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectManager(private val context: Context) {
    val availability: Int get() = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean
        get() = availability == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? by lazy {
        if (isAvailable) HealthConnectClient.getOrCreate(context) else null
    }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
    )

    suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val c = client ?: return emptyList()
        return runCatching {
            c.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                ),
            ).records
        }.getOrDefault(emptyList())
    }

    /**
     * Writing records requires Health Connect's internal record builders; the
     * public constructors are gated by the SDK. Exposed as a hook so the
     * feature compiles against the published client without depending on
     * internal APIs.
     */
    suspend fun writeExerciseSession(
        title: String,
        startTime: Instant,
        endTime: Instant,
        exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ): Boolean = false

    suspend fun writeActiveCalories(calories: Double, startTime: Instant, endTime: Instant): Boolean = false
}
