package com.wakeguard.alarm.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.wakeguard.alarm.ui.theme.PowerButtonAction
import com.wakeguard.alarm.ui.theme.PowerButtonSettings
import com.wakeguard.alarm.ui.theme.ThemePreferences
import kotlinx.coroutines.launch
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
    var draft by remember(alarmDefaults) { mutableStateOf(alarmDefaults) }
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ThemePreferences(context) }
    val savedPower by preferences.powerButtonSettings.collectAsState(initial = null)
    val savedDuration by preferences.ringingDurationMinutes.collectAsState(initial = null)
    var unlimited by remember(savedDuration) { mutableStateOf((savedDuration ?: 0) == 0) }
    var duration by remember(savedDuration) { mutableIntStateOf(savedDuration?.takeIf { it > 0 } ?: 15) }
    val scope = rememberCoroutineScope()
    var powerDraft by remember(savedPower) { mutableStateOf(savedPower ?: PowerButtonSettings()) }
    Column(Modifier.fillMaxSize().background(W.background).statusBarsPadding().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = W.primary) }
            Spacer(Modifier.weight(1f))
            Text("Settings", color = W.text, fontSize = 21.sp)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(66.dp))
        }
        Spacer(Modifier.height(24.dp))
        SettingsAccordion("Default Alarm Settings") {
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
            AnimatedVisibility(visible = draft != alarmDefaults) {
                Button(onClick = { onAlarmDefaultsChange(draft) }, enabled = draft != alarmDefaults,
                    modifier = Modifier.fillMaxWidth()) { Text("Save defaults") }
            }
        }
        }
        Spacer(Modifier.height(12.dp))
        SettingsAccordion("Ringing Behaviour") {
            Text("Applies to all alarms when they start ringing. Unlimited keeps ringing until you snooze or dismiss, subject to Android system limits.", color = W.secondary, fontSize = 13.sp)
            listOf(true to "Unlimited", false to "Custom duration").forEach { (option, label) ->
                Row(Modifier.fillMaxWidth().clickable { unlimited = option }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = unlimited == option, onClick = { unlimited = option })
                    Text(label, color = W.text)
                }
            }
            if (!unlimited) {
                NumberWheelRow("Stop after", "minutes", 1..1440, duration) { duration = it }
                Text("When this limit is reached, ringing stops without confirming Awake Check.", color = W.secondary, fontSize = 13.sp)
            }
            val selectedDuration = if (unlimited) 0 else duration
            val durationChanged = savedDuration != null && savedDuration != selectedDuration
            AnimatedVisibility(visible = durationChanged) {
                Button(onClick = { scope.launch { preferences.setRingingDurationMinutes(selectedDuration) } },
                    enabled = durationChanged,
                    modifier = Modifier.fillMaxWidth()) { Text("Save ringing behaviour") }
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsAccordion("Power Button Controls") {
            Text("Uses screen-off detection while a real alarm is ringing. Other screen-off events may also trigger this action.", color = W.secondary, fontSize = 13.sp)
            listOf(PowerButtonAction.NONE to "Do nothing", PowerButtonAction.SNOOZE to "Snooze alarm", PowerButtonAction.DISMISS to "Dismiss alarm (Swipe only)").forEach { (option, label) ->
                Row(Modifier.fillMaxWidth().clickable { powerDraft = powerDraft.copy(action = option) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = powerDraft.action == option, onClick = { powerDraft = powerDraft.copy(action = option) })
                    Text(label, color = W.text)
                }
            }
            if (powerDraft.action == PowerButtonAction.SNOOZE) {
                HorizontalDivider(color = W.outline)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Use alarm's snooze duration", color = W.text, modifier = Modifier.weight(1f))
                    Switch(checked = powerDraft.useAlarmDuration, onCheckedChange = { powerDraft = powerDraft.copy(useAlarmDuration = it) })
                }
                if (!powerDraft.useAlarmDuration) {
                    NumberWheelRow("Power button snooze", "minutes", 1..60, powerDraft.snoozeMinutes) {
                        powerDraft = powerDraft.copy(snoozeMinutes = it)
                    }
                }
            }
            if (powerDraft.action == PowerButtonAction.DISMISS) {
                Text("Math, Shake and Memory still require their challenge. Awake Check remains active.", color = W.secondary, fontSize = 13.sp)
            }
            val powerChanged = savedPower != null && powerDraft != savedPower
            AnimatedVisibility(visible = powerChanged) {
                Button(onClick = { val snapshot = powerDraft; scope.launch { preferences.setPowerButtonSettings(snapshot) } },
                    enabled = powerChanged, modifier = Modifier.fillMaxWidth()) { Text("Save power button settings") }
            }
        }
        Spacer(Modifier.height(12.dp))
        SettingsAccordion("App Appearance") {
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
        }
        Spacer(Modifier.height(12.dp))
        SettingsAccordion("Ringing Screen Appearance") {
        Text("Applies to Swipe, Math, Shake, Memory, and previews.", color = W.secondary, fontSize = 13.sp)
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
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SettingsAccordion(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(W.card, RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = W.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(if (expanded) "⌃" else "⌄", color = W.primary, fontSize = 24.sp)
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 16.dp)) { content() }
        }
    }
}
