<div align="center">

<span style="display: inline-block; padding: 18px 30px; background-color: #0D0D0F; border-radius: 14px;"><img src="https://raw.githubusercontent.com/Hypertrophy-app/.github/main/assets/wordmark.png" alt="Hypertrophy" width="420" style="max-width: 100%; height: auto;" /></span>

**Offline-first strength training for Android.**

<sub>[hypertrophy.app](https://hypertrophy.app) &nbsp;&middot;&nbsp; [Issues](https://github.com/Hypertrophy-app/hypertrophy-android/issues) &nbsp;&middot;&nbsp; [Releases](https://github.com/Hypertrophy-app/hypertrophy-android/releases)</sub>

</div>

# HYPERTROPHY

[![CI](https://github.com/Hypertrophy-app/hypertrophy-android/actions/workflows/ci.yml/badge.svg)](https://github.com/Hypertrophy-app/hypertrophy-android/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/Hypertrophy-app/hypertrophy-android?include_prereleases&label=release)](https://github.com/Hypertrophy-app/hypertrophy-android/releases)

Offline-first strength training monorepo. The Android client owns the training
experience; the Cloudflare Worker is a thin sync, squad, and asset layer.

- [Contributing](CONTRIBUTING.md)
- [Security policy](SECURITY.md)
- [Discussions](https://github.com/Hypertrophy-app/hypertrophy-android/discussions)
- [Releases](https://github.com/Hypertrophy-app/hypertrophy-android/releases)

Related repositories: [`hypertrophy-site`](https://github.com/Hypertrophy-app/hypertrophy-site)
holds the public site and the legal pages Play Console requires, and
[`hypertrophy-brand`](https://github.com/Hypertrophy-app/hypertrophy-brand)
holds the identity, store assets, listing copy, and Data safety answers.

```
hypertrophy/
  backend/   TypeScript Cloudflare Worker (Hono, Drizzle, Clerk, Zod, D1, R2, Durable Objects)
  android/   Multi-module Android app (Compose, Hilt, Room, Ktor, MediaPipe, SceneView, CameraX)
```

## Backend

Requirements: Node.js 24 LTS or newer and a Cloudflare account for real deploys.

```powershell
cd backend
npm install
copy .dev.vars.example .dev.vars
npm run typecheck
npm run deploy -- --dry-run
npm run dev
```

Bindings configured in `backend/wrangler.toml`:

- `DB`: Cloudflare D1 database `hypertrophy-db`
- `ASSETS_BUCKET`: R2 bucket `hypertrophy-assets`
- `SQUAD_ROOM`: SQLite-backed Durable Object `SquadRoom`

Before the first remote deploy, replace the placeholder `database_id` with the
real D1 id from `npx wrangler d1 create hypertrophy-db` and create the R2 bucket
with `npx wrangler r2 bucket create hypertrophy-assets`. Apply the generated D1
migration with `npm run db:migrate:remote`. Set `CLERK_SECRET_KEY`,
`CLERK_PUBLISHABLE_KEY`, `CLERK_JWT_KEY`, and `CLERK_AUTHORIZED_PARTIES` as Worker secrets.

Squad chat is serialized through `SQUAD_ROOM`, a SQLite-backed Durable Object.
Completed sessions are uploaded as one JSON payload per session, keeping D1
writes well inside the free tier. Each synced squad session also emits at most
two system chat cards (session completion plus the biggest PR) through the
Durable Object, with idempotent outbox replay protection.

Backend verification:

```powershell
cd backend
npm test
npx tsc --noEmit
npx wrangler deploy --dry-run
```

## Android

Requirements: JDK 17 or newer, Android SDK platform 35, build-tools 35.0.0.

Create `android/local.properties` with your SDK path (it is gitignored):

```properties
sdk.dir=C\:\\Android\\Sdk
```

Modules:

- `:app` - Compose shell, navigation, Hilt entry point, WorkManager factory, bundled `pose_landmarker_full.task`
- `:core:designsystem` - Phoenix tokens, dual themes, bundled fonts, ember ring
- `:core:data` - Room database, outbox, Ktor client, Hilt modules, `SyncWorker`, `SyncScheduler`
- `:core:kinematics` - pure Kotlin pose math, squat/hinge/push/pull/curl analyzers, rep and ROM state machine, tempo tracker, form fault rules, auto set/rest detection, 60-frame exercise classifier
- `:core:vision` - CameraX preview plus `STRATEGY_KEEP_ONLY_LATEST` analysis, MediaPipe `PoseLandmarker` in `LIVE_STREAM` with GPU-to-CPU fallback, gold/crimson skeleton overlay, camera permission gate
- `:feature:coach` - gym-distance HUD, offline TTS voice coach with transient audio-focus ducking, media-session music controls, full-screen rest ring, deterministic recap and Share to Squad
- `:feature:workout` - exercise library, weekly planner, builder, substitution and reschedule engines, Health Connect hooks
- `:feature:squad` - squad create/join with 6-character invite codes and `hypertrophy://join/<code>` deep links, Room-backed offline squad cache, Ktor WebSocket client to the `SquadRoomDO` durable object, live presence pills, stealth mode, workout-together host lobby, group chat and system milestone cards
- `:feature:today` - customizable dashboard with reorderable, toggleable cards persisted in DataStore, real streak/week stats, challenges, squad live ticker, and per-muscle recovery
- `:feature:profile` - real front/back muscle recovery map, 90-day consistency heatmap, PR trophy wall, estimated-1RM progression chart, persisted badge gallery, and Health Connect permission/export controls

Build and test:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

`compileSdk` and `targetSdk` are 35, `minSdk` is 26. The version catalog lives at
`android/gradle/libs.versions.toml`. Unit tests cover the smart rescheduling
engine and equipment substitution engine in `:feature:workout`, the synthetic
pose kinematics suite in `:core:kinematics`, the deterministic session recap
and PR math in `:feature:coach`, the session outbox builder and `SyncRepository`
flush idempotency plus squad refresh rules in `:core:data`, the dashboard layout
and insight math in `:feature:today`, recovery/progression/badge math and the
Health Connect session export contract in `:feature:profile`, squad wire
parsing, host-lobby codecs, cache mapping, invite-code and presence parsing in
`:feature:squad`, GPU-to-CPU pose fallback in `:core:vision`, and the milestone
card/auth middleware contracts in `backend`.

### Current gaps

- Clerk sign-in is not initialized in the Android app until the platform
  baseline supports the catalog-pinned Clerk release. The app now gates every
  screen behind a device-local account (`SessionStore`): the email is persisted
  locally, no password or token is stored, and Google/passkey sign-in reports
  that they need the Clerk integration. Local data is therefore still stored
  under `local-user`; network requests still require a valid backend JWT.
- Firebase Cloud Messaging is not configured, so squad invites are delivered by
  invite code or deep link rather than push notifications. This avoids adding a
  paid cloud dependency to an otherwise local-first stack.
- Health Connect export is user-triggered from Profile. Exports are idempotent
  (session-derived `clientRecordId`), but no background export worker runs yet.
- The instrumentation (`androidTest`) lane is not executed because the build
  environment has no emulator. JVM unit tests, `testDebugUnitTest`, and
  `assembleDebug` are the enforced Android gates.
- SceneView remains catalog-only until `compileSdk 37`; `ExerciseFormViewer`
  keeps a hardware-free preview contract in the meantime.

### Dependency baseline note

The platform baseline is `compileSdk 35` with Kotlin 2.1.21 and Compose BOM
2025.05.01. Three requested libraries were published after that baseline:

- `io.github.sceneview:sceneview:4.69.5` requires `compileSdk 37`, Kotlin
  2.4.21, and Compose BOM 2026.09.00. It is pinned in the version catalog but
  intentionally left off the module classpath. `ExerciseFormViewer` ships the
  orbit, pinch-zoom, animation-speed, and emissive-highlight contract over a
  hardware-free preview; replace its body with `SceneView { }` once the
  platform baseline moves. `.glb` caching in `ExerciseModelCache` is already
  SceneView-ready (`filesDir/models/<exerciseId>.glb`).
- `com.clerk:clerk-android-ui:1.1.10` imports Kotlin 2.4.20 and Compose BOM
  2026.06.01, so it is also catalog-only until the baseline moves.
- `androidx.health.connect:connect-client:1.1.0` declares `minCompileSdk 36` but
  works on the 35 baseline. AGP's AAR metadata gate is disabled with
  `android.experimental.disableCompileSdkChecks=true` so the project can keep
  `compileSdk 35` as specified.

Moving to `compileSdk 37`, Kotlin 2.4.x, and a matching Compose BOM is the
recommended follow-up when the SDK baseline is allowed to change.

## Design system

Phoenix UI ships two themes driven by `LocalPhoenixColors`: Obsidian Forge (dark)
and Daylight Ash (light). Static font instances are bundled in
`android/core/designsystem/src/main/res/font/`:

- Space Grotesk: Medium, SemiBold, Bold
- Plus Jakarta Sans: Regular, Medium, SemiBold
- JetBrains Mono: Medium, Bold, tabular numerals via `tnum`

Fonts are derived from the official Google Fonts variable sources and remain
under the SIL Open Font License. License texts are in
`android/core/designsystem/FONT_LICENSES/`.

## Invariants

- UI reads only Room `Flow` queries; the network never feeds a screen directly.
- Local writes land in Room and `sync_outbox`, then `SyncWorker` drains the outbox.
- Composite indexes cover `user_id`, `squad_id`, and `created_at`.
- Camera frames are processed in memory by MediaPipe and closed immediately.
  No frames or raw landmark streams are stored or transmitted.
- No paid APIs, paid SaaS dependencies, or cloud build services.
