package app.hypertrophy.feature.profile

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Thin wrapper around the Health Connect client. Writes use session-derived
 * client record ids, which makes exports idempotent: syncing twice updates the
 * same records instead of duplicating activities in the user's health store.
 */
class HealthConnectManager(private val context: Context) {
    val availability: Int get() = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean
        get() = availability == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? by lazy {
        if (isAvailable) HealthConnectClient.getOrCreate(context) else null
    }

    private val writePermissions: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
    )

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

    suspend fun hasWritePermissions(): Boolean {
        val c = client ?: return false
        val granted = runCatching { c.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
        return granted.containsAll(writePermissions)
    }

    /**
     * Writes one exercise session and its estimated active-calorie record per
     * local workout. Returns the number of workouts exported, or zero when the
     * client or permissions are unavailable.
     */
    suspend fun writeSessions(sessions: List<WorkoutSessionEntity>): Int {
        val c = client ?: return 0
        if (!hasWritePermissions()) return 0

        val drafts = HealthConnectExport.drafts(sessions)
        if (drafts.isEmpty()) return 0

        var written = 0
        for (chunk in drafts.chunked(MAX_SESSIONS_PER_BATCH)) {
            val records = mutableListOf<Record>()
            for (draft in chunk) {
                val start = Instant.ofEpochMilli(draft.startMillis)
                val end = Instant.ofEpochMilli(draft.endMillis)
                val startOffset = offsetAt(start)
                val endOffset = offsetAt(end)
                records += ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    metadata = Metadata.manualEntry(draft.exerciseClientRecordId, 1L),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                    title = draft.title,
                )
                records += ActiveCaloriesBurnedRecord(
                    startTime = start,
                    startZoneOffset = startOffset,
                    endTime = end,
                    endZoneOffset = endOffset,
                    energy = Energy.kilocalories(draft.activeCalories),
                    metadata = Metadata.manualEntry(draft.caloriesClientRecordId, 1L),
                )
            }
            c.insertRecords(records)
            written += chunk.size
        }
        return written
    }

    suspend fun writeExerciseSession(
        title: String,
        startTime: Instant,
        endTime: Instant,
        exerciseType: Int = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
    ): Boolean {
        val c = client ?: return false
        if (!hasWritePermissions()) return false
        return runCatching {
            c.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = startTime,
                        startZoneOffset = offsetAt(startTime),
                        endTime = endTime,
                        endZoneOffset = offsetAt(endTime),
                        metadata = Metadata.manualEntry(),
                        exerciseType = exerciseType,
                        title = title.take(HealthConnectExport.MAX_TITLE_LENGTH),
                    ),
                ),
            )
            true
        }.getOrDefault(false)
    }

    suspend fun writeActiveCalories(calories: Double, startTime: Instant, endTime: Instant): Boolean {
        val c = client ?: return false
        if (!hasWritePermissions()) return false
        return runCatching {
            c.insertRecords(
                listOf(
                    ActiveCaloriesBurnedRecord(
                        startTime = startTime,
                        startZoneOffset = offsetAt(startTime),
                        endTime = endTime,
                        endZoneOffset = offsetAt(endTime),
                        energy = Energy.kilocalories(calories.coerceAtLeast(0.0)),
                        metadata = Metadata.manualEntry(),
                    ),
                ),
            )
            true
        }.getOrDefault(false)
    }

    private fun offsetAt(instant: Instant): ZoneOffset =
        ZoneId.systemDefault().rules.getOffset(instant)

    companion object {
        /** Two records per session; Health Connect caps a request at 1,000. */
        const val MAX_SESSIONS_PER_BATCH = 250
    }
}
