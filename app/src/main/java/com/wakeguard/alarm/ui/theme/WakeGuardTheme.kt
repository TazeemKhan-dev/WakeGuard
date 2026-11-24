package com.wakeguard.alarm.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** App-wide palette. Ringing activities deliberately do not read this state. */
object WakeGuardColors {
    var isDark by mutableStateOf(false)
        private set
    internal fun update(dark: Boolean) { isDark = dark }
    val background get() = if (isDark) Color(0xFF090A0E) else Color(0xFFF7F9FE)
    val card get() = if (isDark) Color(0xFF1B1D26) else Color.White
    val primary get() = if (isDark) Color(0xFFA49AFF) else Color(0xFF2864F0)
    val text get() = if (isDark) Color(0xFFF6F6FC) else Color(0xFF111B32)
    val secondary get() = if (isDark) Color(0xFFACADBD) else Color(0xFF68758F)
    val subtle get() = if (isDark) Color(0xFF272A35) else Color(0xFFF5F8FF)
    val outline get() = if (isDark) Color(0xFF454858) else Color(0xFFCAD5E8)
    val selected get() = if (isDark) Color(0xFF363149) else Color(0xFFEAF0FF)
}

@Composable
fun WakeGuardTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        WakeGuardColors.update(dark)
        val activity = context.findActivity()
        if (activity != null) {
            // Android calls this "light status bars" when the icons are dark.
            WindowInsetsControllerCompat(activity.window, view)
                .isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = WakeGuardColors.primary,
            onPrimary = Color(0xFF101018),
            background = WakeGuardColors.background,
            surface = WakeGuardColors.card,
            onSurface = WakeGuardColors.text,
            onBackground = WakeGuardColors.text
        ) else lightColorScheme(
            primary = WakeGuardColors.primary,
            background = WakeGuardColors.background,
            surface = WakeGuardColors.card,
            onSurface = WakeGuardColors.text,
            onBackground = WakeGuardColors.text
        ), content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
