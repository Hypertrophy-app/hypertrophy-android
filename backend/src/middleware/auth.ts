import { verifyToken } from "@clerk/backend";
import { createMiddleware } from "hono/factory";

import type { Env } from "../env";

export interface AuthVariables {
  userId: string;
  sessionId: string | null;
}

type AuthEnv = { Bindings: Env; Variables: AuthVariables };

/**
 * Verifies the Clerk session JWT that the Android client attaches as a
 * bearer token. Clerk is the only auth dependency; tokens are never persisted.
 */
export const requireAuth = createMiddleware<AuthEnv>(async (c, next) => {
  const authorization = c.req.header("Authorization");
  if (!authorization?.startsWith("Bearer ")) {
    return c.json({ error: "missing_bearer_token" }, 401);
  }

  const token = authorization.slice("Bearer ".length).trim();
  if (!token) {
    return c.json({ error: "missing_bearer_token" }, 401);
  }

  try {
    const payload = await verifyToken(token, {
      secretKey: c.env.CLERK_SECRET_KEY,
      authorizedParties: c.env.CLERK_AUTHORIZED_PARTIES
        ? [c.env.CLERK_AUTHORIZED_PARTIES]
        : undefined,
    });

    if (!payload.sub) {
      return c.json({ error: "invalid_session" }, 401);
    }

    c.set("userId", payload.sub);
    c.set("sessionId", typeof payload.sid === "string" ? payload.sid : null);
  } catch {
    return c.json({ error: "invalid_session" }, 401);
  }

  await next();
});
