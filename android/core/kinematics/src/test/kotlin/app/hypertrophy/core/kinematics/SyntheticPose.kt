package app.hypertrophy.core.kinematics

import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthetic 33-landmark pose generator for kinematics tests.
 *
 * Knee and elbow flexion is expressed in depth (z) so the 3D joint angle stays
 * exact without moving the joint laterally. Lateral knobs (kneeInset,
 * ankleSpread, shoulderDrop) exist purely to drive valgus and asymmetry rules.
 */
object SyntheticPose {
    const val VISIBILITY = 0.97f

    fun frame(
        timestampMillis: Long = 0L,
        torsoLeanDegrees: Float = 0f,
        kneeAngleDegrees: Float = 172f,
        elbowAngleDegrees: Float = 170f,
        kneeInset: Float = 0f,
        ankleSpread: Float = 0f,
        shoulderDrop: Float = 0f,
        shoulderLift: Float = 0f,
        leftElbowOverride: Pair<Float, Float>? = null,
        leftWristOverride: Pair<Float, Float>? = null,
        visibility: Float = VISIBILITY,
        hidden: Set<Int> = emptySet(),
    ): PoseFrame {
        val points = Array(PoseLandmarks.COUNT) { Landmark(0.5f, 0.5f, 0f, visibility) }

        fun put(index: Int, x: Float, y: Float, z: Float = 0f) {
            points[index] = Landmark(
                x = x,
                y = y,
                z = z,
                visibility = if (index in hidden) 0.1f else visibility,
            )
        }

        val hipMidX = 0.5f
        val hipMidY = 0.48f
        val lean = Math.toRadians(torsoLeanDegrees.toDouble())
        val torsoLength = 0.26f
        val shoulderMidX = hipMidX + (sin(lean) * torsoLength).toFloat()
        val shoulderMidY = hipMidY - (cos(lean) * torsoLength).toFloat() + shoulderLift

        // Hips
        put(PoseLandmarks.LEFT_HIP, 0.45f, hipMidY)
        put(PoseLandmarks.RIGHT_HIP, 0.55f, hipMidY)

        // Shoulders, with optional asymmetric drop on the left side.
        put(PoseLandmarks.LEFT_SHOULDER, shoulderMidX - 0.08f, shoulderMidY + shoulderDrop)
        put(PoseLandmarks.RIGHT_SHOULDER, shoulderMidX + 0.08f, shoulderMidY)

        // Head follows the torso axis.
        val noseX = shoulderMidX + (sin(lean) * 0.12f).toFloat()
        val noseY = shoulderMidY - (cos(lean) * 0.12f).toFloat()
        put(PoseLandmarks.NOSE, noseX, noseY)
        put(PoseLandmarks.LEFT_EYE, noseX - 0.02f, noseY - 0.015f)
        put(PoseLandmarks.RIGHT_EYE, noseX + 0.02f, noseY - 0.015f)
        put(PoseLandmarks.LEFT_EAR, noseX - 0.04f, noseY)
        put(PoseLandmarks.RIGHT_EAR, noseX + 0.04f, noseY)
        put(1, noseX - 0.03f, noseY - 0.01f)
        put(3, noseX - 0.045f, noseY - 0.005f)
        put(4, noseX + 0.03f, noseY - 0.01f)
        put(6, noseX + 0.045f, noseY - 0.005f)
        put(9, noseX - 0.02f, noseY + 0.025f)
        put(10, noseX + 0.02f, noseY + 0.025f)

        // Legs: flexion rotates the shin into z, keeping the 3D angle exact.
        val kneeRad = Math.toRadians(kneeAngleDegrees.toDouble())
        val shinLength = 0.2f
        val ankleDY = (-cos(kneeRad) * shinLength).toFloat()
        val ankleDZ = (sin(kneeRad) * shinLength).toFloat()

        val leftKneeX = 0.45f + kneeInset
        val rightKneeX = 0.55f - kneeInset
        put(PoseLandmarks.LEFT_KNEE, leftKneeX, 0.68f)
        put(PoseLandmarks.RIGHT_KNEE, rightKneeX, 0.68f)

        val leftAnkleX = leftKneeX - ankleSpread
        val rightAnkleX = rightKneeX + ankleSpread
        put(PoseLandmarks.LEFT_ANKLE, leftAnkleX, 0.68f + ankleDY, ankleDZ)
        put(PoseLandmarks.RIGHT_ANKLE, rightAnkleX, 0.68f + ankleDY, ankleDZ)
        put(PoseLandmarks.LEFT_HEEL, leftAnkleX, 0.68f + ankleDY + 0.03f, ankleDZ)
        put(PoseLandmarks.RIGHT_HEEL, rightAnkleX, 0.68f + ankleDY + 0.03f, ankleDZ)
        put(PoseLandmarks.LEFT_FOOT_INDEX, leftAnkleX, 0.68f + ankleDY + 0.05f, ankleDZ)
        put(PoseLandmarks.RIGHT_FOOT_INDEX, rightAnkleX, 0.68f + ankleDY + 0.05f, ankleDZ)

        // Arms.
        val elbowRad = Math.toRadians(elbowAngleDegrees.toDouble())
        val forearm = 0.12f
        val wristDY = (-cos(elbowRad) * forearm).toFloat()
        val wristDZ = (sin(elbowRad) * forearm).toFloat()

        val defaultLeftElbow = 0.40f to 0.34f
        val leftElbow = leftElbowOverride ?: defaultLeftElbow
        put(PoseLandmarks.LEFT_ELBOW, leftElbow.first, leftElbow.second)
        put(PoseLandmarks.RIGHT_ELBOW, 1f - leftElbow.first, leftElbow.second)

        val leftWrist = leftWristOverride
            ?: (leftElbow.first to leftElbow.second + wristDY)
        val leftWristZ = if (leftWristOverride == null) wristDZ else 0f
        put(PoseLandmarks.LEFT_WRIST, leftWrist.first, leftWrist.second, leftWristZ)
        put(PoseLandmarks.RIGHT_WRIST, 1f - leftWrist.first, leftWrist.second, leftWristZ)

        // Hands: small offsets from the wrists.
        put(17, leftWrist.first - 0.01f, leftWrist.second + 0.02f)
        put(18, 1f - leftWrist.first + 0.01f, leftWrist.second + 0.02f)
        put(19, leftWrist.first - 0.015f, leftWrist.second + 0.015f)
        put(20, 1f - leftWrist.first + 0.015f, leftWrist.second + 0.015f)
        put(21, leftWrist.first - 0.02f, leftWrist.second + 0.01f)
        put(22, 1f - leftWrist.first + 0.02f, leftWrist.second + 0.01f)

        return PoseFrame(points.toList(), timestampMillis)
    }
}
