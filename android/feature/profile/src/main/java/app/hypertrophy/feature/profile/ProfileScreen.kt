package app.hypertrophy.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.data.local.PersonalRecordWithExercise
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle
import java.time.LocalDate
import java.util.Locale
import kotlin.math.min

@Composable
fun ProfileRoute(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = remember(viewModel) { viewModel.healthConnect.permissions }
    val launcher = rememberLauncherForActivityResult(
        contract = remember { PermissionController.createRequestPermissionResultContract() },
    ) { granted: Set<String> ->
        viewModel.onHealthPermissionResult(granted.containsAll(permissions))
    }

    ProfileScreen(
        state = state,
        onSelectExercise = viewModel::selectExercise,
        onRequestHealthPermissions = { launcher.launch(permissions) },
        onExportToHealthConnect = viewModel::exportToHealthConnect,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onSelectExercise: (String) -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onExportToHealthConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    val now = remember(state.recovery) { System.currentTimeMillis() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ProfileHeader(state = state)
        StatsGrid(state = state)

        SectionTitle("Muscle recovery")
        RecoverySection(recovery = state.recovery, nowMillis = now)

        SectionTitle("90-day consistency")
        val heatmapDays = remember(state.sessionDays) {
            buildHeatmap(
                sessionDays = state.sessionDays,
                days = 90,
                nowEpochDay = LocalDate.now().toEpochDay(),
            )
        }
        ConsistencyHeatmap(
            days = heatmapDays,
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
        )
        Text(
            text = "${state.totals.sessionsLast30Days} sessions in the last 30 days",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )

        SectionTitle("PR trophy wall")
        PrTrophyWall(records = state.personalRecords)

        SectionTitle("Estimated 1RM progression")
        ProgressionSection(
            state = state,
            onSelectExercise = onSelectExercise,
        )

        SectionTitle("Badges")
        BadgeGallery(badges = state.badges)

        SectionTitle("Health Connect")
        HealthConnectCard(
            state = state,
            onRequestHealthPermissions = onRequestHealthPermissions,
            onExportToHealthConnect = onExportToHealthConnect,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState) {
    val colors = LocalPhoenixColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(colors.elevated, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(state.displayName),
                style = PhoenixMonoTextStyle.copy(fontSize = 20.sp, lineHeight = 22.sp),
                color = colors.solarGold,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "PROFILE",
                style = PhoenixMonoTextStyle.copy(fontSize = 26.sp, lineHeight = 28.sp),
                color = colors.textPrimary,
            )
            Text(
                text = state.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            if (state.username.isNotBlank()) {
                Text(
                    text = "@${state.username}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(state: ProfileUiState) {
    val colors = LocalPhoenixColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "SESSIONS",
                value = state.totals.sessions.toString(),
                caption = "all time",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "STREAK",
                value = "${state.totals.streakDays}d",
                caption = "current",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "VOLUME",
                value = "${ProfileInsights.formatCompactVolume(state.totals.volumeKg)} kg",
                caption = "lifted",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "LAST 30",
                value = state.totals.sessionsLast30Days.toString(),
                caption = "sessions",
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text(
        text = "Totals come from your logged sessions on this device.",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
    )
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    Column(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = PhoenixMonoTextStyle.copy(fontSize = 10.sp, lineHeight = 12.sp),
            color = colors.textSecondary,
        )
        Text(
            text = value,
            style = PhoenixMonoTextStyle.copy(fontSize = 22.sp, lineHeight = 24.sp),
            color = colors.textPrimary,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun RecoverySection(recovery: List<MuscleRecovery>, nowMillis: Long) {
    val colors = LocalPhoenixColors.current
    val byMuscle = remember(recovery) { recovery.associateBy { it.muscle } }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "FRONT",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = PhoenixMonoTextStyle.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = colors.textSecondary,
        )
        Text(
            text = "BACK",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = PhoenixMonoTextStyle.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = colors.textSecondary,
        )
    }

    AnatomyMap(
        recoveryByMuscle = byMuscle,
        nowMillis = nowMillis,
        modifier = Modifier
            .fillMaxWidth()
            .height(288.dp),
    )

    RecoveryLegend()

    val trained = recovery
        .filter { it.lastTrainedAtMillis != null }
        .sortedByDescending { it.lastTrainedAtMillis }
    Text(
        text = if (trained.isEmpty()) {
            "No muscle data yet. Log a session to light up the map."
        } else {
            trained.take(4).joinToString("  |  ") { item ->
                val hours = ProfileInsights.hoursSince(item.lastTrainedAtMillis ?: 0L, nowMillis)
                "${item.muscle.label} ${hours}h ago"
            }
        },
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
    )
}

@Composable
private fun AnatomyMap(
    recoveryByMuscle: Map<MuscleGroup, MuscleRecovery>,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    Canvas(modifier = modifier) {
        val gap = size.width * 0.08f
        val figureWidth = (size.width - gap) / 2f
        drawAnatomyFigure(
            originX = 0f,
            figureWidth = figureWidth,
            figureHeight = size.height,
            isFront = true,
            recoveryByMuscle = recoveryByMuscle,
            nowMillis = nowMillis,
            colors = colors,
        )
        drawAnatomyFigure(
            originX = figureWidth + gap,
            figureWidth = figureWidth,
            figureHeight = size.height,
            isFront = false,
            recoveryByMuscle = recoveryByMuscle,
            nowMillis = nowMillis,
            colors = colors,
        )
    }
}

private fun DrawScope.drawAnatomyFigure(
    originX: Float,
    figureWidth: Float,
    figureHeight: Float,
    isFront: Boolean,
    recoveryByMuscle: Map<MuscleGroup, MuscleRecovery>,
    nowMillis: Long,
    colors: PhoenixColors,
) {
    fun x(fraction: Float): Float = originX + fraction * figureWidth
    fun y(fraction: Float): Float = fraction * figureHeight
    fun sizeX(fraction: Float): Float = fraction * figureWidth
    fun sizeY(fraction: Float): Float = fraction * figureHeight

    val body = colors.elevated
    drawOval(
        color = body,
        topLeft = Offset(x(0.44f), y(0.015f)),
        size = Size(sizeX(0.12f), sizeY(0.085f)),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(x(0.37f), y(0.12f)),
        size = Size(sizeX(0.26f), sizeY(0.38f)),
        cornerRadius = CornerRadius(sizeX(0.08f)),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(x(0.25f), y(0.15f)),
        size = Size(sizeX(0.075f), sizeY(0.32f)),
        cornerRadius = CornerRadius(sizeX(0.04f)),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(x(0.675f), y(0.15f)),
        size = Size(sizeX(0.075f), sizeY(0.32f)),
        cornerRadius = CornerRadius(sizeX(0.04f)),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(x(0.235f), y(0.44f)),
        size = Size(sizeX(0.065f), sizeY(0.19f)),
        cornerRadius = CornerRadius(sizeX(0.03f)),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(x(0.70f), y(0.44f)),
        size = Size(sizeX(0.065f), sizeY(0.19f)),
        cornerRadius = CornerRadius(sizeX(0.03f)),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(x(0.38f), y(0.49f)),
        size = Size(sizeX(0.105f), sizeY(0.46f)),
        cornerRadius = CornerRadius(sizeX(0.045f)),
    )
    drawRoundRect(
        color = body,
        topLeft = Offset(x(0.515f), y(0.49f)),
        size = Size(sizeX(0.105f), sizeY(0.46f)),
        cornerRadius = CornerRadius(sizeX(0.045f)),
    )

    val placements = if (isFront) FRONT_PLACEMENTS else BACK_PLACEMENTS
    for (placement in placements) {
        val recovery = recoveryByMuscle[placement.muscle]
        val status = recovery?.status(nowMillis) ?: RecoveryStatus.PRIME
        val trained = recovery?.lastTrainedAtMillis != null
        val color = when {
            !trained -> colors.textSecondary.copy(alpha = 0.20f)
            status == RecoveryStatus.FATIGUED -> colors.formErrorCrimson.copy(alpha = 0.92f)
            status == RecoveryStatus.RECOVERING -> colors.emberOrange.copy(alpha = 0.90f)
            else -> colors.solarGold.copy(alpha = 0.85f)
        }
        for ((pointX, pointY) in placement.points) {
            drawOval(
                color = color,
                topLeft = Offset(
                    x = x(pointX) - sizeX(placement.radiusX),
                    y = y(pointY) - sizeY(placement.radiusY),
                ),
                size = Size(sizeX(placement.radiusX * 2f), sizeY(placement.radiusY * 2f)),
            )
        }
    }
}

@Composable
private fun RecoveryLegend() {
    val colors = LocalPhoenixColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LegendItem("No data", colors.textSecondary.copy(alpha = 0.35f))
        LegendItem("Fatigued", colors.formErrorCrimson)
        LegendItem("Recovering", colors.emberOrange)
        LegendItem("Prime", colors.solarGold)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    val colors = LocalPhoenixColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawOval(color = color)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ConsistencyHeatmap(days: List<HeatmapDay>, modifier: Modifier = Modifier) {
    val colors = LocalPhoenixColors.current
    Canvas(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        val columns = 13
        val rows = 7
        val gap = 3f
        val cell = min(
            (size.width - gap * (columns - 1)) / columns,
            (size.height - gap * (rows - 1)) / rows,
        ).coerceAtLeast(1f)
        val totalWidth = columns * cell + (columns - 1) * gap
        val left = (size.width - totalWidth) / 2f
        days.forEachIndexed { index, day ->
            val column = index / rows
            if (column >= columns) return@forEachIndexed
            val row = index % rows
            val intensity = heatmapIntensity(day.sessionCount)
            val color = if (day.sessionCount == 0) {
                colors.elevated
            } else {
                colors.solarGold.copy(alpha = 0.25f + 0.75f * intensity)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left + column * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.2f),
            )
        }
    }
}

@Composable
private fun PrTrophyWall(records: List<PersonalRecordWithExercise>) {
    val colors = LocalPhoenixColors.current
    if (records.isEmpty()) {
        EmptyCard("No records yet. Log a heavy set and the PR wall fills in automatically.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        records.take(6).forEach { record ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.exerciseName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "${formatKg(record.record.maxWeightKg)} kg x ${record.record.maxReps} reps",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "~${formatKg(record.record.estimated1rm)} kg",
                        style = PhoenixMonoTextStyle.copy(fontSize = 16.sp, lineHeight = 18.sp),
                        color = colors.solarGold,
                    )
                    Text(
                        text = ProfileInsights.formatDate(record.record.achievedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressionSection(
    state: ProfileUiState,
    onSelectExercise: (String) -> Unit,
) {
    val colors = LocalPhoenixColors.current
    if (state.exerciseOptions.isEmpty()) {
        EmptyCard("No progression data yet. Complete a session with weights to draw your first trend line.")
        return
    }

    val selected = state.exerciseOptions.firstOrNull { it.exerciseId == state.selectedExerciseId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.exerciseOptions.forEach { option ->
            val isSelected = option.exerciseId == state.selectedExerciseId
            Text(
                text = option.name,
                modifier = Modifier
                    .background(
                        color = if (isSelected) colors.solarGold else colors.elevated,
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onSelectExercise(option.exerciseId) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) colors.canvas else colors.textPrimary,
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = selected?.name.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Text(
            text = selected?.let { "best ~${formatKg(it.bestEstimated1RmKg)} kg" }.orEmpty(),
            style = PhoenixMonoTextStyle.copy(fontSize = 13.sp, lineHeight = 15.sp),
            color = colors.solarGold,
        )
    }

    ProgressionChart(
        points = state.progression,
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
    )

    if (state.progression.size >= 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = ProfileInsights.formatDate(state.progression.first().createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            Text(
                text = ProfileInsights.formatDate(state.progression.last().createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    } else {
        Text(
            text = "Log another session to see the trend.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun ProgressionChart(points: List<ProgressionPoint>, modifier: Modifier = Modifier) {
    val colors = LocalPhoenixColors.current
    Canvas(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        for (line in 0..3) {
            val yPos = size.height * line / 3f
            drawLine(
                color = colors.elevated,
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 1f,
            )
        }
        if (points.isEmpty()) return@Canvas

        val values = points.map { it.estimated1RmKg }
        val rawMin = values.min()
        val rawMax = values.max()
        val padding = if (rawMax - rawMin < 1.0) 1.0 else (rawMax - rawMin) * 0.15
        val low = rawMin - padding
        val high = rawMax + padding
        val span = (high - low).coerceAtLeast(0.001)

        fun xAt(index: Int): Float = if (points.size == 1) {
            size.width / 2f
        } else {
            size.width * index / (points.size - 1).toFloat()
        }

        fun yAt(value: Double): Float =
            (size.height - ((value - low) / span * size.height)).toFloat()

        val path = Path()
        points.forEachIndexed { index, point ->
            val xPos = xAt(index)
            val yPos = yAt(point.estimated1RmKg)
            if (index == 0) path.moveTo(xPos, yPos) else path.lineTo(xPos, yPos)
        }
        drawPath(
            path = path,
            color = colors.solarGold,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
        points.forEachIndexed { index, point ->
            drawCircle(
                color = colors.solarGold,
                radius = 4f,
                center = Offset(xAt(index), yAt(point.estimated1RmKg)),
            )
        }
    }
}

@Composable
private fun BadgeGallery(badges: List<BadgeState>) {
    if (badges.isEmpty()) {
        EmptyCard("Badges unlock as you train. The first one lands after your first session.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        badges.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { badge ->
                    BadgeCard(
                        badge = badge,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BadgeCard(badge: BadgeState, modifier: Modifier = Modifier) {
    val colors = LocalPhoenixColors.current
    val unlocked = badge.unlocked
    Column(
        modifier = modifier
            .background(
                color = if (unlocked) colors.elevated else colors.surface,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = badge.definition.title,
                style = MaterialTheme.typography.labelLarge,
                color = if (unlocked) colors.solarGold else colors.textPrimary,
            )
            Text(
                text = badge.progressLabel,
                style = PhoenixMonoTextStyle.copy(fontSize = 9.sp, lineHeight = 11.sp),
                color = if (unlocked) colors.solarGold else colors.textSecondary,
            )
        }
        Text(
            text = badge.definition.description,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        val fraction = (badge.progress.toFloat() / badge.definition.target.toFloat())
            .coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(colors.canvas, RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(
                        color = if (unlocked) colors.solarGold else colors.emberOrange,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
        if (unlocked) {
            Text(
                text = "Unlocked ${ProfileInsights.formatDate(badge.unlockedAtMillis ?: 0L)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HealthConnectCard(
    state: ProfileUiState,
    onRequestHealthPermissions: () -> Unit,
    onExportToHealthConnect: () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when {
                !state.healthConnectAvailable -> "Health Connect is not available on this device."
                state.healthConnectGranted -> "Connected. Workouts export as strength sessions with estimated active calories."
                else -> "Grant write access to export workouts and active calories to Health Connect."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        state.healthMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = colors.solarGold,
            )
        }
        if (state.healthConnectAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.healthConnectGranted) {
                    TextButton(onClick = onRequestHealthPermissions) {
                        Text("Grant permission", color = colors.solarGold)
                    }
                } else {
                    TextButton(
                        onClick = onExportToHealthConnect,
                        enabled = !state.healthBusy,
                    ) {
                        Text(
                            text = if (state.healthBusy) "Exporting..." else "Export sessions",
                            color = if (state.healthBusy) colors.textSecondary else colors.solarGold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalPhoenixColors.current
    Text(
        text = text.uppercase(Locale.US),
        style = PhoenixMonoTextStyle.copy(fontSize = 12.sp, lineHeight = 14.sp),
        color = colors.textSecondary,
    )
}

@Composable
private fun EmptyCard(message: String) {
    val colors = LocalPhoenixColors.current
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
    )
}

private fun initials(displayName: String): String {
    val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val letters = parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return if (letters.isEmpty()) "A" else letters.joinToString("")
}

private fun formatKg(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

private data class MusclePlacement(
    val muscle: MuscleGroup,
    val points: List<Pair<Float, Float>>,
    val radiusX: Float,
    val radiusY: Float,
)

private val FRONT_PLACEMENTS = listOf(
    MusclePlacement(MuscleGroup.CHEST, listOf(0.5f to 0.245f), 0.115f, 0.06f),
    MusclePlacement(
        MuscleGroup.FRONT_DELTS,
        listOf(0.335f to 0.19f, 0.665f to 0.19f),
        0.05f,
        0.04f,
    ),
    MusclePlacement(
        MuscleGroup.SIDE_DELTS,
        listOf(0.295f to 0.215f, 0.705f to 0.215f),
        0.035f,
        0.038f,
    ),
    MusclePlacement(
        MuscleGroup.BICEPS,
        listOf(0.295f to 0.32f, 0.705f to 0.32f),
        0.034f,
        0.062f,
    ),
    MusclePlacement(
        MuscleGroup.FOREARMS,
        listOf(0.272f to 0.45f, 0.728f to 0.45f),
        0.028f,
        0.058f,
    ),
    MusclePlacement(MuscleGroup.ABS, listOf(0.5f to 0.37f), 0.062f, 0.075f),
    MusclePlacement(
        MuscleGroup.OBLIQUES,
        listOf(0.442f to 0.37f, 0.558f to 0.37f),
        0.02f,
        0.068f,
    ),
    MusclePlacement(
        MuscleGroup.QUADS,
        listOf(0.433f to 0.66f, 0.567f to 0.66f),
        0.045f,
        0.115f,
    ),
)

private val BACK_PLACEMENTS = listOf(
    MusclePlacement(MuscleGroup.TRAPS, listOf(0.5f to 0.185f), 0.10f, 0.035f),
    MusclePlacement(
        MuscleGroup.REAR_DELTS,
        listOf(0.335f to 0.19f, 0.665f to 0.19f),
        0.05f,
        0.04f,
    ),
    MusclePlacement(
        MuscleGroup.LATS,
        listOf(0.435f to 0.29f, 0.565f to 0.29f),
        0.055f,
        0.075f,
    ),
    MusclePlacement(
        MuscleGroup.TRICEPS,
        listOf(0.295f to 0.32f, 0.705f to 0.32f),
        0.034f,
        0.062f,
    ),
    MusclePlacement(
        MuscleGroup.FOREARMS,
        listOf(0.272f to 0.45f, 0.728f to 0.45f),
        0.028f,
        0.058f,
    ),
    MusclePlacement(MuscleGroup.LOWER_BACK, listOf(0.5f to 0.42f), 0.045f, 0.045f),
    MusclePlacement(
        MuscleGroup.GLUTES,
        listOf(0.45f to 0.54f, 0.55f to 0.54f),
        0.05f,
        0.045f,
    ),
    MusclePlacement(
        MuscleGroup.HAMSTRINGS,
        listOf(0.433f to 0.65f, 0.567f to 0.65f),
        0.045f,
        0.10f,
    ),
    MusclePlacement(
        MuscleGroup.CALVES,
        listOf(0.43f to 0.84f, 0.57f to 0.84f),
        0.035f,
        0.075f,
    ),
)
