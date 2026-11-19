import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";
import { and, eq } from "drizzle-orm";

import { createDb } from "./db/client";
import { squadMembers } from "./db/schema";
import { SquadRoomDO } from "./durable-objects/SquadRoomDO";
import type { Env } from "./env";
import { requireAuth, type AuthVariables } from "./middleware/auth";
import { apiRoutes } from "./routes/api";
import { assetRoutes } from "./routes/assets";
import { squadRoutes } from "./routes/squads";
import { workoutRoutes } from "./routes/workouts";

const app = new Hono<{ Bindings: Env; Variables: AuthVariables }>();

app.use("*", logger());
app.use(
  "/v1/*",
  cors({
    origin: (origin) => origin,
    allowHeaders: ["Authorization", "Content-Type"],
    allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    maxAge: 600,
  }),
);
app.use(
  "/api/*",
  cors({
    origin: (origin) => origin,
    allowHeaders: ["Authorization", "Content-Type"],
    allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    maxAge: 600,
  }),
);

app.get("/health", (c) =>
  c.json({
    status: "ok",
    service: "hypertrophy-api",
    environment: c.env.ENVIRONMENT,
    now: new Date().toISOString(),
  }),
);

app.use("/v1/*", requireAuth);
app.use("/api/*", requireAuth);

app.get("/v1/me", (c) =>
  c.json({
    userId: c.get("userId"),
    sessionId: c.get("sessionId"),
  }),
);

app.route("/v1/workouts", workoutRoutes);
app.route("/v1/squads", squadRoutes);
app.route("/v1/assets", assetRoutes);
app.route("/api", apiRoutes);

// Realtime squad room upgrade. The handshake carries the Clerk JWT in the
// Authorization header; after verification the verified sub replaces any
// client-supplied userId so a socket can never impersonate another member.
app.get("/rooms/:squadId", requireAuth, async (c) => {
  const squadId = c.req.param("squadId");
  const userId = c.get("userId");

  const db = createDb(c.env.DB);
  const [member] = await db
    .select({ squadId: squadMembers.squadId })
    .from(squadMembers)
    .where(and(eq(squadMembers.squadId, squadId), eq(squadMembers.userId, userId)))
    .limit(1);
  if (!member) {
    return c.json({ error: "forbidden" }, 403);
  }

  const stub = c.env.SQUAD_ROOM.get(c.env.SQUAD_ROOM.idFromName(squadId));
  const url = new URL(c.req.url);
  url.searchParams.set("squadId", squadId);
  url.searchParams.set("userId", userId);
  return stub.fetch(new Request(url.toString(), { headers: c.req.raw.headers }));
});

app.notFound((c) => c.json({ error: "not_found" }, 404));

app.onError((error, c) => {
  console.error("unhandled_error", error);
  return c.json({ error: "internal_error" }, 500);
});

export { SquadRoomDO };
export default app;
