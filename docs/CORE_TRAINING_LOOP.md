# Core Training Loop: Implementation and Context

Status: implementation in progress
Scope owner: Android client
Related: `AGENTS.md`, `android/feature/workout`, `android/feature/coach`, `android/feature/today`

## 1. Why this slice exists

The app already has a workout builder, a weekly schedule, a camera coach, a
session history, and a sync outbox. They are not connected. A saved template
cannot be started, a scheduled day has no exercise payload, Today cannot launch
a planned session, and the coach records every set against one exercise.

This document defines the vertical slice that makes one real planned workout
run end to end:

```
builder -> template -> scheduled day -> coach queue -> completed session
        -> scheduled day marked done -> Today reflects completion
```

The slice is deliberately local-first. It does not add pull sync, squad live
progress, push notifications, or session resume. Those are follow-ups with
explicit contracts at the end of this document.

## 2. Product rules

1. A training session is a named list of exercises. Each exercise has a target
   set count, target rep count, target load, and rest duration.
2. The builder must be able to save a reusable template and schedule that
   template (or the current draft) on a calendar day.
3. Starting a scheduled workout must load its exact exercise queue into the
   camera coach. Starting a quick workout must still work with no plan.
4. Every logged set must carry its own `exerciseId`. A set is never attributed
   to whichever exercise happened to be selected when the session ended.
5. Finishing a session with at least one set must:
   - persist the session locally,
   - queue exactly one outbox entry for the session,
   - update personal records per exercise,
   - mark the linked scheduled workout complete.
6. Finishing with no sets must not create a session, an outbox entry, or a
   completion mark. It may show an empty recap.
7. The camera screen is the only live training surface. The legacy fake
   workout screen is removed.
8. All plan data stays local. Templates and schedules do not sync to D1 in this
   slice.

## 3. Current code map

| Concern | File |
| --- | --- |
| Plan JSON contract | `android/core/data/.../plan/WorkoutPlan.kt` (new) |
| Scheduled workout row | `android/core/data/.../local/ScheduledWorkoutEntity.kt` |
| Template row | `android/core/data/.../local/Entities.kt` |
| Schedule queries | `android/core/data/.../local/TrainingDao.kt` |
| Session row and outbox | `android/core/data/.../sync/SessionOutboxBuilder.kt` |
| Builder state | `android/feature/workout/.../WorkoutBuilderViewModel.kt` |
| Builder UI | `android/feature/workout/.../ui/WorkoutBuilderScreen.kt` |
| Coach state | `android/feature/coach/.../CoachViewModel.kt` |
| Coach UI | `android/feature/coach/.../CoachScreen.kt` |
| Today plan card | `android/feature/today/.../TodayScreen.kt` |
| Navigation | `android/app/.../navigation/HypertrophyApp.kt` |

## 4. Data model

### 4.1 Planned exercise

The existing `exercises_json` column stores a JSON array. The current writer
emits objects with `exerciseId`, `sets`, `reps`, and `weightKg`. The new
contract adds an optional `restSeconds` with a default so old rows still parse.

```kotlin
@Serializable
data class PlannedExercise(
    val exerciseId: String,
    val sets: Int = 3,
    val reps: Int = 8,
    val weightKg: Double = 0.0,
    val restSeconds: Int = 90,
)
```

`WorkoutPlanCodec` is the only encoder and decoder. It:

- ignores unknown keys,
- applies defaults for missing keys,
- clamps `sets` to 1..10, `reps` to 1..50, `weightKg` to >= 0, and
  `restSeconds` to 15..600,
- returns an empty list for null, blank, or malformed JSON,
- drops entries with a blank `exerciseId`.

The same codec is used for templates and scheduled workouts. That guarantees a
template can be copied into a scheduled day without translation.

### 4.2 Scheduled workout

No schema change. The existing columns are enough:

- `template_id` links back to the source template when one exists.
- `title` is the session name shown in Today and the coach.
- `exercises_json` now holds the plan instead of `null`.
- `is_completed` is flipped after a saved session.
- `is_rest_day` days are never startable.

### 4.3 Session

No schema change. `WorkoutSessionEntity.sets_json` already stores a local mirror
of every set, and `SessionOutboxBuilder` already supports multiple exercise ids
in one batched payload. The coach only needs to pass the correct id per set.

### 4.4 Identity

`AuthSessionStore.requireUserId` remains the single identity source. Every
schedule read and write is scoped by `user_id`. Marking a scheduled workout
complete also filters by `user_id` so one account cannot complete another
account's row.

## 5. Builder flow

### 5.1 Template list

The builder screen gains a template strip above the exercise search. Each card
shows the title, exercise count, and total set count. Actions:

- `Load`: decode the template into the current draft and remember the template
  id so a later save updates the same row.
- `Start`: create a scheduled workout for today from the template and navigate
  to the coach with that id.
- `Delete`: remove the template for the active user.

Templates are ordered by `updated_at DESC`, so the most recently saved or
edited template appears first.

### 5.2 Draft actions

The draft has a title and an ordered exercise list. The title lives in the view
model so it survives configuration changes and is reused by save, schedule, and
start.

- `Save template`: upsert the draft. If the draft was loaded from a template,
  the same row id and `created_at` are preserved. Otherwise a new id is created.
- `Start now`: create a scheduled workout for today with the draft payload and
  navigate to the coach.
- `Schedule day`: tapping a day in the weekly strip writes the draft to that
  day. If the builder is empty, tapping a day creates a rest day (or removes an
  existing rest day). Completed days are never overwritten.
- `New`: clears the draft and the editing id.

### 5.3 Weekly strip

The strip now renders the current Monday-to-Sunday week instead of the first
seven rows in the schedule. Each cell shows:

- weekday,
- session title or `REST`,
- a completion marker,
- `+N` when more than one session is planned that day.

Tapping a cell schedules the current draft or toggles a rest day as described
above. The current behavior of silently writing `Custom Session` with a null
payload is removed.

### 5.4 Reschedule preservation

The smart reschedule engine returns `PlannedWorkout` values. The old mapping
rebuilt entities from scratch and dropped `exercises_json` and `template_id`.
The new mapping starts from the existing entity and only changes the fields the
engine owns. The merge action rebuilds the merged exercise list from the
existing payloads so key compound lifts survive the merge.

## 6. Coach flow

### 6.1 Entry

The coach route accepts an optional `workoutId`:

- omitted or blank: quick workout,
- `today`: first incomplete, non-rest scheduled workout for today, or quick if
  none exists,
- any other value: that scheduled workout id.

The view model loads the scheduled row, decodes `exercises_json`, resolves each
exercise id against Room, and builds an ordered queue. A plan with no resolvable
exercises falls back to a quick workout and reports the title it attempted to
load.

### 6.2 Queue state

The coach keeps:

- `workoutTitle`,
- an ordered `ActiveExercise` list,
- `currentIndex`,
- a per-exercise completed set list,
- a session-level tempo list for the recap,
- the linked `scheduledWorkoutId`.

The UI state exposes a display queue with exercise name, target sets, completed
sets, and a current marker. The athlete can tap any queue item to switch to that
exercise. Switching cancels any running rest timer and keeps completed sets.

### 6.3 Exercise kind mapping

The camera analyzer only understands five movement patterns. The exercise
catalog uses a richer kinematic profile. Mapping:

| Catalog profile | Analyzer kind |
| --- | --- |
| `SQUAT` | `SQUAT` |
| `HINGE` | `HINGE` |
| `HORIZONTAL_PUSH`, `VERTICAL_PUSH` | `PUSH` |
| `PULL` | `PULL` |
| `ISOLATION` with biceps, delts, or core | `CURL_RAISE` |
| `ISOLATION` with triceps | `PUSH` |
| anything else | `SQUAT` |

The kind chips remain as a manual analyzer override. In a planned workout they
change the analyzer only; they do not change the exercise id or display name.
In a quick workout they change the exercise identity as before.

### 6.4 Set lifecycle

1. Reps are counted by the existing state machine.
2. `Log set` or the auto-set detector records a `CompletedSet` with the active
   `exerciseId`.
3. The completed set is appended to that exercise's list, not a global list
   tagged with a moving id.
4. Rest starts using the exercise's `restSeconds`.
5. If the exercise reached its target sets and another exercise exists, the
   next exercise becomes active when rest ends or is skipped. The rest overlay
   announces the upcoming exercise.
6. The athlete may switch exercises at any time through the queue.

### 6.5 Finish

`endSession` snapshots all completed sets plus any unlogged reps in the current
set. Then it:

1. Returns an empty recap and skips persistence if there are no sets.
2. Groups sets by `exerciseId`.
3. Computes top weight and Epley estimate per exercise.
4. Upserts a personal record per exercise when the previous row is missing or
   the new values are strictly better. A first row is a baseline, not a
   "new PR" announcement, matching `PersonalRecordMath`.
5. Builds one `SessionOutboxBuilder` payload with the workout title and every
   set. `setIndex` remains the index in the whole session.
6. Writes the session and outbox row.
7. Marks the scheduled workout complete when the session came from one.
8. Saves the recap baseline under `workout:<normalized title>` so the next
   session of the same workout can compare itself.

### 6.6 Recap

The recap uses session-wide metrics:

- title instead of a single exercise name,
- total sets and reps across all exercises,
- average ROM,
- tempo consistency across every rep in the session,
- cumulative form score,
- top load and best estimated 1RM.

The "new PR" badge appears when at least one exercise strictly beat its stored
record. The recap has two actions: share and finish. Finishing pops the coach
and returns to Today, which is already observing Room.

## 7. Navigation

```
today --start scheduled--> coach?workoutId=<scheduled id>
today --quick workout----> coach
builder --start now-------> coach?workoutId=<scheduled id>
FAB --start scheduled-----> coach?workoutId=today
FAB --quick workout-------> coach
```

The legacy `workout` destination and its fake screen are deleted. Every live
session goes through the camera coach.

The bottom bar stays hidden on the coach route. The coach keeps its existing
back behavior: back during an active set leaves the session; back during rest
skips rest; back on the recap finishes the workout.

## 8. Edge cases

- Missing exercise row: skip it. If nothing resolves, fall back to quick.
- Malformed plan JSON: decode as empty, never crash the builder or coach.
- Completed scheduled workout started directly: load it, but do not flip the
  completion flag again (the update is idempotent).
- Rest day started directly: treat as quick because there is no payload.
- Multiple sessions on one day: all are listed. The FAB picks the first
  incomplete non-rest session.
- Zero-set finish: recap only, no session row, no outbox row, no completion.
- Extra sets beyond target: allowed. The queue shows `completed/target` and the
  session records every set.
- Switching exercise mid-set: the partial reps are discarded, matching the
  existing analyzer-switch behavior.
- Process death during a session: still loses the active session. Session
  resume is the next slice.

## 9. Verification

Required commands from `AGENTS.md`:

```
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

New unit coverage:

- `WorkoutPlanCodecTest`: round trip, legacy JSON without `restSeconds`,
  clamping, malformed input, blank ids.
- `SessionSummaryTest`: per-exercise grouping, top set selection, Epley
  estimate, empty input.
- `ExerciseKindMappingTest`: every catalog profile maps to a valid analyzer.

Manual device check:

1. Build a two-exercise session with different set counts.
2. Save it as a template and confirm it appears in the template strip.
3. Start it from the template and confirm the coach title and queue.
4. Log sets on both exercises and confirm each set keeps its own id in
   `sets_json`.
5. Finish and confirm Today shows the scheduled day as completed.
6. Start a quick workout with no plan and confirm it still works.
7. Start a scheduled workout from Today and from the FAB.

## 10. Follow-ups

### Session resume

Add `active_session_drafts` with the plan id, current exercise index, completed
sets JSON, and updated timestamp. Write after every set and rest transition.
Restore on coach entry and offer `Resume` or `Discard`. Clear on finish.

### Sync

After this slice, the outbox contains one correct multi-exercise payload. The
next sync slice should:

- call `SyncScheduler.syncNow()` after a session and after sign-in,
- add a pull endpoint that returns sessions with sets,
- merge remote sessions into Room,
- surface sync failures.

### Squad

The coach now has a real workout title and per-set exercise ids. The squad slice
can add an active `squadId`, publish `LIVE_SET_PROGRESS` from `completeSet`, and
pass the id into `SessionOutboxBuilder` so milestone cards work.

### Builder polish

- Per-exercise rest editing.
- Reordering exercises in the draft.
- Duplicate a template.
- Explicit delete/clear day action.
