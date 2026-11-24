# WakeGuard — Architecture and Current File Tree
Updated: 2026-11-21

## Architecture at current checkpoint
Single Android app, Kotlin + Jetpack Compose + Material 3. Room stores alarm definitions; the UI uses `AlarmRepository`; `AlarmScheduler` schedules Android AlarmManager PendingIntents. Receiver validates alarm identity/generation and starts ringing service/full-screen Activity. The foreground service manages ringtone/vibration and snooze/dismiss lifecycle. Compose renders ringing screens and dismissal challenges. DataStore persists independent app and alarm-screen theme selections.

## Known source layout (based on shared files, not a complete filesystem listing)
```text
app/src/main/java/com/wakeguard/alarm/
├── MainActivity.kt
├── AlarmEditorScreen.kt
├── data/
│   ├── local/
│   │   ├── AlarmEntity.kt
│   │   ├── AlarmDao.kt
│   │   └── WakeGuardDatabase.kt
│   └── repository/AlarmRepository.kt
├── domain/scheduler/AlarmScheduler.kt
├── receiver/AlarmReceiver.kt
├── service/AlarmRingingService.kt
└── ui/
    ├── settings/SettingsScreen.kt
    ├── theme/
    │   ├── ThemePreferences.kt
    │   ├── WakeGuardTheme.kt
    │   └── AlarmAppearance.kt     # recent supplied addition; verify installed
    └── ringing/
        ├── AlarmRingActivity.kt
        ├── AlarmRingScreen.kt
        └── challenges/
            ├── ChallengeRouter.kt
            ├── ChallengeLayout.kt
            ├── MathChallengeScreen.kt
            └── ShakeChallengeScreen.kt
```
Other receivers (e.g. reboot/dismiss) and resources may exist; inspect project before treating this as exhaustive. Past duplicate root-level classes caused compilation errors; check if duplicates remain.

## Current alarm data model
Observed `AlarmEntity` fields from shared code include: database `id`, unique `schedulerId`, hour/minute, label, enabled, repeatDays, optional `groupId`, snoozeMinutes, vibrationEnabled, soundUri, `scheduleGeneration`, challengeType, mathDifficulty, mathQuestionCount, shakeCount. Database migrations were added during development; verify exact current schema/version from actual source before changing.

## Core flows
1. **Create/edit:** Alarm Editor -> MainActivity callback -> Room repository -> generation-aware scheduler -> update Compose list.
2. **Toggle/delete:** Persist state in Room, schedule/cancel pending OS alarm; rollback/reload on failures.
3. **Ring:** AlarmManager -> `AlarmReceiver` -> foreground `AlarmRingingService` -> `AlarmRingActivity` -> `ChallengeRouter` -> Swipe/Math/Shake.
4. **Preview:** Alarm Editor -> dismissal method -> `AlarmRingActivity(preview_mode=true)` -> finish back to dismissal settings. No real alarm should be stopped by preview.
5. **Theme:** `ThemePreferences` DataStore holds app theme and alarm theme; `WakeGuardTheme` colors the app; `AlarmAppearance`/ringing palette selects independent ringing colors. Confirm all screens consume selected palette.
6. **Back navigation:** Settings -> home; editor child pages -> editor; preview Activity -> dismissal method; editor -> home. Actual alarm Back must not bypass required dismissal.

## Important design rules
- Alarm engine independent of Compose lifecycle; generation-aware PendingIntent identity and receiver validation.
- Persist before schedule/cancel where practical; avoid stale triggers and duplicate ringing.
- Optional alarm label: empty/default names are not rendered; do not inject `Preview:`.
- Swipe UI uses animated chevrons with no mouse icon; Shake UI uses drawn phone icon and animated progress.
- Preserve challenge callbacks and preview mode while styling.
- Real-world reliability under force-stop, battery restrictions, reboot and permissions is not guaranteed; test explicitly.

## Planned Linked Alarm Groups (not implemented)
Define group entity and occurrence/session identity based on local wake date. On explicit awake confirmation, cancel only still-pending occurrences in that group's current session; do not cancel unrelated alarms or future repeats. Handle races with already-ringing alarms, snoozes, reboot and time changes. Do not infer functionality from `groupId` field alone.

## Ringtone and system UI architecture (2026-11-21)
- `ringtone/WakeGuardSounds.kt`: built-in sound registry, `builtin:` keys, raw audio resource IDs, display title and MediaPlayer playback; existing keys should be preserved when possible.
- `ringtone/RingtoneScreen.kt`: expandable Loud/Medium/Low built-in groups, preview, and custom audio file selection using `ActivityResultContracts.OpenDocument`; persists URI read access where available.
- `AlarmEditorScreen.kt` holds `soundUri` in editor state and passes it through `onSave`; `MainActivity.kt` persists it to Room `AlarmEntity.soundUri`. Actual ringtone playback flows through receiver/service; verify latest installed versions before editing.
- User confirmed custom ringtone filename and Play/Stop preview now work. Current bundled audio quality is not accepted; replacing sounds is separate from UI implementation.
- `ui/theme/WakeGuardTheme.kt` is the active app theme with `ThemeMode` and `WakeGuardColors`; it now controls status bar icon contrast for Light/Dark/System. `Theme.kt` also exists and has a different theme signature; avoid conflating the two.
- Screen-off receiver in `AlarmRingingService` was introduced to approximate power-button dismissal; verify installed code and avoid treating ACTION_SCREEN_OFF as a unique power-key signal.

## Proposed Awake Check architecture (NOT IMPLEMENTED)
- Persist an awake-check occurrence/session record separate from normal alarm and snooze state.
- On genuine successful challenge dismissal, schedule a check at now + configurable delay; show actionable confirmation when due.
- If not confirmed before configurable response deadline, schedule/restart ringing; ensure idempotent event IDs, stale-generation guards, restart/reboot recovery, and notification/full-screen restrictions.
- An explicit "I'm Awake" action completes the check; later, Linked Alarm Groups may consume it to cancel only pending alarms for the current local-date group session.
- Do not rely on Compose timers or a permanently running foreground service for long waits; select permitted Android scheduling primitives after inspecting current engine and OS support.

## Build and verification status
Latest Gradle config, clean build, current phone regression and Git push are **not confirmed**. Inspect project files and build output before declaring release-ready or changing dependency versions.
