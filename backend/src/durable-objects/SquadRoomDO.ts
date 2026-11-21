import { DurableObject } from "cloudflare:workers";

import type { Env } from "../env";

interface ChatMessage {
  id: string;
  squadId: string;
  userId: string;
  body: string;
  createdAt: number;
}

interface PresencePayload {
  type: "PRESENCE_UPDATE";
  userId: string;
  status: "WORKING_OUT" | "RESTING" | "IDLE" | "OFFLINE";
  at: number;
}

interface SessionExercise {
  exerciseId: string;
  sets: number;
  reps: number;
  restSeconds: number;
}

interface HostSessionState {
  hostUserId: string;
  queue: SessionExercise[];
  restSeconds: number;
  startedAt: number | null;
  createdAt: number;
}

interface SocketAttachment {
  userId: string;
  squadId: string;
  presence: "WORKING_OUT" | "RESTING" | "IDLE" | "OFFLINE";
}

const MAX_MESSAGES = 100;

/** A lobby older than this no longer blocks a new host. */
const SESSION_STALE_MS = 6 * 60 * 60 * 1000;

/**
 * SQLite-backed Durable Object that serializes all writes for one squad room.
 * Uses the WebSocket Hibernation API so idle sockets cost zero compute.
 */
export class SquadRoomDO extends DurableObject<Env> {
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

  private squadIdFromRequest(request: Request): string {
    const url = new URL(request.url);
    const match = url.pathname.match(/\/rooms\/([^/]+)/);
    return match?.[1] ?? "default";
  }

  private readAttachment(socket: WebSocket): SocketAttachment | null {
    try {
      return socket.deserializeAttachment() as SocketAttachment | null;
    } catch {
      return null;
    }
  }

  private broadcast(payload: unknown, exclude?: WebSocket): void {
    const text = JSON.stringify(payload);
    for (const socket of this.ctx.getWebSockets()) {
      if (socket === exclude) continue;
      try {
        socket.send(text);
      } catch {
        try {
          socket.close(1011, "broadcast_failed");
        } catch {
          // ignore
        }
      }
    }
  }

  private async recentMessages(squadId: string, limit = 50): Promise<ChatMessage[]> {
    const rows = this.ctx.storage.sql
      .exec(
        `SELECT id, squad_id, user_id, body, created_at
         FROM messages
         WHERE squad_id = ?
         ORDER BY created_at DESC
         LIMIT ?`,
        squadId,
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

  private trimMessages(squadId: string): void {
    this.ctx.storage.sql.exec(
      `DELETE FROM messages
       WHERE squad_id = ? AND id NOT IN (
         SELECT id FROM messages WHERE squad_id = ? ORDER BY created_at DESC LIMIT ?
       )`,
      squadId,
      squadId,
      MAX_MESSAGES,
    );
  }

  private async getSessionState(squadId: string): Promise<HostSessionState | null> {
    return (await this.ctx.storage.get<HostSessionState>(`session:${squadId}`)) ?? null;
  }

  private async setSessionState(squadId: string, state: HostSessionState): Promise<void> {
    await this.ctx.storage.put(`session:${squadId}`, state);
  }

  override async fetch(request: Request): Promise<Response> {
    const upgrade = request.headers.get("Upgrade");
    if (upgrade?.toLowerCase() === "websocket") {
      const url = new URL(request.url);
      const squadId = url.searchParams.get("squadId") ?? "default";
      const userId = url.searchParams.get("userId") ?? "anonymous";

      const pair = new WebSocketPair();
      const client = pair[0];
      const server = pair[1];

      this.ctx.acceptWebSocket(server);
      server.serializeAttachment({
        userId,
        squadId,
        presence: "IDLE",
      } satisfies SocketAttachment);

      // Send recent chat history to the newly connected client.
      const messages = await this.recentMessages(squadId);
      try {
        server.send(JSON.stringify({ type: "history", messages }));
      } catch {
        // ignore
      }

      // Late joiners need the current lobby even when it was created before
      // they connected. Replaying create/start also rebuilds the queue UI.
      const session = await this.getSessionState(squadId);
      if (session) {
        try {
          server.send(
            JSON.stringify({
              type: "HOST_SESSION_CREATE",
              hostUserId: session.hostUserId,
              queue: session.queue,
              restSeconds: session.restSeconds,
              createdAt: session.createdAt,
            }),
          );
          if (session.startedAt !== null) {
            server.send(
              JSON.stringify({
                type: "HOST_SESSION_START",
                hostUserId: session.hostUserId,
                queue: session.queue,
                restSeconds: session.restSeconds,
                createdAt: session.createdAt,
                startedAt: session.startedAt,
              }),
            );
          }
        } catch {
          // ignore
        }
      }

      // Announce presence.
      const presence: PresencePayload = {
        type: "PRESENCE_UPDATE",
        userId,
        status: "IDLE",
        at: Date.now(),
      };
      this.broadcast(presence, server);
      try {
        server.send(JSON.stringify(presence));
      } catch {
        // ignore
      }

      return new Response(null, { status: 101, webSocket: client });
    }

    const url = new URL(request.url);

    // REST: recent messages
    if (url.pathname.endsWith("/messages") && request.method === "GET") {
      const squadId = url.searchParams.get("squadId") ?? "default";
      const messages = await this.recentMessages(squadId);
      return Response.json({ messages });
    }

    // REST: post a chat message (used by squads route proxy)
    if (url.pathname.endsWith("/messages") && request.method === "POST") {
      const incoming = await request.json<{ squadId?: string; userId?: string; body?: string }>();
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
      this.trimMessages(incoming.squadId);

      const event = { type: "CHAT_MESSAGE", message };
      this.broadcast(event);

      return Response.json({ message }, { status: 201 });
    }

    // REST: session state
    if (url.pathname.endsWith("/session") && request.method === "GET") {
      const squadId = url.searchParams.get("squadId") ?? "default";
      const state = await this.getSessionState(squadId);
      return Response.json({ session: state });
    }

    return new Response("Not found", { status: 404 });
  }

  override async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") {
      socket.close(1003, "text_frames_only");
      return;
    }

    const attachment = this.readAttachment(socket);
    if (!attachment) {
      socket.close(1008, "missing_attachment");
      return;
    }

    let parsed: { type?: string } & Record<string, unknown>;
    try {
      parsed = JSON.parse(message) as { type?: string } & Record<string, unknown>;
    } catch {
      socket.send(JSON.stringify({ type: "error", error: "invalid_json" }));
      return;
    }

    const now = Date.now();

    switch (parsed.type) {
      case "ping": {
        socket.send(JSON.stringify({ type: "pong", at: now }));
        break;
      }

      case "PRESENCE_UPDATE": {
        const status = parsed.status;
        if (
          status !== "WORKING_OUT" &&
          status !== "RESTING" &&
          status !== "IDLE" &&
          status !== "OFFLINE"
        ) {
          socket.send(JSON.stringify({ type: "error", error: "invalid_presence" }));
          return;
        }
        attachment.presence = status;
        socket.serializeAttachment(attachment);

        const event: PresencePayload = {
          type: "PRESENCE_UPDATE",
          userId: attachment.userId,
          status,
          at: now,
        };
        this.broadcast(event);
        break;
      }

      case "HOST_SESSION_CREATE": {
        const existing = await this.getSessionState(attachment.squadId);
        if (
          existing &&
          existing.hostUserId !== attachment.userId &&
          now - existing.createdAt < SESSION_STALE_MS
        ) {
          socket.send(JSON.stringify({ type: "error", error: "session_exists" }));
          return;
        }
        const queue = Array.isArray(parsed.queue) ? (parsed.queue as SessionExercise[]) : [];
        const restSeconds = typeof parsed.restSeconds === "number" ? parsed.restSeconds : 90;
        const state: HostSessionState = {
          hostUserId: attachment.userId,
          queue,
          restSeconds,
          startedAt: null,
          createdAt: now,
        };
        await this.setSessionState(attachment.squadId, state);
        this.broadcast({
          type: "HOST_SESSION_CREATE",
          hostUserId: attachment.userId,
          queue,
          restSeconds,
          createdAt: now,
        });
        break;
      }

      case "HOST_SESSION_START": {
        const state = await this.getSessionState(attachment.squadId);
        if (!state) {
          socket.send(JSON.stringify({ type: "error", error: "no_session" }));
          return;
        }
        if (state.hostUserId !== attachment.userId) {
          socket.send(JSON.stringify({ type: "error", error: "not_host" }));
          return;
        }
        state.startedAt = now;
        await this.setSessionState(attachment.squadId, state);
        this.broadcast({
          type: "HOST_SESSION_START",
          hostUserId: state.hostUserId,
          queue: state.queue,
          restSeconds: state.restSeconds,
          createdAt: state.createdAt,
          startedAt: now,
        });
        break;
      }

      case "LIVE_SET_PROGRESS": {
        this.broadcast({
          type: "LIVE_SET_PROGRESS",
          userId: attachment.userId,
          exerciseId: parsed.exerciseId,
          setIndex: parsed.setIndex,
          reps: parsed.reps,
          weightKg: parsed.weightKg,
          isPr: parsed.isPr === true,
          at: now,
        });
        break;
      }

      case "CHAT_MESSAGE": {
        const body = typeof parsed.body === "string" ? parsed.body.slice(0, 500) : "";
        if (!body) {
          socket.send(JSON.stringify({ type: "error", error: "invalid_body" }));
          return;
        }
        const message: ChatMessage = {
          id: crypto.randomUUID(),
          squadId: attachment.squadId,
          userId: attachment.userId,
          body,
          createdAt: now,
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
        this.trimMessages(attachment.squadId);
        this.broadcast({ type: "CHAT_MESSAGE", message });
        break;
      }

      default: {
        socket.send(JSON.stringify({ type: "error", error: "unknown_type" }));
      }
    }
  }

  override async webSocketClose(
    socket: WebSocket,
    code: number,
    reason: string,
    wasClean: boolean,
  ): Promise<void> {
    const attachment = this.readAttachment(socket);
    if (!attachment) return;

    attachment.presence = "OFFLINE";
    try {
      socket.serializeAttachment(attachment);
    } catch {
      // socket already closed
    }

    const event: PresencePayload = {
      type: "PRESENCE_UPDATE",
      userId: attachment.userId,
      status: "OFFLINE",
      at: Date.now(),
    };
    this.broadcast(event);
  }

  override async webSocketError(socket: WebSocket, error: unknown): Promise<void> {
    try {
      socket.close(1011, "socket_error");
    } catch {
      // ignore
    }
  }
}
