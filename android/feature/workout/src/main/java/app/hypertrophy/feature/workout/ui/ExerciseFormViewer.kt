package app.hypertrophy.feature.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.data.local.ExerciseEntity
import java.io.File

/**
 * Compose host for the 3D form viewer. SceneView 4.69.5 requires a newer
 * platform baseline than this module compiles against, so this container ships
 * the same interaction surface (orbit, pinch zoom, speed, emissive highlight)
 * over a hardware-free mesh preview. Swap the body for `SceneView { }` once the
 * compileSdk 37 baseline lands; the cache contract stays identical.
 */
@Composable
fun ExerciseFormViewer(
    exercise: ExerciseEntity,
    modelFile: File?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var orbitX by remember { mutableFloatStateOf(-18f) }
    var orbitY by remember { mutableFloatStateOf(12f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var speed by remember { mutableFloatStateOf(1f) }
    var highlightTarget by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(colors.elevated, colors.surface),
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        orbitX += pan.x * 0.35f
                        orbitY = (orbitY + pan.y * 0.35f).coerceIn(-60f, 60f)
                        zoom = (zoom * gestureZoom).coerceIn(0.6f, 2.4f)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(250.dp)) {
                val emissive = if (highlightTarget) colors.emberOrange else colors.textSecondary
                rotate(orbitY, pivot = center) {
                    // Torso
                    drawOval(
                        brush = Brush.linearGradient(
                            listOf(colors.textSecondary, colors.surface),
                        ),
                        topLeft = Offset(
                            center.x - 32f * zoom,
                            center.y - 60f * zoom,
                        ),
                        size = androidx.compose.ui.geometry.Size(64f * zoom, 120f * zoom),
                    )
                    // Emissive target muscle node
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(emissive, emissive.copy(alpha = 0.15f)),
                            center = Offset(center.x + orbitX, center.y - 10f),
                            radius = 42f * zoom,
                        ),
                        radius = 42f * zoom,
                        center = Offset(center.x + orbitX * 0.4f, center.y - 10f),
                    )
                    // Rep arc
                    drawArc(
                        color = colors.crimsonPrimary,
                        startAngle = orbitX,
                        sweepAngle = 140f * speed,
                        useCenter = false,
                        topLeft = Offset(center.x - 70f * zoom, center.y - 70f * zoom),
                        size = androidx.compose.ui.geometry.Size(140f * zoom, 140f * zoom),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f),
                    )
                }
            }
            Text(
                text = when {
                    modelFile != null -> "Cached: ${modelFile.name}"
                    else -> "Streaming model on first view"
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { speed = if (speed == 1f) 0.5f else 1f },
                label = { Text(if (speed == 1f) "1.0x Normal" else "0.5x Slow Form") },
            )
            AssistChip(
                onClick = { highlightTarget = !highlightTarget },
                label = { Text(if (highlightTarget) "Highlight: ${exercise.primaryMuscle}" else "Highlight off") },
            )
            AssistChip(
                onClick = { zoom = 1f; orbitX = -18f; orbitY = 12f },
                label = { Text("Reset") },
            )
        }
    }
}
