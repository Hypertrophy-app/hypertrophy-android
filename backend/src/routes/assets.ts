import { Hono } from "hono";

import type { Env } from "../env";
import type { AuthVariables } from "../middleware/auth";

type AppEnv = { Bindings: Env; Variables: AuthVariables };

export const assetRoutes = new Hono<AppEnv>();

const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

assetRoutes.put("/:key{.+}", async (c) => {
  const key = c.req.param("key");
  if (!key || key.includes("..")) {
    return c.json({ error: "invalid_key" }, 400);
  }

  const contentLength = Number(c.req.header("content-length") ?? "0");
  if (contentLength > MAX_UPLOAD_BYTES) {
    return c.json({ error: "asset_too_large" }, 413);
  }

  const body = await c.req.arrayBuffer();
  if (body.byteLength === 0 || body.byteLength > MAX_UPLOAD_BYTES) {
    return c.json({ error: "invalid_body" }, 400);
  }

  const contentType = c.req.header("content-type") ?? "application/octet-stream";
  const object = await c.env.ASSETS_BUCKET.put(key, body, {
    httpMetadata: {
      contentType,
      cacheControl: "private, max-age=31536000, immutable",
    },
    customMetadata: {
      ownerId: c.get("userId"),
    },
  });

  return c.json({ key: object?.key ?? key, size: body.byteLength, contentType }, 201);
});

assetRoutes.get("/:key{.+}", async (c) => {
  const key = c.req.param("key");
  if (!key || key.includes("..")) {
    return c.json({ error: "invalid_key" }, 400);
  }

  const object = await c.env.ASSETS_BUCKET.get(key);
  if (!object) {
    return c.json({ error: "not_found" }, 404);
  }

  if (object.customMetadata?.ownerId !== c.get("userId")) {
    return c.json({ error: "forbidden" }, 403);
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("etag", object.httpEtag);
  return new Response(object.body, { headers });
});

assetRoutes.delete("/:key{.+}", async (c) => {
  const key = c.req.param("key");
  if (!key || key.includes("..")) {
    return c.json({ error: "invalid_key" }, 400);
  }

  const object = await c.env.ASSETS_BUCKET.head(key);
  if (!object) {
    return c.json({ error: "not_found" }, 404);
  }
  if (object.customMetadata?.ownerId !== c.get("userId")) {
    return c.json({ error: "forbidden" }, 403);
  }

  await c.env.ASSETS_BUCKET.delete(key);
  return c.body(null, 204);
});
