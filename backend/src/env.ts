import type { SquadRoomDO } from "./durable-objects/SquadRoomDO";

export interface Env {
  DB: D1Database;
  ASSETS_BUCKET: R2Bucket;
  SQUAD_ROOM: DurableObjectNamespace<SquadRoomDO>;
  CLERK_SECRET_KEY: string;
  CLERK_PUBLISHABLE_KEY: string;
  CLERK_JWT_KEY: string;
  CLERK_AUTHORIZED_PARTIES?: string;
  ENVIRONMENT: string;
}
