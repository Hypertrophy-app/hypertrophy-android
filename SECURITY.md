# Security Policy

## Supported versions

Hypertrophy is pre-release. Only the latest build on the `main` branch and the
latest Play Console track receive fixes.

## Reporting a vulnerability

Use GitHub private vulnerability reporting:

https://github.com/Hypertrophy-app/hypertrophy-android/security/advisories/new

Do not open a public issue, discussion, or pull request for a security problem.
Do not include real user data, API keys, or keystore material in a report; use
synthetic examples.

We aim to acknowledge a report within three business days and to agree on a
disclosure timeline within ten. Credit is given in the advisory unless you ask
otherwise.

## Scope

In scope:

- The Android client in `android/`, including local storage, sync, and the pose
  pipeline.
- The Cloudflare Worker in `backend/`, including authentication, D1 and R2
  access, and the `SquadRoom` Durable Object.
- Credentials or personal data exposed through the app, the Worker, or the
  release pipeline.

Out of scope:

- Vulnerabilities in third-party dependencies without a demonstrated impact on
  Hypertrophy. Report those upstream.
- Missing hardening headers or rate limits with no demonstrated impact.
- Physical access attacks on an unlocked device.

## Design commitments

- Camera frames are processed in memory by MediaPipe and closed immediately.
  No frames or raw landmark streams are stored or transmitted.
- The client stores an email address for the local account and no password or
  token.
- Worker secrets live in Cloudflare, never in the repository.
- Release signing material lives in GitHub Actions secrets, never in the
  repository.
