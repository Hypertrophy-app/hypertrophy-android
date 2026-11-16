CREATE TABLE `challenges` (
	`id` text PRIMARY KEY NOT NULL,
	`squad_id` text NOT NULL,
	`title` text NOT NULL,
	`metric_type` text NOT NULL,
	`target_value` integer NOT NULL,
	`starts_at` integer NOT NULL,
	`ends_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`squad_id`) REFERENCES `squads`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `challenges_squad_ends_idx` ON `challenges` (`squad_id`,`ends_at`);--> statement-breakpoint
CREATE TABLE `exercises` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`primary_muscle` text NOT NULL,
	`secondary_muscles_json` text,
	`equipment` text NOT NULL,
	`difficulty` text DEFAULT 'intermediate' NOT NULL,
	`instructions_json` text,
	`glb_asset_key` text,
	`kinematic_profile_type` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `exercises_primary_muscle_idx` ON `exercises` (`primary_muscle`);--> statement-breakpoint
CREATE INDEX `exercises_kinematic_idx` ON `exercises` (`kinematic_profile_type`);--> statement-breakpoint
CREATE TABLE `personal_records` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`exercise_id` text NOT NULL,
	`max_weight_kg` integer DEFAULT 0 NOT NULL,
	`max_reps` integer DEFAULT 0 NOT NULL,
	`estimated_1rm` integer DEFAULT 0 NOT NULL,
	`achieved_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`exercise_id`) REFERENCES `exercises`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `personal_records_user_exercise_idx` ON `personal_records` (`user_id`,`exercise_id`);--> statement-breakpoint
CREATE INDEX `personal_records_achieved_idx` ON `personal_records` (`achieved_at`);--> statement-breakpoint
CREATE TABLE `squad_members` (
	`squad_id` text NOT NULL,
	`user_id` text NOT NULL,
	`role` text DEFAULT 'athlete' NOT NULL,
	`joined_at` integer NOT NULL,
	PRIMARY KEY(`squad_id`, `user_id`),
	FOREIGN KEY (`squad_id`) REFERENCES `squads`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `squad_members_squad_joined_idx` ON `squad_members` (`squad_id`,`joined_at`);--> statement-breakpoint
CREATE INDEX `squad_members_user_joined_idx` ON `squad_members` (`user_id`,`joined_at`);--> statement-breakpoint
CREATE TABLE `squads` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`invite_code` text NOT NULL,
	`owner_id` text NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`owner_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE UNIQUE INDEX `squads_invite_code_unique` ON `squads` (`invite_code`);--> statement-breakpoint
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
CREATE TABLE `user_badges` (
	`user_id` text NOT NULL,
	`badge_code` text NOT NULL,
	`unlocked_at` integer NOT NULL,
	PRIMARY KEY(`user_id`, `badge_code`),
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `user_badges_unlocked_idx` ON `user_badges` (`unlocked_at`);--> statement-breakpoint
CREATE TABLE `users` (
	`id` text PRIMARY KEY NOT NULL,
	`username` text,
	`display_name` text NOT NULL,
	`avatar_url` text,
	`streak_count` integer DEFAULT 0 NOT NULL,
	`last_workout_date` integer,
	`privacy_stealth_mode` integer DEFAULT false NOT NULL,
	`dashboard_layout_json` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE INDEX `users_created_at_idx` ON `users` (`created_at`);--> statement-breakpoint
CREATE INDEX `users_username_idx` ON `users` (`username`);--> statement-breakpoint
CREATE TABLE `workout_sessions` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`squad_id` text,
	`title` text NOT NULL,
	`started_at` integer NOT NULL,
	`ended_at` integer NOT NULL,
	`total_volume_kg` integer DEFAULT 0 NOT NULL,
	`avg_form_score` integer,
	`ai_recap_text` text,
	`sets_json` text NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `workout_sessions_user_created_idx` ON `workout_sessions` (`user_id`,`created_at`);--> statement-breakpoint
CREATE INDEX `workout_sessions_squad_created_idx` ON `workout_sessions` (`squad_id`,`created_at`);--> statement-breakpoint
CREATE INDEX `workout_sessions_created_at_idx` ON `workout_sessions` (`created_at`);--> statement-breakpoint
CREATE TABLE `workout_templates` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`title` text NOT NULL,
	`exercises_json` text NOT NULL,
	`is_public` integer DEFAULT false NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE INDEX `workout_templates_user_updated_idx` ON `workout_templates` (`user_id`,`updated_at`);--> statement-breakpoint
CREATE INDEX `workout_templates_public_updated_idx` ON `workout_templates` (`is_public`,`updated_at`);