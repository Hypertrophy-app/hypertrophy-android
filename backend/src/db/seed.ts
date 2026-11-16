import { createDb } from "./client";
import { exercises, userBadges } from "./schema";

export interface SeedExercise {
  id: string;
  name: string;
  primaryMuscle: string;
  secondaryMuscles: string[];
  equipment: string;
  difficulty: "beginner" | "intermediate" | "advanced";
  instructions: string[];
  kinematicProfileType:
    | "SQUAT"
    | "HINGE"
    | "HORIZONTAL_PUSH"
    | "VERTICAL_PUSH"
    | "PULL"
    | "ISOLATION";
}

export interface SeedBadge {
  code: string;
  name: string;
  description: string;
}

export const SEED_EXERCISES: SeedExercise[] = [
  { id: "ex_back_squat", name: "Back Squat", primaryMuscle: "quadriceps", secondaryMuscles: ["glutes", "hamstrings", "core"], equipment: "barbell", difficulty: "intermediate", instructions: ["Brace core", "Sit back and down", "Drive through heels"], kinematicProfileType: "SQUAT" },
  { id: "ex_front_squat", name: "Front Squat", primaryMuscle: "quadriceps", secondaryMuscles: ["glutes", "core"], equipment: "barbell", difficulty: "advanced", instructions: ["Elbows high", "Upright torso", "Full depth"], kinematicProfileType: "SQUAT" },
  { id: "ex_goblet_squat", name: "Goblet Squat", primaryMuscle: "quadriceps", secondaryMuscles: ["glutes", "core"], equipment: "dumbbell", difficulty: "beginner", instructions: ["Hold weight at chest", "Sit between hips", "Knees track toes"], kinematicProfileType: "SQUAT" },
  { id: "ex_leg_press", name: "Leg Press", primaryMuscle: "quadriceps", secondaryMuscles: ["glutes", "hamstrings"], equipment: "machine", difficulty: "beginner", instructions: ["Feet shoulder width", "Control descent", "Do not lock knees"], kinematicProfileType: "SQUAT" },
  { id: "ex_bulgarian_split_squat", name: "Bulgarian Split Squat", primaryMuscle: "quadriceps", secondaryMuscles: ["glutes", "hamstrings"], equipment: "dumbbell", difficulty: "intermediate", instructions: ["Rear foot elevated", "Vertical shin", "Drive up"], kinematicProfileType: "SQUAT" },
  { id: "ex_deadlift", name: "Conventional Deadlift", primaryMuscle: "hamstrings", secondaryMuscles: ["glutes", "lower_back", "traps"], equipment: "barbell", difficulty: "intermediate", instructions: ["Hinge at hips", "Bar close to shins", "Lock out hips"], kinematicProfileType: "HINGE" },
  { id: "ex_rdl", name: "Romanian Deadlift", primaryMuscle: "hamstrings", secondaryMuscles: ["glutes", "lower_back"], equipment: "barbell", difficulty: "intermediate", instructions: ["Soft knees", "Hinge until stretch", "Return with hips"], kinematicProfileType: "HINGE" },
  { id: "ex_hip_thrust", name: "Hip Thrust", primaryMuscle: "glutes", secondaryMuscles: ["hamstrings", "quadriceps"], equipment: "barbell", difficulty: "intermediate", instructions: ["Shoulders on bench", "Drive hips up", "Squeeze glutes"], kinematicProfileType: "HINGE" },
  { id: "ex_kettlebell_swing", name: "Kettlebell Swing", primaryMuscle: "glutes", secondaryMuscles: ["hamstrings", "core"], equipment: "kettlebell", difficulty: "beginner", instructions: ["Hike bell back", "Explosive hip snap", "Float to chest"], kinematicProfileType: "HINGE" },
  { id: "ex_good_morning", name: "Good Morning", primaryMuscle: "hamstrings", secondaryMuscles: ["glutes", "lower_back"], equipment: "barbell", difficulty: "advanced", instructions: ["Bar on traps", "Hinge forward", "Keep back neutral"], kinematicProfileType: "HINGE" },
  { id: "ex_bench_press", name: "Barbell Bench Press", primaryMuscle: "chest", secondaryMuscles: ["triceps", "front_delts"], equipment: "barbell", difficulty: "intermediate", instructions: ["Retract scapulae", "Lower to chest", "Press up"], kinematicProfileType: "HORIZONTAL_PUSH" },
  { id: "ex_dumbbell_bench", name: "Dumbbell Bench Press", primaryMuscle: "chest", secondaryMuscles: ["triceps", "front_delts"], equipment: "dumbbell", difficulty: "beginner", instructions: ["Neutral wrists", "Full range", "Control descent"], kinematicProfileType: "HORIZONTAL_PUSH" },
  { id: "ex_push_up", name: "Push-Up", primaryMuscle: "chest", secondaryMuscles: ["triceps", "core"], equipment: "bodyweight", difficulty: "beginner", instructions: ["Plank position", "Elbows 45 deg", "Full lockout"], kinematicProfileType: "HORIZONTAL_PUSH" },
  { id: "ex_incline_bench", name: "Incline Bench Press", primaryMuscle: "upper_chest", secondaryMuscles: ["triceps", "front_delts"], equipment: "barbell", difficulty: "intermediate", instructions: ["30-45 degree bench", "Lower to upper chest", "Press"], kinematicProfileType: "HORIZONTAL_PUSH" },
  { id: "ex_dips", name: "Chest Dips", primaryMuscle: "chest", secondaryMuscles: ["triceps", "front_delts"], equipment: "bodyweight", difficulty: "intermediate", instructions: ["Lean forward", "Lower until stretch", "Press up"], kinematicProfileType: "HORIZONTAL_PUSH" },
  { id: "ex_overhead_press", name: "Overhead Press", primaryMuscle: "front_delts", secondaryMuscles: ["triceps", "upper_chest"], equipment: "barbell", difficulty: "intermediate", instructions: ["Brace core", "Press overhead", "Lock out"], kinematicProfileType: "VERTICAL_PUSH" },
  { id: "ex_dumbbell_shoulder_press", name: "Dumbbell Shoulder Press", primaryMuscle: "front_delts", secondaryMuscles: ["triceps", "side_delts"], equipment: "dumbbell", difficulty: "beginner", instructions: ["Seated or standing", "Press up", "Control down"], kinematicProfileType: "VERTICAL_PUSH" },
  { id: "ex_lateral_raise", name: "Lateral Raise", primaryMuscle: "side_delts", secondaryMuscles: [], equipment: "dumbbell", difficulty: "beginner", instructions: ["Slight elbow bend", "Raise to shoulder", "Lower slowly"], kinematicProfileType: "ISOLATION" },
  { id: "ex_pike_push_up", name: "Pike Push-Up", primaryMuscle: "front_delts", secondaryMuscles: ["triceps"], equipment: "bodyweight", difficulty: "intermediate", instructions: ["Hips high", "Head toward floor", "Press up"], kinematicProfileType: "VERTICAL_PUSH" },
  { id: "ex_handstand_push_up", name: "Handstand Push-Up", primaryMuscle: "front_delts", secondaryMuscles: ["triceps", "core"], equipment: "bodyweight", difficulty: "advanced", instructions: ["Kick to wall", "Lower head", "Press"], kinematicProfileType: "VERTICAL_PUSH" },
  { id: "ex_pull_up", name: "Pull-Up", primaryMuscle: "lats", secondaryMuscles: ["biceps", "rear_delts"], equipment: "bodyweight", difficulty: "intermediate", instructions: ["Dead hang", "Pull chest to bar", "Lower controlled"], kinematicProfileType: "PULL" },
  { id: "ex_chin_up", name: "Chin-Up", primaryMuscle: "lats", secondaryMuscles: ["biceps"], equipment: "bodyweight", difficulty: "intermediate", instructions: ["Supinated grip", "Pull up", "Full hang"], kinematicProfileType: "PULL" },
  { id: "ex_barbell_row", name: "Barbell Row", primaryMuscle: "lats", secondaryMuscles: ["rhomboids", "biceps"], equipment: "barbell", difficulty: "intermediate", instructions: ["Hinge forward", "Pull to torso", "Squeeze back"], kinematicProfileType: "PULL" },
  { id: "ex_dumbbell_row", name: "Dumbbell Row", primaryMuscle: "lats", secondaryMuscles: ["rhomboids", "biceps"], equipment: "dumbbell", difficulty: "beginner", instructions: ["Support on bench", "Pull elbow back", "Lower"], kinematicProfileType: "PULL" },
  { id: "ex_lat_pulldown", name: "Lat Pulldown", primaryMuscle: "lats", secondaryMuscles: ["biceps"], equipment: "machine", difficulty: "beginner", instructions: ["Grip wide", "Pull to chest", "Control up"], kinematicProfileType: "PULL" },
  { id: "ex_bicep_curl", name: "Bicep Curl", primaryMuscle: "biceps", secondaryMuscles: ["forearms"], equipment: "dumbbell", difficulty: "beginner", instructions: ["Elbows fixed", "Curl up", "Lower slowly"], kinematicProfileType: "ISOLATION" },
  { id: "ex_tricep_pushdown", name: "Tricep Pushdown", primaryMuscle: "triceps", secondaryMuscles: [], equipment: "cable", difficulty: "beginner", instructions: ["Elbows at sides", "Push down", "Squeeze triceps"], kinematicProfileType: "ISOLATION" },
  { id: "ex_lateral_pulldown", name: "Face Pull", primaryMuscle: "rear_delts", secondaryMuscles: ["rhomboids", "rotator_cuff"], equipment: "cable", difficulty: "beginner", instructions: ["Pull to face", "External rotate", "Squeeze"], kinematicProfileType: "PULL" },
  { id: "ex_plank", name: "Plank", primaryMuscle: "core", secondaryMuscles: ["shoulders"], equipment: "bodyweight", difficulty: "beginner", instructions: ["Straight line", "Brace core", "Breathe"], kinematicProfileType: "ISOLATION" },
  { id: "ex_hanging_leg_raise", name: "Hanging Leg Raise", primaryMuscle: "core", secondaryMuscles: ["hip_flexors"], equipment: "bodyweight", difficulty: "intermediate", instructions: ["Hang from bar", "Raise legs", "Control down"], kinematicProfileType: "ISOLATION" },
];

export const SEED_BADGES: SeedBadge[] = [
  { code: "first_workout", name: "First Rep", description: "Complete your first workout session." },
  { code: "streak_3", name: "On a Roll", description: "Train 3 days in a row." },
  { code: "streak_7", name: "Iron Week", description: "Train 7 days in a row." },
  { code: "streak_30", name: "Iron Month", description: "Train 30 days in a row." },
  { code: "volume_1000", name: "Ton Club", description: "Lift 1,000 kg in a single session." },
  { code: "volume_5000", name: "Five Ton", description: "Lift 5,000 kg in a single session." },
  { code: "pr_first", name: "Personal Best", description: "Set your first personal record." },
  { code: "pr_10", name: "PR Machine", description: "Set 10 personal records." },
  { code: "squad_join", name: "Squad Up", description: "Join a squad." },
  { code: "squad_create", name: "Captain", description: "Create a squad." },
  { code: "challenge_win", name: "Champion", description: "Win a squad challenge." },
  { code: "form_90", name: "Form Check", description: "Average form score above 90%." },
  { code: "exercises_10", name: "Explorer", description: "Try 10 different exercises." },
  { code: "early_bird", name: "Early Bird", description: "Complete a workout before 7am." },
  { code: "night_owl", name: "Night Owl", description: "Complete a workout after 9pm." },
];

export function seedSql(): string {
  const now = Date.now();
  const lines: string[] = [];
  lines.push("PRAGMA foreign_keys = ON;");
  for (const ex of SEED_EXERCISES) {
    lines.push(
      `INSERT INTO exercises (id, name, primary_muscle, secondary_muscles_json, equipment, difficulty, instructions_json, glb_asset_key, kinematic_profile_type, created_at) VALUES (` +
        `'${ex.id}', '${escape(ex.name)}', '${escape(ex.primaryMuscle)}', '${escape(JSON.stringify(ex.secondaryMuscles))}', '${escape(ex.equipment)}', '${ex.difficulty}', '${escape(JSON.stringify(ex.instructions))}', NULL, '${ex.kinematicProfileType}', ${now}` +
        `) ON CONFLICT(id) DO NOTHING;`,
    );
  }
  return lines.join("\n");
}

function escape(value: string): string {
  return value.replace(/'/g, "''");
}

export async function seedDatabase(d1: D1Database): Promise<void> {
  const db = createDb(d1);
  const now = Date.now();

  await db.batch(
    SEED_EXERCISES.map((ex) =>
      db
        .insert(exercises)
        .values({
          id: ex.id,
          name: ex.name,
          primaryMuscle: ex.primaryMuscle,
          secondaryMusclesJson: JSON.stringify(ex.secondaryMuscles),
          equipment: ex.equipment,
          difficulty: ex.difficulty,
          instructionsJson: JSON.stringify(ex.instructions),
          glbAssetKey: null,
          kinematicProfileType: ex.kinematicProfileType,
          createdAt: now,
        })
        .onConflictDoNothing(),
    ) as any,
  );

  // Badges are stored as user_badges only when earned; the catalog is
  // documented here for clients. No rows are seeded globally.
  void userBadges;
  void now;
}
