package app.hypertrophy.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.designsystem.component.PhoenixEmberRing
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle

@Composable
fun WorkoutRoute(
    onBack: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    WorkoutScreen(
        session = session,
        sessionCount = sessions.size,
        onCompleteSet = viewModel::completeSet,
        onBack = onBack,
    )
}

@Composable
fun WorkoutScreen(
    session: LiveSessionUiState,
    sessionCount: Int,
    onCompleteSet: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "LIVE SESSION",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            TextButton(onClick = onBack) {
                Text(text = "Close", color = colors.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Set ${session.setNumber} of ${session.totalSets}",
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Text(
            text = session.exerciseName,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "Camera coaching runs entirely on-device. Frames are never stored or uploaded.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(40.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PhoenixEmberRing(progress = session.progress, modifier = Modifier.size(240.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${session.reps}", style = PhoenixMonoTextStyle, color = colors.textPrimary)
                Text(
                    text = "REPS",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Logged sessions: $sessionCount",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onCompleteSet,
            enabled = !session.isComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.crimsonPrimary,
                contentColor = colors.onCrimson,
            ),
        ) {
            Text(
                text = if (session.isComplete) "Session complete" else "Complete set",
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
    }
}
