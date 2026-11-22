package app.hypertrophy.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.data.catalog.ExerciseCatalog
import app.hypertrophy.core.data.local.ChallengeEntity
import app.hypertrophy.core.data.local.ScheduledWorkoutEntity
import app.hypertrophy.core.data.squad.TeammateSetProgress
import app.hypertrophy.core.designsystem.component.sharedFireElement
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TodayRoute(
    onOpenWorkout: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayScreen(
        state = state,
        layout = layout,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onToggle = viewModel::setVisible,
        onOpenWorkout = onOpenWorkout,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    layout: DashboardLayout,
    onMoveUp: (TodayCard) -> Unit,
    onMoveDown: (TodayCard) -> Unit,
    onToggle: (TodayCard, Boolean) -> Unit,
    onOpenWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var customizing by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 28.sp,
                        lineHeight = 30.sp,
                    ),
                    color = colors.textPrimary,
                )
                Text(
                    text = dateFormatter.format(LocalDate.now()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${state.streakDays}",
                    style = PhoenixMonoTextStyle.copy(fontSize = 26.sp, lineHeight = 28.sp),
                    color = colors.solarGold,
                )
                Text(
                    text = "day streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }

        if (layout.visibleCards().isEmpty()) {
            Text(
                text = "All cards are hidden. Use Customize cards to bring them back.",
                color = colors.textSecondary,
            )
        }

        layout.visibleCards().forEach { card ->
            TodayCardShell(card = card) {
                when (card) {
                    TodayCard.TODAYS_PLAN -> PlanCard(state = state, onOpenWorkout = onOpenWorkout)
                    TodayCard.SQUAD_LIVE_TICKER -> SquadTickerCard(progress = state.squadProgress)
                    TodayCard.ACTIVE_CHALLENGE -> ChallengeCard(challenge = state.activeChallenge)
                    TodayCard.MUSCLE_RECOVERY_SUMMARY -> RecoveryCard(state = state)
                    TodayCard.CONSISTENCY_STRIP -> ConsistencyCard(state = state)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { customizing = !customizing }) {
            Text(
                text = if (customizing) "Done customizing" else "Customize cards",
                color = colors.solarGold,
            )
        }

        if (customizing) {
            layout.order.forEach { card ->
                DashboardCustomizeRow(
                    card = card,
                    visible = layout.isVisible(card),
                    onMoveUp = { onMoveUp(card) },
                    onMoveDown = { onMoveDown(card) },
                    onToggle = { isVisible -> onToggle(card, isVisible) },
                )
            }
        }

        TextButton(onClick = onOpenWorkout) {
            Text("Open live workout", color = colors.solarGold)
        }
    }
}

@Composable
private fun TodayCardShell(
    card: TodayCard,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPhoenixColors.current
    val morphModifier = if (card == TodayCard.TODAYS_PLAN) {
        Modifier.sharedFireElement("today-plan-to-workout")
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(morphModifier)
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = cardTitle(card),
            style = PhoenixMonoTextStyle.copy(fontSize = 13.sp, lineHeight = 15.sp),
            color = colors.textSecondary,
        )
        content()
    }
}

private fun cardTitle(card: TodayCard): String = when (card) {
    TodayCard.TODAYS_PLAN -> "TODAY'S PLAN"
    TodayCard.SQUAD_LIVE_TICKER -> "SQUAD LIVE"
    TodayCard.ACTIVE_CHALLENGE -> "ACTIVE CHALLENGE"
    TodayCard.MUSCLE_RECOVERY_SUMMARY -> "MUSCLE RECOVERY"
    TodayCard.CONSISTENCY_STRIP -> "CONSISTENCY"
}

@Composable
private fun PlanCard(state: TodayUiState, onOpenWorkout: () -> Unit) {
    val colors = LocalPhoenixColors.current
    if (state.scheduled.isEmpty()) {
        Text(
            text = "No session scheduled today.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        TextButton(onClick = onOpenWorkout) {
            Text("Start live workout", color = colors.solarGold)
        }
    } else {
        state.scheduled.forEach { scheduled ->
            ScheduleRow(scheduled = scheduled)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "${state.week.sessions} sessions",
            style = PhoenixMonoTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textSecondary,
        )
        Text(
            text = "${TodayInsights.formatVolume(state.week.volumeKg)} kg",
            style = PhoenixMonoTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ScheduleRow(scheduled: ScheduledWorkoutEntity) {
    val colors = LocalPhoenixColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scheduled.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Text(
                text = when {
                    scheduled.isRestDay -> "Rest and recovery"
                    scheduled.isCompleted -> "Completed"
                    scheduled.isKeyCompound -> "Key compound day"
                    else -> "Training day"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (scheduled.isCompleted) colors.solarGold else colors.textSecondary,
            )
        }
        Text(
            text = if (scheduled.isCompleted) "DONE" else "PLANNED",
            style = PhoenixMonoTextStyle.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = if (scheduled.isCompleted) colors.solarGold else colors.textSecondary,
        )
    }
}

@Composable
private fun SquadTickerCard(progress: Map<String, TeammateSetProgress>) {
    val colors = LocalPhoenixColors.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000L)
            now = System.currentTimeMillis()
        }
    }
    val fresh = progress.values
        .filter { now - it.at <= 30_000L }
        .sortedByDescending { it.at }
    if (fresh.isEmpty()) {
        Text(
            text = "No teammate sets in the last 30 seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        return
    }
    fresh.take(4).forEach { set ->
        Text(
            text = buildString {
                append(set.userId)
                append(" | ")
                append(exerciseLabel(set.exerciseId))
                append(" | ")
                append(formatWeight(set.weightKg))
                append(" kg x ")
                append(set.reps)
                if (set.isPr) append(" PR")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (set.isPr) colors.solarGold else colors.textPrimary,
        )
    }
}

@Composable
private fun ChallengeCard(challenge: ChallengeEntity?) {
    val colors = LocalPhoenixColors.current
    if (challenge == null) {
        Text(
            text = "No active challenge. Join a squad to compete.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        return
    }
    Text(
        text = challenge.title,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textPrimary,
    )
    Text(
        text = TodayInsights.challengeSummary(challenge, System.currentTimeMillis()),
        style = MaterialTheme.typography.labelMedium,
        color = colors.textSecondary,
    )
}

@Composable
private fun RecoveryCard(state: TodayUiState) {
    val colors = LocalPhoenixColors.current
    if (state.muscleTraining.isEmpty()) {
        Text(
            text = "No sessions logged yet. Every muscle is fresh.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        return
    }
    val now = System.currentTimeMillis()
    state.muscleTraining.take(5).forEach { muscle ->
        val level = TodayInsights.recoveryLevel(muscle.lastTrainedAtMillis, now)
        val color = when (level) {
            RecoveryLevel.FATIGUED -> colors.formErrorCrimson
            RecoveryLevel.RECOVERING -> colors.emberOrange
            RecoveryLevel.FRESH -> colors.solarGold
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = muscle.label, color = colors.textPrimary)
            Text(text = level.label, color = color)
        }
    }
}

@Composable
private fun ConsistencyCard(state: TodayUiState) {
    val colors = LocalPhoenixColors.current
    ConsistencyStrip(
        sessionDays = state.sessionDays,
        todayEpochDay = state.todayEpochDay,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
    )
    Text(
        text = "${state.sessionsLast30Days} sessions in the last 30 days",
        style = PhoenixMonoTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = colors.textSecondary,
    )
}

@Composable
private fun ConsistencyStrip(
    sessionDays: Set<Long>,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    val counts = remember(sessionDays) { sessionDays.groupingBy { it }.eachCount() }
    Canvas(modifier = modifier.background(colors.elevated, RoundedCornerShape(8.dp))) {
        val columns = 12
        val rows = 7
        val gap = 3f
        val cell = ((size.width - gap * (columns + 1)) / columns)
            .coerceAtMost((size.height - gap * (rows + 1)) / rows)
            .coerceAtLeast(1f)
        for (index in 0 until columns * rows) {
            val day = todayEpochDay - (columns * rows - 1 - index)
            val count = counts[day] ?: 0
            val alpha = when {
                count <= 0 -> 0.15f
                count == 1 -> 0.45f
                count == 2 -> 0.72f
                else -> 1f
            }
            val col = index / rows
            val row = index % rows
            drawRect(
                color = colors.solarGold.copy(alpha = alpha),
                topLeft = Offset(
                    x = gap + col * (cell + gap),
                    y = gap + row * (cell + gap),
                ),
                size = Size(cell, cell),
            )
        }
    }
}

@Composable
private fun DashboardCustomizeRow(
    card: TodayCard,
    visible: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalPhoenixColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = card.key.replace("_", " ").uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (visible) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMoveUp) { Text("Up", color = colors.textSecondary) }
        TextButton(onClick = onMoveDown) { Text("Down", color = colors.textSecondary) }
        Switch(checked = visible, onCheckedChange = onToggle)
    }
}

private fun exerciseLabel(exerciseId: String): String =
    ExerciseCatalog.exercises.firstOrNull { it.id == exerciseId }?.name
        ?: exerciseId.removePrefix("ex_").replace('_', ' ')

private fun formatWeight(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
