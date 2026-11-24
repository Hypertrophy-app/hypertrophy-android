# Contributing

Hypertrophy ships to the Play Store. Changes land through reviewed pull
requests that pass CI, and the invariants in [`AGENTS.md`](AGENTS.md) are not
negotiable.

## Setup

Follow the [README](README.md). In short:

```powershell
cd backend
npm install
copy .dev.vars.example .dev.vars

cd ..\android
# create local.properties with sdk.dir
.\gradlew.bat assembleDebug
```

## Branching

`main` is always releasable. Work happens on short-lived branches:

```
feat/coach-rest-ring
fix/sync-outbox-replay
docs/release-checklist
build/agp-8-10
```

Rebase on `main` before requesting review. Merge with **squash**; the pull
request title becomes the commit subject. Branches are deleted on merge.

## Commits

Conventional Commits, imperative mood, no emojis:

```
feat(squad): show presence in the host lobby
fix(data): drop duplicate outbox rows on replay
build(deps): bump compose bom to 2025.06.00
docs: record the release checklist
```

## Pull requests

Every pull request must:

1. Pass the `Backend` and `Android` checks.
2. State the commands run in the Verification section of the template.
3. Have one approval from a code owner.
4. Add or update tests for behaviour changes. Pure UI polish needs a screenshot
   of both themes instead.

Required checks are enforced by a repository ruleset on `main`. Force pushes
and branch deletion are blocked; organization admins can bypass in an
emergency and must note it on the pull request.

## Invariants

- UI reads Room `Flow` queries only. The network never feeds a screen.
- Writes land in Room plus `sync_outbox`, then `SyncWorker` drains them.
- Camera frames are processed in memory and closed immediately. Nothing raw is
  persisted or transmitted.
- No paid APIs, paid SaaS dependencies, or cloud build services.
- No emojis in code, UI strings, comments, commit messages, or documentation.
- Every screen supports Obsidian Forge and Daylight Ash.

## Releases

Releases are cut from `main` with a semver tag:

```powershell
git tag v0.2.0
git push origin v0.2.0
```

The Release workflow builds a signed `.aab`, attaches it to a draft GitHub
release, and uploads it as a workflow artifact. `versionName` comes from the
tag; `versionCode` is the workflow run number, which only increases.

Signing material is configured as repository secrets:

| Secret | Contents |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the upload keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Promote from the Play Console internal track to closed testing, then
production. A release is not done until the draft release notes are reviewed
and the version is live on the intended track.

## Reporting

Defects and scoped proposals go to Issues. Questions and open-ended ideas go to
Discussions. Vulnerabilities go through [`SECURITY.md`](SECURITY.md).
