# WakeGuard — Development Plan

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

## Milestone ledger
| Milestone | Status |
|---|---|
| 0–5 Setup, core, Room, repeats, challenges, appearance/audio | Implemented; retest |
| 6 Reliability | Ongoing; overlap and vibration open |
| 7 Awake Check | Implemented; earlier tests passed |
| 8.3–8.4 Linked groups | Implemented; earlier tests passed |
| 8.5 Memory | Implemented |
| 8.6 Alarm defaults and editor polish | Delivered |
| 8.7 Upcoming Alerts and skip/edit fixes | Delivered; regression pending |
| 8.8 Power controls, ringing duration, Swipe and settings polish | Delivered in part; awake confirmation pending |
| 8.9 Quran Typing | Implemented; latest feedback approved; regression/content review pending |
| 9 Stability and release polish | Next |

## Next work (priority order)
1. Inspect fresh source/actual installed replacements and run Windows `git status --short`, `git log -1 --oneline`, `.\gradlew.bat :app:assembleDebug`.
2. Fix one-time alarm state/countdown: observed Ring Once still ON after unattended ringing stopped, home displayed tomorrow despite no verified future occurrence. Distinguish active, snoozed, awaiting check, completed and scheduled; do not invent tomorrow for consumed Once. Preserve recurring and linked sessions.
3. Implement optional single power-button I'm Awake confirmation for Swipe only; validate active check token and use normal confirmation path. Do not bypass other challenges; no double-press feature.
4. Verify Unlimited beyond old 15-minute limit and longer supervised tests, custom timeout, screen-off/background, battery restrictions, process death, reboot, overlapping alarms, stale events and permissions. Android cannot guarantee indefinite ringing; ensure timeout does not falsely confirm awake or skip linked alarms.
5. Regression: all five challenges, previews, snooze, Awake Check, linked groups, upcoming cancellation, skip/edit, settings persistence, themes, ringtone and vibration. Verify Quran content against reliable references before release.
6. Commit/push verified checkpoint and record actual branch/hash; no speculative Gradle/dependency changes. Later: approved musical ringtone collection, accessibility, onboarding and release polish.
