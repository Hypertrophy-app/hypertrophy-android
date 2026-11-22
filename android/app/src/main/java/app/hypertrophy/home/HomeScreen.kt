package app.hypertrophy.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hypertrophy.R
import app.hypertrophy.core.designsystem.component.PhoenixEmberRing
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle

@Composable
fun HomeScreen(
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.home_eyebrow),
            style = MaterialTheme.typography.labelMedium,
            color = colors.emberOrange,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.displayMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PhoenixEmberRing(progress = 0.86f, modifier = Modifier.size(260.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "7",
                    style = PhoenixMonoTextStyle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.home_streak_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStartWorkout()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.crimsonPrimary,
                contentColor = colors.onCrimson,
            ),
        ) {
            Text(
                text = stringResource(R.string.home_start_workout),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
