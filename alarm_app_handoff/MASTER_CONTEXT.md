# WakeGuard — Master Context and AI Handoff
Updated: 2026-11-21

## Project and objective
WakeGuard is an Android-only, offline-first alarm app (`com.wakeguard.alarm`) built in Kotlin, Jetpack Compose and Material 3. The long-term differentiator is **Linked Alarm Groups**: after an explicit awake confirmation, cancel remaining alarms in the same wake-up session *for that day only*, while preserving unrelated alarms and future repeats. This feature is **not yet implemented**.

## Current state (source evidenced, not a release certification)
The source files shared in the November 18 conversation show:
- Alarm home screen: create, edit, toggle, select and delete alarms, next-alarm summary.
- Room-backed persistence via `AlarmEntity`, `WakeGuardDatabase`, DAO/repository; scheduled alarms use `AlarmScheduler` and a `scheduleGeneration` guard. Room integration is now present in source, contrary to the November 16 handoff documents. App-restart persistence and full regression testing still need explicit confirmation.
- Repeat-day choices (once, weekdays, weekends, daily, custom), configurable 1–60 minute snooze, vibration and optional label.
- Ringing foreground service/full-screen Activity; swipe dismissal, math questions, shake challenge; preview launches `AlarmRingActivity` with `preview_mode=true`.
- Theme settings: Studio White, Midnight Black, System for the app, plus an independent alarm-screen theme (Midnight Black, Studio White, Follow app) persisted using DataStore. The alarm theme is intended to cover Swipe/Math/Shake and previews.
- Navigation work: Settings and Alarm Editor return to home; nested dismissal method and snooze pages have a Compose `BackHandler`; preview is a separate Activity and should finish back to dismissal settings.
- Latest UI polish: animated upward chevrons on Swipe, **no mouse/hand icon**; phone-shaped motion indicator and animated progress on Shake; empty/default alarm names should be hidden and no `Preview:` prefix should appear. These changes were delivered as replacement files and should be verified against the current project tree.

**Verification boundary:** No latest Gradle build output, comprehensive physical-phone retest, or successful Git push was supplied in this conversation. Do not mark these as confirmed. Some recent replacement files were provided separately; inspect actual on-disk files before assuming all edits were applied.

## Technical architecture
- UI: `MainActivity.kt`, `AlarmEditorScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/theme/*`.
- Persistence: `data/local/AlarmEntity.kt`, `AlarmDao`, `WakeGuardDatabase` (Room); `data/repository/AlarmRepository`.
- Scheduling: `domain/scheduler/AlarmScheduler.kt`, `receiver/AlarmReceiver.kt`.
- Runtime: `service/AlarmRingingService.kt`, `ui/ringing/AlarmRingActivity.kt`, `AlarmRingScreen.kt`.
- Challenges: `ui/ringing/challenges/ChallengeRouter.kt`, `ChallengeLayout.kt`, `MathChallengeScreen.kt`, `ShakeChallengeScreen.kt`.
- Preferences: `ThemePreferences.kt` uses AndroidX DataStore Preferences. A shared `AlarmAppearance.kt` was introduced for ringing palette resolution.
- Room alarm model includes scheduler ID, generation, repeat days, snooze, vibration, challenge type/difficulty/question count/shake count and a `groupId` field. A field is **not** evidence that linked-group behavior exists.

## Important behavior and design constraints
- Preserve reliable scheduling, generation-aware cancellation, foreground ringing, snooze, vibration, and challenge completion when changing UI.
- Actual ringing alarms must not be silently dismissed by system Back. Preview can exit normally.
- Empty alarm name means no label text on any ringing/preview screen; avoid placeholders such as `New Alarm` and `Preview alarm`.
- Swipe uses animated chevrons without a mouse/hand icon; Shake uses a proper phone graphic rather than a rectangle glyph.
- Do not change AGP/Kotlin/Gradle versions without inspecting current project config. Previous KSP/AGP errors included `unexpected jvm signature V`, source-set conflict, and `BaseExtension` cast failure.
- Min SDK was previously reported as 24; recheck actual Gradle files before making compatibility claims.

## Workflow preferences
Windows + Android Studio, physical realme RMX3868 test device. Fast AI-assisted coding, complete copy/paste-ready source files with exact paths, minimal scoped changes. Verify build and phone behavior before declaring a milestone complete. Use Git commits as checkpoints.

## Updates confirmed in conversation through 2026-11-21
- One-time alarms previously remained visually ON after firing; the user confirmed the Room Flow observation / one-time disable fix works. Preserve it.
- Editing an existing alarm to a new time should turn it ON; latest inspected `MainActivity.kt` explicitly sets `isEnabled = true` and schedules an incremented generation. Full device regression still needed.
- Ringtone screen now has three expandable sections (Loud / Medium / Low) and a separate custom audio picker; device ringtone category removed.
- Custom audio filename/selection/Play-Stop preview fix was reported **working** by user. Do not overwrite with an older `RingtoneScreen.kt` or `AlarmEditorScreen.kt`.
- Bundled ringtone recordings remain **unsatisfactory** to user (bee-like, single-tone). A new collection must use genuinely musical, lyric-free arrangements; approve samples before packaging; check commercial redistribution rights.
- Light Mode status bar icons were nearly invisible. Updated active `ui/theme/WakeGuardTheme.kt` to set system bar icon contrast; user confirmed **"ok good"**. `Theme.kt` is an older/different overload; do not accidentally replace the active `WakeGuardTheme(mode: ThemeMode)`.
- A screen-off broadcast dismissal approximation was added for power-button request; Android does not expose power key reliably, and screen-off can occur for other reasons. Earlier generated code had an invalid Intent constructor; a corrective snippet was provided. Recheck actual working source and test carefully.
- Vibration was reported nonfunctional earlier and intentionally deferred.
- Build output for the current latest full tree, complete device regression and Git push remain **unconfirmed**. A prior screenshot showed build errors during an earlier patch; do not claim a clean build solely from subsequent UI success.

## Upcoming feature — Awake Check (not implemented)
After challenge dismissal, wait a user-configurable X minutes, show "Are you still awake?", accept "I'm Awake", and ring again after a configurable no-response interval. Separate from snooze; must persist and schedule reliably without an open Compose screen. Once verified, Linked Alarm Groups can consume awake confirmation to cancel only remaining same-day group alarms.

## Immediate next actions
1. In project root run `git status --short`, `git log -1 --oneline`, and `.\gradlew.bat :app:assembleDebug`.
2. Confirm current source incorporates the latest UI, label, theme and Back-navigation replacements; resolve any compile errors without broad dependency upgrades.
3. On the phone test create/edit/delete/toggle, persistence across restart, repeating alarms, lock-screen ringing, snooze, vibration, Swipe/Math/Shake, preview return navigation, and theme persistence.
4. Commit and push after successful verification; record commit hash and branch in `PROGRESS.txt`.
5. Design and implement Awake Check after the reliability regression, then Linked Alarm Groups.

See `ARCHITECTURE.md`, `DEVELOPMENT_PLAN.md`, and `PROGRESS.txt`.
