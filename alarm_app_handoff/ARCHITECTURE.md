# Alarm App — Technical Architecture
Last updated: 2026-11-15
Status: Proposed design, not yet implemented.

## Architecture principles
- Single-module Android app initially; keep layers clear but avoid unnecessary abstractions.
- Compose UI -> ViewModel -> use cases/repository -> Room + Android scheduler.
- Alarm runtime (BroadcastReceiver / alarm playback service / notification) must not depend on an active Activity or ViewModel.
- Persist desired state in Room and reconcile OS alarms from it; do not treat AlarmManager as the database.
- Offline-first; no server for alarms.

## Proposed package tree
app/src/main/java/com/example/wakeguard/
  MainActivity.kt
  data/local/{AppDatabase.kt,AlarmDao.kt,GroupDao.kt,OccurrenceDao.kt}
  data/local/entity/{AlarmEntity.kt,AlarmGroupEntity.kt,AlarmOccurrenceEntity.kt}
  data/repository/{AlarmRepository.kt,GroupRepository.kt}
  domain/{model/,usecase/}
  alarm/{AlarmScheduler.kt,AlarmReceiver.kt,AlarmRingingService.kt,BootReceiver.kt,AlarmNotificationManager.kt,ScheduleReconciler.kt}
  presentation/{home/,editor/,groups/,ringing/,challenge/,settings/}
  di/ (only if needed)

## Data model (draft)
AlarmEntity:
- id: UUID/string primary key
- hour, minute: integer local time
- label: string
- enabled: boolean
- repeatDays: set of ISO weekdays (store via converter or join table)
- soundUri: nullable string
- vibrate: boolean
- groupId: nullable FK
- challengeType: NONE | MATH
- snoozeMinutes: integer

AlarmGroupEntity:
- id: UUID/string primary key
- name: string
- linkedDismissEnabled: boolean
- verificationRequired: boolean

AlarmOccurrenceEntity:
- id: unique stable ID
- alarmId: FK
- scheduledAt: epoch millis (instant)
- sessionKey: groupId + local wake-date (or alarmId/date for ungrouped)
- status: PENDING | FIRING | SNOOZED | DISMISSED | GROUP_CANCELLED | MISSED
- triggeredAt/dismissedAt: nullable epoch millis
- requestCode or stable PendingIntent identity

Add schema migrations once a release is used; timestamps and session boundaries must be well defined.

## Trigger flow
1. User creates/updates alarm in Compose UI.
2. ViewModel validates and calls repository/use case.
3. Room transaction persists alarm and planned next occurrence(s).
4. Scheduler registers OS alarm using AlarmManager.setAlarmClock as appropriate; unique PendingIntent per occurrence.
5. At trigger, AlarmReceiver checks Room occurrence state and transitions PENDING -> FIRING idempotently.
6. Start permitted ringing experience: notification/full-screen intent as policy allows, sound/vibration managed by service or compliant equivalent.
7. Dismiss/snooze action updates Room, stops ringing, cancels/replaces pending OS schedule, and schedules next repeat if applicable.

## Linked group flow
Example: alarms 04:30, 04:35, 04:40, 04:45; group G; session G|2026-11-16.
- On challenge completion, ask for explicit wake-up confirmation.
- If NO: dismiss/snooze current alarm as selected; retain future occurrences.
- If YES: transactionally mark remaining PENDING/SNOOZED occurrences with matching sessionKey as GROUP_CANCELLED, and mark current occurrence DISMISSED.
- Cancel OS PendingIntents for those occurrence IDs. Receiver checks database state before ringing, so a race cannot resurrect a cancelled occurrence.
- Recalculate next repeating occurrence for each definition; never disable alarm definitions simply to dismiss one session.
- Different groups and other dates are unaffected.
- Define handling of an already-FIRING second alarm (e.g. stop it only with explicit group action) before implementation.

## Android components and permissions to evaluate
- AlarmManager + PendingIntent; use setAlarmClock for user-facing alarm-clock events where appropriate.
- Exact alarm access: evaluate USE_EXACT_ALARM versus SCHEDULE_EXACT_ALARM against app category, Android version and Play policy. Do not request both indiscriminately.
- POST_NOTIFICATIONS on Android 13+ where needed.
- USE_FULL_SCREEN_INTENT / full-screen notification eligibility on newer Android versions.
- Foreground service type, declaration and start restrictions for alarm playback under selected target SDK.
- RECEIVE_BOOT_COMPLETED; reconcile alarms after boot. Also evaluate TIME_SET, TIMEZONE_CHANGED, package replace and permission change recovery.
- Keyguard/lock-screen presentation via supported APIs, not deprecated bypasses.

## Reliability and safety requirements
- Never promise operation after user force-stop; Android can suppress delivery.
- No reliance on WorkManager for exact ringing time; use it only for suitable non-exact housekeeping.
- Prevent duplicate alarm triggers and multiple ringing services.
- Ensure audio focus, volume, vibration, cleanup, notification channels, and accessibility.
- User must have an accessible way to stop a ringing alarm even if a challenge fails.
- Use stable IDs, transactions, and explicit occurrence states to prevent cancellation races.
- Test physical device before declaring milestone done.

## Testing strategy
Unit tests: next-occurrence calculations, weekday repeats, group session cancellation, duplicate triggers, snooze.
Instrumentation/device tests: locked screen, process death, reboot, notification and exact-alarm permissions, overlapping alarms, repeat preservation.

## Open implementation decisions
- Minimum SDK and target SDK versions (choose at project setup).
- Hilt vs manual dependency wiring (prefer simplicity).
- Service implementation details for current Android policy.
- Exact alarm permission model based on distribution plan.
- UI design and app name (WakeGuard is placeholder only).
