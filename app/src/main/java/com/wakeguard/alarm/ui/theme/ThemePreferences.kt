package com.wakeguard.alarm.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wakeGuardThemeDataStore by preferencesDataStore(name = "wakeguard_appearance")

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class AlarmScreenTheme { DARK, LIGHT, FOLLOW_APP }

class ThemePreferences(private val context: Context) {
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
