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

class ThemePreferences(private val context: Context) {
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
