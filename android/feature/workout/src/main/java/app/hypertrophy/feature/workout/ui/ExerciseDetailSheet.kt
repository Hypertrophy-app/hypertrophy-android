package app.hypertrophy.feature.workout.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hypertrophy.core.data.local.ExerciseEntity
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: ExerciseEntity,
    modelFile: File?,
    onDismiss: () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${exercise.primaryMuscle.replace('_', ' ')}  |  ${exercise.equipment}  |  ${exercise.kinematicProfileType.replace('_', ' ')}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.emberOrange,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ExerciseFormViewer(exercise = exercise, modelFile = modelFile)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Orbit with one finger, pinch to zoom, and slow the animation to audit joint angles. Models cache to filesDir/models so the demo works offline.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
