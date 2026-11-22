package app.hypertrophy.feature.coach

import android.content.Intent
import androidx.camera.core.CameraSelector
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.hypertrophy.core.data.squad.SquadLiveBus
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.BackHandler
import app.hypertrophy.core.designsystem.component.PhoenixIgnitionButton
import app.hypertrophy.core.designsystem.component.ThermalProgressRing
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle
import app.hypertrophy.core.kinematics.CameraAim
import app.hypertrophy.core.kinematics.ExerciseKind
import app.hypertrophy.core.kinematics.FormCue
import app.hypertrophy.core.kinematics.PoseFrame
import app.hypertrophy.core.kinematics.RepPhase
import app.hypertrophy.core.vision.CameraPermissionGate
import app.hypertrophy.core.vision.CameraPosePreview
import app.hypertrophy.feature.coach.music.MusicController
import app.hypertrophy.feature.coach.music.MusicState
import app.hypertrophy.feature.coach.recap.SessionRecap
import kotlin.math.roundToInt

/**
 * Entry point for the live coach experience. It owns the stateful pieces
 * (Hilt view model, haptics, share intents) and delegates rendering to the
 * stateless [CoachScreen].
 */
@Composable
fun CoachRoute(
    onBack: () -> Unit,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var announcedReps by remember { mutableStateOf(0) }

    // A long haptic pulses at gym distance on every validated rep.
    LaunchedEffect(state.reps) {
        if (state.reps > announcedReps) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        announcedReps = state.reps
    }

    val newPr = state.recap?.newPr == true
    LaunchedEffect(newPr) {
        if (newPr) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    CoachScreen(
        state = state,
        onPoseFrame = viewModel::onPoseFrame,
        onSelectKind = viewModel::selectKind,
        onToggleAutoClassify = viewModel::toggleAutoClassify,
        onAdjustWeight = viewModel::adjustWeight,
        onAdjustTargetReps = viewModel::adjustTargetReps,
        onLogSet = viewModel::logSetManually,
        onEndSession = viewModel::endSession,
        onAddRestTime = { viewModel.addRestTime(15) },
        onSkipRest = viewModel::skipRest,
        onToggleMusic = viewModel::toggleMusic,
        onNextTrack = viewModel::nextTrack,
        onOpenMusicSettings = {
            runCatching { context.startActivity(MusicController.settingsIntent()) }
        },
        onShareRecap = {
            state.recap?.let { recap ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, recap.shareText)
                }
                context.startActivity(
                    Intent.createChooser(share, "Share with your squad"),
                )
            }
        },
        onClearRecap = viewModel::clearRecap,
        onFinishWorkout = {
            viewModel.clearRecap()
            onBack()
        },
        onBack = onBack,
    )
}

/**
 * Gym-distance HUD over the live camera feed.
 *
 * The rep counter, tempo bars, and ROM gauge sit low on the screen so they
 * remain readable from a metre away while the skeleton overlay stays visible.
 * Rest and recap replace the HUD with focused full-screen surfaces.
 */
@Composable
fun CoachScreen(
    state: CoachUiState,
    onPoseFrame: (PoseFrame) -> Unit,
    onSelectKind: (ExerciseKind) -> Unit,
    onToggleAutoClassify: () -> Unit,
    onAdjustWeight: (Double) -> Unit,
    onAdjustTargetReps: (Int) -> Unit,
    onLogSet: () -> Unit,
    onEndSession: () -> Unit,
    onAddRestTime: () -> Unit,
    onSkipRest: () -> Unit,
    onToggleMusic: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenMusicSettings: () -> Unit,
    onShareRecap: () -> Unit,
    onClearRecap: () -> Unit,
    onFinishWorkout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Back dismisses the recap or rest overlay before leaving the session.
    BackHandler(enabled = state.recap != null || state.isResting) {
        when {
            state.recap != null -> onClearRecap()
            else -> onSkipRest()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        CameraPermissionGate(modifier = Modifier.fillMaxSize()) {
            CameraPosePreview(
                modifier = Modifier.fillMaxSize(),
                lensFacing = CameraSelector.LENS_FACING_FRONT,
                onPoseFrame = onPoseFrame,
                onCameraError = { message -> cameraError = message },
                violatingJoint = state.formCue?.jointIndex,
                formOptimal = state.formCue == null,
            )
        }

        // Legibility scrims above and below keep the camera visible in the
        // middle third of the screen without washing out the skeleton.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to colors.canvas.copy(alpha = 0.9f),
                            0.30f to Color.Transparent,
                            0.56f to Color.Transparent,
                            1f to colors.canvas.copy(alpha = 0.97f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            CoachTopBar(
                state = state,
                onBack = onBack,
                onEndSession = onEndSession,
            )
            Spacer(modifier = Modifier.height(10.dp))
            ExerciseKindChips(
                selected = state.kind,
                autoClassify = state.autoClassify,
                detectedKind = state.detectedKind,
                onSelect = onSelectKind,
                onToggleAutoClassify = onToggleAutoClassify,
            )
            Spacer(modifier = Modifier.height(10.dp))
            state.cameraAim?.let { aim ->
                if (!aim.isLocked) {
                    CameraAimBanner(aim = aim)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            cameraError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.formErrorCrimson,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            state.formCue?.let { cue ->
                FormBanner(cue = cue)
                Spacer(modifier = Modifier.height(10.dp))
            }

            CoachHud(
                state = state,
                onAdjustWeight = onAdjustWeight,
                onAdjustTargetReps = onAdjustTargetReps,
                onLogSet = onLogSet,
                onEndSession = onEndSession,
                onToggleMusic = onToggleMusic,
                onNextTrack = onNextTrack,
                onOpenMusicSettings = onOpenMusicSettings,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.isResting) {
            RestOverlay(
                state = state,
                onAddRestTime = onAddRestTime,
                onSkipRest = onSkipRest,
                onAdjustWeight = onAdjustWeight,
                onAdjustTargetReps = onAdjustTargetReps,
            )
        }

        state.recap?.let { recap ->
            RecapOverlay(
                recap = recap,
                onShare = onShareRecap,
                onDone = onClearRecap,
                onFinish = onFinishWorkout,
            )
        }
    }
}

@Composable
private fun CoachTopBar(
    state: CoachUiState,
    onBack: () -> Unit,
    onEndSession: () -> Unit,
) {
    val colors = LocalPhoenixColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Back",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "SET ${state.sets + 1}",
                style = PhoenixMonoTextStyle.copy(fontSize = 16.sp, lineHeight = 18.sp),
                color = colors.solarGold,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "End session",
                style = MaterialTheme.typography.labelLarge,
                color = colors.formErrorCrimson,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onEndSession)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.exerciseName.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "${state.reps} of ${state.targetReps} target reps this set",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ExerciseKindChips(
    selected: ExerciseKind,
    autoClassify: Boolean,
    detectedKind: ExerciseKind?,
    onSelect: (ExerciseKind) -> Unit,
    onToggleAutoClassify: () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    val kinds = listOf(
        ExerciseKind.SQUAT,
        ExerciseKind.HINGE,
        ExerciseKind.PUSH,
        ExerciseKind.PULL,
        ExerciseKind.CURL_RAISE,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.forEach { kind ->
            Chip(
                label = kindLabel(kind),
                selected = kind == selected,
                onClick = { onSelect(kind) },
            )
        }
        Chip(
            label = when {
                autoClassify && detectedKind != null -> "Auto: ${kindLabel(detectedKind)}"
                autoClassify -> "Auto-detect on"
                else -> "Auto-detect"
            },
            selected = autoClassify,
            onClick = onToggleAutoClassify,
            accent = colors.solarGold,
        )
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color? = null,
) {
    val colors = LocalPhoenixColors.current
    val background = when {
        selected -> (accent ?: colors.crimsonPrimary).copy(alpha = 0.9f)
        else -> colors.surface.copy(alpha = 0.82f)
    }
    val contentColor = when {
        !selected -> colors.textSecondary
        accent == colors.solarGold -> colors.onGold
        else -> colors.onCrimson
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = contentColor,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun CameraAimBanner(aim: CameraAim) {
    val colors = LocalPhoenixColors.current
    val progress by animateFloatAsState(
        targetValue = aim.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "camera-aim",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.9f))
            .border(1.dp, colors.solarGold.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = aim.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${aim.progressPercent}%",
                style = PhoenixMonoTextStyle.copy(fontSize = 14.sp, lineHeight = 16.sp),
                color = colors.solarGold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.elevated),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.solarGold),
            )
        }
    }
}

@Composable
private fun FormBanner(cue: FormCue) {
    val colors = LocalPhoenixColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.formErrorCrimson.copy(alpha = 0.18f))
            .border(1.dp, colors.formErrorCrimson.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colors.formErrorCrimson),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = cue.message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun CoachHud(
    state: CoachUiState,
    onAdjustWeight: (Double) -> Unit,
    onAdjustTargetReps: (Int) -> Unit,
    onLogSet: () -> Unit,
    onEndSession: () -> Unit,
    onToggleMusic: () -> Unit,
    onNextTrack: () -> Unit,
    onOpenMusicSettings: () -> Unit,
) {
    val colors = LocalPhoenixColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface.copy(alpha = 0.95f))
            .padding(16.dp),
    ) {
        TempoBars(phase = state.phase, tempoLabel = state.tempoLabel)
        Spacer(modifier = Modifier.height(14.dp))
        DepthGauge(
            romPercent = state.romPercent,
            angle = state.angle,
            targetAngle = state.targetAngle,
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.reps.toString().padStart(2, '0'),
                    style = PhoenixMonoTextStyle.copy(
                        fontSize = 84.sp,
                        lineHeight = 84.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.textPrimary,
                )
                Text(
                    text = "REPS THIS SET",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactStepper(
                    label = "WEIGHT",
                    value = "${formatWeight(state.weightKg)} kg",
                    onDecrease = { onAdjustWeight(-2.5) },
                    onIncrease = { onAdjustWeight(2.5) },
                )
                CompactStepper(
                    label = "TARGET",
                    value = "${state.targetReps} reps",
                    onDecrease = { onAdjustTargetReps(-1) },
                    onIncrease = { onAdjustTargetReps(1) },
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PhoenixIgnitionButton(
                text = "Log set",
                onClick = onLogSet,
                enabled = state.reps > 0 && !state.isResting,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = onEndSession,
                modifier = Modifier.height(56.dp),
            ) {
                Text(
                    text = "Finish",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        TeammateTicker()

        MusicChip(
            music = state.music,
            onToggle = onToggleMusic,
            onNext = onNextTrack,
            onOpenSettings = onOpenMusicSettings,
        )
    }
}

/**
 * Live teammate progress relayed from the squad room through [SquadLiveBus].
 * Entries expire after [SquadLiveBus.MAX_AGE_MILLIS] so a finished set never
 * lingers in the HUD.
 */
@Composable
private fun TeammateTicker() {
    val colors = LocalPhoenixColors.current
    val progress by SquadLiveBus.teammateProgress.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000L)
            now = System.currentTimeMillis()
        }
    }

    val fresh = progress.values
        .filter { now - it.at <= SquadLiveBus.MAX_AGE_MILLIS }
        .sortedByDescending { it.at }
    if (fresh.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "SQUAD LIVE",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        fresh.forEach { teammate ->
            Text(
                text = buildString {
                    append(teammate.userId)
                    append(" set ")
                    append(teammate.setIndex + 1)
                    append(" | ")
                    append(formatWeight(teammate.weightKg))
                    append(" kg x ")
                    append(teammate.reps)
                    if (teammate.isPr) append(" PR")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (teammate.isPr) colors.solarGold else colors.textPrimary,
            )
        }
    }
}

@Composable
private fun TempoBars(phase: RepPhase, tempoLabel: String) {
    val colors = LocalPhoenixColors.current
    val active = when (phase) {
        RepPhase.ECCENTRIC -> 0
        RepPhase.BOTTOM_INFLECTION -> 1
        RepPhase.CONCENTRIC, RepPhase.REP_COMPLETE -> 2
        RepPhase.TOP_READY -> -1
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "LIVE TEMPO",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = tempoLabel,
                style = PhoenixMonoTextStyle.copy(fontSize = 16.sp, lineHeight = 18.sp),
                color = colors.solarGold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TempoSegment(weight = 3f, active = active == 0, color = colors.crimsonPrimary)
            TempoSegment(weight = 1.2f, active = active == 1, color = colors.emberOrange)
            TempoSegment(weight = 1f, active = active == 2, color = colors.solarGold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TempoLabel("ECC", 3f, active == 0, colors)
            TempoLabel("PAUSE", 1.2f, active == 1, colors)
            TempoLabel("CON", 1f, active == 2, colors)
        }
    }
}

@Composable
private fun RowScope.TempoSegment(weight: Float, active: Boolean, color: Color) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.18f,
        animationSpec = tween(durationMillis = 160),
        label = "tempo-alpha",
    )
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun RowScope.TempoLabel(
    text: String,
    weight: Float,
    active: Boolean,
    colors: PhoenixColors,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) colors.textPrimary else colors.textSecondary,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun DepthGauge(romPercent: Float, angle: Float?, targetAngle: Float) {
    val colors = LocalPhoenixColors.current
    val animatedRom by animateFloatAsState(
        targetValue = (romPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 160),
        label = "rom-gauge",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "DEPTH",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${romPercent.roundToInt()}%",
                style = PhoenixMonoTextStyle.copy(fontSize = 18.sp, lineHeight = 20.sp),
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "target ${targetAngle.roundToInt()} deg",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.elevated),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedRom)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                colors.crimsonPrimary,
                                colors.emberOrange,
                                colors.solarGold,
                            ),
                        ),
                    ),
            )
        }
        angle?.let { live ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Live joint angle ${live.roundToInt()} deg",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CompactStepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    val colors = LocalPhoenixColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = value,
                style = PhoenixMonoTextStyle.copy(fontSize = 20.sp, lineHeight = 22.sp),
                color = colors.textPrimary,
            )
        }
        StepperButton(symbol = "-", onClick = onDecrease)
        StepperButton(symbol = "+", onClick = onIncrease)
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    val colors = LocalPhoenixColors.current

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.elevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = PhoenixMonoTextStyle.copy(fontSize = 20.sp, lineHeight = 20.sp),
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun MusicChip(
    music: MusicState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalPhoenixColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.elevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!music.available) {
            Text(
                text = "Connect music controls",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "ENABLE",
                style = MaterialTheme.typography.labelLarge,
                color = colors.solarGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = music.title.ifBlank { "Now playing" },
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = music.artist.ifBlank { "Media session" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (music.isPlaying) "PAUSE" else "PLAY",
                style = MaterialTheme.typography.labelLarge,
                color = colors.solarGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Text(
                text = "NEXT",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onNext)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun RestOverlay(
    state: CoachUiState,
    onAddRestTime: () -> Unit,
    onSkipRest: () -> Unit,
    onAdjustWeight: (Double) -> Unit,
    onAdjustTargetReps: (Int) -> Unit,
) {
    val colors = LocalPhoenixColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas.copy(alpha = 0.98f))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "REST",
            style = MaterialTheme.typography.labelLarge,
            color = colors.emberOrange,
        )
        Text(
            text = "Set ${state.sets + 1} up next",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center,
        ) {
            ThermalProgressRing(
                progress = state.restProgress,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 14.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatSeconds(state.restSecondsRemaining),
                    style = PhoenixMonoTextStyle.copy(
                        fontSize = 78.sp,
                        lineHeight = 82.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "SECONDS",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RestStepper(
                label = "WEIGHT",
                value = "${formatWeight(state.weightKg)} kg",
                onDecrease = { onAdjustWeight(-2.5) },
                onIncrease = { onAdjustWeight(2.5) },
                modifier = Modifier.weight(1f),
            )
            RestStepper(
                label = "TARGET",
                value = "${state.targetReps} reps",
                onDecrease = { onAdjustTargetReps(-1) },
                onIncrease = { onAdjustTargetReps(1) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onAddRestTime,
                modifier = Modifier.height(56.dp),
            ) {
                Text(
                    text = "+15s",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            PhoenixIgnitionButton(
                text = "Skip rest",
                onClick = onSkipRest,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RestStepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = PhoenixMonoTextStyle.copy(fontSize = 22.sp, lineHeight = 24.sp),
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepperButton(symbol = "-", onClick = onDecrease)
            StepperButton(symbol = "+", onClick = onIncrease)
        }
    }
}

@Composable
private fun RecapOverlay(
    recap: SessionRecap,
    onShare: () -> Unit,
    onDone: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = LocalPhoenixColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Text(
            text = "SESSION RECAP",
            style = MaterialTheme.typography.labelLarge,
            color = colors.emberOrange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = recap.headline,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )

        if (recap.newPr) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "NEW PERSONAL RECORD",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.solarGold)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecapStat(label = "FORM", value = "${recap.formScore}", modifier = Modifier.weight(1f))
            RecapStat(
                label = "ROM",
                value = "${recap.romPercent.roundToInt()}%",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = recap.body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "SQUAD SHARE PREVIEW",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = recap.shareText,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .padding(14.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
        PhoenixIgnitionButton(
            text = "Share to Squad",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = "Back to coach",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = "Finish workout",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun RecapStat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPhoenixColors.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = PhoenixMonoTextStyle.copy(fontSize = 32.sp, lineHeight = 34.sp),
            color = colors.solarGold,
        )
    }
}

private fun kindLabel(kind: ExerciseKind): String = when (kind) {
    ExerciseKind.SQUAT -> "Squat"
    ExerciseKind.HINGE -> "Hinge"
    ExerciseKind.PUSH -> "Push"
    ExerciseKind.PULL -> "Pull"
    ExerciseKind.CURL_RAISE -> "Curl"
    ExerciseKind.UNKNOWN -> "Auto"
}

private fun formatWeight(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }

private fun formatSeconds(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remainder = safe % 60
    return if (minutes > 0) {
        "$minutes:${remainder.toString().padStart(2, '0')}"
    } else {
        safe.toString()
    }
}
