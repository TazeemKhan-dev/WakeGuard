# WakeGuard — Development Plan
Updated: 2026-11-21

## Delivery approach
Android-only, offline-first, AI-assisted. Preserve a working vertical slice, build and test on physical realme phone, then commit verified milestones. Avoid speculative AGP/Kotlin/Gradle changes. Source implementation and device verification are separate statuses.

## Milestone 0 — Setup [IMPLEMENTED]
- [x] Kotlin + Jetpack Compose Android project, physical phone development, Git workflow.
- [ ] Confirm latest branch/commit and successful remote push.

## Milestone 1 — Core alarms [IMPLEMENTED IN SOURCE; RETEST]
- [x] Home list, add/edit, enable/disable, delete, next-alarm summary.
- [x] AlarmManager scheduling, full-screen ringing, foreground service.
- [x] Snooze/dismiss, vibration, ringing timeout in source.
- [ ] Verify current build and real alarm on locked device, including snooze and timeout.

## Milestone 2 — Room persistence [IMPLEMENTED IN SOURCE; RETEST]
- [x] Room alarm entity, DAO, database, repository integrated with MainActivity.
- [x] Persistence operations for create/update/delete/toggle.
- [x] Generation-aware rescheduling/cancellation logic in source.
- [ ] Confirm persistence across app restart, no duplicate triggers, and migration behavior.

## Milestone 3 — Repeat scheduling [IMPLEMENTED IN SOURCE; RELIABILITY PENDING]
- [x] Once, weekdays, weekends, daily, custom day selection.
- [x] Repeat scheduling/re-arm logic in shared source.
- [ ] Test day rollover, timezone changes, reboot, permission revocation and stale PendingIntents.

## Milestone 4 — Dismissal challenges and appearance [IMPLEMENTED IN SOURCE; RETEST]
- [x] Swipe, Math (difficulty/question count), Shake (movement target).
- [x] Preview mode for dismissal methods.
- [x] Studio White / Midnight Black / System app appearance via DataStore.
- [x] Independent alarm-screen theme (Dark / Light / Follow app).
- [x] Nested Back navigation for dismissal settings and snooze; preview Activity returns on finish.
- [x] Swipe chevron animation (no mouse icon), Shake phone/progress polish.
- [x] Optional label display; remove `Preview:` and default placeholders.
- [ ] Test all challenge paths and preview Back, theme persistence, UI animations, and blank/named labels on device.

## Milestone 5 — Ringtones and theme fixes [IMPLEMENTED IN CURRENT USER BUILD; AUDIO QUALITY OPEN]
- [x] Per-alarm ringtone key persisted using existing `soundUri`; selection and playback plumbing added in source.
- [x] Ringtone picker organized as Loud / Medium / Low expandable groups; separate custom audio file picker; no device ringtone category.
- [x] Custom audio picker now shows selected file and supports preview; user confirmed working on 2026-11-21.
- [x] App Light Mode status bar contrast fix applied; user confirmed working on 2026-11-21.
- [x] Edited alarms auto-enable/reschedule on save in the latest inspected `MainActivity.kt` source.
- [ ] Replace disliked bundled ringtone audio with genuinely good, lyric-free instrumental music; avoid reusing old bee-like/single-note audio. Approve previews first, verify licenses for third-party audio.
- [ ] Verify selected custom ringtone survives app restart and rings reliably, including URI access.

## Milestone 6 — Alarm reliability regression [NEXT; NOT YET FULLY VERIFIED]
- [ ] Confirm current Git status, branch/commit, clean `:app:assembleDebug` and phone smoke test.
- [ ] Test edited OFF alarm -> new upcoming time -> ON -> rings, and one-time alarm disables after dismissal without app restart.
- [ ] Test real alarm vs preview, snooze, timeout, repeat, locked screen, reboot, timezone/time changes, permission changes, Doze, process recreation, overlapping alarms.
- [ ] Test Swipe / Math / Shake and screen-off dismissal approximation. Screen-off is NOT a guaranteed power-button-only event.
- [ ] Revisit vibration (previously reported not working; intentionally deferred).
- [ ] Commit/push verified checkpoint; record branch/hash in `PROGRESS.txt`.

## Milestone 7 — Awake Check [NEXT FEATURE AFTER REGRESSION; NOT IMPLEMENTED]
- [ ] After successful challenge dismissal, schedule a configurable wait (X minutes), then prompt "Are you still awake?".
- [ ] Provide explicit "I'm Awake" confirmation and configurable response timeout; no response triggers a new ringing occurrence.
- [ ] Keep Awake Check distinct from snooze, persist state, and schedule using Android mechanisms independent of the UI/process.
- [ ] Define repeat/retry policy and safe handling of reboot, permission changes, stale events and overlapping alarms.
- [ ] Validate with real locked-device tests before treating it as reliable.

## Milestone 8 — Linked Alarm Groups [PLANNED; NOT IMPLEMENTED]
- [ ] Create/edit groups and associate alarms.
- [ ] Define per-group local wake-date/session key and occurrence lifecycle.
- [ ] Explicit awake confirmation cancels later pending alarms for this session only, preserving unrelated alarms and future repeats.
- [ ] Handle snoozes, races, reboot and time changes.

## Milestone 9 — Final polish and release [PENDING]
- [ ] Volume/vibration options, accessibility, battery/permission onboarding, release signing and APK testing.

## Next coding session
1. Ask for latest source ZIP or targeted current files when needed; do not assume prior patches are still the working files.
2. Run `git status --short`, `git log -1 --oneline`, and `.\gradlew.bat :app:assembleDebug` on Windows.
3. Run a focused alarm reliability regression; fix confirmed bugs only.
4. Commit/push a verified checkpoint and record its hash.
5. Implement Awake Check in small testable steps, starting with its data model and scheduling design.
6. Provide individual updated `.kt` files with exact paths; ZIP only for collections of audio assets or when requested.
