# WakeGuard — Master Context and AI Handoff
Updated: 2026-11-16

## Actual current status
WakeGuard is an Android-only, offline-first alarm app. The user confirms that alarm creation and full-screen ringing have been implemented and worked on their physical realme RMX3868 phone. Dismiss, 5-minute snooze, 15-minute ringing timeout, and vibration were implemented. The project is **now beginning local Room persistence**; do not mark Room complete. After Git restoration and package reorganization, the latest build success and physical-device retest have **not** been confirmed.

## Goal
Defining feature: Linked Alarm Groups. E.g. alarms at 04:30, 04:35, 04:40, 04:45 belong to one wake-up session. After explicit awake confirmation, cancel remaining alarms for that morning only, preserving unrelated alarms and future repeating occurrences.

## Working style
- User wants fast AI-assisted vibe coding, not Android/Kotlin lessons.
- Provide exact paths, full copy/paste-ready files when needed, minimal incremental changes and concrete test steps.
- Preserve working alarm functionality; no unnecessary global Android Studio or SDK changes.
- Verify a build and device behavior before marking milestones complete.
- Use Git commits as stable checkpoints.

## Environment and history
- Windows, Android Studio, physical realme RMX3868; app ID/package `com.wakeguard.alarm`.
- Kotlin, Jetpack Compose, Material 3, AlarmManager, BroadcastReceiver, foreground ringing service, full-screen ringing Activity.
- Recent app `minSdk = 24`; other SDK and plugin versions must be read from current project files, not assumed.
- Room attempt encountered unresolved Room imports (runtime dependencies initially missing), KSP `unexpected jvm signature V`, AGP 9 source-set error, and AGP/Kotlin `BaseExtension` class-cast error.
- A previous attempt proposed changing AGP 9.0.1 / Gradle 9.1 to AGP 8.7.3 / Gradle 8.9, but the user's final restored configuration is **unknown**. Do not repeat version changes without reading actual files.
- Git restoration left duplicate classes in root and refactored packages. User has been reorganizing; actual cleanup and latest build result remain unverified.
- `AlarmRingActivity.kt` had API-27-only `setShowWhenLocked` and `setTurnScreenOn` calls while minSdk was 24. An SDK guard and legacy window flag fallback were suggested; build success not confirmed.

## Scope
Persist alarm definitions locally (Room), support enable/disable, edit/delete, weekday repeats, reboot rescheduling, then linked groups and optional math challenge. Keep alarm engine independent of active UI. Avoid claiming guaranteed ringing after user force-stop or across all device restrictions.

## Next action
1. Obtain current `git status --short`, recent build result and current Gradle files if changing dependencies.
2. Verify there are no duplicate Kotlin classes and that existing ringing still works.
3. Add Room dependencies alone and sync/build; then add entity/DAO/database/repository one small step at a time.
4. Verify alarms persist across app restart before updating progress.

See ARCHITECTURE.md for observed/target tree and DEVELOPMENT_PLAN.md for milestones.
