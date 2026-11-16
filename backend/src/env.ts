import type { SquadRoom } from "./durable/squad-room";

export interface Env {
  DB: D1Database;
  ASSETS_BUCKET: R2Bucket;
  SQUAD_ROOM: DurableObjectNamespace<SquadRoom>;
  CLERK_SECRET_KEY: string;
  CLERK_PUBLISHABLE_KEY: string;
  CLERK_AUTHORIZED_PARTIES?: string;
  ENVIRONMENT: string;
}
