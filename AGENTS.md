# AGENTS.md - HYPERTROPHY Execution Directives

## 1. Environment & Platform Constraints
- Host OS: Windows 11/10. Always use `.\gradlew.bat` (never `./gradlew`) for Android builds and tests.
- Repository root must remain short (e.g., `C:\dev\hypertrophy`) to avoid Windows MAX_PATH issues during NDK/Gradle compilation.
- ZERO PAID UTILITIES: Never introduce paid APIs, paid SaaS dependencies, or cloud build services (no EAS, no paid TTS, no paid 3D APIs). All AI pose detection, voice coaching, and 3D rendering must execute 100% locally on the Android device.
- NO EMOJIS anywhere in source code, UI strings, comments, commit messages, or documentation.

## 2. Design System Invariants (Phoenix UI)
- Typography: Bundle `.ttf` files in `core/designsystem/src/main/res/font/`:
  - `Space Grotesk` (Bold, SemiBold, Medium): Screen headers, workout titles, streak counters, brand wordmark.
  - `Plus Jakarta Sans` (Regular, Medium, SemiBold): Body copy, instructions, chat messages, buttons.
  - `JetBrains Mono` (Bold, Medium): Live rep counters, rest countdown timers, weights, tempo ratios (`fontFeatureSettings = "tnum"`).
- Dual Theme Support: Every screen and custom Canvas component must support both Light ("Daylight Ash") and Dark ("Obsidian Forge") via `LocalPhoenixColors`.
  - Dark Tokens: Canvas `#0D0D0F`, Surface `#18181C`, Elevated `#222228`, TextPrimary `#F5F5F7`, TextSecondary `#9E9EA8`.
  - Light Tokens: Canvas `#F8F7F4`, Surface `#FFFFFF`, Elevated `#EFECE6`, TextPrimary `#111114`, TextSecondary `#5C5C66`.
  - Brand/Semantic Phoenix Tokens: CrimsonPrimary `#D91E36`, EmberOrange `#FF6B1A`, SolarGold `#FFB800`, FormErrorCrimson `#FF2A4B`.
- Motion & Transitions: Use Compose `SharedTransitionLayout` for card-to-workout morphs, custom Compose `Brush.sweepGradient` animated ember rings for timers, and haptic feedback (`HapticFeedbackType.LongPress`) on rep completion and PR detection.

## 3. Architectural Invariants
- Offline-First Rule: The UI NEVER reads directly from the network. UI observes Room `Flow<T>` queries. Network responses write to Room; user actions write to Room + `sync_outbox` table and trigger `SyncWorker`.
- Cloudflare D1 Read/Write Discipline: Always add composite indexes on `user_id`, `squad_id`, and `created_at`. Batch workout set uploads into a single JSON payload per completed session rather than 1 HTTP write per set to stay well inside D1 free-tier daily limits.
- Privacy-First AI: Camera frames (`ImageProxy`) are processed in memory by MediaPipe `PoseLandmarker` and immediately closed (`imageProxy.close()`). Never save, record, or transmit camera frames or raw landmark streams over the network (only transmit compact workout summary metrics or rep count ticks).

## 4. Verification Protocol
- Backend verification: `cd backend && npx tsc --noEmit && npx wrangler deploy --dry-run`
- Android unit/kinematics verification: `cd android && .\gradlew.bat testDebugUnitTest`
- Android build verification: `cd android && .\gradlew.bat assembleDebug`
- Never mark a phase complete until all commands above exit with code `0`.
