## Summary

<!-- What changes, and why. Link the issue this closes. -->

Closes #

## Verification

<!-- Paste the commands you ran and their result. Both must be green. -->

- [ ] `cd backend && npm run typecheck && npm test && npx wrangler deploy --dry-run`
- [ ] `cd android && .\gradlew.bat testDebugUnitTest`
- [ ] `cd android && .\gradlew.bat assembleDebug`

## Checklist

- [ ] No emojis in code, UI strings, comments, or commit messages.
- [ ] No new paid APIs, paid SaaS, or cloud build services.
- [ ] UI still reads Room flows only; nothing renders straight from the network.
- [ ] Camera frames are still closed immediately and never persisted.
- [ ] Both themes render correctly: Obsidian Forge and Daylight Ash.
- [ ] Screenshots or a short screen recording for visible UI changes.
