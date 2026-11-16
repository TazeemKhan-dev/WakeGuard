# WakeGuard — Development Plan
Updated: 2026-11-16

## Delivery approach
AI-assisted, Android-only, offline-first. One working vertical slice at a time, test on physical realme phone, commit verified milestones. Do not rework Gradle versions without inspecting the real current configuration.

## Milestone 0 — Setup [COMPLETED EARLIER]
- [x] Create Kotlin + Compose app, run on phone, use Git.

## Milestone 1 — Alarm creation and ringing [IMPLEMENTED; RETEST CURRENT BUILD]
- [x] Home UI and create alarm.
- [x] Schedule trigger and show full-screen ringing.
- [x] Dismiss, 5-minute snooze, 15-minute timeout, vibration.
- [ ] Confirm clean build and phone test after Git recovery/refactor.

## Milestone 2 — Local Room persistence [STARTING NOW]
- [ ] Verify baseline build and current Gradle files.
- [ ] Choose compatible Room annotation processing configuration without global IDE changes.
- [ ] Add Room dependencies only; sync and build.
- [ ] Add entity, DAO, database and repository; build.
- [ ] Connect Compose home screen to persisted alarms; test app restart.
- [ ] Persist enable/disable, edit/delete and schedule reconciliation.
Acceptance: alarm definitions survive app restart and schedule correctly without duplicates.

## Milestone 3 — Repeating alarms and reboot recovery
- [ ] Weekday selection and next occurrence.
- [ ] Reschedule after trigger and after reboot/time changes.
- [ ] Test day rollover, time zone, permission changes.

## Milestone 4 — Linked Alarm Groups (defining feature)
- [ ] Create/edit groups and assign alarms.
- [ ] Define group + local wake-date session key.
- [ ] On explicit awake confirmation cancel only later pending alarms for same session.
- [ ] Preserve unrelated alarms and future repeating days.
- [ ] Handle race with alarms already firing.
Acceptance: dismissing linked 04:30 alarm can cancel 04:35/04:40/04:45 for *today only*.

## Milestone 5 — Challenges, settings, polish
- [ ] Optional simple math challenge, awake confirmation and accessible dismiss.
- [ ] Alarm sounds, vibration, configurable snooze/timeout settings.
- [ ] Permission onboarding, UI polish and APK.

## Milestone 6 — Reliability testing
- [ ] Locked screen, background/process recreation, Doze/battery saver.
- [ ] Reboot, time/time-zone change, permission revocation.
- [ ] Overlaps, snooze collisions, group cancellation and next-day repeat preservation.

## Next coding session
1. Read MASTER_CONTEXT.md, PROGRESS.txt, ARCHITECTURE.md.
2. Ask for `git status --short` and latest build result; inspect current Gradle files before changes.
3. Fix only confirmed baseline build issues.
4. Start Room integration with smallest testable dependency change.
5. Update PROGRESS.txt only after user reports verified outcome.
