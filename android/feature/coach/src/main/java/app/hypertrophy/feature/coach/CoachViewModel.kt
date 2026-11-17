package app.hypertrophy.feature.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.local.PersonalRecordEntity
import app.hypertrophy.core.data.local.WorkoutDao
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
import app.hypertrophy.feature.coach.recap.SessionHistoryStore
import app.hypertrophy.feature.coach.recap.SessionMetrics
import app.hypertrophy.feature.coach.recap.SessionRecap
import app.hypertrophy.feature.coach.recap.SessionRecapGenerator
import app.hypertrophy.feature.coach.voice.VoiceCoach
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class CoachUiState(
    val exerciseName: String = SquatAnalyzer.displayName,
    val kind: ExerciseKind = ExerciseKind.SQUAT,
    val phase: RepPhase = RepPhase.TOP_READY,
    val reps: Int = 0,
    val sets: Int = 0,
    val targetReps: Int = 8,
    val weightKg: Double = 60.0,
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

private data class RecordedSet(
    val reps: Int,
    val weightKg: Double,
    val romPercent: Float,
    val tempoLabel: String,
    val formScore: Int,
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val voiceCoach: VoiceCoach,
    private val musicController: MusicController,
    private val historyStore: SessionHistoryStore,
    private val workoutDao: WorkoutDao,
) : ViewModel() {
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
    private var setRomSum = 0f
    private var setRomCount = 0
    private val completedSets = mutableListOf<RecordedSet>()
    private var currentExerciseId = "ex_back_squat"

    private var restJob: Job? = null

    @Volatile
    private var restEndsAtMs = 0L

    @Volatile
    private var restTotalMs = 0L

    init {
        voiceCoach.initialize()
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
     * Frame entry point. MediaPipe calls this from its result thread, so the
     * whole pipeline is serialized on [lock].
     */
    fun onPoseFrame(frame: PoseFrame) {
        synchronized(lock) {
            val current = _state.value
            if (current.recap != null) return

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
                        applyKind(classification.kind, detected = true)
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

    fun selectKind(kind: ExerciseKind) {
        synchronized(lock) { applyKind(kind) }
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
        _state.update {
            it.copy(weightKg = (it.weightKg + deltaKg).coerceIn(0.0, 500.0))
        }
    }

    fun adjustTargetReps(delta: Int) {
        _state.update {
            it.copy(targetReps = (it.targetReps + delta).coerceIn(1, 30))
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
            if (_state.value.isResting || _state.value.recap != null) return
            val reps = stateMachine.completedReps
            if (reps < 1) return
            completeSet(reps)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            val sets = synchronized(lock) {
                val all = completedSets.toMutableList()
                if (stateMachine.completedReps > 0) {
                    all += RecordedSet(
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
                all.toList()
            }
            if (sets.isEmpty()) return@launch

            val reps = sets.sumOf { it.reps }
            val avgRom = sets.map { it.romPercent }.average().toFloat()
            val topWeight = sets.maxOf { it.weightKg }
            val bestSet = sets.maxByOrNull { it.weightKg * (1.0 + it.reps / 30.0) }!!
            val estimated1Rm = bestSet.weightKg * (1.0 + bestSet.reps / 30.0)
            val metrics = SessionMetrics(
                exerciseName = _state.value.exerciseName,
                sets = sets.size,
                reps = reps,
                avgRomPercent = avgRom,
                tempoConsistencyPercent = SessionRecapGenerator.tempoConsistency(
                    synchronized(lock) { repTempos.toList() },
                ),
                formScore = _state.value.formScore,
                topWeightKg = topWeight,
                estimated1RmKg = estimated1Rm,
            )

            val previousPr = workoutDao.findPersonalRecord(LOCAL_USER_ID, currentExerciseId)
            val newPr = previousPr != null &&
                (topWeight > previousPr.maxWeightKg ||
                    estimated1Rm > previousPr.estimated1rm)

            val now = System.currentTimeMillis()
            if (previousPr == null || newPr) {
                workoutDao.upsertPersonalRecord(
                    PersonalRecordEntity(
                        id = previousPr?.id ?: UUID.randomUUID().toString(),
                        userId = LOCAL_USER_ID,
                        exerciseId = currentExerciseId,
                        maxWeightKg = maxOf(topWeight, previousPr?.maxWeightKg ?: 0.0),
                        maxReps = maxOf(sets.maxOf { it.reps }, previousPr?.maxReps ?: 0),
                        estimated1rm = maxOf(estimated1Rm, previousPr?.estimated1rm ?: 0.0),
                        achievedAt = now,
                    ),
                )
            }

            val previousSession = historyStore.previous(currentExerciseId)
            historyStore.save(currentExerciseId, metrics)
            val recap = SessionRecapGenerator.generate(metrics, previousSession, newPr)

            restJob?.cancel()
            restJob = null
            _state.update {
                it.copy(
                    recap = recap,
                    isResting = false,
                    restSecondsRemaining = 0,
                    restProgress = 1f,
                )
            }
            voiceCoach.announce("Session complete. Nice work.")
        }
    }

    fun clearRecap() {
        synchronized(lock) {
            completedSets.clear()
            repTempos.clear()
            formScore = 100
            cueCount = 0
            formCue = null
            stateMachine = ExerciseStateMachine(analyzer)
            faultDetector = FormFaultDetector()
            restDetector = AutoSetRestDetector()
            cameraAssistant.reset()
            autoClassifier.reset()
            _state.update {
                it.copy(
                    recap = null,
                    reps = 0,
                    sets = 0,
                    romPercent = 0f,
                    phase = RepPhase.TOP_READY,
                    formCue = null,
                    formScore = 100,
                    angle = null,
                )
            }
        }
    }

    fun toggleMusic() = musicController.togglePlayPause()

    fun nextTrack() = musicController.next()

    override fun onCleared() {
        super.onCleared()
        voiceCoach.shutdown()
    }

    private fun applyKind(kind: ExerciseKind, detected: Boolean = false) {
        val info = exerciseInfo(kind)
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
        currentExerciseId = info.second

        _state.update {
            it.copy(
                exerciseName = info.first,
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

    private fun maybeAutoSet(frame: PoseFrame) {
        val current = _state.value
        if (current.isResting || current.recap != null) return
        val event = restDetector.update(frame, stateMachine.completedReps)
        if (event != null) completeSet(event.reps)
    }

    private fun completeSet(reps: Int) {
        if (reps < 1) return
        val rom = if (setRomCount > 0) setRomSum / setRomCount else 0f
        val set = RecordedSet(
            reps = reps,
            weightKg = _state.value.weightKg,
            romPercent = rom,
            tempoLabel = repTempos.lastOrNull()?.label() ?: "3-1-1",
            formScore = _state.value.formScore,
        )
        completedSets += set

        voiceCoach.announce("Set logged. $reps reps. Rest ninety seconds.")
        _state.update {
            it.copy(
                sets = completedSets.size,
                reps = 0,
                phase = RepPhase.TOP_READY,
                romPercent = 0f,
                formCue = null,
            )
        }
        resetTracking()
        startRest(REST_SECONDS)
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
        restTotalMs = seconds * 1_000L
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
        if (finished) voiceCoach.announce("Rest complete. Next set.")
    }

    private fun scheduleCueClear() {
        cueClearJob?.cancel()
        cueClearJob = viewModelScope.launch {
            delay(2_500L)
            formCue = null
            _state.update { it.copy(formCue = null) }
        }
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
        const val LOCAL_USER_ID = "local-user"
        const val REST_SECONDS = 90
    }
}
