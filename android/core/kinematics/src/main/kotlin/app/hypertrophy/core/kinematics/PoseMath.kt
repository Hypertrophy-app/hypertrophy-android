package app.hypertrophy.core.kinematics

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Geometry helpers shared by every biomechanical analyzer. All angles are
 * degrees and every helper returns null when confidence gating fails.
 */
object PoseMath {
    const val MIN_VISIBILITY = 0.65f

    fun isVisible(landmark: Landmark?, threshold: Float = MIN_VISIBILITY): Boolean =
        landmark != null && landmark.visibility > threshold

    fun angleBetween(a: Landmark?, b: Landmark?, c: Landmark?): Float? {
        val pa = a ?: return null
        val pb = b ?: return null
        val pc = c ?: return null
        if (!isVisible(pa) || !isVisible(pb) || !isVisible(pc)) return null
        val abx = pa.x - pb.x
        val aby = pa.y - pb.y
        val abz = pa.z - pb.z
        val cbx = pc.x - pb.x
        val cby = pc.y - pb.y
        val cbz = pc.z - pb.z

        val dot = abx * cbx + aby * cby + abz * cbz
        val magAb = sqrt(abx * abx + aby * aby + abz * abz)
        val magCb = sqrt(cbx * cbx + cby * cby + cbz * cbz)
        if (magAb < 1e-6f || magCb < 1e-6f) return null

        val cosine = (dot / (magAb * magCb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine.toDouble())).toFloat()
    }

    fun midpoint(a: Landmark, b: Landmark): Landmark = Landmark(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
        z = (a.z + b.z) / 2f,
        visibility = minOf(a.visibility, b.visibility),
    )

    /** Torso inclination in degrees away from vertical (0 = upright). */
    fun torsoInclination(
        leftShoulder: Landmark?,
        rightShoulder: Landmark?,
        leftHip: Landmark?,
        rightHip: Landmark?,
    ): Float? {
        if (!isVisible(leftShoulder) || !isVisible(rightShoulder) ||
            !isVisible(leftHip) || !isVisible(rightHip)
        ) {
            return null
        }
        val shoulder = midpoint(leftShoulder!!, rightShoulder!!)
        val hip = midpoint(leftHip!!, rightHip!!)

        val dx = shoulder.x - hip.x
        val dy = shoulder.y - hip.y
        // Vertical in image space points up, i.e. negative y.
        val angle = atan2(dx, -dy)
        return Math.toDegrees(angle.toDouble()).toFloat().let { kotlin.math.abs(it) }
    }

    /**
     * Knee valgus estimate: positive when knees converge inside the ankle
     * line, negative when they splay outward.
     */
    fun kneeValgus(
        leftKnee: Landmark?,
        rightKnee: Landmark?,
        leftAnkle: Landmark?,
        rightAnkle: Landmark?,
    ): Float {
        if (!isVisible(leftKnee) || !isVisible(rightKnee) ||
            !isVisible(leftAnkle) || !isVisible(rightAnkle)
        ) {
            return 0f
        }
        val kneeSpan = kotlin.math.abs(leftKnee!!.x - rightKnee!!.x)
        val ankleSpan = kotlin.math.abs(leftAnkle!!.x - rightAnkle!!.x)
        if (ankleSpan < 1e-4f) return 0f
        return (ankleSpan - kneeSpan) / ankleSpan
    }

    fun distance(a: Landmark, b: Landmark): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun averageVisibility(frame: PoseFrame, landmarkIndices: List<Int> = PoseLandmarks.FRAMING_LANDMARKS): Float {
        val values = landmarkIndices.mapNotNull { frame.landmark(it)?.visibility }
        if (values.isEmpty()) return 0f
        return values.average().toFloat()
    }

    /**
     * True when every framing landmark sits comfortably inside the frame.
     * A small margin keeps ankles and head from clipping at the edge.
     */
    fun isFullyFramed(frame: PoseFrame, margin: Float = 0.02f): Boolean =
        PoseLandmarks.FRAMING_LANDMARKS.all { index ->
            val landmark = frame.landmark(index) ?: return@all false
            isVisible(landmark) &&
                landmark.x in margin..(1f - margin) &&
                landmark.y in margin..(1f - margin)
        }
}
