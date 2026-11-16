import {
  index,
  integer,
  primaryKey,
  sqliteTable,
  text,
} from "drizzle-orm/sqlite-core";

export const users = sqliteTable(
  "users",
  {
    id: text("id").primaryKey(),
    username: text("username"),
    displayName: text("display_name").notNull(),
    avatarUrl: text("avatar_url"),
    streakCount: integer("streak_count").notNull().default(0),
    lastWorkoutDate: integer("last_workout_date"),
    privacyStealthMode: integer("privacy_stealth_mode", { mode: "boolean" }).notNull().default(false),
    dashboardLayoutJson: text("dashboard_layout_json"),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [
    index("users_created_at_idx").on(table.createdAt),
    index("users_username_idx").on(table.username),
  ],
);

export const exercises = sqliteTable(
  "exercises",
  {
    id: text("id").primaryKey(),
    name: text("name").notNull(),
    primaryMuscle: text("primary_muscle").notNull(),
    secondaryMusclesJson: text("secondary_muscles_json"),
    equipment: text("equipment").notNull(),
    difficulty: text("difficulty", { enum: ["beginner", "intermediate", "advanced"] })
      .notNull()
      .default("intermediate"),
    instructionsJson: text("instructions_json"),
    glbAssetKey: text("glb_asset_key"),
    kinematicProfileType: text("kinematic_profile_type", {
      enum: ["SQUAT", "HINGE", "HORIZONTAL_PUSH", "VERTICAL_PUSH", "PULL", "ISOLATION"],
    }).notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [
    index("exercises_primary_muscle_idx").on(table.primaryMuscle),
    index("exercises_kinematic_idx").on(table.kinematicProfileType),
  ],
);

export const workoutTemplates = sqliteTable(
  "workout_templates",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id),
    title: text("title").notNull(),
    exercisesJson: text("exercises_json").notNull(),
    isPublic: integer("is_public", { mode: "boolean" }).notNull().default(false),
    createdAt: integer("created_at").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (table) => [
    index("workout_templates_user_updated_idx").on(table.userId, table.updatedAt),
    index("workout_templates_public_updated_idx").on(table.isPublic, table.updatedAt),
  ],
);

export const workoutSessions = sqliteTable(
  "workout_sessions",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id),
    squadId: text("squad_id"),
    title: text("title").notNull(),
    startedAt: integer("started_at").notNull(),
    endedAt: integer("ended_at").notNull(),
    totalVolumeKg: integer("total_volume_kg").notNull().default(0),
    avgFormScore: integer("avg_form_score"),
    aiRecapText: text("ai_recap_text"),
    setsJson: text("sets_json").notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [
    index("workout_sessions_user_created_idx").on(table.userId, table.createdAt),
    index("workout_sessions_squad_created_idx").on(table.squadId, table.createdAt),
    index("workout_sessions_created_at_idx").on(table.createdAt),
  ],
);

export const personalRecords = sqliteTable(
  "personal_records",
  {
    id: text("id").primaryKey(),
    userId: text("user_id")
      .notNull()
      .references(() => users.id),
    exerciseId: text("exercise_id")
      .notNull()
      .references(() => exercises.id),
    maxWeightKg: integer("max_weight_kg").notNull().default(0),
    maxReps: integer("max_reps").notNull().default(0),
    estimated1rm: integer("estimated_1rm").notNull().default(0),
    achievedAt: integer("achieved_at").notNull(),
  },
  (table) => [
    index("personal_records_user_exercise_idx").on(table.userId, table.exerciseId),
    index("personal_records_achieved_idx").on(table.achievedAt),
  ],
);

export const squads = sqliteTable(
  "squads",
  {
    id: text("id").primaryKey(),
    name: text("name").notNull(),
    inviteCode: text("invite_code").notNull().unique(),
    ownerId: text("owner_id")
      .notNull()
      .references(() => users.id),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [
    index("squads_owner_created_idx").on(table.ownerId, table.createdAt),
    index("squads_created_at_idx").on(table.createdAt),
  ],
);

export const squadMembers = sqliteTable(
  "squad_members",
  {
    squadId: text("squad_id")
      .notNull()
      .references(() => squads.id),
    userId: text("user_id")
      .notNull()
      .references(() => users.id),
    role: text("role", { enum: ["owner", "coach", "athlete"] })
      .notNull()
      .default("athlete"),
    joinedAt: integer("joined_at").notNull(),
  },
  (table) => [
    primaryKey({ columns: [table.squadId, table.userId] }),
    index("squad_members_squad_joined_idx").on(table.squadId, table.joinedAt),
    index("squad_members_user_joined_idx").on(table.userId, table.joinedAt),
  ],
);

export const challenges = sqliteTable(
  "challenges",
  {
    id: text("id").primaryKey(),
    squadId: text("squad_id")
      .notNull()
      .references(() => squads.id),
    title: text("title").notNull(),
    metricType: text("metric_type", {
      enum: ["total_volume", "sessions", "streak", "reps"],
    }).notNull(),
    targetValue: integer("target_value").notNull(),
    startsAt: integer("starts_at").notNull(),
    endsAt: integer("ends_at").notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [
    index("challenges_squad_ends_idx").on(table.squadId, table.endsAt),
  ],
);

export const userBadges = sqliteTable(
  "user_badges",
  {
    userId: text("user_id")
      .notNull()
      .references(() => users.id),
    badgeCode: text("badge_code").notNull(),
    unlockedAt: integer("unlocked_at").notNull(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.badgeCode] }),
    index("user_badges_unlocked_idx").on(table.unlockedAt),
  ],
);

export const syncOutbox = sqliteTable(
  "sync_outbox",
  {
    id: text("id").primaryKey(),
    userId: text("user_id").notNull(),
    entityType: text("entity_type").notNull(),
    entityId: text("entity_id").notNull(),
    operation: text("operation", { enum: ["create", "update", "delete"] }).notNull(),
    payloadJson: text("payload_json").notNull(),
    attempts: integer("attempts").notNull().default(0),
    createdAt: integer("created_at").notNull(),
    syncedAt: integer("synced_at"),
  },
  (table) => [
    index("sync_outbox_user_created_idx").on(table.userId, table.createdAt),
    index("sync_outbox_synced_created_idx").on(table.syncedAt, table.createdAt),
  ],
);

export type UserRow = typeof users.$inferSelect;
export type ExerciseRow = typeof exercises.$inferSelect;
export type WorkoutSessionRow = typeof workoutSessions.$inferSelect;
export type WorkoutSessionInsert = typeof workoutSessions.$inferInsert;
export type SquadRow = typeof squads.$inferSelect;
