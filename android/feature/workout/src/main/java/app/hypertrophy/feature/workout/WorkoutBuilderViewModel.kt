package app.hypertrophy.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.assets.ExerciseModelCache
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.local.TrainingDao
import app.hypertrophy.core.data.local.WorkoutTemplateEntity
import app.hypertrophy.feature.workout.engine.PlannedWorkout
import app.hypertrophy.feature.workout.engine.RescheduleResult
import app.hypertrophy.feature.workout.engine.SmartRescheduleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BuilderExercise(
    val exercise: ExerciseEntity,
    val sets: Int = 3,
    val reps: Int = 8,
    val targetWeightKg: Double = 60.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    private val trainingDao: TrainingDao,
    private val modelCache: ExerciseModelCache,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<String?>(null)
    private val equipment = MutableStateFlow<String?>(null)

    val exercises: StateFlow<List<ExerciseEntity>> = combine(query, muscle, equipment) { q, m, e ->
        Triple(q.ifBlank { null }, m, e)
    }.flatMapLatest { (q, m, e) ->
        trainingDao.searchExercises(q, m, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseCatalog.exercises)

    val templates: StateFlow<List<WorkoutTemplateEntity>> = trainingDao
        .observeTemplates(LOCAL_USER_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val schedule: StateFlow<List<ScheduledWorkoutEntity>> = trainingDao
        .observeSchedule(LOCAL_USER_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _builder = MutableStateFlow<List<BuilderExercise>>(emptyList())
    val builder: StateFlow<List<BuilderExercise>> = _builder

    private val _banner = MutableStateFlow<RescheduleResult?>(null)
    val banner: StateFlow<RescheduleResult?> = _banner

    private val _modelFile = MutableStateFlow<File?>(null)
    val modelFile: StateFlow<File?> = _modelFile

    private val _selectedExercise = MutableStateFlow<ExerciseEntity?>(null)
    val selectedExercise: StateFlow<ExerciseEntity?> = _selectedExercise

    init {
        viewModelScope.launch {
            if (trainingDao.countExercises() == 0) {
                trainingDao.upsertExercises(ExerciseCatalog.exercises)
            }
            // No fabricated plans: a fresh install starts with an empty week
            // until the user schedules a session themselves.
            detectOverdue()
        }
    }

    fun setQuery(value: String) { query.value = value }
    fun setMuscle(value: String?) { muscle.value = value }
    fun setEquipment(value: String?) { equipment.value = value }

    fun addExercise(exercise: ExerciseEntity) {
        if (_builder.value.none { it.exercise.id == exercise.id }) {
            _builder.value = _builder.value + BuilderExercise(exercise)
        }
    }

    fun removeExercise(id: String) {
        _builder.value = _builder.value.filterNot { it.exercise.id == id }
    }

    fun updateSets(id: String, sets: Int) {
        _builder.value = _builder.value.map {
            if (it.exercise.id == id) it.copy(sets = sets.coerceIn(1, 10)) else it
        }
    }

    fun updateReps(id: String, reps: Int) {
        _builder.value = _builder.value.map {
            if (it.exercise.id == id) it.copy(reps = reps.coerceIn(1, 50)) else it
        }
    }

    fun updateWeight(id: String, weightKg: Double) {
        _builder.value = _builder.value.map {
            if (it.exercise.id == id) it.copy(targetWeightKg = weightKg.coerceAtLeast(0.0)) else it
        }
    }

    /**
     * Swaps the exercise for the best same-muscle, same-kinematic alternative
     * available with current equipment. Completed set history stays attached to
     * the builder entry, so only the upcoming exercise reference changes.
     */
    fun substituteExercise(target: ExerciseEntity) {
        viewModelScope.launch {
            val substitute = trainingDao.findSubstitutes(
                primaryMuscle = target.primaryMuscle,
                kinematicProfile = target.kinematicProfileType,
                excludeId = target.id,
                preferredEquipment = target.equipment,
                limit = 1,
            ).firstOrNull() ?: return@launch

            _builder.value = _builder.value.map {
                if (it.exercise.id == target.id) it.copy(exercise = substitute) else it
            }
        }
    }

    fun selectExercise(exercise: ExerciseEntity) {        _selectedExercise.value = exercise
        viewModelScope.launch {
            _modelFile.value = modelCache.cachedModel(exercise.id)
                ?: modelCache.resolveModel(exercise.id)
        }
    }

    fun clearSelection() {
        _selectedExercise.value = null
        _modelFile.value = null
    }

    fun saveTemplate(title: String) {
        val items = _builder.value
        if (title.isBlank() || items.isEmpty()) return
        val now = System.currentTimeMillis()
        val exercisesJson = items.joinToString(prefix = "[", postfix = "]") { item ->
            """{"exerciseId":"${item.exercise.id}","sets":${item.sets},"reps":${item.reps},"weightKg":${item.targetWeightKg}}"""
        }
        viewModelScope.launch {
            trainingDao.upsertTemplate(
                WorkoutTemplateEntity(
                    id = UUID.randomUUID().toString(),
                    userId = LOCAL_USER_ID,
                    title = title,
                    exercisesJson = exercisesJson,
                    isPublic = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _builder.value = emptyList()
        }
    }

    fun assignDay(dayEpoch: Long, isRestDay: Boolean = false) {
        val title = if (isRestDay) "Rest Day" else "Custom Session"
        viewModelScope.launch {
            trainingDao.upsertScheduledOne(
                ScheduledWorkoutEntity(
                    id = UUID.randomUUID().toString(),
                    userId = LOCAL_USER_ID,
                    templateId = null,
                    title = title,
                    scheduledDay = dayEpoch,
                    isRestDay = isRestDay,
                    isCompleted = false,
                    isKeyCompound = !isRestDay,
                    exercisesJson = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun applyShiftWeekPlusOneDay() {
        val current = _banner.value ?: return
        val today = LocalDate.now().toEpochDay()
        val result = SmartRescheduleEngine.shiftWeekPlusOneDay(current.plans, today)
        viewModelScope.launch {
            trainingDao.upsertScheduledAll(result.plans.map { it.toEntity() })
            _banner.value = null
            detectOverdue()
        }
    }

    fun applyMergeKeyCompoundToday() {
        val current = _banner.value ?: return
        val today = LocalDate.now().toEpochDay()
        val result = SmartRescheduleEngine.mergeKeyCompoundLiftsToday(current.plans, today)
        viewModelScope.launch {
            trainingDao.upsertScheduledAll(result.plans.map { it.toEntity() })
            result.overdueIds.forEach { trainingDao.deleteScheduled(it) }
            _banner.value = null
            detectOverdue()
        }
    }

    fun dismissBanner() { _banner.value = null }

    fun refreshBanner() {
        viewModelScope.launch { detectOverdue() }
    }

    private suspend fun detectOverdue() {
        val today = LocalDate.now().toEpochDay()
        val plans = schedule.value.map { it.toPlan() }
        val late = SmartRescheduleEngine.overdue(plans, today)
        _banner.value = if (late.isEmpty()) {
            null
        } else {
            RescheduleResult(
                action = app.hypertrophy.feature.workout.engine.RescheduleAction.NONE,
                plans = plans,
                overdueIds = late.map { it.id },
                mergedExerciseIds = emptyList(),
            )
        }
    }

    private fun ScheduledWorkoutEntity.toPlan() = PlannedWorkout(
        id = id,
        dayEpoch = scheduledDay,
        title = title,
        isCompleted = isCompleted,
        isRestDay = isRestDay,
        isKeyCompound = isKeyCompound,
        exerciseIds = emptyList(),
    )

    private fun PlannedWorkout.toEntity() = ScheduledWorkoutEntity(
        id = id,
        userId = LOCAL_USER_ID,
        templateId = null,
        title = title,
        scheduledDay = dayEpoch,
        isRestDay = isRestDay,
        isCompleted = isCompleted,
        isKeyCompound = isKeyCompound,
        exercisesJson = null,
        createdAt = System.currentTimeMillis(),
    )

    companion object {
        const val LOCAL_USER_ID = "local-user"
    }
}
