import { and, desc, eq, inArray, notInArray, sql } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";

import { createDb } from "../db/client";
import {
  squadMembers,
  squads,
  syncOutbox,
  users,
  workoutSessions,
} from "../db/schema";
import type { Env } from "../env";
import { buildMilestoneCards } from "../milestones";
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

const sessionPayloadSchema = z.object({
  id: z.string().uuid(),
  squadId: z.string().min(1).max(80).nullable().optional(),
  title: z.string().trim().min(1).max(120),
  startedAt: z.number().int().positive(),
  completedAt: z.number().int().positive().optional(),
  endedAt: z.number().int().positive().optional(),
  sets: z.array(setSchema).min(1).max(200),
});

const outboxEntrySchema = z.object({
  id: z.string().min(1).max(120),
  entityType: z.string().min(1).max(80),
  entityId: z.string().min(1).max(120),
  operation: z.enum(["create", "update", "delete"]),
  payload: z.unknown(),
  createdAt: z.number().int().positive(),
});

const syncSchema = z.object({
  entries: z.array(outboxEntrySchema).min(1).max(100),
});

const createSquadSchema = z.object({
  name: z.string().trim().min(2).max(60),
});

const joinSquadSchema = z.object({
  inviteCode: z.string().trim().min(4).max(40),
});

export const apiRoutes = new Hono<AppEnv>();

function newInviteCode(): string {
  return crypto.randomUUID().replace(/-/g, "").slice(0, 6).toUpperCase();
}

async function ensureUser(db: ReturnType<typeof createDb>, userId: string): Promise<void> {
  const now = Date.now();
  await db
    .insert(users)
    .values({
      id: userId,
      displayName: userId,
      createdAt: now,
      updatedAt: now,
    })
    .onConflictDoNothing();
}

// Batch sync: one D1 batch per request.
apiRoutes.post("/sync", async (c) => {
  const parsed = syncSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "invalid_payload", issues: parsed.error.issues }, 400);
  }

  const db = createDb(c.env.DB);
  const userId = c.get("userId");
  const now = Date.now();
  await ensureUser(db, userId);

  const entries = parsed.data.entries;
  const synced: string[] = [];
  const failed: { id: string; error: string }[] = [];

  // Parse session payloads up front so squad membership and PR history can be
  // loaded with a fixed number of reads instead of one query per entry.
  const parsedSessions = new Map<string, z.infer<typeof sessionPayloadSchema>>();
  for (const entry of entries) {
    if (entry.entityType !== "workout_session") continue;
    const sessionParsed = sessionPayloadSchema.safeParse(entry.payload);
    if (!sessionParsed.success) {
      failed.push({ id: entry.id, error: "invalid_session_payload" });
      continue;
    }
    parsedSessions.set(entry.id, sessionParsed.data);
  }

  // A client-supplied squadId is only trusted when the authenticated user is
  // actually a member; otherwise the session is stored without a squad.
  const requestedSquadIds = [
    ...new Set(
      [...parsedSessions.values()]
        .map((session) => session.squadId)
        .filter((squadId): squadId is string => typeof squadId === "string" && squadId.length > 0),
    ),
  ];
  const memberSquadIds = new Set<string>();
  if (requestedSquadIds.length > 0) {
    const membershipRows = await db
      .select({ squadId: squadMembers.squadId })
      .from(squadMembers)
      .where(and(eq(squadMembers.userId, userId), inArray(squadMembers.squadId, requestedSquadIds)));
    for (const row of membershipRows) {
      memberSquadIds.add(row.squadId);
    }
  }

  const incomingSessionIds = [
    ...new Set([...parsedSessions.values()].map((session) => session.id)),
  ];

  const pendingCards: { squadId: string; userId: string; body: string }[] = [];
  if (memberSquadIds.size > 0) {
    // Milestone idempotency: a retried sync reuses the outbox entry id, so an
    // id that is already persisted means the cards were already emitted.
    const priorOutboxRows = await db
      .select({ id: syncOutbox.id })
      .from(syncOutbox)
      .where(inArray(syncOutbox.id, entries.map((entry) => entry.id)));
    const alreadySyncedEntryIds = new Set(priorOutboxRows.map((row) => row.id));

    // Defensive second guard: a session that already exists (for example it
    // was written by POST /v1/workouts/sessions) must not produce cards again.
    const existingSessionIds = new Set<string>();
    if (incomingSessionIds.length > 0) {
      const existingRows = await db
        .select({ id: workoutSessions.id })
        .from(workoutSessions)
        .where(inArray(workoutSessions.id, incomingSessionIds));
      for (const row of existingRows) {
        existingSessionIds.add(row.id);
      }
    }

    // Sync writes are batched, so the incoming rows are invisible to this
    // query. Excluding every incoming session id also stops an entry from
    // being compared against itself when one payload contains several
    // sessions. Bounded to recent history to keep the read cheap.
    const historyRows = await db
      .select({ setsJson: workoutSessions.setsJson })
      .from(workoutSessions)
      .where(
        incomingSessionIds.length > 0
          ? and(eq(workoutSessions.userId, userId), notInArray(workoutSessions.id, incomingSessionIds))
          : eq(workoutSessions.userId, userId),
      )
      .orderBy(desc(workoutSessions.createdAt))
      .limit(500);
    const priorSetsJson = historyRows.map((row) => row.setsJson);

    const [userRow] = await db
      .select({ displayName: users.displayName })
      .from(users)
      .where(eq(users.id, userId))
      .limit(1);
    const displayName = userRow?.displayName?.trim() || userId;

    for (const [entryId, session] of parsedSessions) {
      const squadId =
        session.squadId && memberSquadIds.has(session.squadId) ? session.squadId : null;
      if (!squadId) continue;
      if (alreadySyncedEntryIds.has(entryId) || existingSessionIds.has(session.id)) continue;

      const cards = buildMilestoneCards({
        displayName,
        sessionTitle: session.title,
        sets: session.sets,
        priorSetsJson,
      });
      for (const card of cards) {
        pendingCards.push({ squadId, userId: card.userId, body: card.body });
      }
    }
  }

  const statements: any[] = [];
  for (const entry of entries) {
    // Always record the outbox entry idempotently.
    statements.push(
      db
        .insert(syncOutbox)
        .values({
          id: entry.id,
          userId,
          entityType: entry.entityType,
          entityId: entry.entityId,
          operation: entry.operation,
          payloadJson: JSON.stringify(entry.payload ?? null),
          createdAt: entry.createdAt,
        })
        .onConflictDoNothing(),
    );

    if (entry.entityType === "workout_session") {
      const session = parsedSessions.get(entry.id);
      if (!session) continue; // Already reported in `failed`.

      const squadId =
        session.squadId && memberSquadIds.has(session.squadId) ? session.squadId : null;
      const endedAt = session.endedAt ?? session.completedAt ?? session.startedAt;
      const totalVolumeKg = Math.round(
        session.sets.reduce((sum, set) => sum + set.reps * set.weightKg, 0),
      );
      statements.push(
        db
          .insert(workoutSessions)
          .values({
            id: session.id,
            userId,
            squadId,
            title: session.title,
            startedAt: session.startedAt,
            endedAt,
            totalVolumeKg,
            setsJson: JSON.stringify(session.sets),
            createdAt: now,
          })
          .onConflictDoUpdate({
            target: workoutSessions.id,
            set: {
              title: session.title,
              startedAt: session.startedAt,
              endedAt,
              totalVolumeKg,
              setsJson: JSON.stringify(session.sets),
            },
          }),
      );
    }

    synced.push(entry.id);
  }

  if (statements.length > 0) {
    await db.batch(statements as any);
  }

  // Milestone cards are best-effort: a Durable Object hiccup must never fail
  // an otherwise durable sync, so errors are logged and swallowed.
  const milestones: string[] = [];
  for (const card of pendingCards) {
    try {
      const stub = c.env.SQUAD_ROOM.get(c.env.SQUAD_ROOM.idFromName(card.squadId));
      const response = await stub.fetch("https://squad-room.internal/messages", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          squadId: card.squadId,
          userId: card.userId,
          body: card.body,
        }),
      });
      if (!response.ok) {
        console.error("milestone_post_failed", card.squadId, response.status);
        continue;
      }
      milestones.push(card.body);
    } catch (error) {
      console.error("milestone_post_failed", card.squadId, String(error));
    }
  }

  return c.json({ synced, failed, milestones, syncedAt: now });
});

// Squad management
apiRoutes.get("/squads", async (c) => {
  const db = createDb(c.env.DB);
  const userId = c.get("userId");

  const rows = await db
    .select({
      id: squads.id,
      name: squads.name,
      ownerId: squads.ownerId,
      createdAt: squads.createdAt,
      inviteCode: squads.inviteCode,
      role: squadMembers.role,
    })
    .from(squadMembers)
    .innerJoin(squads, eq(squads.id, squadMembers.squadId))
    .where(eq(squadMembers.userId, userId))
    .orderBy(desc(squads.createdAt))
    .limit(50);

  return c.json({ squads: rows });
});

apiRoutes.post("/squads", async (c) => {
  const parsed = createSquadSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "invalid_payload", issues: parsed.error.issues }, 400);
  }

  const db = createDb(c.env.DB);
  const userId = c.get("userId");
  const now = Date.now();
  await ensureUser(db, userId);

  const id = crypto.randomUUID();
  let inviteCode = newInviteCode();
  // Best-effort uniqueness; collisions are astronomically unlikely.
  for (let i = 0; i < 3; i++) {
    const existing = await db
      .select({ id: squads.id })
      .from(squads)
      .where(eq(squads.inviteCode, inviteCode))
      .limit(1);
    if (existing.length === 0) break;
    inviteCode = newInviteCode();
  }

  await db.batch([
    db.insert(squads).values({
      id,
      name: parsed.data.name,
      inviteCode,
      ownerId: userId,
      createdAt: now,
    }),
    db.insert(squadMembers).values({
      squadId: id,
      userId,
      role: "owner",
      joinedAt: now,
    }),
  ]);

  return c.json({ id, name: parsed.data.name, inviteCode, ownerId: userId, createdAt: now }, 201);
});

apiRoutes.post("/squads/join", async (c) => {
  const parsed = joinSquadSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "invalid_payload", issues: parsed.error.issues }, 400);
  }

  const db = createDb(c.env.DB);
  const userId = c.get("userId");
  const now = Date.now();
  await ensureUser(db, userId);

  const squad = await db
    .select({ id: squads.id, name: squads.name })
    .from(squads)
    .where(eq(squads.inviteCode, parsed.data.inviteCode))
    .limit(1);

  if (squad.length === 0) {
    return c.json({ error: "invalid_invite_code" }, 404);
  }

  const foundSquad = squad[0];
  if (!foundSquad) {
    return c.json({ error: "invalid_invite_code" }, 404);
  }

  const squadId = foundSquad.id;
  await db
    .insert(squadMembers)
    .values({ squadId, userId, role: "athlete", joinedAt: now })
    .onConflictDoNothing();

  return c.json({ squadId, name: foundSquad.name, joinedAt: now }, 200);
});

apiRoutes.get("/squads/:id/leaderboard", async (c) => {
  const squadId = c.req.param("id");
  const db = createDb(c.env.DB);
  const userId = c.get("userId");

  // Membership check.
  const membership = await db
    .select({ role: squadMembers.role })
    .from(squadMembers)
    .where(and(eq(squadMembers.squadId, squadId), eq(squadMembers.userId, userId)))
    .limit(1);
  if (membership.length === 0) {
    return c.json({ error: "not_a_squad_member" }, 403);
  }

  const rows = await db
    .select({
      userId: workoutSessions.userId,
      totalVolumeKg: sql<number>`COALESCE(SUM(${workoutSessions.totalVolumeKg}), 0)`,
      sessionCount: sql<number>`COUNT(${workoutSessions.id})`,
      lastSessionAt: sql<number>`MAX(${workoutSessions.createdAt})`,
    })
    .from(workoutSessions)
    .where(eq(workoutSessions.squadId, squadId))
    .groupBy(workoutSessions.userId)
    .orderBy(desc(sql`SUM(${workoutSessions.totalVolumeKg})`))
    .limit(50);

  // Enrich with display names.
  const userIds = rows.map((r) => r.userId);
  const userRows =
    userIds.length > 0
      ? await db
          .select({ id: users.id, displayName: users.displayName, avatarUrl: users.avatarUrl })
          .from(users)
          .where(sql`${users.id} IN (${sql.join(userIds.map((id) => sql`${id}`), sql`, `)})`)
      : [];
  const userMap = new Map(userRows.map((u) => [u.id, u]));

  const leaderboard = rows.map((r, index) => ({
    rank: index + 1,
    userId: r.userId,
    displayName: userMap.get(r.userId)?.displayName ?? r.userId,
    avatarUrl: userMap.get(r.userId)?.avatarUrl ?? null,
    totalVolumeKg: Number(r.totalVolumeKg ?? 0),
    sessionCount: Number(r.sessionCount ?? 0),
    lastSessionAt: r.lastSessionAt ?? null,
  }));

  return c.json({ squadId, leaderboard });
});

// R2 asset URL resolver.
apiRoutes.get("/assets/resolve", async (c) => {
  const key = c.req.query("key");
  if (!key || key.includes("..")) {
    return c.json({ error: "invalid_key" }, 400);
  }

  const object = await c.env.ASSETS_BUCKET.head(key);
  if (!object) {
    return c.json({ error: "not_found" }, 404);
  }
  if (object.customMetadata?.ownerId && object.customMetadata.ownerId !== c.get("userId")) {
    return c.json({ error: "forbidden" }, 403);
  }

  return c.json({
    key,
    url: `/v1/assets/${key}`,
    size: object.size,
    contentType: object.httpMetadata?.contentType ?? "application/octet-stream",
  });
});
