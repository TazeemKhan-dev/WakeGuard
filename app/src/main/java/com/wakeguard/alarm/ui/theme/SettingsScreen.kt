package com.wakeguard.alarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.ui.theme.AlarmScreenTheme
import com.wakeguard.alarm.ui.theme.ThemeMode
import com.wakeguard.alarm.ui.theme.WakeGuardColors as W

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    alarmMode: AlarmScreenTheme,
    onAlarmModeChange: (AlarmScreenTheme) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(W.background).statusBarsPadding().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = W.primary) }
            Spacer(Modifier.weight(1f))
            Text("Settings", color = W.text, fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(66.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Appearance", color = W.text, fontSize = 22.sp)
        Text("Choose how WakeGuard looks.", color = W.secondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().background(W.card, RoundedCornerShape(20.dp))) {
            listOf(
                Triple(ThemeMode.LIGHT, "Light", "Studio White"),
                Triple(ThemeMode.DARK, "Dark", "Midnight Black"),
                Triple(ThemeMode.SYSTEM, "System default", "Follow your phone's appearance")
            ).forEachIndexed { index, (option, title, description) ->
                Row(Modifier.fillMaxWidth().clickable { onModeChange(option) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = W.text, fontSize = 16.sp)
                        Text(description, color = W.secondary, fontSize = 12.sp)
                    }
                    RadioButton(selected = mode == option, onClick = { onModeChange(option) })
                }
                if (index < 2) HorizontalDivider(color = W.outline)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Alarm screen appearance", color = W.text, fontSize = 22.sp)
        Text("Applies to Swipe, Math, Shake, and previews.", color = W.secondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().background(W.card, RoundedCornerShape(20.dp))) {
            listOf(
                Triple(AlarmScreenTheme.DARK, "Midnight Black", "Always use the dark ringing screen"),
                Triple(AlarmScreenTheme.LIGHT, "Studio White", "Always use the light ringing screen"),
                Triple(AlarmScreenTheme.FOLLOW_APP, "Follow app theme", "Use the selected app appearance")
            ).forEachIndexed { index, (option, title, description) ->
                Row(Modifier.fillMaxWidth().clickable { onAlarmModeChange(option) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = W.text, fontSize = 16.sp)
                        Text(description, color = W.secondary, fontSize = 12.sp)
                    }
                    RadioButton(selected = alarmMode == option, onClick = { onAlarmModeChange(option) })
                }
                if (index < 2) HorizontalDivider(color = W.outline)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Alarm screen changes apply to new ringing screens and previews.", color = W.secondary, fontSize = 13.sp)
    }
}
