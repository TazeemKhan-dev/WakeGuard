# Alarm App — Development Plan
Last updated: 2026-11-15

## Delivery approach
AI-assisted implementation, Android-only, offline-first, working vertical slices. Do not build all UI before verifying native alarm scheduling. Initial personal MVP estimate 5–10 focused days, not a guarantee; Android behavior and testing are the critical uncertainty.

## Milestone 0 — Setup and first launch (0.5–1 day)
- [ ] Install/configure Android Studio and Android SDK.
- [ ] Create Kotlin + Jetpack Compose app with a stable SDK target.
- [ ] Run on physical Android phone via USB debugging.
- [ ] Establish Git repository, README and build instructions.
Acceptance: a fresh app launches on the phone and builds reproducibly.

## Milestone 1 — Alarm engine proof of concept (1–2 days)
- [ ] Implement schedule/cancel for a one-time alarm using AlarmManager and PendingIntent.
- [ ] Receive trigger in BroadcastReceiver when UI is closed.
- [ ] Present compliant alarm notification / ringing UI on locked phone.
- [ ] Play ringtone and vibrate using a lifecycle-safe mechanism.
- [ ] Support dismiss and snooze; stop audio reliably.
- [ ] Handle permission denial with actionable settings guidance.
Acceptance: a test alarm rings on a locked physical phone, and dismiss/snooze work without duplicate ringing.

## Milestone 2 — Persistent alarm CRUD and repeats (1 day)
- [ ] Room entities, DAO, repository; Compose list and editor.
- [ ] Time, enabled state, label, selected weekdays.
- [ ] Schedule next valid occurrence; after trigger, schedule next repeat.
- [ ] Handle midnight, day rollover, daylight-saving/time-zone rules.
Acceptance: alarms survive app restart and repeats do not create duplicate PendingIntents.

## Milestone 3 — Linked Alarm Groups (1 day)
- [ ] Create/edit groups; assign alarms.
- [ ] Define session key (groupId + local wake date) and occurrence statuses.
- [ ] On explicit awake confirmation, atomically mark/cancel pending occurrences in the current session.
- [ ] Reconcile OS scheduled PendingIntents and database state; handle race with a concurrently firing alarm.
- [ ] Ensure next day's repeating alarms remain scheduled.
Acceptance: confirming the 04:30 alarm cancels 04:35/04:40/04:45 only for that morning; unrelated and next-day alarms still work.

## Milestone 4 — Challenges and confirmation (0.5–1 day)
- [ ] Add optional simple math challenge.
- [ ] Add “Are you awake?” confirmation, explicit choice to dismiss linked alarms or keep them active.
- [ ] Provide accessible alternate/emergency dismiss.
Acceptance: completion routes correctly; the user can always stop an alarm safely.

## Milestone 5 — UX, settings, packaging (1–2 days)
- [ ] Compose Material 3 polish, alarm labels, sound/vibration options, snooze settings.
- [ ] Clear onboarding for notifications/exact alarm/full-screen permissions as relevant.
- [ ] Build installable debug APK; optionally signed release APK.
Acceptance: user can set up and use app without development tools after installation.

## Milestone 6 — Reliability matrix (2–4 days; can overlap)
- [ ] Locked screen and display off.
- [ ] App UI removed from recents; app process killed by system (not force-stop).
- [ ] Battery saver / Doze behavior.
- [ ] Phone reboot, clock change, time-zone change.
- [ ] Notification, exact alarm, and full-screen permission denied/revoked.
- [ ] Simultaneous alarms, group dismissal race, snooze collision.
- [ ] Next-day repeat preserved after group cancellation.
- [ ] Test at least on user's actual Android phone and document OS/device limitations.
Acceptance: observed behavior and remaining limitations documented; no unverified reliability claims.

## Explicitly out of scope for MVP
Cloud sync, accounts, subscriptions, analytics backend, iOS, photo/QR challenges, advanced anti-cheat behavior, device-wide guaranteed behavior.

## Work protocol for each AI coding session
1. Read PROGRESS.txt.
2. Select one unchecked task.
3. Supply full files or focused diffs with exact paths and build commands.
4. Ask user to run and report build/runtime logs.
5. Update PROGRESS.txt with observed (not assumed) results.
6. Commit to Git after each verified milestone.
