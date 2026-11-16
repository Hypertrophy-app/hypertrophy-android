CREATE TABLE `squad_members` (
	`squad_id` text NOT NULL,
	`user_id` text NOT NULL,
	`role` text DEFAULT 'athlete' NOT NULL,
	`joined_at` integer NOT NULL,
	PRIMARY KEY(`squad_id`, `user_id`)
);
--> statement-breakpoint
CREATE INDEX `squad_members_squad_joined_idx` ON `squad_members` (`squad_id`,`joined_at`);--> statement-breakpoint
CREATE INDEX `squad_members_user_joined_idx` ON `squad_members` (`user_id`,`joined_at`);--> statement-breakpoint
CREATE TABLE `squads` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`owner_id` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `squads_owner_created_idx` ON `squads` (`owner_id`,`created_at`);--> statement-breakpoint
CREATE INDEX `squads_created_at_idx` ON `squads` (`created_at`);--> statement-breakpoint
CREATE TABLE `sync_outbox` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`entity_type` text NOT NULL,
	`entity_id` text NOT NULL,
	`operation` text NOT NULL,
	`payload_json` text NOT NULL,
	`attempts` integer DEFAULT 0 NOT NULL,
	`created_at` integer NOT NULL,
	`synced_at` integer
);
--> statement-breakpoint
CREATE INDEX `sync_outbox_user_created_idx` ON `sync_outbox` (`user_id`,`created_at`);--> statement-breakpoint
CREATE INDEX `sync_outbox_synced_created_idx` ON `sync_outbox` (`synced_at`,`created_at`);--> statement-breakpoint
CREATE TABLE `users` (
	`id` text PRIMARY KEY NOT NULL,
	`display_name` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `users_created_at_idx` ON `users` (`created_at`);--> statement-breakpoint
CREATE TABLE `workout_sessions` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`squad_id` text,
	`title` text NOT NULL,
	`started_at` integer NOT NULL,
	`completed_at` integer NOT NULL,
	`total_volume_kg` integer DEFAULT 0 NOT NULL,
	`sets_json` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `workout_sessions_user_created_idx` ON `workout_sessions` (`user_id`,`created_at`);--> statement-breakpoint
CREATE INDEX `workout_sessions_squad_created_idx` ON `workout_sessions` (`squad_id`,`created_at`);--> statement-breakpoint
CREATE INDEX `workout_sessions_created_at_idx` ON `workout_sessions` (`created_at`);