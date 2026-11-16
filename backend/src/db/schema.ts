import { index, integer, primaryKey, sqliteTable, text } from "drizzle-orm/sqlite-core";

export const users = sqliteTable(
  "users",
  {
    id: text("id").primaryKey(),
    displayName: text("display_name").notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [index("users_created_at_idx").on(table.createdAt)],
);

export const squads = sqliteTable(
  "squads",
  {
    id: text("id").primaryKey(),
    name: text("name").notNull(),
    ownerId: text("owner_id").notNull(),
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
    squadId: text("squad_id").notNull(),
    userId: text("user_id").notNull(),
    role: text("role", { enum: ["owner", "coach", "athlete"] }).notNull().default("athlete"),
    joinedAt: integer("joined_at").notNull(),
  },
  (table) => [
    primaryKey({ columns: [table.squadId, table.userId] }),
    index("squad_members_squad_joined_idx").on(table.squadId, table.joinedAt),
    index("squad_members_user_joined_idx").on(table.userId, table.joinedAt),
  ],
);

/**
 * One row per completed training session. Individual sets live in the
 * batched `setsJson` payload so a full session upload stays a single write
 * against the D1 free tier.
 */
export const workoutSessions = sqliteTable(
  "workout_sessions",
  {
    id: text("id").primaryKey(),
    userId: text("user_id").notNull(),
    squadId: text("squad_id"),
    title: text("title").notNull(),
    startedAt: integer("started_at").notNull(),
    completedAt: integer("completed_at").notNull(),
    totalVolumeKg: integer("total_volume_kg").notNull().default(0),
    setsJson: text("sets_json").notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (table) => [
    index("workout_sessions_user_created_idx").on(table.userId, table.createdAt),
    index("workout_sessions_squad_created_idx").on(table.squadId, table.createdAt),
    index("workout_sessions_created_at_idx").on(table.createdAt),
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

export type WorkoutSessionRow = typeof workoutSessions.$inferSelect;
export type WorkoutSessionInsert = typeof workoutSessions.$inferInsert;
