import { and, desc, eq } from "drizzle-orm";
import { Hono } from "hono";
import { z } from "zod";

import { createDb } from "../db/client";
import { squadMembers, squads } from "../db/schema";
import type { Env } from "../env";
import type { AuthVariables } from "../middleware/auth";

type AppEnv = { Bindings: Env; Variables: AuthVariables };

const createSquadSchema = z.object({
  name: z.string().trim().min(2).max(60),
});

const messageSchema = z.object({
  body: z.string().trim().min(1).max(500),
});

export const squadRoutes = new Hono<AppEnv>();

squadRoutes.get("/", async (c) => {
  const db = createDb(c.env.DB);
  const rows = await db
    .select({
      id: squads.id,
      name: squads.name,
      ownerId: squads.ownerId,
      createdAt: squads.createdAt,
      role: squadMembers.role,
    })
    .from(squadMembers)
    .innerJoin(squads, eq(squads.id, squadMembers.squadId))
    .where(eq(squadMembers.userId, c.get("userId")))
    .orderBy(desc(squads.createdAt))
    .limit(50);

  return c.json({ squads: rows });
});

squadRoutes.post("/", async (c) => {
  const parsed = createSquadSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "invalid_payload", issues: parsed.error.issues }, 400);
  }

  const db = createDb(c.env.DB);
  const userId = c.get("userId");
  const id = crypto.randomUUID();
  const now = Date.now();

  await db.batch([
    db.insert(squads).values({ id, name: parsed.data.name, ownerId: userId, createdAt: now }),
    db
      .insert(squadMembers)
      .values({ squadId: id, userId, role: "owner", joinedAt: now }),
  ]);

  return c.json({ id, name: parsed.data.name, ownerId: userId, createdAt: now }, 201);
});

squadRoutes.post("/:squadId/messages", async (c) => {
  const squadId = c.req.param("squadId");
  const parsed = messageSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "invalid_payload", issues: parsed.error.issues }, 400);
  }

  const db = createDb(c.env.DB);
  const membership = await db
    .select({ role: squadMembers.role })
    .from(squadMembers)
    .where(and(eq(squadMembers.squadId, squadId), eq(squadMembers.userId, c.get("userId"))))
    .limit(1);

  if (membership.length === 0) {
    return c.json({ error: "not_a_squad_member" }, 403);
  }

  const stub = c.env.SQUAD_ROOM.get(c.env.SQUAD_ROOM.idFromName(squadId));
  return stub.fetch("https://squad-room.internal/messages", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      squadId,
      userId: c.get("userId"),
      body: parsed.data.body,
    }),
  });
});

squadRoutes.get("/:squadId/messages", async (c) => {
  const squadId = c.req.param("squadId");
  const stub = c.env.SQUAD_ROOM.get(c.env.SQUAD_ROOM.idFromName(squadId));
  return stub.fetch("https://squad-room.internal/messages", { method: "GET" });
});
