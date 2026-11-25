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
 * bearer token. Verification uses the Clerk JWT key so the edge never needs
 * a network round trip to Clerk.
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
    // Clerk native session tokens are minted without an Origin header, so they
    // never carry the azp claim. Passing authorizedParties to verifyToken
    // rejects every one of them (Clerk enforces a missing azp as a failure), so
    // the claim is checked by hand below instead.
    const payload = await verifyToken(token, { jwtKey: c.env.CLERK_JWT_KEY });

    // Comma-separated allowlist so browser origins can be added without a code
    // change (see wrangler.toml CLERK_AUTHORIZED_PARTIES). The check only
    // applies to tokens that actually have an azp claim.
    const authorizedParties = c.env.CLERK_AUTHORIZED_PARTIES
      ? c.env.CLERK_AUTHORIZED_PARTIES.split(",")
          .map((party) => party.trim())
          .filter(Boolean)
      : [];
    const azp = typeof payload.azp === "string" ? payload.azp : null;
    if (azp && authorizedParties.length > 0 && !authorizedParties.includes(azp)) {
      return c.json({ error: "invalid_session" }, 401);
    }

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
