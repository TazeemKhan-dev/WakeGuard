package com.wakeguard.alarm

import android.content.Intent
import com.wakeguard.alarm.ui.ringing.challenges.QuranTypingConfig
import com.wakeguard.alarm.ringtone.RingtoneScreen
import com.wakeguard.alarm.ringtone.ringtoneDisplayName
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.wakeguard.alarm.ui.ringing.AlarmRingActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.wakeguard.alarm.ui.theme.WakeGuardColors as W
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.data.local.AlarmEntity
import com.wakeguard.alarm.ui.theme.AlarmDefaults
import java.util.Calendar
import kotlin.math.abs

private val bg get() = W.background
private val panel get() = W.card
private val blue get() = W.primary
private val muted get() = W.secondary
private val allDays = (1..7).toSet()
private val weekdays = setOf(2, 3, 4, 5, 6)
private val weekends = setOf(1, 7)

@Composable
private fun Wheel(values: List<String>, initial: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier, infinite: Boolean = false, rowHeight: Int = 56, itemContent: (@Composable (String, Boolean) -> Unit)? = null) {
    val count = if (infinite) Int.MAX_VALUE else values.size
    val start = if (infinite) (count / 2 / values.size) * values.size + initial else initial.coerceIn(0, values.lastIndex)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val latestOnValue by rememberUpdatedState(onValue)
    val isolate = remember { object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = Offset(0f, available.y)
        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity(0f, available.y)
    } }
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    LaunchedEffect(state, values) {
        snapshotFlow {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - center)
            }?.index
        }.collect { index ->
            if (index != null) latestOnValue(index % values.size)
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = rowHeight.dp),
        modifier = modifier.height((rowHeight * 3).dp).nestedScroll(isolate),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(count) { index ->
            val value = values[index % values.size]
            val selected by remember {
                derivedStateOf {
                    val info = state.layoutInfo
                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                    info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index == index
                }
            }
            Box(Modifier.fillMaxWidth().height(rowHeight.dp), contentAlignment = Alignment.Center) {
                if (itemContent != null) itemContent(value, selected) else Text(value, color = if (selected) W.text else W.secondary,
                    fontSize = if (selected) 27.sp else 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun NumberWheelRow(title: String, unit: String, range: IntRange, value: Int, onValue: (Int) -> Unit) {
    val values = remember(range) { range.map { it.toString() } }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = W.text)
            Text(unit, color = muted, fontSize = 12.sp)
        }
        Wheel(values, value.coerceIn(range.first, range.last) - range.first,
            { onValue(it + range.first) }, Modifier.width(100.dp), rowHeight = 36)
    }
}

@Composable
fun AlarmEditorScreen(
    existing: AlarmEntity?,
    alarmDefaults: AlarmDefaults = AlarmDefaults(),
    availableAlarms: List<AlarmEntity> = emptyList(),
    onCancel: () -> Unit,
    onSave: (Int, Int, String, String, Boolean, Int, String, String, Int, Int, String, Int, Int, String?, Boolean, Int, Int, Set<Long>) -> Unit
) {
    val context = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var hour by remember(existing?.id) { mutableIntStateOf(existing?.hour ?: now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(existing?.id) { mutableIntStateOf(existing?.minute ?: now.get(Calendar.MINUTE)) }
    var label by remember(existing?.id) { mutableStateOf(existing?.label?.takeUnless { it == "New Alarm" } ?: "") }
    var soundUri by remember(existing?.id) { mutableStateOf(existing?.soundUri) }
    var ringtoneScreen by remember { mutableStateOf(false) }
    var vibration by remember(existing?.id) { mutableStateOf(existing?.vibrationEnabled ?: true) }
    var snoozeMinutes by remember(existing?.id) { mutableIntStateOf(existing?.snoozeMinutes ?: alarmDefaults.snoozeMinutes) }
    var challenge by remember(existing?.id) { mutableStateOf(existing?.challengeType?.let { if (QuranTypingConfig.isTyping(it)) "QURAN_TYPING" else it } ?: "SWIPE") }
    var typingConfig by remember(existing?.id) { mutableStateOf(QuranTypingConfig.parse(existing?.challengeType.orEmpty())) }
    val storedChallenge = if (challenge == "QURAN_TYPING") typingConfig.encode() else challenge
    var difficulty by remember(existing?.id) { mutableStateOf(existing?.mathDifficulty ?: "EASY") }
    var questionCount by remember(existing?.id) { mutableIntStateOf(existing?.mathQuestionCount ?: 3) }
    var shakeCount by remember(existing?.id) { mutableIntStateOf(existing?.shakeCount ?: 10) }
    var memoryDifficulty by remember(existing?.id) { mutableStateOf(existing?.memoryDifficulty ?: "EASY") }
    var memoryRounds by remember(existing?.id) { mutableIntStateOf(existing?.memoryRounds ?: 3) }
    var memoryDurationSeconds by remember(existing?.id) { mutableIntStateOf(existing?.memoryDurationSeconds ?: 10) }
    var awakeCheckEnabled by remember(existing?.id) { mutableStateOf(existing?.awakeCheckEnabled ?: alarmDefaults.awakeCheckEnabled) }
    var awakeCheckDelayText by remember(existing?.id) { mutableStateOf((existing?.awakeCheckDelayMinutes ?: alarmDefaults.awakeCheckDelayMinutes).toString()) }
    var awakeCheckTimeoutText by remember(existing?.id) { mutableStateOf((existing?.awakeCheckTimeoutSeconds ?: alarmDefaults.awakeCheckTimeoutSeconds).toString()) }
    var linkedSelection by remember(existing?.id) { mutableStateOf(existing?.groupId != null) }
    val otherAlarms = availableAlarms.filter { it.id != existing?.id }
    val linkedGroupMembers = otherAlarms.filter { existing?.groupId != null && it.groupId == existing.groupId }
    var selectedIds by remember(existing?.id) {
        mutableStateOf(linkedGroupMembers.map { it.id }.toSet())
    }
    fun toggleLinked(candidate: AlarmEntity) {
        val ids = if (candidate.groupId == null) setOf(candidate.id)
            else otherAlarms.filter { it.groupId == candidate.groupId }.map { it.id }.toSet()
        selectedIds = if (candidate.id in selectedIds) selectedIds - ids else selectedIds + ids
    }
    LaunchedEffect(linkedSelection) {
        if (linkedSelection) awakeCheckEnabled = true
    }
    val awakeCheckDelay = awakeCheckDelayText.toIntOrNull()?.takeIf { it in 1..1440 }
    val awakeCheckTimeout = awakeCheckTimeoutText.toIntOrNull()?.takeIf { it in 1..3600 }

    var selectedDays by remember(existing?.id) {
        mutableStateOf(existing?.repeatDays?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in 1..7 }?.toSet() ?: emptySet())
    }
    var custom by remember(existing?.id) {
        mutableStateOf(selectedDays.isNotEmpty() && selectedDays != allDays &&
            selectedDays != weekdays && selectedDays != weekends)
    }
    
    var challengeScreen by remember { mutableStateOf(false) }

    // Consume Android system Back inside nested editor pages before MainActivity
    // can close the entire editor. Preview is a separate Activity, so Android
    // returns to this challenge settings page when that Activity finishes.
    BackHandler(enabled = challengeScreen || ringtoneScreen) {
        when {
            ringtoneScreen -> ringtoneScreen = false
            challengeScreen -> challengeScreen = false
        }
    }
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

    if (ringtoneScreen) {
        RingtoneScreen(selected = soundUri, onSelect = { soundUri = it }, onBack = { ringtoneScreen = false })
        return
    }

    if (challengeScreen) {
        ChallengeSettingsScreen(
            challenge = challenge,
            onChallengeChange = { challenge = it },
            typingConfig = typingConfig,
            onTypingConfigChange = { typingConfig = it },
            difficulty = difficulty,
            onDifficultyChange = { difficulty = it },
            questionCount = questionCount,
            onQuestionCountChange = { questionCount = it },
            shakeCount = shakeCount,
            onShakeCountChange = { shakeCount = it },
            memoryDifficulty = memoryDifficulty,
            onMemoryDifficultyChange = { level ->
                if (memoryDifficulty != level) {
                    memoryDifficulty = level
                    memoryDurationSeconds = when (level) { "MEDIUM" -> 7; "HARD" -> 5; else -> 10 }
                }
            },
            memoryRounds = memoryRounds,
            onMemoryRoundsChange = { memoryRounds = it },
            memoryDurationSeconds = memoryDurationSeconds,
            onMemoryDurationChange = { memoryDurationSeconds = it },
            onPreview = {
                context.startActivity(Intent(context, AlarmRingActivity::class.java).apply {
                    putExtra("preview_mode", true)
                    putExtra("alarm_label", label.trim())
                    putExtra("snooze_minutes", snoozeMinutes)
                    putExtra("challenge_type", storedChallenge)
                    putExtra("math_difficulty", difficulty)
                    putExtra("math_question_count", questionCount)
                    putExtra("shake_count", shakeCount)
                    putExtra("memory_difficulty", memoryDifficulty)
                    putExtra("memory_rounds", memoryRounds)
                    putExtra("memory_duration_seconds", memoryDurationSeconds)
                })
            },
            onBack = { challengeScreen = false }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("Cancel", color = blue) }
            Text(if (existing == null) "New alarm" else "Edit alarm", color = W.text,
                fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            TextButton(enabled = (!awakeCheckEnabled || (awakeCheckDelay != null && awakeCheckTimeout != null)) && (!linkedSelection || selectedIds.isNotEmpty()), onClick = {
                onSave(hour, minute, label.ifBlank { "New Alarm" },
                    selectedDays.sorted().joinToString(","), vibration, snoozeMinutes,
                    storedChallenge, difficulty, questionCount, shakeCount, memoryDifficulty, memoryRounds, memoryDurationSeconds, soundUri,
                    awakeCheckEnabled, awakeCheckDelay ?: 2, awakeCheckTimeout ?: 60, if (linkedSelection) selectedIds else emptySet())
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
                }, Modifier.weight(1f), infinite = true)
                Wheel((0..59).map { "%02d".format(it) }, minute, { minute = it }, Modifier.weight(1f), infinite = true)
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
                        Text(name, color = if (active) Color.White else W.text, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            if (custom) {
                Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("Repeat", color = W.text, fontSize = 16.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(repeatDescription, color = blue, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, name ->
                            val day = i + 1
                            Box(Modifier.size(37.dp).background(if (day in selectedDays) blue else W.subtle,
                                RoundedCornerShape(50)).clickable {
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                            }, contentAlignment = Alignment.Center) {
                                Text(name, color = if (day in selectedDays) Color.White else W.text, fontSize = 13.sp)
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
                    Text("Dismissal method", color = W.text, fontWeight = FontWeight.SemiBold)
                    Text(when (challenge) {
                        "MATH" -> "Math · ${difficulty.lowercase()} · $questionCount questions"
                        "SHAKE" -> "Shake · $shakeCount movements"
                        "QURAN_TYPING" -> "Quran Typing · ${typingConfig.length.lowercase()} · ${typingConfig.rounds} ayahs"
                        "MEMORY" -> "Memory · ${memoryDifficulty.lowercase()} · $memoryRounds rounds · ${memoryDurationSeconds}s"
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
                        Text("Alarm name", color = W.text,
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("Give this alarm a name (optional)",
                            color = W.secondary, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    placeholder = { Text("e.g. Morning workout", color = W.secondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = W.text,
                        unfocusedTextColor = W.text,
                        cursorColor = blue,
                        focusedBorderColor = blue,
                        unfocusedBorderColor = W.outline,
                        focusedContainerColor = W.subtle,
                        unfocusedContainerColor = W.subtle
                    )
                )
            }
            Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("Alarm options", color = W.text,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(Modifier.padding(top = 12.dp), color = W.outline)
                Row(Modifier.fillMaxWidth().clickable { ringtoneScreen = true }.padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Ringtone", color = W.text)
                        Text(ringtoneDisplayName(context, soundUri), color = muted, fontSize = 12.sp)
                    }
                    Text("›", color = muted, fontSize = 24.sp)
                }
                HorizontalDivider(color = W.outline)
                Row(Modifier.fillMaxWidth().padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vibrate", color = W.text)
                    Switch(checked = vibration, onCheckedChange = { vibration = it })
                }
                HorizontalDivider(color = W.outline)
                NumberWheelRow("Snooze", "minutes", 1..60, snoozeMinutes) { snoozeMinutes = it }
            }
            Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text("Linked alarms", color = W.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Linked alarms are skipped only after you confirm I'm Awake. Awake Check is required.",
                    color = muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Link this alarm", color = W.text, modifier = Modifier.weight(1f))
                    Switch(checked = linkedSelection, onCheckedChange = {
                        linkedSelection = it
                        if (!it) selectedIds = emptySet()
                    }, enabled = otherAlarms.isNotEmpty() || existing?.groupId != null)
                }
                if (linkedSelection) {
                    if (linkedGroupMembers.isNotEmpty()) {
                        Text("Currently linked with: " + linkedGroupMembers.joinToString { formatTime(it.hour, it.minute) },
                            color = muted, fontSize = 12.sp)
                    }
                    Text("Select alarms to link", color = W.text, fontSize = 13.sp)
                    otherAlarms.forEach { candidate ->
                        val chosen = candidate.id in selectedIds
                        Row(Modifier.fillMaxWidth().clickable { toggleLinked(candidate) }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = chosen, onCheckedChange = { toggleLinked(candidate) })
                            Text(formatTime(candidate.hour, candidate.minute) + "  " +
                                candidate.label.takeUnless { it == "New Alarm" }.orEmpty(), color = W.text, fontSize = 13.sp)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Awake Check", color = W.text, fontWeight = FontWeight.SemiBold)
                        Text("Ask if you're awake after dismissing the alarm", color = muted, fontSize = 12.sp)
                    }
                    Switch(checked = awakeCheckEnabled, onCheckedChange = { awakeCheckEnabled = it },
                        enabled = !linkedSelection)
                }
                if (awakeCheckEnabled) {
                    Spacer(Modifier.height(12.dp))
                    NumberWheelRow("Ask after", "minutes", 1..1440, awakeCheckDelay ?: 2) { awakeCheckDelayText = it.toString() }
                    HorizontalDivider(color = W.outline)
                    NumberWheelRow("Response time", "seconds", 1..3600, awakeCheckTimeout ?: 60) { awakeCheckTimeoutText = it.toString() }
                }
                Text(if (linkedSelection) "Required for linked alarms" else "Awake Check runs after dismissal, not snooze.",
                    color = muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ChallengeSettingsScreen(
    challenge: String, onChallengeChange: (String) -> Unit,
    typingConfig: QuranTypingConfig, onTypingConfigChange: (QuranTypingConfig) -> Unit,
    difficulty: String, onDifficultyChange: (String) -> Unit,
    questionCount: Int, onQuestionCountChange: (Int) -> Unit,
    shakeCount: Int, onShakeCountChange: (Int) -> Unit,
    memoryDifficulty: String, onMemoryDifficultyChange: (String) -> Unit,
    memoryRounds: Int, onMemoryRoundsChange: (Int) -> Unit,
    memoryDurationSeconds: Int, onMemoryDurationChange: (Int) -> Unit,
    onPreview: () -> Unit, onBack: () -> Unit
) {
    val purple = W.primary
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = blue) }
            Spacer(Modifier.weight(1f))
            Text("Dismissal method", color = W.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(54.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("How would you like to stop your alarm?", color = W.text, fontSize = 17.sp)
        Text("Try a preview before saving your alarm.", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        val methods = remember { listOf("SWIPE", "MATH", "SHAKE", "MEMORY", "QURAN_TYPING") }
        val names = remember { listOf("Swipe", "Math", "Shake", "Memory", "Quran Typing") }
        val descriptions = remember { listOf(
            "Swipe up to dismiss", "Answer arithmetic questions", "Move your phone to complete",
            "Memorize and recall highlighted blocks", "Type the English meaning of Quranic ayahs"
        ) }
        val selectedIndex = methods.indexOf(challenge).coerceAtLeast(0)
        Text("Scroll to choose your challenge", color = muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp))) {
            Wheel(names, selectedIndex, { onChallengeChange(methods[it]) },
                Modifier.fillMaxWidth(), infinite = true, rowHeight = 52) { name, selected ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                        .fillMaxHeight().background(if (selected) W.selected else Color.Transparent, RoundedCornerShape(12.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, color = if (selected) purple else muted,
                        fontSize = if (selected) 21.sp else 16.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(descriptions[selectedIndex], color = muted, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        if (challenge == "MATH") {
            Spacer(Modifier.height(8.dp))
            Text("Difficulty", color = W.text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EASY", "MEDIUM", "HARD").forEach { level ->
                    FilterChip(selected = difficulty == level, onClick = { onDifficultyChange(level) },
                        label = { Text(level.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = blue, selectedLabelColor = Color.White,
                            labelColor = W.text))
                }
            }
            NumberWheelRow("Questions", "questions", 1..10, questionCount, onQuestionCountChange)
        }
        if (challenge == "SHAKE") {
            Spacer(Modifier.height(8.dp))
            NumberWheelRow("Required movements", "movements", 1..30, shakeCount, onShakeCountChange)
        }
        if (challenge == "MEMORY") {
            Spacer(Modifier.height(8.dp))
            Text("Difficulty", color = W.text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("EASY", "MEDIUM", "HARD").forEach { level ->
                    FilterChip(selected = memoryDifficulty == level, onClick = { onMemoryDifficultyChange(level) },
                        label = { Text(level.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = blue,
                            selectedLabelColor = Color.White, labelColor = W.text))
                }
            }
            Text(when (memoryDifficulty) {
                "MEDIUM" -> "4 × 4 grid · 5 highlighted blocks"
                "HARD" -> "5 × 5 grid · 8 highlighted blocks"
                else -> "3 × 3 grid · 3 highlighted blocks"
            }, color = muted, fontSize = 12.sp)
            NumberWheelRow("Rounds", "rounds", 1..10, memoryRounds, onMemoryRoundsChange)
            key(memoryDifficulty) {
                NumberWheelRow("Memorization duration", "seconds", 5..10, memoryDurationSeconds, onMemoryDurationChange)
            }
        }
        if (challenge == "QURAN_TYPING") {
            Text("Ayah length", color = W.text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SHORT", "MEDIUM", "MIXED").forEach { length ->
                    FilterChip(selected = typingConfig.length == length,
                        onClick = { onTypingConfigChange(typingConfig.copy(length = length)) },
                        label = { Text(length.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
            NumberWheelRow("Ayahs required", "ayahs", 1..5, typingConfig.rounds) {
                onTypingConfigChange(typingConfig.copy(rounds = it))
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPreview, modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = blue),
            shape = RoundedCornerShape(28.dp)) { Text("▶  Try preview", color = Color.White) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Continue", color = blue) }
    }
}
