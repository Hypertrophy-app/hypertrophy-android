package app.hypertrophy.feature.today

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ChallengeEntity
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.data.local.PersonalRecordWithExercise
import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.local.TrainingDao
import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.local.WorkoutSessionEntity
import app.hypertrophy.core.data.squad.SquadLiveBus
import app.hypertrophy.core.data.squad.TeammateSetProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val todayEpochDay: Long = LocalDate.now().toEpochDay(),
    val scheduled: List<ScheduledWorkoutEntity> = emptyList(),
    val week: WeekStats = WeekStats(sessions = 0, volumeKg = 0.0),
    val streakDays: Int = 0,
    val sessionsLast30Days: Int = 0,
    val sessionDays: Set<Long> = emptySet(),
    val personalRecords: List<PersonalRecordWithExercise> = emptyList(),
    val challenges: List<ChallengeEntity> = emptyList(),
    val muscleTraining: List<MuscleTraining> = emptyList(),
    val squadProgress: Map<String, TeammateSetProgress> = emptyMap(),
) {
    val activeChallenge: ChallengeEntity? get() = challenges.firstOrNull()
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val workoutDao: WorkoutDao,
    private val trainingDao: TrainingDao,
) : ViewModel() {
    private val store = DashboardStore(dataStore)

    val layout: StateFlow<DashboardLayout> = store.layout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardLayout())

    private val todayEpochDay: Long = LocalDate.now().toEpochDay()
    private val nowMillis: Long = System.currentTimeMillis()
    private val historyWindowStart: Long = nowMillis - TimeUnit.DAYS.toMillis(90)

    private val exerciseMap = trainingDao
        .searchExercises(query = null, muscle = null, equipment = null)
        .map { exercises -> exercises.associateBy(ExerciseEntity::id) }

    val state: StateFlow<TodayUiState> = combine(
        combine(
            trainingDao.observeScheduledForDay(USER_ID, todayEpochDay),
            trainingDao.observeActiveChallenges(USER_ID, nowMillis),
            workoutDao.observeSessionsSince(USER_ID, historyWindowStart),
            workoutDao.observePersonalRecordBoard(USER_ID, limit = 4),
            workoutDao.observeSessionTimestamps(USER_ID),
        ) { scheduled, challenges, sessions, records, timestamps ->
            buildBaseState(scheduled, challenges, sessions, records, timestamps)
        },
        exerciseMap,
        SquadLiveBus.teammateProgress,
    ) { base, exercises, progress ->
        base.state.copy(
            muscleTraining = TodayInsights.lastTrainedByMuscle(base.sessionsForMuscles, exercises),
            squadProgress = progress,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayUiState(todayEpochDay = todayEpochDay),
    )

    init {
        viewModelScope.launch {
            if (trainingDao.countExercises() == 0) {
                trainingDao.upsertExercises(ExerciseCatalog.exercises)
            }
            // A fresh install has no planned days. The schedule stays empty
            // until the user plans a workout; Today renders its empty state.
        }
    }

    fun moveUp(card: TodayCard) {
        viewModelScope.launch { store.setLayout(layout.value.moveUp(card)) }
    }

    fun moveDown(card: TodayCard) {
        viewModelScope.launch { store.setLayout(layout.value.moveDown(card)) }
    }

    fun toggle(card: TodayCard) {
        viewModelScope.launch { store.setLayout(layout.value.toggle(card)) }
    }

    private data class BaseState(
        val state: TodayUiState,
        val sessionsForMuscles: List<WorkoutSessionEntity>,
    )

    private fun buildBaseState(
        scheduled: List<ScheduledWorkoutEntity>,
        challenges: List<ChallengeEntity>,
        sessions: List<WorkoutSessionEntity>,
        records: List<PersonalRecordWithExercise>,
        allSessionTimestamps: List<Long>,
    ): BaseState {
        val sessionDays = allSessionTimestamps
            .map { TodayInsights.epochDayOf(it) }
            .toSet()
        val base = TodayUiState(
            todayEpochDay = todayEpochDay,
            scheduled = scheduled,
            week = TodayInsights.weekStats(sessions, todayEpochDay),
            streakDays = TodayInsights.streakDays(sessionDays, todayEpochDay),
            sessionsLast30Days = TodayInsights.sessionsInWindow(sessions, days = 30, todayEpochDay),
            sessionDays = sessionDays,
            personalRecords = records,
            challenges = challenges,
        )
        return BaseState(state = base, sessionsForMuscles = sessions)
    }

    companion object {
        const val USER_ID = "local-user"
    }
}
