package app.hypertrophy.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import app.hypertrophy.core.data.sync.SessionOutboxBuilder
import app.hypertrophy.core.data.sync.SessionSetInput
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveSessionUiState(
    val exerciseName: String,
    val setNumber: Int,
    val totalSets: Int,
    val reps: Int,
    val weightKg: Double,
    val progress: Float,
    val isComplete: Boolean,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
) : ViewModel() {
    val sessions: StateFlow<List<WorkoutSessionEntity>> = workoutDao
        .observeSessions(LOCAL_USER_ID)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val completedSets = mutableListOf<SessionSetInput>()

    private val _session = MutableStateFlow(
        LiveSessionUiState(
            exerciseName = "Back Squat",
            setNumber = 1,
            totalSets = 4,
            reps = 12,
            weightKg = 60.0,
            progress = 0.25f,
            isComplete = false,
        ),
    )
    val session: StateFlow<LiveSessionUiState> = _session

    fun completeSet() {
        val current = _session.value
        if (current.isComplete) return

        completedSets += SessionSetInput(
            exerciseId = EXERCISE_ID,
            reps = current.reps,
            weightKg = current.weightKg,
        )

        if (current.setNumber < current.totalSets) {
            val next = current.setNumber + 1
            _session.value = current.copy(
                setNumber = next,
                progress = next.toFloat() / current.totalSets,
            )
            return
        }

        _session.value = current.copy(progress = 1f, isComplete = true)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val built = SessionOutboxBuilder.build(
                userId = LOCAL_USER_ID,
                sessionId = UUID.randomUUID().toString(),
                title = current.exerciseName,
                startedAt = now - TimeUnit.MINUTES.toMillis(20),
                endedAt = now,
                sets = completedSets.toList(),
                avgFormScore = null,
                aiRecapText = null,
                createdAt = now,
            )
            // Offline-first: Room is the source of truth and the outbox entry
            // is drained once as a single batched session payload.
            workoutDao.upsertSession(built.session)
            workoutDao.enqueueOutbox(built.outboxEntry)
            completedSets.clear()
        }
    }

    companion object {
        const val LOCAL_USER_ID = "local-user"
        const val EXERCISE_ID = "ex_back_squat"
    }
}
