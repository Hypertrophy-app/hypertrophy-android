package app.hypertrophy.core.data.catalog

import app.hypertrophy.core.data.local.ExerciseEntity

/**
 * Offline bootstrap catalog. Mirrors the D1 seed so the exercise library and
 * substitution engine work before the first successful sync.
 */
object ExerciseCatalog {
    val exercises: List<ExerciseEntity> = listOf(
        ex("ex_back_squat", "Back Squat", "quadriceps", "barbell", "SQUAT", listOf("glutes", "hamstrings")),
        ex("ex_front_squat", "Front Squat", "quadriceps", "barbell", "SQUAT", listOf("glutes", "core")),
        ex("ex_goblet_squat", "Goblet Squat", "quadriceps", "dumbbell", "SQUAT", listOf("glutes")),
        ex("ex_leg_press", "Leg Press", "quadriceps", "machine", "SQUAT", listOf("glutes")),
        ex("ex_bulgarian_split_squat", "Bulgarian Split Squat", "quadriceps", "dumbbell", "SQUAT", listOf("glutes")),
        ex("ex_deadlift", "Conventional Deadlift", "hamstrings", "barbell", "HINGE", listOf("glutes", "lower_back")),
        ex("ex_rdl", "Romanian Deadlift", "hamstrings", "barbell", "HINGE", listOf("glutes")),
        ex("ex_hip_thrust", "Hip Thrust", "glutes", "barbell", "HINGE", listOf("hamstrings")),
        ex("ex_kettlebell_swing", "Kettlebell Swing", "glutes", "kettlebell", "HINGE", listOf("hamstrings")),
        ex("ex_good_morning", "Good Morning", "hamstrings", "barbell", "HINGE", listOf("glutes")),
        ex("ex_bench_press", "Barbell Bench Press", "chest", "barbell", "HORIZONTAL_PUSH", listOf("triceps")),
        ex("ex_dumbbell_bench", "Dumbbell Bench Press", "chest", "dumbbell", "HORIZONTAL_PUSH", listOf("triceps")),
        ex("ex_push_up", "Push-Up", "chest", "bodyweight", "HORIZONTAL_PUSH", listOf("triceps", "core")),
        ex("ex_incline_bench", "Incline Bench Press", "chest", "barbell", "HORIZONTAL_PUSH", listOf("front_delts")),
        ex("ex_dips", "Chest Dips", "chest", "bodyweight", "HORIZONTAL_PUSH", listOf("triceps")),
        ex("ex_overhead_press", "Overhead Press", "front_delts", "barbell", "VERTICAL_PUSH", listOf("triceps")),
        ex("ex_dumbbell_shoulder_press", "Dumbbell Shoulder Press", "front_delts", "dumbbell", "VERTICAL_PUSH", listOf("triceps")),
        ex("ex_lateral_raise", "Lateral Raise", "side_delts", "dumbbell", "ISOLATION", emptyList()),
        ex("ex_pike_push_up", "Pike Push-Up", "front_delts", "bodyweight", "VERTICAL_PUSH", listOf("triceps")),
        ex("ex_handstand_push_up", "Handstand Push-Up", "front_delts", "bodyweight", "VERTICAL_PUSH", listOf("triceps")),
        ex("ex_pull_up", "Pull-Up", "lats", "bodyweight", "PULL", listOf("biceps")),
        ex("ex_chin_up", "Chin-Up", "lats", "bodyweight", "PULL", listOf("biceps")),
        ex("ex_barbell_row", "Barbell Row", "lats", "barbell", "PULL", listOf("rhomboids")),
        ex("ex_dumbbell_row", "Dumbbell Row", "lats", "dumbbell", "PULL", listOf("rhomboids")),
        ex("ex_lat_pulldown", "Lat Pulldown", "lats", "machine", "PULL", listOf("biceps")),
        ex("ex_bicep_curl", "Bicep Curl", "biceps", "dumbbell", "ISOLATION", emptyList()),
        ex("ex_tricep_pushdown", "Tricep Pushdown", "triceps", "cable", "ISOLATION", emptyList()),
        ex("ex_face_pull", "Face Pull", "rear_delts", "cable", "PULL", listOf("rhomboids")),
        ex("ex_plank", "Plank", "core", "bodyweight", "ISOLATION", emptyList()),
        ex("ex_hanging_leg_raise", "Hanging Leg Raise", "core", "bodyweight", "ISOLATION", listOf("hip_flexors")),
    )

    val muscleGroups: List<String> = exercises.map { it.primaryMuscle }.distinct().sorted()
    val equipmentTypes: List<String> = exercises.map { it.equipment }.distinct().sorted()

    private fun ex(
        id: String,
        name: String,
        primaryMuscle: String,
        equipment: String,
        kinematic: String,
        secondary: List<String>,
    ): ExerciseEntity = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscle = primaryMuscle,
        secondaryMusclesJson = secondary.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" },
        equipment = equipment,
        difficulty = "intermediate",
        instructionsJson = "[]",
        glbAssetKey = "models/$id.glb",
        kinematicProfileType = kinematic,
        createdAt = 0L,
    )
}
