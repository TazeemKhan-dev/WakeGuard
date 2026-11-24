package com.wakeguard.alarm.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.wakeguard.alarm.NumberWheelRow
import com.wakeguard.alarm.ui.theme.AlarmDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.ui.theme.AlarmScreenTheme
import com.wakeguard.alarm.ui.theme.ThemeMode
import com.wakeguard.alarm.ui.theme.WakeGuardColors as W

@Composable
fun SettingsScreen(
    alarmDefaults: AlarmDefaults,
    onAlarmDefaultsChange: (AlarmDefaults) -> Unit,
    mode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit,
    alarmMode: AlarmScreenTheme,
    onAlarmModeChange: (AlarmScreenTheme) -> Unit,
    onBack: () -> Unit
) {
    var draft by remember { mutableStateOf(alarmDefaults) }
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
        Spacer(Modifier.height(28.dp))
        Text("Alarm defaults", color = W.text, fontSize = 22.sp)
        Text("Used when creating new alarms. Existing alarms are unchanged.", color = W.secondary, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().background(W.card, RoundedCornerShape(20.dp)).padding(16.dp)) {
            NumberWheelRow("Snooze", "minutes", 1..60, draft.snoozeMinutes) {
                draft = draft.copy(snoozeMinutes = it)
            }
            HorizontalDivider(color = W.outline)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Awake Check", color = W.text)
                    Text("Enabled for new alarms", color = W.secondary, fontSize = 12.sp)
                }
                Switch(checked = draft.awakeCheckEnabled, onCheckedChange = { draft = draft.copy(awakeCheckEnabled = it) })
            }
            NumberWheelRow("Ask after", "minutes", 1..1440, draft.awakeCheckDelayMinutes) {
                draft = draft.copy(awakeCheckDelayMinutes = it)
            }
            HorizontalDivider(color = W.outline)
            NumberWheelRow("Response time", "seconds", 1..3600, draft.awakeCheckTimeoutSeconds) {
                draft = draft.copy(awakeCheckTimeoutSeconds = it)
            }
            Button(onClick = { onAlarmDefaultsChange(draft) }, enabled = draft != alarmDefaults,
                modifier = Modifier.fillMaxWidth()) { Text("Save defaults") }
        }
        Spacer(Modifier.height(18.dp))
    }
}
