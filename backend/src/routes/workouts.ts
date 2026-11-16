import { desc, eq } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";

import { createDb } from "../db/client";
import { syncOutbox, workoutSessions } from "../db/schema";
import type { Env } from "../env";
import type { AuthVariables } from "../middleware/auth";

type AppEnv = { Bindings: Env; Variables: AuthVariables };

const setSchema = z.object({
  exerciseId: z.string().min(1).max(80),
  setIndex: z.number().int().min(0).max(100),
  reps: z.number().int().min(0).max(1000),
  weightKg: z.number().min(0).max(2000),
  rpe: z.number().min(0).max(10).optional(),
  completedAt: z.number().int().optional(),
});

const sessionSchema = z.object({
  id: z.string().uuid(),
  squadId: z.string().min(1).max(80).nullable().optional(),
  title: z.string().trim().min(1).max(120),
  startedAt: z.number().int().positive(),
  completedAt: z.number().int().positive(),
  sets: z.array(setSchema).min(1).max(200),
});

export const workoutRoutes = new Hono<AppEnv>();

workoutRoutes.get("/sessions", async (c) => {
  const db = createDb(c.env.DB);
  const rows = await db
    .select({
      id: workoutSessions.id,
      title: workoutSessions.title,
      squadId: workoutSessions.squadId,
      startedAt: workoutSessions.startedAt,
      completedAt: workoutSessions.completedAt,
      totalVolumeKg: workoutSessions.totalVolumeKg,
      createdAt: workoutSessions.createdAt,
    })
    .from(workoutSessions)
    .where(eq(workoutSessions.userId, c.get("userId")))
    .orderBy(desc(workoutSessions.createdAt))
    .limit(50);

  return c.json({ sessions: rows });
});

/**
 * Offline-first clients persist a session locally, queue it in `sync_outbox`
 * and upload the whole session as one JSON payload. A single completed
 * session therefore costs one D1 write batch instead of one write per set.
 */
workoutRoutes.post("/sessions", async (c) => {
  const parsed = sessionSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "invalid_payload", issues: parsed.error.issues }, 400);
  }

  const session = parsed.data;
  const userId = c.get("userId");
  const now = Date.now();
  const totalVolumeKg = Math.round(
    session.sets.reduce((sum, set) => sum + set.reps * set.weightKg, 0),
  );
  const setsJson = JSON.stringify(session.sets);
  const db = createDb(c.env.DB);

  await db.batch([
    db
      .insert(workoutSessions)
      .values({
        id: session.id,
        userId,
        squadId: session.squadId ?? null,
        title: session.title,
        startedAt: session.startedAt,
        completedAt: session.completedAt,
        totalVolumeKg,
        setsJson,
        createdAt: now,
      })
      .onConflictDoNothing(),
    db.insert(syncOutbox).values({
      id: crypto.randomUUID(),
      userId,
      entityType: "workout_session",
      entityId: session.id,
      operation: "create",
      payloadJson: setsJson,
      createdAt: now,
    }),
  ]);

  return c.json({ id: session.id, totalVolumeKg, syncedAt: now }, 201);
});
