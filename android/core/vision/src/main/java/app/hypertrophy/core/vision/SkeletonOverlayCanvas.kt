package app.hypertrophy.core.vision

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.kinematics.Landmark
import app.hypertrophy.core.kinematics.PoseLandmarks

/**
 * High-contrast skeleton overlay.
 *
 * Joints and bones render in Solar Gold (#FFB800) while form and ROM are
 * optimal. When a form rule fires, [violatingJoint] and its attached bones
 * pulse Crimson (#FF2A4B) so the athlete can see exactly which joint to fix
 * from gym distance.
 */
@Composable
fun SkeletonOverlayCanvas(
    landmarks: List<Landmark>,
    modifier: Modifier = Modifier,
    violatingJoint: Int? = null,
    formOptimal: Boolean = true,
    mirrorHorizontally: Boolean = false,
) {
    val colors = LocalPhoenixColors.current
    val pulse by rememberInfiniteTransition(label = "skeleton-pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "violation-pulse",
    )

    Canvas(modifier = modifier) {
        if (landmarks.size < PoseLandmarks.COUNT) return@Canvas

        val gold = colors.solarGold
        val crimson = colors.formErrorCrimson
        val shadow = Color.Black.copy(alpha = 0.45f)
        val bones = if (formOptimal) gold else gold.copy(alpha = 0.75f)

        fun point(index: Int): Offset? {
            val landmark = landmarks[index]
            if (landmark.visibility <= 0.5f) return null
            val x = if (mirrorHorizontally) 1f - landmark.x else landmark.x
            return Offset(x * size.width, landmark.y * size.height)
        }

        for ((start, end) in BonePairs) {
            val a = point(start) ?: continue
            val b = point(end) ?: continue
            val touchesViolation = violatingJoint != null &&
                (start == violatingJoint || end == violatingJoint)
            val color = if (touchesViolation) {
                crimson.copy(alpha = 0.55f + 0.45f * pulse)
            } else {
                bones
            }

            drawLine(
                color = shadow,
                start = a,
                end = b,
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = a,
                end = b,
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        for (index in PoseLandmarks.FRAMING_LANDMARKS + listOf(
            PoseLandmarks.LEFT_ELBOW,
            PoseLandmarks.RIGHT_ELBOW,
            PoseLandmarks.LEFT_WRIST,
            PoseLandmarks.RIGHT_WRIST,
        )) {
            val center = point(index) ?: continue
            if (index == violatingJoint) {
                drawViolatingJoint(center, crimson, pulse)
            } else {
                drawCircle(color = shadow, radius = 6.dp.toPx(), center = center)
                drawCircle(color = gold, radius = 4.dp.toPx(), center = center)
            }
        }
    }
}

private fun DrawScope.drawViolatingJoint(center: Offset, crimson: Color, pulse: Float) {
    drawCircle(
        color = crimson.copy(alpha = 0.18f + 0.25f * pulse),
        radius = (12f + 10f * pulse).dp.toPx(),
        center = center,
    )
    drawCircle(
        color = crimson.copy(alpha = 0.65f + 0.35f * pulse),
        radius = 7.dp.toPx(),
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )
    drawCircle(color = crimson, radius = 4.dp.toPx(), center = center)
}

private val BonePairs: List<Pair<Int, Int>> = listOf(
    PoseLandmarks.NOSE to PoseLandmarks.LEFT_SHOULDER,
    PoseLandmarks.NOSE to PoseLandmarks.RIGHT_SHOULDER,
    PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.RIGHT_SHOULDER,
    PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.LEFT_ELBOW,
    PoseLandmarks.LEFT_ELBOW to PoseLandmarks.LEFT_WRIST,
    PoseLandmarks.RIGHT_SHOULDER to PoseLandmarks.RIGHT_ELBOW,
    PoseLandmarks.RIGHT_ELBOW to PoseLandmarks.RIGHT_WRIST,
    PoseLandmarks.LEFT_SHOULDER to PoseLandmarks.LEFT_HIP,
    PoseLandmarks.RIGHT_SHOULDER to PoseLandmarks.RIGHT_HIP,
    PoseLandmarks.LEFT_HIP to PoseLandmarks.RIGHT_HIP,
    PoseLandmarks.LEFT_HIP to PoseLandmarks.LEFT_KNEE,
    PoseLandmarks.LEFT_KNEE to PoseLandmarks.LEFT_ANKLE,
    PoseLandmarks.RIGHT_HIP to PoseLandmarks.RIGHT_KNEE,
    PoseLandmarks.RIGHT_KNEE to PoseLandmarks.RIGHT_ANKLE,
    PoseLandmarks.LEFT_ANKLE to PoseLandmarks.LEFT_HEEL,
    PoseLandmarks.LEFT_HEEL to PoseLandmarks.LEFT_FOOT_INDEX,
    PoseLandmarks.LEFT_ANKLE to PoseLandmarks.LEFT_FOOT_INDEX,
    PoseLandmarks.RIGHT_ANKLE to PoseLandmarks.RIGHT_HEEL,
    PoseLandmarks.RIGHT_HEEL to PoseLandmarks.RIGHT_FOOT_INDEX,
    PoseLandmarks.RIGHT_ANKLE to PoseLandmarks.RIGHT_FOOT_INDEX,
)
