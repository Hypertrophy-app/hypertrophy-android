package app.hypertrophy.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle
import java.util.concurrent.TimeUnit

@Composable
fun ProfileRoute(viewModel: ProfileViewModel = hiltViewModel()) {
    ProfileScreen(healthConnectAvailable = viewModel.healthConnect.isAvailable)
}

@Composable
fun ProfileScreen(
    healthConnectAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    val now = System.currentTimeMillis()
    val recovery = MuscleGroup.entries.map { muscle ->
        MuscleRecovery(muscle, lastTrainedAtMillis = null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "PROFILE",
            style = PhoenixMonoTextStyle.copy(fontSize = 28.sp, lineHeight = 30.sp),
            color = colors.textPrimary,
        )

        Text(
            text = "Muscle recovery map",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        MuscleRecoveryCanvas(recovery = recovery, nowMillis = now, modifier = Modifier.fillMaxWidth().height(260.dp))

        RecoveryLegend()

        Text(
            text = "90-day consistency",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        val days = buildHeatmap(emptyList(), 90, System.currentTimeMillis() / 86_400_000L)
        ConsistencyHeatmap(days = days, modifier = Modifier.fillMaxWidth().height(120.dp))

        Text(
            text = "PR trophy wall",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        PrTrophyWall(modifier = Modifier.fillMaxWidth())

        Text(
            text = "Badges",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        BadgeGallery(modifier = Modifier.fillMaxWidth())

        Text(
            text = if (healthConnectAvailable) {
                "Health Connect available"
            } else {
                "Health Connect not available on this device"
            },
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun MuscleRecoveryCanvas(
    recovery: List<MuscleRecovery>,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    Canvas(modifier = modifier.background(colors.surface)) {
        val cellWidth = size.width / 4f
        val cellHeight = size.height / 4f
        recovery.forEachIndexed { index, item ->
            val col = index % 4
            val row = index / 4
            val status = item.status(nowMillis)
            drawRect(
                color = recoveryColor(status),
                topLeft = Offset(col * cellWidth + 4f, row * cellHeight + 4f),
                size = Size(cellWidth - 8f, cellHeight - 8f),
            )
        }
    }
}

@Composable
private fun RecoveryLegend() {
    val colors = LocalPhoenixColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem("Fatigued", Color(0xFFFF2A4B), colors)
        LegendItem("Recovering", Color(0xFFFF6B1A), colors)
        LegendItem("Prime", Color(0xFFFFB800), colors)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, colors: app.hypertrophy.core.designsystem.theme.PhoenixColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.size(12.dp)) { drawRect(color = color) }
        Text(text = label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = colors.textSecondary)
    }
}

@Composable
private fun ConsistencyHeatmap(days: List<HeatmapDay>, modifier: Modifier = Modifier) {
    val colors = LocalPhoenixColors.current
    Canvas(modifier = modifier.background(colors.surface)) {
        val cols = 13
        val rows = 7
        val cell = (size.width / cols).coerceAtMost(size.height / rows) - 2f
        days.forEachIndexed { index, day ->
            val col = index / rows
            val row = index % rows
            val intensity = heatmapIntensity(day.sessionCount)
            val alpha = 0.12f + 0.88f * intensity
            drawRect(
                color = colors.solarGold.copy(alpha = alpha),
                topLeft = Offset(col * (cell + 2f) + 2f, row * (cell + 2f) + 2f),
                size = Size(cell, cell),
            )
        }
    }
}

@Composable
private fun PrTrophyWall(modifier: Modifier = Modifier) {
    val colors = LocalPhoenixColors.current
    Row(
        modifier = modifier.background(colors.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Text(
                text = "TROPHY",
                style = PhoenixMonoTextStyle.copy(fontSize = 14.sp, lineHeight = 16.sp),
                color = colors.solarGold,
                modifier = Modifier
                    .background(colors.elevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BadgeGallery(modifier: Modifier = Modifier) {
    val colors = LocalPhoenixColors.current
    Row(
        modifier = modifier.background(colors.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(4) {
            Text(
                text = "BADGE",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .background(colors.elevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}
