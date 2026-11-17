package app.hypertrophy.feature.workout.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentSubstitutionEngineTest {
    private val target = ExerciseOption(
        id = "ex_barbell_row",
        name = "Barbell Row",
        primaryMuscle = "lats",
        kinematicProfileType = "PULL",
        equipment = "barbell",
    )

    @Test
    fun matchesSameMuscleAndKinematicProfile() {
        val candidates = listOf(
            target,
            option("ex_lat_pulldown", "Lat Pulldown", "lats", "PULL", "machine"),
            option("ex_pull_up", "Pull-Up", "lats", "PULL", "bodyweight"),
            option("ex_bicep_curl", "Bicep Curl", "biceps", "ISOLATION", "dumbbell"),
            option("ex_rdl", "Romanian Deadlift", "hamstrings", "HINGE", "barbell"),
        )

        val result = EquipmentSubstitutionEngine.findSubstitutes(
            target = target,
            candidates = candidates,
            availableEquipment = setOf("machine"),
        )

        assertEquals(listOf("ex_lat_pulldown", "ex_pull_up"), result.map { it.id })
    }

    @Test
    fun promotesAvailableEquipmentFirst() {
        val candidates = listOf(
            option("ex_pull_up", "Pull-Up", "lats", "PULL", "bodyweight"),
            option("ex_lat_pulldown", "Lat Pulldown", "lats", "PULL", "machine"),
        )

        val result = EquipmentSubstitutionEngine.findSubstitutes(
            target = target,
            candidates = candidates,
            availableEquipment = setOf("machine"),
        )

        assertEquals("ex_lat_pulldown", result.first().id)
    }

    @Test
    fun neverReturnsTargetItself() {
        val result = EquipmentSubstitutionEngine.findSubstitutes(
            target = target,
            candidates = listOf(target),
            availableEquipment = setOf("barbell"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun respectsLimit() {
        val candidates = (1..10).map {
            option("ex_$it", "Variant $it", "lats", "PULL", "machine")
        }

        val result = EquipmentSubstitutionEngine.findSubstitutes(
            target = target,
            candidates = candidates,
            availableEquipment = setOf("machine"),
            limit = 3,
        )

        assertEquals(3, result.size)
    }

    private fun option(
        id: String,
        name: String,
        muscle: String,
        kinematic: String,
        equipment: String,
    ) = ExerciseOption(id, name, muscle, kinematic, equipment)
}
