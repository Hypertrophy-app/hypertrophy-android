package app.hypertrophy.feature.coach

import app.hypertrophy.core.kinematics.ExerciseKind

/**
 * Maps the richer catalog kinematic profile onto the five movement patterns the
 * on-device analyzer understands. The planned exercise keeps its own id and
 * display name; only the joint triangle used for rep counting changes.
 */
object ExerciseKindMapping {
    fun forProfile(kinematicProfile: String, primaryMuscle: String): ExerciseKind =
        when (kinematicProfile.uppercase()) {
            "SQUAT" -> ExerciseKind.SQUAT
            "HINGE" -> ExerciseKind.HINGE
            "HORIZONTAL_PUSH", "VERTICAL_PUSH" -> ExerciseKind.PUSH
            "PULL" -> ExerciseKind.PULL
            "ISOLATION" -> isolationKind(primaryMuscle.lowercase())
            else -> ExerciseKind.SQUAT
        }

    private fun isolationKind(primaryMuscle: String): ExerciseKind = when (primaryMuscle) {
        "triceps" -> ExerciseKind.PUSH
        else -> ExerciseKind.CURL_RAISE
    }
}
