# Offline flight-mode verification

Manual end-to-end check performed on a physical device or emulator before
submitting the release bundle.

1. Enable airplane mode (no Wi-Fi, no mobile data).
2. Cold-start the app. Confirm the splash and home dashboard render from Room
   cache without a network error screen.
3. Open a previously cached exercise detail and confirm the 3D viewer loads the
   `.glb` from `filesDir/models/<exerciseId>.glb` (see `ExerciseModelCache`).
4. Start a coach session, complete 3 sets of reps with the AI camera. Confirm:
   - Rep counter, ROM gauge, and tempo bar update live.
   - Auto set/rest detection logs each set.
   - `WorkoutDao` rows and `sync_outbox` entries are written (inspect via
     Android Studio Database Inspector).
   - Local PR calculation runs in `CoachViewModel.endSession()` and a
     `PersonalRecordEntity` row is upserted on the first session.
5. Disable airplane mode and trigger `SyncWorker` (or wait for the scheduled
   run). Confirm:
   - `SyncRepository.syncPending()` uploads the single JSON payload.
   - Outbox rows are marked `synced_at` and are not re-uploaded on a second run
     (idempotency key = first pending entry id).
   - Cloudflare D1 reflects the session once (no duplicates).

Automated coverage for the pure pieces lives in the module unit tests:
`:core:kinematics`, `:feature:coach`, `:feature:today`, `:feature:profile`,
`:feature:squad`, and `:feature:workout`.
