package com.wakeguard.alarm

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.wakeguard.alarm.ui.ringing.AlarmRingActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.data.local.AlarmEntity
import java.util.Calendar
import kotlin.math.abs

private val bg = Color(0xFF071126)
private val panel = Color(0xFF14213C)
private val blue = Color(0xFF6485FF)
private val muted = Color(0xFFB2BFDA)
private val allDays = (1..7).toSet()
private val weekdays = setOf(2, 3, 4, 5, 6)
private val weekends = setOf(1, 7)

@Composable
private fun Wheel(values: List<String>, initial: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(0, values.lastIndex))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    LaunchedEffect(state, values) {
        snapshotFlow {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - center)
            }?.index
        }.collect { index ->
            if (index != null && index in values.indices) onValue(index)
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = 56.dp),
        modifier = modifier.height(168.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(values) { index, value ->
            val selected by remember {
                derivedStateOf {
                    val info = state.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index == index
                }
            }
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                Text(value, color = if (selected) Color.White else Color.Gray,
                    fontSize = if (selected) 27.sp else 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun AlarmEditorScreen(
    existing: AlarmEntity?,
    onCancel: () -> Unit,
    onSave: (Int, Int, String, String, Boolean, Int, String, String, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var hour by remember(existing?.id) { mutableIntStateOf(existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(existing?.id) { mutableIntStateOf(existing?.minute ?: now.get(Calendar.MINUTE)) }
    var label by remember(existing?.id) { mutableStateOf(existing?.label?.takeUnless { it == "New Alarm" } ?: "") }
    var vibration by remember(existing?.id) { mutableStateOf(existing?.vibrationEnabled ?: true) }
    var snoozeMinutes by remember(existing?.id) { mutableIntStateOf(existing?.snoozeMinutes ?: 5) }
    var challenge by remember(existing?.id) { mutableStateOf(existing?.challengeType ?: "SWIPE") }
    var difficulty by remember(existing?.id) { mutableStateOf(existing?.mathDifficulty ?: "EASY") }
    var questionCount by remember(existing?.id) { mutableIntStateOf(existing?.mathQuestionCount ?: 3) }
    var shakeCount by remember(existing?.id) { mutableIntStateOf(existing?.shakeCount ?: 10) }
    var selectedDays by remember(existing?.id) {
        mutableStateOf(existing?.repeatDays?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in 1..7 }?.toSet() ?: emptySet())
    }
    var custom by remember(existing?.id) {
        mutableStateOf(selectedDays.isNotEmpty() && selectedDays != allDays &&
            selectedDays != weekdays && selectedDays != weekends)
    }
    var snoozeScreen by remember { mutableStateOf(false) }
    var challengeScreen by remember { mutableStateOf(false) }
    val period = if (hour < 12) 0 else 1
    val displayHour = (hour % 12).let { if (it == 0) 12 else it }
    val repeatDescription = when {
        selectedDays.isEmpty() -> "Ring once"
        selectedDays == allDays -> "Every day"
        selectedDays == weekdays -> "Monday to Friday"
        selectedDays == weekends -> "Saturday and Sunday"
        else -> listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            .filterIndexed { index, _ -> index + 1 in selectedDays }.joinToString(", ")
    }

    if (challengeScreen) {
        ChallengeSettingsScreen(
            challenge = challenge,
            onChallengeChange = { challenge = it },
            difficulty = difficulty,
            onDifficultyChange = { difficulty = it },
            questionCount = questionCount,
            onQuestionCountChange = { questionCount = it },
            shakeCount = shakeCount,
            onShakeCountChange = { shakeCount = it },
            onPreview = {
                context.startActivity(Intent(context, AlarmRingActivity::class.java).apply {
                    putExtra("preview_mode", true)
                    putExtra("alarm_label", label.ifBlank { "Preview alarm" })
                    putExtra("snooze_minutes", snoozeMinutes)
                    putExtra("challenge_type", challenge)
                    putExtra("math_difficulty", difficulty)
                    putExtra("math_question_count", questionCount)
                    putExtra("shake_count", shakeCount)
                })
            },
            onBack = { challengeScreen = false }
        )
        return
    }

    if (snoozeScreen) {
        Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().padding(20.dp)) {
            TextButton(onClick = { snoozeScreen = false }) { Text("‹  Snooze", color = blue, fontSize = 19.sp) }
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).padding(18.dp)) {
                Text("Interval", color = Color.White, fontSize = 17.sp)
                Text("Scroll to choose minutes", color = muted, fontSize = 12.sp)
                Wheel((1..60).map { "$it min" }, (snoozeMinutes - 1).coerceIn(0, 59),
                    onValue = { snoozeMinutes = it + 1 }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(14.dp))
            Text("Snooze interval is configurable from 1 to 60 minutes. A maximum snooze count is not yet supported by the alarm engine.",
                color = muted, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { snoozeScreen = false }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = blue)) { Text("Done") }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("Cancel", color = blue) }
            Text(if (existing == null) "New alarm" else "Edit alarm", color = Color.White,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            TextButton(onClick = {
                onSave(hour, minute, label.ifBlank { "New Alarm" },
                    selectedDays.sorted().joinToString(","), vibration, snoozeMinutes,
                    challenge, difficulty, questionCount, shakeCount)
            }) { Text("Done", color = blue) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Native Compose scrolling hour/minute/AM-PM wheels; no clock-face dialog.
            Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Wheel((1..12).map { it.toString() }, displayHour - 1, { index ->
                    val h = index + 1
                    hour = (h % 12) + if (period == 1) 12 else 0
                }, Modifier.weight(1f))
                Wheel((0..59).map { "%02d".format(it) }, minute, { minute = it }, Modifier.weight(1f))
                Wheel(listOf("AM", "PM"), period, { p ->
                    hour = (hour % 12) + if (p == 1) 12 else 0
                }, Modifier.weight(0.85f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Ring once", "Weekdays", "Weekends", "Daily", "Custom").forEach { name ->
                    val days = when (name) {
                        "Weekdays" -> weekdays
                        "Weekends" -> weekends
                        "Daily" -> allDays
                        else -> emptySet()
                    }
                    val active = if (name == "Custom") custom else !custom && selectedDays == days
                    Box(Modifier.weight(1f).background(if (active) blue else panel,
                        RoundedCornerShape(22.dp)).clickable {
                        if (name == "Custom") {
                            custom = true
                            if (selectedDays.isEmpty()) selectedDays = weekdays
                        } else { custom = false; selectedDays = days }
                    }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text(name, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            if (custom) {
                Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("Repeat", color = Color.White, fontSize = 16.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(repeatDescription, color = blue, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, name ->
                            val day = i + 1
                            Box(Modifier.size(37.dp).background(if (day in selectedDays) blue else Color(0xFF484848),
                                RoundedCornerShape(50)).clickable {
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                            }, contentAlignment = Alignment.Center) {
                                Text(name, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth()
                .background(panel, RoundedCornerShape(18.dp))
                .clickable { challengeScreen = true }
                .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("◎", color = blue, fontSize = 27.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dismissal method", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(when (challenge) {
                        "MATH" -> "Math · ${difficulty.lowercase()} · $questionCount questions"
                        "SHAKE" -> "Shake · $shakeCount movements"
                        else -> "Swipe up"
                    }, color = muted, fontSize = 12.sp)
                }
                Text("›", color = blue, fontSize = 27.sp)
            }
            Column(
                Modifier.fillMaxWidth()
                    .background(panel, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✎", color = blue, fontSize = 22.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Alarm name", color = Color.White,
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Give this alarm a name (optional)",
                            color = Color(0xFFB2BFDA), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("e.g. Morning workout", color = Color(0xFF9CAAC5)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = blue,
                        focusedBorderColor = blue,
                        unfocusedBorderColor = Color(0xFF405273),
                        focusedContainerColor = Color(0xFF0B1830),
                        unfocusedContainerColor = Color(0xFF0B1830)
                    )
                )
            }
            Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("Alarm options", color = Color.White,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(Modifier.padding(top = 12.dp), color = Color(0xFF35445F))
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Ringtone", color = Color.White)
                        Text("Default · picker coming in engine update", color = muted, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Color(0xFF35445F))
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vibrate", color = Color.White)
                    Switch(checked = vibration, onCheckedChange = { vibration = it })
                }
                HorizontalDivider(color = Color(0xFF35445F))
                Row(Modifier.fillMaxWidth().clickable { snoozeScreen = true }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Snooze", color = Color.White)
                        Text("$snoozeMinutes minutes", color = muted, fontSize = 12.sp)
                    }
                    Text("›", color = muted, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ChallengeSettingsScreen(
    challenge: String, onChallengeChange: (String) -> Unit,
    difficulty: String, onDifficultyChange: (String) -> Unit,
    questionCount: Int, onQuestionCountChange: (Int) -> Unit,
    shakeCount: Int, onShakeCountChange: (Int) -> Unit,
    onPreview: () -> Unit, onBack: () -> Unit
) {
    val purple = Color(0xFF9D71F8)
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = blue) }
            Spacer(Modifier.weight(1f))
            Text("Dismissal method", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(54.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("How would you like to stop your alarm?", color = Color.White, fontSize = 17.sp)
        Text("Try a preview before saving your alarm.", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        listOf(
            Triple("SWIPE", "↑", "Swipe up to dismiss"),
            Triple("MATH", "▦", "Answer arithmetic questions"),
            Triple("SHAKE", "▯", "Move your phone to complete")
        ).forEach { (method, symbol, description) ->
            val active = challenge == method
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)
                .background(if (active) Color(0xFF23336A) else panel, RoundedCornerShape(17.dp))
                .clickable { onChallengeChange(method) }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(symbol, fontSize = 30.sp, color = if (active) purple else blue)
                Spacer(Modifier.width(17.dp))
                Column(Modifier.weight(1f)) {
                    Text(method.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White,
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(description, color = muted, fontSize = 12.sp)
                }
                if (active) Text("✓", color = Color(0xFF86AAFF), fontSize = 23.sp)
            }
        }
        if (challenge == "MATH") {
            Spacer(Modifier.height(8.dp))
            Text("Difficulty", color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EASY", "MEDIUM", "HARD").forEach { level ->
                    FilterChip(selected = difficulty == level, onClick = { onDifficultyChange(level) },
                        label = { Text(level.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = blue, selectedLabelColor = Color.White,
                            labelColor = Color.White))
                }
            }
            Text("Questions: $questionCount", color = Color.White)
            Slider(value = questionCount.toFloat(), onValueChange = { onQuestionCountChange(it.toInt().coerceIn(1,10)) },
                valueRange = 1f..10f, steps = 8)
        }
        if (challenge == "SHAKE") {
            Spacer(Modifier.height(8.dp))
            Text("Required movements: $shakeCount", color = Color.White)
            Slider(value = shakeCount.toFloat(), onValueChange = { onShakeCountChange(it.toInt().coerceIn(1,30)) },
                valueRange = 1f..30f, steps = 28)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onPreview, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = blue),
            shape = RoundedCornerShape(28.dp)) { Text("▶  Try preview", color = Color.White) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Continue", color = Color.White) }
    }
}
