package app.hypertrophy.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    workoutDao: WorkoutDao,
) : ViewModel() {
    val sessions: StateFlow<List<WorkoutSessionEntity>> = workoutDao
        .observeSessions(LOCAL_USER_ID)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    companion object {
        const val LOCAL_USER_ID = "local-user"
    }
}
