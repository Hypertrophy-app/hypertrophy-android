package app.hypertrophy.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.assets.ExerciseModelCache
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.local.TrainingDao
import app.hypertrophy.core.data.local.WorkoutTemplateEntity
import app.hypertrophy.core.data.network.AuthSessionStore
import app.hypertrophy.core.data.plan.PlannedExercise
import app.hypertrophy.core.data.plan.WorkoutPlanCodec
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BuilderExercise(
    val exercise: ExerciseEntity,
    val sets: Int = 3,
    val reps: Int = 8,
    val targetWeightKg: Double = 60.0,
) {
    fun toPlanned(): PlannedExercise = PlannedExercise(
        exerciseId = exercise.id,
        sets = sets,
        reps = reps,
        weightKg = targetWeightKg,
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutBuilderViewModel @Inject constructor(
    private val trainingDao: TrainingDao,
    private val modelCache: ExerciseModelCache,
) : ViewModel() {
    private val userId: String = AuthSessionStore.requireUserId

    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<String?>(null)
    private val equipment = MutableStateFlow<String?>(null)

    val exercises: StateFlow<List<ExerciseEntity>> = combine(query, muscle, equipment) { q, m, e ->
        Triple(q.ifBlank { null }, m, e)
    }.flatMapLatest { (q, m, e) ->
        trainingDao.searchExercises(q, m, e)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseCatalog.exercises)

    val templates: StateFlow<List<WorkoutTemplateEntity>> = trainingDao
        .observeTemplates(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val schedule: StateFlow<List<ScheduledWorkoutEntity>> = trainingDao
        .observeSchedule(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _builder = MutableStateFlow<List<BuilderExercise>>(emptyList())
    val builder: StateFlow<List<BuilderExercise>> = _builder

    private val _draftTitle = MutableStateFlow("")
    val draftTitle: StateFlow<String> = _draftTitle

    private val _editingTemplateId = MutableStateFlow<String?>(null)
    val editingTemplateId: StateFlow<String?> = _editingTemplateId

    /** One-shot navigation request emitted after a workout is scheduled today. */
    private val _startWorkoutId = MutableStateFlow<String?>(null)
    val startWorkoutId: StateFlow<String?> = _startWorkoutId

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

    fun setDraftTitle(value: String) {
        _draftTitle.value = value
    }

    fun newDraft() {
        _builder.value = emptyList()
        _draftTitle.value = ""
        _editingTemplateId.value = null
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

    fun selectExercise(exercise: ExerciseEntity) {
        _selectedExercise.value = exercise
        viewModelScope.launch {
            _modelFile.value = modelCache.cachedModel(exercise.id)
                ?: modelCache.resolveModel(exercise.id)
        }
    }

    fun clearSelection() {
        _selectedExercise.value = null
        _modelFile.value = null
    }

    fun loadTemplate(templateId: String) {
        viewModelScope.launch {
            val template = trainingDao.findTemplate(templateId) ?: return@launch
            val plan = WorkoutPlanCodec.decode(template.exercisesJson)
            if (plan.isEmpty()) return@launch

            val catalog = ExerciseCatalog.exercises.associateBy { it.id }
            val fromRoom = trainingDao.findExercisesByIds(plan.map { it.exerciseId })
                .associateBy { it.id }
            _builder.value = plan.mapNotNull { planned ->
                val exercise = fromRoom[planned.exerciseId]
                    ?: catalog[planned.exerciseId]
                    ?: return@mapNotNull null
                BuilderExercise(
                    exercise = exercise,
                    sets = planned.sets,
                    reps = planned.reps,
                    targetWeightKg = planned.weightKg,
                )
            }
            _draftTitle.value = template.title
            _editingTemplateId.value = template.id
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            trainingDao.deleteTemplate(templateId, userId)
            if (_editingTemplateId.value == templateId) {
                _editingTemplateId.value = null
            }
        }
    }

    fun saveTemplate() {
        val items = _builder.value
        if (items.isEmpty()) return
        val title = titleOrDefault()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = _editingTemplateId.value?.let { trainingDao.findTemplate(it) }
            val id = existing?.id ?: UUID.randomUUID().toString()
            trainingDao.upsertTemplate(
                WorkoutTemplateEntity(
                    id = id,
                    userId = userId,
                    title = title,
                    exercisesJson = WorkoutPlanCodec.encode(items.map { it.toPlanned() }),
                    isPublic = false,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            _editingTemplateId.value = id
        }
    }

    /**
     * Writes the current draft onto a calendar day. With an empty draft a day
     * becomes a rest day (or toggles an existing rest day off). Completed days
     * are never overwritten from the builder.
     */
    fun scheduleDay(dayEpoch: Long) {
        viewModelScope.launch {
            val existing = trainingDao.scheduledForDay(userId, dayEpoch).firstOrNull()
            if (existing?.isCompleted == true) return@launch

            val items = _builder.value
            if (items.isEmpty()) {
                when {
                    existing == null -> trainingDao.upsertScheduledOne(
                        restDay(dayEpoch),
                    )
                    existing.isRestDay -> trainingDao.deleteScheduled(existing.id)
                    else -> Unit
                }
                return@launch
            }

            trainingDao.upsertScheduledOne(
                scheduledEntity(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = titleOrDefault(),
                    dayEpoch = dayEpoch,
                    exercises = items.map { it.toPlanned() },
                    templateId = _editingTemplateId.value,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Schedules the draft for today and asks the route to open the coach. */
    fun startBuilderToday() {
        val items = _builder.value
        if (items.isEmpty()) return
        viewModelScope.launch {
            val entity = scheduledEntity(
                id = UUID.randomUUID().toString(),
                title = titleOrDefault(),
                dayEpoch = LocalDate.now().toEpochDay(),
                exercises = items.map { it.toPlanned() },
                templateId = _editingTemplateId.value,
                createdAt = System.currentTimeMillis(),
            )
            trainingDao.upsertScheduledOne(entity)
            _startWorkoutId.value = entity.id
        }
    }

    /** Schedules a saved template for today and asks the route to open it. */
    fun startTemplateToday(templateId: String) {
        viewModelScope.launch {
            val template = trainingDao.findTemplate(templateId) ?: return@launch
            val exercises = WorkoutPlanCodec.decode(template.exercisesJson)
            if (exercises.isEmpty()) return@launch
            val entity = scheduledEntity(
                id = UUID.randomUUID().toString(),
                title = template.title,
                dayEpoch = LocalDate.now().toEpochDay(),
                exercises = exercises,
                templateId = template.id,
                createdAt = System.currentTimeMillis(),
            )
            trainingDao.upsertScheduledOne(entity)
            _startWorkoutId.value = entity.id
        }
    }

    fun consumeStartWorkout() {
        _startWorkoutId.value = null
    }

    fun applyShiftWeekPlusOneDay() {
        val current = _banner.value ?: return
        val today = LocalDate.now().toEpochDay()
        val result = SmartRescheduleEngine.shiftWeekPlusOneDay(current.plans, today)
        viewModelScope.launch {
            persistReschedule(result, deleteOverdue = false)
            _banner.value = null
            detectOverdue()
        }
    }

    fun applyMergeKeyCompoundToday() {
        val current = _banner.value ?: return
        val today = LocalDate.now().toEpochDay()
        val result = SmartRescheduleEngine.mergeKeyCompoundLiftsToday(current.plans, today)
        viewModelScope.launch {
            persistReschedule(result, deleteOverdue = true)
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
        val plans = trainingDao.observeSchedule(userId).first().map { it.toPlan() }
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

    /**
     * Applies a reschedule result without losing plan payloads. The engine owns
     * dates and completion flags; the existing entity owns ids, titles, and
     * exercise JSON. Merged key-compound lifts are rebuilt from the payloads
     * that were already on disk.
     */
    private suspend fun persistReschedule(result: RescheduleResult, deleteOverdue: Boolean) {
        val existing = trainingDao.observeSchedule(userId).first().associateBy { it.id }
        val exerciseLookup = existing.values
            .flatMap { WorkoutPlanCodec.decode(it.exercisesJson) }
            .associateBy { it.exerciseId }

        val updated = result.plans.mapNotNull { plan ->
            val base = existing[plan.id] ?: return@mapNotNull null
            val exercisesJson = if (plan.exerciseIds.isEmpty()) {
                base.exercisesJson
            } else {
                mergePlanJson(base.exercisesJson, plan.exerciseIds, exerciseLookup)
            }
            base.copy(
                title = plan.title,
                scheduledDay = plan.dayEpoch,
                isCompleted = plan.isCompleted,
                isRestDay = plan.isRestDay,
                isKeyCompound = plan.isKeyCompound,
                exercisesJson = exercisesJson,
            )
        }
        trainingDao.upsertScheduledAll(updated)
        if (deleteOverdue) {
            result.overdueIds.forEach { trainingDao.deleteScheduled(it) }
        }
    }

    private fun mergePlanJson(
        baseJson: String?,
        orderedIds: List<String>,
        lookup: Map<String, PlannedExercise>,
    ): String {
        val merged = LinkedHashMap<String, PlannedExercise>()
        WorkoutPlanCodec.decode(baseJson).forEach { merged[it.exerciseId] = it }
        orderedIds.forEach { id ->
            if (id !in merged) lookup[id]?.let { merged[id] = it }
        }
        val ordered = orderedIds.mapNotNull { merged[it] } +
            merged.values.filter { it.exerciseId !in orderedIds }
        return WorkoutPlanCodec.encode(ordered)
    }

    private fun titleOrDefault(): String =
        _draftTitle.value.trim().ifBlank { DEFAULT_TITLE }

    private fun restDay(dayEpoch: Long): ScheduledWorkoutEntity = ScheduledWorkoutEntity(
        id = UUID.randomUUID().toString(),
        userId = userId,
        templateId = null,
        title = "Rest Day",
        scheduledDay = dayEpoch,
        isRestDay = true,
        isCompleted = false,
        isKeyCompound = false,
        exercisesJson = null,
        createdAt = System.currentTimeMillis(),
    )

    private fun scheduledEntity(
        id: String,
        title: String,
        dayEpoch: Long,
        exercises: List<PlannedExercise>,
        templateId: String?,
        createdAt: Long,
    ): ScheduledWorkoutEntity = ScheduledWorkoutEntity(
        id = id,
        userId = userId,
        templateId = templateId,
        title = title,
        scheduledDay = dayEpoch,
        isRestDay = false,
        isCompleted = false,
        isKeyCompound = exercises.any { isCompound(it.exerciseId) },
        exercisesJson = WorkoutPlanCodec.encode(exercises),
        createdAt = createdAt,
    )

    private fun isCompound(exerciseId: String): Boolean = when (exerciseId) {
        "ex_back_squat",
        "ex_front_squat",
        "ex_deadlift",
        "ex_rdl",
        "ex_bench_press",
        "ex_incline_bench",
        "ex_overhead_press",
        "ex_barbell_row",
        -> true
        else -> false
    }

    private fun ScheduledWorkoutEntity.toPlan() = PlannedWorkout(
        id = id,
        dayEpoch = scheduledDay,
        title = title,
        isCompleted = isCompleted,
        isRestDay = isRestDay,
        isKeyCompound = isKeyCompound,
        exerciseIds = WorkoutPlanCodec.decode(exercisesJson).map { it.exerciseId },
    )

    private companion object {
        const val DEFAULT_TITLE = "Custom Session"
    }
}
