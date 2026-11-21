import { Hono } from "hono";
import { describe, expect, it } from "vitest";

import type { Env } from "../env";
import { requireAuth, type AuthVariables } from "../middleware/auth";

function createApp() {
  const app = new Hono<{ Bindings: Env; Variables: AuthVariables }>();
  app.use("/v1/*", requireAuth);
  app.get("/v1/me", (c) => c.json({ userId: c.get("userId") }));
  return app;
}

const env = {} as Env;

describe("requireAuth", () => {
  it("rejects a missing Authorization header", async () => {
    const response = await createApp().request("/v1/me", {}, env);

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: "missing_bearer_token" });
  });

  it("rejects an empty bearer token", async () => {
    const response = await createApp().request(
      "/v1/me",
      { headers: { Authorization: "Bearer    " } },
      env,
    );

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: "missing_bearer_token" });
  });

  it("rejects non-bearer schemes", async () => {
    const response = await createApp().request(
      "/v1/me",
      { headers: { Authorization: "Basic dXNlcjpwYXNz" } },
      env,
    );

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: "missing_bearer_token" });
  });
});
