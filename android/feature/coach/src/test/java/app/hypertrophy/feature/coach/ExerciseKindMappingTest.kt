package app.hypertrophy.feature.coach

import app.hypertrophy.core.kinematics.ExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseKindMappingTest {
    @Test
    fun primaryMovementProfilesMapToAnalyzerKinds() {
        assertEquals(ExerciseKind.SQUAT, ExerciseKindMapping.forProfile("SQUAT", "quadriceps"))
        assertEquals(ExerciseKind.HINGE, ExerciseKindMapping.forProfile("hinge", "hamstrings"))
        assertEquals(ExerciseKind.PUSH, ExerciseKindMapping.forProfile("HORIZONTAL_PUSH", "chest"))
        assertEquals(ExerciseKind.PUSH, ExerciseKindMapping.forProfile("vertical_push", "shoulders"))
        assertEquals(ExerciseKind.PULL, ExerciseKindMapping.forProfile("PULL", "back"))
    }

    @Test
    fun isolationProfileUsesPrimaryMuscleForPushOrCurl() {
        assertEquals(ExerciseKind.PUSH, ExerciseKindMapping.forProfile("ISOLATION", "triceps"))
        assertEquals(ExerciseKind.PUSH, ExerciseKindMapping.forProfile("isolation", "Triceps"))
        assertEquals(ExerciseKind.CURL_RAISE, ExerciseKindMapping.forProfile("ISOLATION", "biceps"))
        assertEquals(
            ExerciseKind.CURL_RAISE,
            ExerciseKindMapping.forProfile("ISOLATION", "deltoids"),
        )
    }

    @Test
    fun unknownProfileFallsBackToTheSafeSquatAnalyzer() {
        assertEquals(ExerciseKind.SQUAT, ExerciseKindMapping.forProfile("CARRY", "forearms"))
        assertEquals(ExerciseKind.SQUAT, ExerciseKindMapping.forProfile("", "quadriceps"))
    }
}
