package app.hypertrophy.core.kinematics

enum class ExerciseKind {
    SQUAT,
    HINGE,
    PUSH,
    PULL,
    CURL_RAISE,
    UNKNOWN,
}

/**
 * A biomechanical analyzer describes one joint triangle plus the angle targets
 * that bound a full rep. The state machine consumes these targets; analyzers
 * stay stateless so the same instance can be reused across sets.
 */
interface BiomechanicalAnalyzer {
    val kind: ExerciseKind
    val displayName: String

    /** Joint angle in degrees, or null when visibility gating fails. */
    fun primaryAngle(frame: PoseFrame): Float?

    /** Fully extended / top position angle. */
    val topAngle: Float

    /** Target bottom / contracted angle for a complete rep. */
    val bottomAngle: Float

    /** How far past the target the athlete may stop and still count as depth. */
    val depthTolerance: Float

    /** Torso lean beyond this many degrees is flagged. */
    val maxTorsoInclination: Float
}

object SquatAnalyzer : BiomechanicalAnalyzer {
    override val kind = ExerciseKind.SQUAT
    override val displayName = "Squat"
    override val topAngle = 170f
    override val bottomAngle = 90f
    override val depthTolerance = 12f
    override val maxTorsoInclination = 45f

    override fun primaryAngle(frame: PoseFrame): Float? =
        PoseMath.angleBetween(
            frame.landmark(PoseLandmarks.LEFT_HIP),
            frame.landmark(PoseLandmarks.LEFT_KNEE),
            frame.landmark(PoseLandmarks.LEFT_ANKLE),
        )
}

object HingeAnalyzer : BiomechanicalAnalyzer {
    override val kind = ExerciseKind.HINGE
    override val displayName = "Hinge"
    override val topAngle = 175f
    override val bottomAngle = 95f
    override val depthTolerance = 10f
    override val maxTorsoInclination = 75f

    override fun primaryAngle(frame: PoseFrame): Float? =
        PoseMath.angleBetween(
            frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            frame.landmark(PoseLandmarks.LEFT_HIP),
            frame.landmark(PoseLandmarks.LEFT_KNEE),
        )
}

object PushAnalyzer : BiomechanicalAnalyzer {
    override val kind = ExerciseKind.PUSH
    override val displayName = "Push"
    override val topAngle = 170f
    override val bottomAngle = 80f
    override val depthTolerance = 12f
    override val maxTorsoInclination = 60f

    override fun primaryAngle(frame: PoseFrame): Float? =
        PoseMath.angleBetween(
            frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            frame.landmark(PoseLandmarks.LEFT_ELBOW),
            frame.landmark(PoseLandmarks.LEFT_WRIST),
        )
}

object PullAnalyzer : BiomechanicalAnalyzer {
    override val kind = ExerciseKind.PULL
    override val displayName = "Pull"
    override val topAngle = 170f
    override val bottomAngle = 55f
    override val depthTolerance = 12f
    override val maxTorsoInclination = 60f

    override fun primaryAngle(frame: PoseFrame): Float? =
        PoseMath.angleBetween(
            frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            frame.landmark(PoseLandmarks.LEFT_ELBOW),
            frame.landmark(PoseLandmarks.LEFT_WRIST),
        )
}

object CurlRaiseAnalyzer : BiomechanicalAnalyzer {
    override val kind = ExerciseKind.CURL_RAISE
    override val displayName = "Curl / Raise"
    override val topAngle = 165f
    override val bottomAngle = 45f
    override val depthTolerance = 15f
    override val maxTorsoInclination = 25f

    override fun primaryAngle(frame: PoseFrame): Float? =
        PoseMath.angleBetween(
            frame.landmark(PoseLandmarks.LEFT_SHOULDER),
            frame.landmark(PoseLandmarks.LEFT_ELBOW),
            frame.landmark(PoseLandmarks.LEFT_WRIST),
        )
}

fun analyzerFor(kind: ExerciseKind): BiomechanicalAnalyzer = when (kind) {
    ExerciseKind.SQUAT -> SquatAnalyzer
    ExerciseKind.HINGE -> HingeAnalyzer
    ExerciseKind.PUSH -> PushAnalyzer
    ExerciseKind.PULL -> PullAnalyzer
    ExerciseKind.CURL_RAISE -> CurlRaiseAnalyzer
    ExerciseKind.UNKNOWN -> SquatAnalyzer
}
