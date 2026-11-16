# WakeGuard — Architecture and Current File Tree
Updated: 2026-11-16

## Current architecture
Single Android app module, Kotlin + Jetpack Compose. AlarmManager schedules alarm PendingIntents; receiver starts alarm ringing experience; ringing service manages playback/vibration/timeout; ringing Activity provides dismiss and snooze UI. Room will become the source of truth for alarm definitions; it is NOT yet verified integrated.

## Current tree from user screenshots (partial, not a live filesystem listing)
```text
WakeGuard/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       └── java/com/wakeguard/alarm/
│           ├── MainActivity.kt
│           ├── data/
│           │   ├── local/                 # Room attempt; contents need verification
│           │   └── repository/            # Room attempt; contents need verification
│           ├── domain/
│           │   ├── model/
│           │   └── scheduler/
│           │       └── AlarmScheduler.kt  # observed in screenshot
│           ├── receiver/
│           │   ├── AlarmReceiver.kt       # destination; verify current file
│           │   └── DismissReceiver.kt     # destination; verify current file
│           ├── service/
│           │   └── AlarmRingingService.kt # observed in screenshot
│           └── ui/
│               └── ringing/
│                   └── AlarmRingActivity.kt # observed in screenshot
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

The screenshot history also showed *root-level duplicates* `AlarmReceiver.kt`, `AlarmScheduler.kt`, `AlarmRingingService.kt`, `DismissReceiver.kt`, and `AlarmRingActivity.kt` during Git recovery. Do NOT assume they are still present or that they have been deleted. Confirm with a directory listing / Git status and package declarations before changing anything.

## Room files previously attempted (existence now unverified)
- `data/local/AlarmEntity.kt`
- `data/local/AlarmDao.kt`
- `data/local/WakeGuardDatabase.kt`
- `data/repository/AlarmRepository.kt`
- ViewModel file for Compose integration (exact path/name must be checked)
These were part of the failed integration; do not mark functional until compiled and tested.

## Target data model
- Alarm: stable ID, time, label, enabled, repeat weekdays, optional group ID, snooze minutes, vibration, sound.
- Group: stable ID, name, linked-dismiss setting.
- Occurrence: unique ID, scheduled instant, group wake-date/session key, PENDING/FIRING/SNOOZED/DISMISSED/GROUP_CANCELLED status.

## Target flows
1. UI -> ViewModel/repository -> Room transaction -> AlarmManager schedule/cancel.
2. AlarmReceiver -> validate occurrence -> ringing service/notification/full-screen Activity.
3. Dismiss/snooze -> stop ringing -> update state -> schedule next valid occurrence.
4. Linked group explicit confirmation -> cancel only same-session pending occurrences; keep future repeats.
5. Reboot/time-change receiver -> reconcile Room state with OS alarm schedules.

## Design rules
Keep alarm runtime independent of Compose Activity; unique PendingIntent identity; idempotent receiver; prevent duplicate ringing; respect Android permission and foreground service limits; provide accessible dismiss; test on device.
