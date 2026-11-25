# WakeGuard — Architecture and Current File Tree

Updated: 2026-11-25

## Source layout
Derived from latest supplied ZIP plus three new Quran files. Physical paths, not package declarations; AwakeCheckManager.kt is physically at root but declares `com.wakeguard.alarm.awake`; SettingsScreen.kt is physically under ui/theme. Not a fresh local filesystem export.

```text
app/src/main/java/com/wakeguard/alarm/
├── AlarmEditorScreen.kt
├── AwakeCheckManager.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AlarmDao.java
│   │   ├── AlarmEntity.kt
│   │   └── WakeGuardDatabase.java
│   └── repository/
│       └── AlarmRepository.kt
├── domain/
│   └── scheduler/
│       └── AlarmScheduler.kt
├── linked/
│   └── LinkedAlarmManager.kt
├── receiver/
│   ├── AlarmBootReceiver.kt
│   ├── AlarmReceiver.kt
│   ├── AwakeCheckReceiver.kt
│   └── DismissReceiver.kt
├── ringtone/
│   ├── RingtoneScreen.kt
│   └── WakeGuardSounds.kt
├── service/
│   ├── AlarmRingingService.kt
│   └── RingingSessionStore.kt
├── ui/
│   ├── ringing/
│   │   ├── AlarmRingActivity.kt
│   │   ├── AlarmRingScreen.kt
│   │   ├── AwakeCheckActivity.kt
│   │   ├── SwipeUpControl.kt
│   │   └── challenges/
│   │       ├── ChallengeLayout.kt
│   │       ├── ChallengeRouter.kt
│   │       ├── MathChallengeScreen.kt
│   │       ├── MemoryChallengeScreen.kt
│   │       ├── MemoryChallengeState.kt
│   │       ├── QuranAyahs.kt
│   │       ├── QuranTypingConfig.kt
│   │       ├── QuranTypingScreen.kt
│   │       └── ShakeChallengeScreen.kt
│   └── theme/
│       ├── AlarmAppearance.kt
│       ├── Color.kt
│       ├── SettingsScreen.kt
│       ├── Theme.kt
│       ├── ThemePreferences.kt
│       ├── Type.kt
│       └── WakeGuardTheme.kt
└── upcoming/
    ├── UpcomingAlertsManager.kt
    └── UpcomingAlertsScreen.kt
```

## Components and flows
- MainActivity/AlarmEditorScreen -> AlarmRepository -> Room AlarmEntity/AlarmDao.java/WakeGuardDatabase.java -> AlarmScheduler -> AlarmReceiver -> AlarmRingingService -> AlarmRingActivity -> ChallengeRouter. Preserve scheduler identity and generation guards.
- RingingSessionStore tracks runtime session state; inspect actual recovery behavior before changes. AlarmBootReceiver handles reboot recovery; DismissReceiver handles notification actions.
- AwakeCheckManager uses separate SharedPreferences check records and scheduled prompt/timeout/confirm actions via AwakeCheckReceiver and AwakeCheckActivity. Explicit confirmation feeds LinkedAlarmManager; preserve unrelated and future occurrences.
- UpcomingAlertsManager/Screen expose pending snooze and check state; cancellation must not implicitly confirm a linked session.
- ThemePreferences DataStore holds appearance, alarm defaults, power settings and ringing duration; SettingsScreen accordions edit these. AlarmAppearance/WakeGuardTheme provide palettes; Theme.kt is a separate overload.
- AlarmRingScreen contains redesigned Swipe UI; SwipeUpControl.kt exists in baseline, verify usage before removing. ChallengeLayout and Router integrate Math, Shake, Memory and Quran Typing.
- QuranTypingConfig encodes `QURAN_TYPING:<length>:<rounds>` in existing challengeType string; no Room migration. QuranAyahs contains 22 passages; QuranTypingScreen validates English input and highlights original text. Latest screen is quran_highlight_fix replacement, collection is quran_refined replacement.
- RingtoneScreen and WakeGuardSounds manage built-in keys/custom URI and preview; preserve persisted soundUri and URI access.
- Screen-off receiver approximates single power press only while ringing; cannot uniquely identify hardware key. Double press dropped. Unlimited duration is preference-driven; verify lifecycle and system limits.

## Current checkpoint — 2026-11-25
Source baseline: app(20261125-023822).zip plus subsequent individual replacements. The current local Android Studio tree has not been re-exported. User screenshots demonstrate Quran Typing running; user approved the latest punctuation highlighting. Full current build output, regression results and Git hash/push remain unconfirmed.

- Core: Room persistence, generation-aware AlarmManager scheduling, create/edit/delete/toggle, Once/Weekdays/Weekends/Daily/Custom, reboot recovery, full-screen foreground ringing, snooze and optional labels. Earlier core tests passed; overlap previously failed and needs retest.
- Awake Check: configurable delay and response timeout, explicit I'm Awake, re-ring on ignore; distinct from snooze. Earlier device tests passed.
- Linked Alarm Groups: multi-select linking, group enable/disable, skip today/tomorrow, explicit awake confirmation skips remaining current-session alarms only. Earlier group tests passed; snooze or challenge completion alone must not skip remaining alarms.
- Memory: configurable grid, highlights, memorize time and rounds; wrong answer retries with a new pattern. Five dismissal methods now implemented: Swipe, Math, Shake, Memory, Quran Typing.
- Alarm editor: infinite hour/minute wheels, inline number pickers, global defaults for new alarms (individual overrides retained). Defaults include snooze and Awake Check.
- Upcoming Alerts: pending snoozes and Awake Checks, countdowns, individual cancellation/confirmation and clear-all confirmation. One-time snooze cancellation should disable the alarm; recurring schedules must remain intact. Retest latest patches.
- Skip UI: temporarily skipped alarms appear OFF, manual ON clears skip, editing clears skip and re-enables/reschedules even if time unchanged. Custom skip dialog polish delivered; retest.
- Power controls: Do nothing (default), Snooze (alarm duration or custom), Dismiss (Swipe only). Screen-off/on observed during real ringing on realme; not a physical key API. Double press dropped because camera shortcut interferes. Power-button I'm Awake confirmation for Swipe remains pending.
- Ringing duration: global Unlimited default or custom minutes, replacing old fixed 15-minute timeout. Long-duration reliability and custom timeout lifecycle unverified. Swipe redesign delivered with draggable handle, animation and live clock.
- Settings: independent accordions, Save buttons only for unsaved changes, disappear on save/revert; appearance auto-saves. DataStore persists preferences.
- Quran Typing: Arabic passage, English meaning and reference; English-only input; Short/Medium/Mixed and 1–5 reminders. 22 curated passages (including consecutive verses), offline; no isolated context-dependent fragments intended. Inline feedback, optional punctuation/case, correct punctuation highlights. User approved latest UI/feedback; full content proofreading and regression pending. No new challenges planned.
- Ringtones: Loud/Medium/Low and custom picker; custom preview and light status bar previously confirmed. Bundled music quality remains open; vibration previously failed and deferred.

## Next work (priority order)
1. Inspect fresh source/actual installed replacements and run Windows `git status --short`, `git log -1 --oneline`, `.\gradlew.bat :app:assembleDebug`.
2. Fix one-time alarm state/countdown: observed Ring Once still ON after unattended ringing stopped, home displayed tomorrow despite no verified future occurrence. Distinguish active, snoozed, awaiting check, completed and scheduled; do not invent tomorrow for consumed Once. Preserve recurring and linked sessions.
3. Implement optional single power-button I'm Awake confirmation for Swipe only; validate active check token and use normal confirmation path. Do not bypass other challenges; no double-press feature.
4. Verify Unlimited beyond old 15-minute limit and longer supervised tests, custom timeout, screen-off/background, battery restrictions, process death, reboot, overlapping alarms, stale events and permissions. Android cannot guarantee indefinite ringing; ensure timeout does not falsely confirm awake or skip linked alarms.
5. Regression: all five challenges, previews, snooze, Awake Check, linked groups, upcoming cancellation, skip/edit, settings persistence, themes, ringtone and vibration. Verify Quran content against reliable references before release.
6. Commit/push verified checkpoint and record actual branch/hash; no speculative Gradle/dependency changes. Later: approved musical ringtone collection, accessibility, onboarding and release polish.
