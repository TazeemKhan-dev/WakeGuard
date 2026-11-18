package com.wakeguard.alarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Uses the same DataStore for previews and real ringing activities. */
@Composable
fun alarmScreenIsDark(): Boolean {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ThemePreferences(context) }
    val alarmMode by preferences.alarmScreenMode.collectAsState(initial = AlarmScreenTheme.DARK)
    val appMode by preferences.mode.collectAsState(initial = ThemeMode.SYSTEM)
    val systemDark = isSystemInDarkTheme()
    return when (alarmMode) {
        AlarmScreenTheme.DARK -> true
        AlarmScreenTheme.LIGHT -> false
        AlarmScreenTheme.FOLLOW_APP -> when (appMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
        }
    }
}

object AlarmPalette {
    val backgroundDark = Color(0xFF090A0E)
    val backgroundLight = Color(0xFFF7F9FE)
    fun text(dark: Boolean) = if (dark) Color(0xFFF6F6FC) else Color(0xFF111B32)
    fun muted(dark: Boolean) = if (dark) Color(0xFFACADBD) else Color(0xFF68758F)
    fun accent(dark: Boolean) = if (dark) Color(0xFFA49AFF) else Color(0xFF2864F0)
    fun panel(dark: Boolean) = if (dark) Color(0xFF1B1D26) else Color.White
    fun track(dark: Boolean) = if (dark) Color(0xFF36384B) else Color(0xFFDCE5F6)
}
