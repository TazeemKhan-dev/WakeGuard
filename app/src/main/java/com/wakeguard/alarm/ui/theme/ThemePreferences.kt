package com.wakeguard.alarm.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wakeGuardThemeDataStore by preferencesDataStore(name = "wakeguard_appearance")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class AlarmScreenTheme { DARK, LIGHT, FOLLOW_APP }

data class AlarmDefaults(
    val snoozeMinutes: Int = 5,
    val awakeCheckEnabled: Boolean = true,
    val awakeCheckDelayMinutes: Int = 2,
    val awakeCheckTimeoutSeconds: Int = 60
)

enum class ChallengeSoundMode { NORMAL, REDUCED, MUTED }

data class ChallengeSoundSettings(
    val mode: ChallengeSoundMode = ChallengeSoundMode.NORMAL,
    val volumePercent: Int = 20
) {
    val playerVolume: Float get() = when (mode) {
        ChallengeSoundMode.NORMAL -> 1f
        ChallengeSoundMode.REDUCED -> volumePercent.coerceIn(10, 50) / 100f
        ChallengeSoundMode.MUTED -> 0f
    }
}

enum class PowerButtonAction { NONE, SNOOZE, DISMISS }

data class PowerButtonSettings(
    val action: PowerButtonAction = PowerButtonAction.NONE,
    val useAlarmDuration: Boolean = true,
    val snoozeMinutes: Int = 5
)

class ThemePreferences(private val context: Context) {
    private val challengeSoundModeKey = stringPreferencesKey("challenge_sound_mode")
    private val challengeVolumeKey = intPreferencesKey("challenge_sound_volume")
    val challengeSoundSettings: Flow<ChallengeSoundSettings> = context.wakeGuardThemeDataStore.data.map { p ->
        ChallengeSoundSettings(
            runCatching { ChallengeSoundMode.valueOf(p[challengeSoundModeKey] ?: "NORMAL") }.getOrDefault(ChallengeSoundMode.NORMAL),
            (p[challengeVolumeKey] ?: 20).coerceIn(10, 50)
        )
    }
    suspend fun setChallengeSoundSettings(settings: ChallengeSoundSettings) {
        context.wakeGuardThemeDataStore.edit {
            it[challengeSoundModeKey] = settings.mode.name
            it[challengeVolumeKey] = settings.volumePercent.coerceIn(10, 50)
        }
    }
    private val ringingDurationKey = intPreferencesKey("ringing_duration_minutes")
    // Zero means no automatic timeout.
    val ringingDurationMinutes: Flow<Int> = context.wakeGuardThemeDataStore.data.map {
        (it[ringingDurationKey] ?: 0).coerceIn(0, 1440)
    }
    suspend fun setRingingDurationMinutes(minutes: Int) {
        context.wakeGuardThemeDataStore.edit { it[ringingDurationKey] = minutes.coerceIn(0, 1440) }
    }
    private val powerActionKey = stringPreferencesKey("power_button_action")
    private val powerUseAlarmKey = booleanPreferencesKey("power_button_use_alarm_duration")
    private val powerMinutesKey = intPreferencesKey("power_button_snooze_minutes")
    val powerButtonSettings: Flow<PowerButtonSettings> = context.wakeGuardThemeDataStore.data.map { p ->
        PowerButtonSettings(
            runCatching { PowerButtonAction.valueOf(p[powerActionKey] ?: "NONE") }.getOrDefault(PowerButtonAction.NONE),
            p[powerUseAlarmKey] ?: true,
            (p[powerMinutesKey] ?: 5).coerceIn(1, 60)
        )
    }
    suspend fun setPowerButtonSettings(settings: PowerButtonSettings) {
        context.wakeGuardThemeDataStore.edit {
            it[powerActionKey] = settings.action.name
            it[powerUseAlarmKey] = settings.useAlarmDuration
            it[powerMinutesKey] = settings.snoozeMinutes.coerceIn(1, 60)
        }
    }
    private val snoozeKey = intPreferencesKey("default_snooze_minutes")
    private val awakeEnabledKey = booleanPreferencesKey("default_awake_enabled")
    private val awakeDelayKey = intPreferencesKey("default_awake_delay_minutes")
    private val awakeTimeoutKey = intPreferencesKey("default_awake_timeout_seconds")
    val alarmDefaults: Flow<AlarmDefaults> = context.wakeGuardThemeDataStore.data.map { p ->
        AlarmDefaults(
            (p[snoozeKey] ?: 5).coerceIn(1, 60),
            p[awakeEnabledKey] ?: true,
            (p[awakeDelayKey] ?: 2).coerceIn(1, 1440),
            (p[awakeTimeoutKey] ?: 60).coerceIn(1, 3600)
        )
    }
    suspend fun setAlarmDefaults(defaults: AlarmDefaults) {
        context.wakeGuardThemeDataStore.edit {
            it[snoozeKey] = defaults.snoozeMinutes.coerceIn(1, 60)
            it[awakeEnabledKey] = defaults.awakeCheckEnabled
            it[awakeDelayKey] = defaults.awakeCheckDelayMinutes.coerceIn(1, 1440)
            it[awakeTimeoutKey] = defaults.awakeCheckTimeoutSeconds.coerceIn(1, 3600)
        }
    }
    private val key = stringPreferencesKey("theme_mode")
    private val alarmKey = stringPreferencesKey("alarm_screen_theme")
    val alarmScreenMode: Flow<AlarmScreenTheme> = context.wakeGuardThemeDataStore.data.map { preferences ->
        runCatching { AlarmScreenTheme.valueOf(preferences[alarmKey] ?: AlarmScreenTheme.DARK.name) }
            .getOrDefault(AlarmScreenTheme.DARK)
    }
    val mode: Flow<ThemeMode> = context.wakeGuardThemeDataStore.data.map { preferences: Preferences ->
        runCatching { ThemeMode.valueOf(preferences[key] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }
    suspend fun setAlarmScreenMode(mode: AlarmScreenTheme) {
        context.wakeGuardThemeDataStore.edit { it[alarmKey] = mode.name }
    }
    suspend fun setMode(mode: ThemeMode) {
        context.wakeGuardThemeDataStore.edit { it[key] = mode.name }
    }
}
