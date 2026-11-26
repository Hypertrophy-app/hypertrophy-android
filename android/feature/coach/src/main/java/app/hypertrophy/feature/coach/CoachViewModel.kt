package app.hypertrophy.feature.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.local.TrainingDao
import app.hypertrophy.core.data.local.WorkoutDao
import app.hypertrophy.core.data.network.AuthSessionStore
import app.hypertrophy.core.data.plan.PlannedExercise
import app.hypertrophy.core.data.plan.WorkoutPlanCodec
import app.hypertrophy.core.data.sync.SessionOutboxBuilder
import app.hypertrophy.core.data.sync.SessionSetInput
import app.hypertrophy.core.kinematics.AutoSetRestDetector
import app.hypertrophy.core.kinematics.BiomechanicalAnalyzer
import app.hypertrophy.core.kinematics.CameraAim
import app.hypertrophy.core.kinematics.CameraPositionAssistant
import app.hypertrophy.core.kinematics.ExerciseAutoClassifier
import app.hypertrophy.core.kinematics.ExerciseKind
import app.hypertrophy.core.kinematics.ExerciseStateMachine
import app.hypertrophy.core.kinematics.FormCue
import app.hypertrophy.core.kinematics.FormFaultDetector
import app.hypertrophy.core.kinematics.PoseFrame
import app.hypertrophy.core.kinematics.PoseLandmarks
import app.hypertrophy.core.kinematics.RepPhase
import app.hypertrophy.core.kinematics.SquatAnalyzer
import app.hypertrophy.core.kinematics.TempoSnapshot
import app.hypertrophy.core.kinematics.analyzerFor
import app.hypertrophy.feature.coach.music.MusicController
import app.hypertrophy.feature.coach.music.MusicState
import app.hypertrophy.feature.coach.recap.PersonalRecordMath
import app.hypertrophy.feature.coach.recap.SessionHistoryStore
import app.hypertrophy.feature.coach.recap.SessionRecap
import app.hypertrophy.feature.coach.recap.SessionRecapGenerator
import app.hypertrophy.feature.coach.voice.VoiceCoach
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One row in the coach exercise queue. */
data class CoachQueueItem(
    val exerciseId: String,
    val name: String,
    val targetSets: Int,
    val completedSets: Int,
    val isCurrent: Boolean,
)

data class CoachUiState(
    val workoutTitle: String = "Quick Workout",
    val exerciseName: String = SquatAnalyzer.displayName,
    val kind: ExerciseKind = ExerciseKind.SQUAT,
    val phase: RepPhase = RepPhase.TOP_READY,
    val reps: Int = 0,
    /** Completed sets for the current exercise. */
    val sets: Int = 0,
    val targetSets: Int = 3,
    /** Completed sets across every exercise in the session. */
    val totalSets: Int = 0,
    val targetReps: Int = 8,
    val weightKg: Double = 60.0,
    val restSeconds: Int = 90,
    val exerciseIndex: Int = 0,
    val exerciseCount: Int = 1,
    val queue: List<CoachQueueItem> = emptyList(),
    val planLoaded: Boolean = true,
    val planNotice: String? = null,
    val romPercent: Float = 0f,
    val angle: Float? = null,
    val targetAngle: Float = SquatAnalyzer.bottomAngle,
    val tempoLabel: String = "3-1-1",
    val formCue: FormCue? = null,
    val formScore: Int = 100,
    val cameraAim: CameraAim? = null,
    val isResting: Boolean = false,
    val restSecondsRemaining: Int = 0,
    val restProgress: Float = 0f,
    val music: MusicState = MusicState(),
    val autoClassify: Boolean = false,
    val detectedKind: ExerciseKind? = null,
    val recap: SessionRecap? = null,
)

private data class ActiveExercise(
    val exerciseId: String,
    val name: String,
    val kind: ExerciseKind,
    val targetSets: Int,
    val targetReps: Int,
    val weightKg: Double,
    val restSeconds: Int,
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val voiceCoach: VoiceCoach,
    private val musicController: MusicController,
    private val historyStore: SessionHistoryStore,
    private val workoutDao: WorkoutDao,
    private val trainingDao: TrainingDao,
) : ViewModel() {
    private val userId: String = AuthSessionStore.requireUserId
    private val lock = Any()
    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    private var analyzer: BiomechanicalAnalyzer = SquatAnalyzer
    private var stateMachine = ExerciseStateMachine(analyzer)
    private var faultDetector = FormFaultDetector()
    private var restDetector = AutoSetRestDetector()
    private val cameraAssistant = CameraPositionAssistant()
    private val autoClassifier = ExerciseAutoClassifier()

    private var formCue: FormCue? = null
    private var cueClearJob: Job? = null
    private var formScore = 100
    private var cueCount = 0

    private val repTempos = mutableListOf<TempoSnapshot>()
    private val sessionTempos = mutableListOf<TempoSnapshot>()
    private var setRomSum = 0f
    private var setRomCount = 0

    private var workoutTitle = "Quick Workout"
    private var plan: List<ActiveExercise> = listOf(quickExercise(ExerciseKind.SQUAT))
    private var currentIndex = 0
    private var scheduledWorkoutId: String? = null
    private var planLoaded = true
    private var pendingAdvance = false
    private var startHandled = false
    private val setsByExercise = linkedMapOf<String, MutableList<CompletedSet>>()

    private var restJob: Job? = null

    /** Epoch millis of the first camera frame; anchors the session timeline. */
    private var sessionStartedAtMs = 0L

    @Volatile
    private var restEndsAtMs = 0L

    @Volatile
    private var restTotalMs = 0L

    init {
        voiceCoach.initialize()
        applyActiveExercise(0)
        // MediaSessionManager calls are binder round trips; keep them off the
        // main thread while the HUD renders at camera frame rate.
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val music = musicController.state()
                _state.update { if (it.music == music) it else it.copy(music = music) }
                delay(1_500L)
            }
        }
    }

    /**
     * Called once by the route. A blank id keeps the quick workout, `today`
     * resolves the first incomplete scheduled session, and anything else is a
     * scheduled workout id.
     */
    fun start(workoutId: String?) {
        val request = workoutId?.takeIf { it.isNotBlank() }
        synchronized(lock) {
            if (startHandled) return
            startHandled = true
            if (request == null) return
            planLoaded = false
            _state.update { it.copy(planLoaded = false, planNotice = null) }
        }
        viewModelScope.launch { loadWorkout(request!!) }
    }

    /**
     * Frame entry point. MediaPipe calls this from its result thread, so the
     * whole pipeline is serialized on [lock].
     */
    fun onPoseFrame(frame: PoseFrame) {
        synchronized(lock) {
            val current = _state.value
            if (!current.planLoaded || current.recap != null) return

            if (sessionStartedAtMs == 0L) {
                sessionStartedAtMs = System.currentTimeMillis()
            }

            if (frame.landmarks.size < PoseLandmarks.COUNT) {
                maybeAutoSet(frame)
                return
            }

            val aim = cameraAssistant.update(frame)

            if (current.autoClassify && stateMachine.completedReps == 0) {
                autoClassifier.addFrame(frame)
                val classification = autoClassifier.classify()
                if (classification != null &&
                    classification.kind != ExerciseKind.UNKNOWN &&
                    classification.confidence >= 0.6f
                ) {
                    if (classification.kind != analyzer.kind) {
                        applyDetectedKind(classification.kind)
                    } else {
                        _state.update { it.copy(detectedKind = classification.kind) }
                    }
                }
            }

            val update = stateMachine.process(frame)
            val cue = faultDetector.evaluate(
                frame = frame,
                analyzer = analyzer,
                phase = update.phase,
                minAngleThisRep = stateMachine.minAngleThisRep,
            )
            if (cue != null) {
                formCue = cue
                cueCount++
                formScore = (formScore - 5).coerceAtLeast(0)
                voiceCoach.announce(cue.message)
                scheduleCueClear()
            }

            update.completedRep?.let { rep ->
                repTempos += rep.tempo
                sessionTempos += rep.tempo
                setRomSum += rep.romPercent
                setRomCount++
                val milestone = when {
                    rep.index == 1 -> "First rep."
                    rep.index % 5 == 0 -> "${rep.index} reps."
                    else -> null
                }
                milestone?.let(voiceCoach::announce)
            }

            val completed = update.completedRep
            _state.update {
                it.copy(
                    phase = update.phase,
                    reps = stateMachine.completedReps,
                    romPercent = completed?.romPercent ?: stateMachine.currentRomPercent,
                    angle = update.angle,
                    targetAngle = analyzer.bottomAngle,
                    tempoLabel = completed?.tempo?.label() ?: it.tempoLabel,
                    formCue = formCue,
                    formScore = formScore,
                    cameraAim = aim,
                )
            }

            maybeAutoSet(frame)
        }
    }

    fun selectExercise(index: Int) {
        synchronized(lock) {
            if (!planLoaded || index !in plan.indices || index == currentIndex) return
            cancelRest()
            pendingAdvance = false
            applyActiveExercise(index, announce = true)
        }
    }

    fun selectKind(kind: ExerciseKind) {
        synchronized(lock) {
            if (!planLoaded) return
            if (scheduledWorkoutId != null) {
                // Planned exercise keeps its id and name; the chips only
                // override the joint triangle used for rep counting.
                applyAnalyzerKind(kind)
            } else {
                switchQuickKind(kind)
            }
        }
    }

    fun toggleAutoClassify() {
        synchronized(lock) {
            autoClassifier.reset()
            _state.update {
                it.copy(autoClassify = !it.autoClassify, detectedKind = null)
            }
        }
    }

    fun adjustWeight(deltaKg: Double) {
        synchronized(lock) {
            val active = plan.getOrNull(currentIndex) ?: return
            val updated = (_state.value.weightKg + deltaKg).coerceIn(0.0, 500.0)
            plan = plan.toMutableList().also {
                it[currentIndex] = active.copy(weightKg = updated)
            }
            _state.update { it.copy(weightKg = updated) }
        }
    }

    fun adjustTargetReps(delta: Int) {
        synchronized(lock) {
            val active = plan.getOrNull(currentIndex) ?: return
            val updated = (_state.value.targetReps + delta).coerceIn(1, 30)
            plan = plan.toMutableList().also {
                it[currentIndex] = active.copy(targetReps = updated)
            }
            _state.update { it.copy(targetReps = updated) }
        }
    }

    fun addRestTime(seconds: Int = 15) {
        if (!_state.value.isResting) return
        restEndsAtMs += seconds * 1_000L
        restTotalMs += seconds * 1_000L
    }

    fun skipRest() {
        restJob?.cancel()
        restJob = null
        // Clear the completed ring so endRest does not also announce that the
        // timer finished; skipping has its own confirmation.
        _state.update { it.copy(restProgress = 0f) }
        endRest()
        voiceCoach.announce("Rest skipped.")
    }

    fun logSetManually() {
        synchronized(lock) {
            if (!planLoaded || _state.value.isResting || _state.value.recap != null) return
            val reps = stateMachine.completedReps
            if (reps < 1) return
            completeSet(reps)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            if (!_state.value.planLoaded) return@launch
            val snapshot = synchronized(lock) {
                val all = setsByExercise.values.flatten().toMutableList()
                if (stateMachine.completedReps > 0) {
                    val active = plan.getOrNull(currentIndex)
                    if (active != null) {
                        all += CompletedSet(
                            exerciseId = active.exerciseId,
                            reps = stateMachine.completedReps,
                            weightKg = _state.value.weightKg,
                            romPercent = if (setRomCount > 0) {
                                setRomSum / setRomCount
                            } else {
                                stateMachine.currentRomPercent
                            },
                            tempoLabel = repTempos.lastOrNull()?.label() ?: "3-1-1",
                            formScore = _state.value.formScore,
                        )
                    }
                }
                all.toList() to sessionTempos.toList()
            }
            val sets = snapshot.first
            val tempos = snapshot.second
            cancelRest()
            if (sets.isEmpty()) {
                // A quick workout with no logged sets must still be stoppable.
                // Show a recap and skip persistence: the outbox requires at
                // least one set and there are no metrics worth saving.
                val recap = SessionRecapGenerator.empty(workoutTitle)
                _state.update {
                    it.copy(
                        recap = recap,
                        isResting = false,
                        restSecondsRemaining = 0,
                        restProgress = 0f,
                    )
                }
                voiceCoach.announce("Session ended. No sets were logged.")
                return@launch
            }

            val now = System.currentTimeMillis()
            var newPr = false
            SessionSummaryBuilder.byExercise(sets).forEach { summary ->
                val previous = workoutDao.findPersonalRecord(userId, summary.exerciseId)
                val isRecord = PersonalRecordMath.isNewRecord(
                    previous = previous,
                    topWeightKg = summary.topWeightKg,
                    estimated1Rm = summary.estimated1RmKg,
                )
                if (previous == null || isRecord) {
                    workoutDao.upsertPersonalRecord(
                        PersonalRecordMath.merge(
                            previous = previous,
                            userId = userId,
                            exerciseId = summary.exerciseId,
                            topWeightKg = summary.topWeightKg,
                            topReps = summary.topReps,
                            estimated1Rm = summary.estimated1RmKg,
                            achievedAtMillis = now,
                            newId = UUID.randomUUID().toString(),
                        ),
                    )
                }
                if (isRecord) newPr = true
            }

            val metrics = SessionSummaryBuilder.metrics(
                title = workoutTitle,
                sets = sets,
                tempos = tempos,
                formScore = _state.value.formScore,
            )
            val historyKey = historyKey(workoutTitle)
            val previousSession = historyStore.previous(userId, historyKey)
            historyStore.save(userId, historyKey, metrics)
            val recap = SessionRecapGenerator.generate(metrics, previousSession, newPr)

            // Offline-first: Room is the source of truth and the outbox entry
            // lets SyncWorker flush one batched payload per session to D1.
            val built = SessionOutboxBuilder.build(
                userId = userId,
                sessionId = UUID.randomUUID().toString(),
                title = workoutTitle,
                startedAt = sessionStartedAtMs.takeIf { it > 0L } ?: now,
                endedAt = now,
                sets = sets.map {
                    SessionSetInput(
                        exerciseId = it.exerciseId,
                        reps = it.reps,
                        weightKg = it.weightKg,
                        romPercent = it.romPercent,
                        tempoLabel = it.tempoLabel,
                        formScore = it.formScore,
                    )
                },
                avgFormScore = sets.map { it.formScore }.average(),
                aiRecapText = recap.shareText,
                createdAt = now,
            )
            workoutDao.upsertSession(built.session)
            workoutDao.enqueueOutbox(built.outboxEntry)
            scheduledWorkoutId?.let { trainingDao.markScheduledCompleted(it, userId) }

            _state.update {
                it.copy(
                    recap = recap,
                    isResting = false,
                    restSecondsRemaining = 0,
                    restProgress = 1f,
                    totalSets = sets.size,
                    queue = queueItems(),
                )
            }
            voiceCoach.announce("Session complete. Nice work.")
        }
    }

    fun toggleMusic() = musicController.togglePlayPause()

    fun nextTrack() = musicController.next()

    override fun onCleared() {
        super.onCleared()
        voiceCoach.shutdown()
    }

    private suspend fun loadWorkout(request: String) {
        val scheduled = if (request == TODAY_SENTINEL) {
            trainingDao.scheduledForDay(userId, LocalDate.now().toEpochDay())
                .firstOrNull { !it.isCompleted && !it.isRestDay }
        } else {
            trainingDao.findScheduled(request)
        }

        if (scheduled == null || scheduled.userId != userId) {
            synchronized(lock) {
                planLoaded = true
                _state.update {
                    it.copy(
                        planLoaded = true,
                        planNotice = "No scheduled session found. Quick workout loaded.",
                    )
                }
            }
            return
        }
        loadScheduled(scheduled)
    }

    private suspend fun loadScheduled(scheduled: ScheduledWorkoutEntity) {
        val resolved = resolvePlan(WorkoutPlanCodec.decode(scheduled.exercisesJson))
        if (resolved.isEmpty()) {
            synchronized(lock) {
                planLoaded = true
                _state.update {
                    it.copy(
                        planLoaded = true,
                        planNotice = "This session has no exercises. Quick workout loaded.",
                    )
                }
            }
            return
        }

        synchronized(lock) {
            workoutTitle = scheduled.title
            scheduledWorkoutId = scheduled.id
            plan = resolved
            currentIndex = 0
            planLoaded = true
            pendingAdvance = false
            setsByExercise.clear()
            sessionTempos.clear()
            applyActiveExercise(0)
            _state.update {
                it.copy(
                    workoutTitle = workoutTitle,
                    planLoaded = true,
                    planNotice = null,
                )
            }
        }
        voiceCoach.announce("${scheduled.title}. ${resolved.size} exercises.")
    }

    private suspend fun resolvePlan(planned: List<PlannedExercise>): List<ActiveExercise> {
        if (planned.isEmpty()) return emptyList()
        val catalog = ExerciseCatalog.exercises.associateBy { it.id }
        val fromRoom = trainingDao.findExercisesByIds(planned.map { it.exerciseId })
            .associateBy { it.id }
        return planned.mapNotNull { item ->
            val exercise = fromRoom[item.exerciseId]
                ?: catalog[item.exerciseId]
                ?: return@mapNotNull null
            ActiveExercise(
                exerciseId = exercise.id,
                name = exercise.name,
                kind = ExerciseKindMapping.forProfile(
                    exercise.kinematicProfileType,
                    exercise.primaryMuscle,
                ),
                targetSets = item.sets,
                targetReps = item.reps,
                weightKg = item.weightKg,
                restSeconds = item.restSeconds,
            )
        }
    }

    private fun applyActiveExercise(index: Int, announce: Boolean = false) {
        val active = plan.getOrNull(index) ?: return
        currentIndex = index
        analyzer = analyzerFor(active.kind)
        stateMachine = ExerciseStateMachine(analyzer)
        faultDetector = FormFaultDetector()
        restDetector = AutoSetRestDetector()
        cameraAssistant.reset()
        autoClassifier.reset()
        repTempos.clear()
        setRomSum = 0f
        setRomCount = 0
        formCue = null

        val completed = setsByExercise[active.exerciseId]?.size ?: 0
        _state.update {
            it.copy(
                workoutTitle = workoutTitle,
                exerciseName = active.name,
                kind = active.kind,
                reps = 0,
                sets = completed,
                targetSets = active.targetSets,
                totalSets = setsByExercise.values.sumOf { sets -> sets.size },
                targetReps = active.targetReps,
                weightKg = active.weightKg,
                restSeconds = active.restSeconds,
                exerciseIndex = index,
                exerciseCount = plan.size,
                queue = queueItems(),
                planLoaded = planLoaded,
                phase = RepPhase.TOP_READY,
                romPercent = 0f,
                angle = null,
                targetAngle = analyzer.bottomAngle,
                formCue = null,
                detectedKind = null,
            )
        }
        if (announce) {
            voiceCoach.announce("${active.name}. ${active.targetSets} sets of ${active.targetReps}.")
        }
    }

    private fun applyAnalyzerKind(kind: ExerciseKind, detected: Boolean = false) {
        analyzer = analyzerFor(kind)
        stateMachine = ExerciseStateMachine(analyzer)
        faultDetector = FormFaultDetector()
        restDetector = AutoSetRestDetector()
        cameraAssistant.reset()
        autoClassifier.reset()
        repTempos.clear()
        setRomSum = 0f
        setRomCount = 0
        formCue = null
        _state.update {
            it.copy(
                kind = kind,
                reps = 0,
                phase = RepPhase.TOP_READY,
                romPercent = 0f,
                angle = null,
                targetAngle = analyzer.bottomAngle,
                formCue = null,
                detectedKind = if (detected) kind else null,
            )
        }
    }

    private fun applyDetectedKind(kind: ExerciseKind) {
        if (scheduledWorkoutId != null) {
            applyAnalyzerKind(kind, detected = true)
        } else {
            switchQuickKind(kind, detected = true)
        }
    }

    private fun switchQuickKind(kind: ExerciseKind, detected: Boolean = false) {
        plan = listOf(quickExercise(kind))
        currentIndex = 0
        applyActiveExercise(0)
        if (detected) {
            _state.update { it.copy(detectedKind = kind) }
        }
    }

    private fun maybeAutoSet(frame: PoseFrame) {
        val current = _state.value
        if (!current.planLoaded || current.isResting || current.recap != null) return
        val event = restDetector.update(frame, stateMachine.completedReps)
        if (event != null) completeSet(event.reps)
    }

    private fun completeSet(reps: Int) {
        if (reps < 1 || !planLoaded) return
        val active = plan.getOrNull(currentIndex) ?: return
        val rom = if (setRomCount > 0) setRomSum / setRomCount else 0f
        val set = CompletedSet(
            exerciseId = active.exerciseId,
            reps = reps,
            weightKg = _state.value.weightKg,
            romPercent = rom,
            tempoLabel = repTempos.lastOrNull()?.label() ?: "3-1-1",
            formScore = _state.value.formScore,
        )
        val exerciseSets = setsByExercise.getOrPut(active.exerciseId) { mutableListOf() }
        exerciseSets += set
        val completedForExercise = exerciseSets.size
        val totalCompleted = setsByExercise.values.sumOf { it.size }
        val reachedTarget = completedForExercise >= active.targetSets
        val hasNext = currentIndex < plan.lastIndex
        pendingAdvance = reachedTarget && hasNext

        _state.update {
            it.copy(
                sets = completedForExercise,
                totalSets = totalCompleted,
                reps = 0,
                phase = RepPhase.TOP_READY,
                romPercent = 0f,
                formCue = null,
                queue = queueItems(),
            )
        }
        resetTracking()

        val nextName = plan.getOrNull(currentIndex + 1)?.name
        when {
            pendingAdvance && nextName != null ->
                voiceCoach.announce("Set logged. $reps reps. $nextName is next after rest.")
            reachedTarget && !hasNext ->
                voiceCoach.announce("Set logged. $reps reps. Final set complete. End the session when ready.")
            else ->
                voiceCoach.announce("Set logged. $reps reps. Rest ${active.restSeconds} seconds.")
        }
        startRest(active.restSeconds)
    }

    private fun resetTracking() {
        stateMachine = ExerciseStateMachine(analyzer)
        faultDetector.reset()
        restDetector.reset()
        repTempos.clear()
        setRomSum = 0f
        setRomCount = 0
        formCue = null
    }

    private fun startRest(seconds: Int) {
        val duration = seconds.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)
        restTotalMs = duration * 1_000L
        restEndsAtMs = System.currentTimeMillis() + restTotalMs
        restJob?.cancel()
        restJob = viewModelScope.launch {
            while (isActive) {
                val remaining = (restEndsAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                val total = restTotalMs.coerceAtLeast(1L)
                val elapsed = (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                _state.update {
                    it.copy(
                        isResting = true,
                        restSecondsRemaining = ceil(remaining / 1_000.0).toInt(),
                        restProgress = elapsed,
                    )
                }
                if (remaining <= 0L) break
                delay(100L)
            }
            restJob = null
            endRest()
        }
    }

    private fun endRest() {
        val finished = _state.value.restProgress >= 1f
        val advanceTo = if (pendingAdvance) currentIndex + 1 else null
        pendingAdvance = false
        _state.update {
            it.copy(
                isResting = false,
                restSecondsRemaining = 0,
                restProgress = 1f,
                reps = 0,
                phase = RepPhase.TOP_READY,
                romPercent = 0f,
            )
        }
        if (advanceTo != null && advanceTo in plan.indices) {
            applyActiveExercise(advanceTo, announce = true)
        } else if (finished) {
            voiceCoach.announce("Rest complete. Next set.")
        }
    }

    private fun cancelRest() {
        restJob?.cancel()
        restJob = null
        restEndsAtMs = 0L
        restTotalMs = 0L
        _state.update {
            it.copy(isResting = false, restSecondsRemaining = 0, restProgress = 0f)
        }
    }

    private fun scheduleCueClear() {
        cueClearJob?.cancel()
        cueClearJob = viewModelScope.launch {
            delay(2_500L)
            formCue = null
            _state.update { it.copy(formCue = null) }
        }
    }

    private fun queueItems(): List<CoachQueueItem> = plan.mapIndexed { index, active ->
        CoachQueueItem(
            exerciseId = active.exerciseId,
            name = active.name,
            targetSets = active.targetSets,
            completedSets = setsByExercise[active.exerciseId]?.size ?: 0,
            isCurrent = index == currentIndex,
        )
    }

    private fun historyKey(title: String): String =
        "workout:" + title.trim().lowercase().replace(Regex("\\s+"), "-")

    private fun quickExercise(kind: ExerciseKind): ActiveExercise {
        val info = exerciseInfo(kind)
        return ActiveExercise(
            exerciseId = info.second,
            name = info.first,
            kind = kind,
            targetSets = 3,
            targetReps = 8,
            weightKg = 60.0,
            restSeconds = REST_SECONDS,
        )
    }

    private fun exerciseInfo(kind: ExerciseKind): Pair<String, String> = when (kind) {
        ExerciseKind.SQUAT -> "Back Squat" to "ex_back_squat"
        ExerciseKind.HINGE -> "Romanian Deadlift" to "ex_rdl"
        ExerciseKind.PUSH -> "Bench Press" to "ex_bench_press"
        ExerciseKind.PULL -> "Pull-Up" to "ex_pull_up"
        ExerciseKind.CURL_RAISE -> "Bicep Curl" to "ex_bicep_curl"
        ExerciseKind.UNKNOWN -> "Quick Workout" to "ex_back_squat"
    }

    companion object {
        const val REST_SECONDS = 90
        const val TODAY_SENTINEL = "today"
        private const val MIN_REST_SECONDS = 15
        private const val MAX_REST_SECONDS = 600
    }
}
