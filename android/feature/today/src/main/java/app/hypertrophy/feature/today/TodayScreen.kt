package app.hypertrophy.feature.today

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle

@Composable
fun TodayRoute(
    onOpenWorkout: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    TodayScreen(
        layout = layout,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onToggle = viewModel::toggle,
        onOpenWorkout = onOpenWorkout,
    )
}

@Composable
fun TodayScreen(
    layout: DashboardLayout,
    onMoveUp: (TodayCard) -> Unit,
    onMoveDown: (TodayCard) -> Unit,
    onToggle: (TodayCard) -> Unit,
    onOpenWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "TODAY",
            style = PhoenixMonoTextStyle.copy(fontSize = 28.sp, lineHeight = 30.sp),
            color = colors.textPrimary,
        )
        Text(
            text = "Customize the cards below. Order and visibility are saved.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        layout.order.forEach { card ->
            DashboardCard(
                card = card,
                visible = layout.isVisible(card),
                onMoveUp = { onMoveUp(card) },
                onMoveDown = { onMoveDown(card) },
                onToggle = { onToggle(card) },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onOpenWorkout) {
            Text("Open workout builder", color = colors.solarGold)
        }
    }
}

@Composable
private fun DashboardCard(
    card: TodayCard,
    visible: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.key.replace("_", " ").uppercase(),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                color = if (visible) colors.textPrimary else colors.textSecondary,
            )
            Text(
                text = if (visible) "Visible" else "Hidden",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }
        TextButton(onClick = onMoveUp) { Text("Up", color = colors.textSecondary) }
        TextButton(onClick = onMoveDown) { Text("Down", color = colors.textSecondary) }
        Switch(checked = visible, onCheckedChange = { onToggle() })
    }
}
