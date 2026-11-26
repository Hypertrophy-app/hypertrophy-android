package app.hypertrophy.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.data.local.PersonalRecordWithExercise
import app.hypertrophy.core.data.local.TrainingDao
import app.hypertrophy.core.data.local.UserBadgeEntity
import app.hypertrophy.core.data.local.UserDao
import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import app.hypertrophy.core.data.network.AuthSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "Athlete",
    val username: String = "",
    val totals: ProfileTotals = ProfileTotals(0, 0.0, 0, 0),
    val personalRecords: List<PersonalRecordWithExercise> = emptyList(),
    val badges: List<BadgeState> = emptyList(),
    val recovery: List<MuscleRecovery> = MuscleGroup.entries.map { MuscleRecovery(it, null) },
    val sessionDays: List<Long> = emptyList(),
    val exerciseOptions: List<ExerciseOption> = emptyList(),
    val selectedExerciseId: String? = null,
    val progression: List<ProgressionPoint> = emptyList(),
    val healthConnectAvailable: Boolean = false,
    val healthConnectGranted: Boolean = false,
    val healthBusy: Boolean = false,
    val healthMessage: String? = null,
)

private data class ProfileCore(
    val displayName: String,
    val username: String,
    val sessions: List<WorkoutSessionEntity>,
    val prs: List<PersonalRecordWithExercise>,
    val exerciseById: Map<String, ExerciseEntity>,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val trainingDao: TrainingDao,
    private val userDao: UserDao,
    val healthConnect: HealthConnectManager,
) : ViewModel() {
    private val userId: String = AuthSessionStore.requireUserId
    private val selectedExerciseId = MutableStateFlow<String?>(null)
    private val healthGranted = MutableStateFlow(false)
    private val healthBusy = MutableStateFlow(false)
    private val healthMessage = MutableStateFlow<String?>(null)

    private val exerciseMap = trainingDao
        .searchExercises(query = null, muscle = null, equipment = null)
        .map { exercises -> exercises.associateBy(ExerciseEntity::id) }

    private val core = combine(
        userDao.observeUser(userId),
        workoutDao.observeSessions(userId),
        workoutDao.observePersonalRecordBoard(userId, limit = 50),
        exerciseMap,
    ) { user, sessions, prs, exercises ->
        ProfileCore(
            displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: "Athlete",
            username = user?.username.orEmpty(),
            sessions = sessions,
            prs = prs,
            exerciseById = exercises,
        )
    }

    val state: StateFlow<ProfileUiState> = combine(
        core,
        trainingDao.observeBadges(userId),
        selectedExerciseId,
        combine(healthGranted, healthBusy, healthMessage) { granted, busy, message ->
            Triple(granted, busy, message)
        },
    ) { core, persistedBadges, selected, health ->
        val today = LocalDate.now().toEpochDay()
        val totals = ProfileInsights.totals(core.sessions, today)
        val options = ProfileInsights.exerciseOptions(core.sessions, core.exerciseById)
        val effectiveExercise = selected
            ?.takeIf { candidate -> options.any { it.exerciseId == candidate } }
            ?: ProfileInsights.defaultExerciseId(options, core.prs)
        val persisted = persistedBadges.associate { it.badgeCode to it.unlockedAt }
        ProfileUiState(
            displayName = core.displayName,
            username = core.username,
            totals = totals,
            personalRecords = core.prs,
            badges = ProfileInsights.badges(totals, core.prs, persisted),
            recovery = ProfileInsights.muscleRecovery(core.sessions, core.exerciseById),
            sessionDays = ProfileInsights.sessionEpochDays(core.sessions),
            exerciseOptions = options,
            selectedExerciseId = effectiveExercise,
            progression = effectiveExercise
                ?.let { ProfileInsights.progressionForExercise(core.sessions, it) }
                ?: emptyList(),
            healthConnectAvailable = healthConnect.isAvailable,
            healthConnectGranted = health.first,
            healthBusy = health.second,
            healthMessage = health.third,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ProfileUiState(healthConnectAvailable = healthConnect.isAvailable),
    )

    init {
        viewModelScope.launch {
            if (trainingDao.countExercises() == 0) {
                trainingDao.upsertExercises(ExerciseCatalog.exercises)
            }
        }
        // Persist newly earned badges once so the wall keeps the unlock date
        // even after a gap in training.
        viewModelScope.launch {
            combine(core, trainingDao.observeBadges(userId)) { core, persisted -> core to persisted }
                .collect { (core, persisted) ->
                    val totals = ProfileInsights.totals(core.sessions, LocalDate.now().toEpochDay())
                    val unlocked = persisted.associate { it.badgeCode to it.unlockedAt }
                    val badges = ProfileInsights.badges(totals, core.prs, unlocked)
                    val now = System.currentTimeMillis()
                    for (definition in ProfileInsights.newlyEarned(badges)) {
                        trainingDao.upsertBadge(
                            UserBadgeEntity(
                                userId = userId,
                                badgeCode = definition.code,
                                unlockedAt = now,
                            ),
                        )
                    }
                }
        }
        viewModelScope.launch {
            healthGranted.value = healthConnect.hasWritePermissions()
        }
    }

    fun selectExercise(exerciseId: String) {
        selectedExerciseId.value = exerciseId
    }

    fun onHealthPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            healthGranted.value = granted && healthConnect.hasWritePermissions()
            if (!healthGranted.value) {
                healthMessage.value = "Health Connect write permission is required"
            }
        }
    }

    fun exportToHealthConnect() {
        if (healthBusy.value) return
        viewModelScope.launch {
            healthBusy.value = true
            healthMessage.value = null
            try {
                if (!healthConnect.isAvailable) {
                    healthMessage.value = "Health Connect is not available on this device"
                    return@launch
                }
                if (!healthConnect.hasWritePermissions()) {
                    healthGranted.value = false
                    healthMessage.value = "Grant Health Connect write permission first"
                    return@launch
                }
                val sessions = workoutDao.recentSessions(userId, limit = 500)
                if (sessions.isEmpty()) {
                    healthMessage.value = "No sessions to export yet"
                    return@launch
                }
                val written = healthConnect.writeSessions(sessions)
                healthMessage.value = when {
                    written == 0 -> "Health Connect rejected the export"
                    written == 1 -> "Exported 1 session to Health Connect"
                    else -> "Exported $written sessions to Health Connect"
                }
            } catch (error: Throwable) {
                healthMessage.value = error.message ?: "Health Connect export failed"
            } finally {
                healthBusy.value = false
            }
        }
    }
}
