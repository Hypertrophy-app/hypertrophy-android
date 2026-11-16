package app.hypertrophy.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ThermalProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = LocalPhoenixColors.current.elevated,
    sparkCount: Int = 18,
) {
    val colors = LocalPhoenixColors.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "thermal-progress",
    )

    var bursting by remember { mutableStateOf(false) }
    LaunchedEffect(animated) {
        if (animated >= 0.999f) {
            bursting = true
        }
    }

    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }

    Canvas(modifier = modifier) {
        val diameter = size.minDimension - strokePx
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter / 2f

        // Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )

        // Sweep gradient ring
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(
                    colors.crimsonPrimary,
                    colors.emberOrange,
                    colors.solarGold,
                    colors.crimsonPrimary,
                ),
                center = center,
            ),
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )

        // Spark bursts on completion
        if (bursting && animated >= 0.999f) {
            val rng = Random(42)
            for (i in 0 until sparkCount) {
                val angle = (i.toFloat() / sparkCount) * 360f + rng.nextFloat() * 12f
                val rad = Math.toRadians(angle.toDouble())
                val sparkRadius = radius + strokePx * 0.6f + rng.nextFloat() * 14f
                val x = center.x + (cos(rad) * sparkRadius).toFloat()
                val y = center.y + (sin(rad) * sparkRadius).toFloat()
                val sparkColor = listOf(colors.crimsonPrimary, colors.emberOrange, colors.solarGold)[i % 3]
                drawCircle(
                    color = sparkColor.copy(alpha = 0.85f),
                    radius = 2.5f + rng.nextFloat() * 2f,
                    center = Offset(x, y),
                )
            }
        }
    }
}
