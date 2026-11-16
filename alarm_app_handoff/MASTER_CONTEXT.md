# Alarm App — Master Context and AI Handoff
Last updated: 2026-11-15
Status: Planning complete enough to begin implementation; NO CODE WRITTEN OR TESTED.

## Purpose
Build an Android-only, offline-first alarm clock inspired by Alarmy for personal use, without premium dependencies. The defining feature is **Linked Alarm Groups**: e.g. 04:30, 04:35, 04:40, 04:45 are associated with one morning wake-up session; after the user completes an optional challenge and explicitly confirms they are awake, cancel the remaining pending occurrences for THAT session only. Future days and unrelated groups must remain intact.

## User preferences and constraints
- User has a Windows PC and an Android phone for testing.
- Wants AI-assisted “vibe coding”; does NOT want a Kotlin/Android learning course.
- Prioritize generating working code, precise file paths, commands, and troubleshooting.
- Android only; no iOS requirement.
- Personal MVP as fast as practical, without compromising core alarm reliability.
- No backend, login, subscription, or cloud dependency for MVP.
- Prior estimate: ~5–10 focused days for a usable personal MVP, highly uncertain; testing across Android devices may extend this.

## Proposed stack (not yet implemented)
Kotlin, Jetpack Compose, Material 3, single Android Gradle module, MVVM + repositories, Room, DataStore, Coroutines/Flow, AlarmManager, PendingIntent, BroadcastReceiver, foreground service for active alarm playback where allowed, Hilt optional (defer if it slows setup), Navigation Compose. Use stable current Android SDK and dependency versions, verify them when implementation begins.

## MVP scope
1. Add/edit/delete/enable/disable alarm, time and weekday repeats.
2. Alarm scheduling and ringing on a locked device, including appropriate sound/vibration and snooze/dismiss.
3. Linked alarm groups with configurable membership.
4. Optional simple math challenge and explicit awake confirmation.
5. Group dismiss cancels remaining occurrences in the same wake-up session, not future repeats.
6. Basic settings, clear permissions onboarding, reliable restart/reschedule behavior.

## Key product decisions
- Alarm definitions, scheduled occurrences, and groups are separate concepts.
- Alarm dismissal and group dismissal are different actions.
- A wake-up confirmation is a user action, not proof of physiological wakefulness.
- Default to keeping later alarms active unless the user explicitly confirms group dismissal.
- Include an accessible emergency/ordinary dismissal path; do not trap users in challenges.
- Preserve future repeating alarms when cancelling today's pending group occurrences.
- The system must work offline and must not rely on UI process staying alive.

## Android reality checks
- Use AlarmManager.setAlarmClock for genuine user-facing alarm-clock events when appropriate, with unique PendingIntent identity for each occurrence.
- Review exact-alarm access/permissions and policy for the chosen target SDK; do not assume all devices or permissions behave alike.
- Full-screen intents and alarm foreground services are restricted on recent Android releases; implement compliant notification and screen behavior.
- After reboot, time/time-zone changes, permission changes, and app upgrades, reconcile persisted occurrences and OS schedules as applicable.
- A user force-stop may block scheduled behavior until the app is launched again. Never promise guaranteed ringing in every state.
- Test on the actual phone, screen locked, app UI swiped away, Doze/battery saver, reboot, overlaps, repeat days, and timezone change.

## Instructions to next AI assistant
1. Read MASTER_CONTEXT.md, DEVELOPMENT_PLAN.md, ARCHITECTURE.md, PROGRESS.txt.
2. Inspect existing project files if any. Do not assume features exist unless code and tests show it.
3. Work on the next unchecked milestone. Give copy/paste-ready files and exact paths, not abstract lessons.
4. Make the smallest testable increment. Tell the user exactly what to run and what output to report.
5. After each increment, update PROGRESS.txt with date, completed files, verification results, unresolved errors, next action.
6. Keep dependencies lean and avoid speculative platform guarantees.
7. If design decisions change, update architecture and development plan together.

## Next action
Confirm Android Studio installation and Android phone version, create a fresh Compose app, run on physical device, then implement one single alarm end-to-end before adding linked groups.
