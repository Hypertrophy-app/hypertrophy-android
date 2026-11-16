import { Hono } from "hono";
import { cors } from "hono/cors";
import { logger } from "hono/logger";

import { SquadRoom } from "./durable/squad-room";
import type { Env } from "./env";
import { requireAuth, type AuthVariables } from "./middleware/auth";
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

app.get("/health", (c) =>
  c.json({
    status: "ok",
    service: "hypertrophy-api",
    environment: c.env.ENVIRONMENT,
    now: new Date().toISOString(),
  }),
);

app.use("/v1/*", requireAuth);

app.get("/v1/me", (c) =>
  c.json({
    userId: c.get("userId"),
    sessionId: c.get("sessionId"),
  }),
);

app.route("/v1/workouts", workoutRoutes);
app.route("/v1/squads", squadRoutes);
app.route("/v1/assets", assetRoutes);

app.notFound((c) => c.json({ error: "not_found" }, 404));

app.onError((error, c) => {
  console.error("unhandled_error", error);
  return c.json({ error: "internal_error" }, 500);
});

export { SquadRoom };
export default app;
