import { DurableObject } from "cloudflare:workers";

import type { Env } from "../env";

interface ChatMessage {
  id: string;
  squadId: string;
  userId: string;
  body: string;
  createdAt: number;
}

interface IncomingMessage {
  squadId: string;
  userId: string;
  body: string;
}

/**
 * SQLite-backed Durable Object that serializes all writes for one squad room.
 * WebSocket fan-out keeps presence and chat ordering consistent while the
 * REST API stays the only way data enters the room.
 */
export class SquadRoom extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);

    ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec(`
        CREATE TABLE IF NOT EXISTS messages (
          id TEXT PRIMARY KEY,
          squad_id TEXT NOT NULL,
          user_id TEXT NOT NULL,
          body TEXT NOT NULL,
          created_at INTEGER NOT NULL
        );
      `);
      this.ctx.storage.sql.exec(`
        CREATE INDEX IF NOT EXISTS messages_squad_created_idx
        ON messages (squad_id, created_at);
      `);
    });
  }

  private async recent(limit = 50): Promise<ChatMessage[]> {
    const rows = this.ctx.storage.sql
      .exec(
        `SELECT id, squad_id, user_id, body, created_at
         FROM messages
         ORDER BY created_at DESC
         LIMIT ?`,
        limit,
      )
      .toArray();

    return rows
      .map((row) => ({
        id: String(row.id),
        squadId: String(row.squad_id),
        userId: String(row.user_id),
        body: String(row.body),
        createdAt: Number(row.created_at),
      }))
      .reverse();
  }

  private broadcast(message: ChatMessage): void {
    const payload = JSON.stringify({ type: "message", message });
    for (const socket of this.ctx.getWebSockets()) {
      try {
        socket.send(payload);
      } catch {
        socket.close(1011, "broadcast_failed");
      }
    }
  }

  override async fetch(request: Request): Promise<Response> {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade?.toLowerCase() === "websocket") {
      const pair = new WebSocketPair();
      const client = pair[0];
      const server = pair[1];
      this.ctx.acceptWebSocket(server);
      return new Response(null, { status: 101, webSocket: client });
    }

    const url = new URL(request.url);

    if (url.pathname.endsWith("/messages") && request.method === "POST") {
      const incoming = await request.json<IncomingMessage>();
      if (!incoming.squadId || !incoming.userId || !incoming.body) {
        return Response.json({ error: "invalid_payload" }, { status: 400 });
      }

      const message: ChatMessage = {
        id: crypto.randomUUID(),
        squadId: incoming.squadId,
        userId: incoming.userId,
        body: incoming.body.slice(0, 500),
        createdAt: Date.now(),
      };

      this.ctx.storage.sql.exec(
        `INSERT INTO messages (id, squad_id, user_id, body, created_at)
         VALUES (?, ?, ?, ?, ?)`,
        message.id,
        message.squadId,
        message.userId,
        message.body,
        message.createdAt,
      );

      this.broadcast(message);
      return Response.json({ message }, { status: 201 });
    }

    if (url.pathname.endsWith("/messages") && request.method === "GET") {
      const messages = await this.recent();
      return Response.json({ messages });
    }

    return new Response("Not found", { status: 404 });
  }

  override async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") {
      socket.close(1003, "text_frames_only");
      return;
    }

    try {
      const parsed = JSON.parse(message) as { type?: string };
      if (parsed.type === "ping") {
        socket.send(JSON.stringify({ type: "pong", at: Date.now() }));
      }
    } catch {
      socket.send(JSON.stringify({ type: "error", error: "invalid_json" }));
    }
  }
}
