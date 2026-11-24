
package com.wakeguard.alarm

import android.Manifest
import com.wakeguard.alarm.awake.AwakeCheckManager
import com.wakeguard.alarm.linked.LinkedAlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import com.wakeguard.alarm.ui.theme.AlarmScreenTheme
import com.wakeguard.alarm.ui.theme.ThemeMode
import com.wakeguard.alarm.ui.theme.ThemePreferences
import com.wakeguard.alarm.ui.theme.AlarmDefaults
import com.wakeguard.alarm.ui.theme.WakeGuardTheme
import com.wakeguard.alarm.ui.theme.WakeGuardColors as W
import com.wakeguard.alarm.ui.settings.SettingsScreen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wakeguard.alarm.data.local.AlarmEntity
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.data.repository.AlarmRepository
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.UUID

private val Background get() = W.background
private val CardColor get() = W.card
private val Accent get() = W.primary
private val Muted get() = W.secondary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = ThemePreferences(applicationContext)
        setContent {
            val scope = rememberCoroutineScope()
            val selectedMode by preferences.mode.collectAsState(initial = ThemeMode.SYSTEM)
            val selectedAlarmMode by preferences.alarmScreenMode.collectAsState(initial = AlarmScreenTheme.DARK)
            val defaults by preferences.alarmDefaults.collectAsState(initial = null)
            WakeGuardTheme(selectedMode) {
                val loadedDefaults = defaults
                if (loadedDefaults == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                WakeGuardHome(
                    themeMode = selectedMode,
                    onThemeChange = { newMode -> scope.launch { preferences.setMode(newMode) } },
                    alarmDefaults = loadedDefaults,
                    onAlarmDefaultsChange = { value -> scope.launch { preferences.setAlarmDefaults(value) } },
                    alarmScreenMode = selectedAlarmMode,
                    onAlarmScreenThemeChange = { newMode -> scope.launch { preferences.setAlarmScreenMode(newMode) } }
                )
                }
            }
        }
    }
}

@Composable
fun WakeGuardHome(
    alarmDefaults: AlarmDefaults,
    onAlarmDefaultsChange: (AlarmDefaults) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    alarmScreenMode: AlarmScreenTheme,
    onAlarmScreenThemeChange: (AlarmScreenTheme) -> Unit
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember(context) {
        AlarmRepository(
            WakeGuardDatabase
                .getInstance(context.applicationContext)
                .alarmDao()
        )
    }

    val alarms = remember {
        mutableStateListOf<AlarmEntity>()
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var isBusy by remember {
        mutableStateOf(false)
    }

    var alarmToDisable by remember { mutableStateOf<AlarmEntity?>(null) }
    var alarmToDelete by remember { mutableStateOf<AlarmEntity?>(null) }
    val selectedForDeletion = remember { mutableStateListOf<Long>() }
    var selectionMode by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    )
                )
            } catch (e: Exception) {
                showMessage("Open settings to allow exact alarms")
            }
        }
    }

    // Observe Room changes, including updates made by the ringing service.
    LaunchedEffect(repository) {
        try {
            repository.observeAllAlarms().collect { savedAlarms ->
                alarms.clear()
                alarms.addAll(savedAlarms)
                isLoading = false
            }
        } catch (e: Exception) {
            isLoading = false
            showMessage("Could not observe saved alarms")
        }
    }

    // Full-screen alarm permission.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 34) {
            val manager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

            if (!manager.canUseFullScreenIntent()) {
                Toast.makeText(
                    context,
                    "Enable full-screen alarms for WakeGuard",
                    Toast.LENGTH_LONG
                ).show()

                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (e: Exception) {
                    showMessage("Check full-screen alarm permission")
                }
            }
        }
    }

    // Android 13+ notification permission.
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                showMessage("Allow notifications for alarm alerts")
            }
        }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    // Refresh the next-alarm countdown as time passes, even without Room changes.
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val nextAlarmTimeMillis = alarms.asSequence()
        .filter { it.isEnabled }
        .mapNotNull { nextAlarmOccurrenceMillis(it, currentTimeMillis) }
        .minOrNull()

    val nextAlarmSummary = nextAlarmTimeMillis?.let { occurrence ->
        val remainingMinutes = ((occurrence - currentTimeMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
        val hours = remainingMinutes / 60L
        val minutes = remainingMinutes % 60L
        val hoursText = when (hours) {
            0L -> ""
            1L -> "1 hour"
            else -> "$hours hours"
        }
        val minutesText = when (minutes) {
            0L -> ""
            1L -> "1 minute"
            else -> "$minutes minutes"
        }
        "Next alarm in " + listOf(hoursText, minutesText)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "less than a minute" }
    } ?: "No alarms set"

    BackHandler(enabled = showSettings || showEditor) {
        when {
            showSettings -> showSettings = false
            showEditor && !isBusy -> {
                showEditor = false
                editingAlarm = null
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            alarmDefaults = alarmDefaults,
            onAlarmDefaultsChange = onAlarmDefaultsChange,
            mode = themeMode,
            onModeChange = onThemeChange,
            alarmMode = alarmScreenMode,
            onAlarmModeChange = onAlarmScreenThemeChange,
            onBack = { showSettings = false }
        )
        return
    }

    if (showEditor) {
        AlarmEditorScreen(
            existing = editingAlarm,
            alarmDefaults = alarmDefaults,
            availableAlarms = alarms.toList(),
            onCancel = { if (!isBusy) showEditor = false },
            onSave = { hour, minute, label, repeatDays, vibrate, snooze, challenge, difficulty, questions, shakes, memoryDifficulty, memoryRounds, memoryDurationSeconds, soundUri, awakeEnabled, awakeDelay, awakeTimeout, selectedIds ->
                if (!isBusy) {
                    isBusy = true
                    scope.launch {
                        val old = editingAlarm
                        var createdId: Long? = null
                        var createdAlarm: AlarmEntity? = null
                        var updated: AlarmEntity? = null
                        val linkedBefore = mutableListOf<AlarmEntity>()
                        try {
                            val conflict = repository.getAllAlarms().firstOrNull { other ->
                                other.id != old?.id && alarmOccurrencesOverlap(
                                    other.hour, other.minute, other.repeatDays,
                                    hour, minute, repeatDays
                                )
                            }
                            if (conflict != null) {
                                showMessage("An alarm already exists at this time on an overlapping day")
                                return@launch
                            }
                            val all = repository.getAllAlarms()
                            val selected = all.filter { it.id in selectedIds && it.id != old?.id }
                            if (selected.size != selectedIds.size) {
                                showMessage("A selected alarm no longer exists")
                                return@launch
                            }
                            val groupIds = selected.mapNotNull { it.groupId }.toSet()
                            val groupMembers = all.filter { it.id != old?.id &&
                                    (it.id in selectedIds || it.groupId in groupIds) }
                            val linkedGroupId = if (selected.isEmpty()) null else {
                                old?.groupId?.takeIf { it in groupIds }
                                    ?: groupIds.minOrNull()
                                    ?: generateSequence { UUID.randomUUID().mostSignificantBits or Long.MIN_VALUE }
                                        .first { candidate -> all.none { it.id == candidate || it.groupId == candidate } }
                            }
                            if (linkedGroupId != null && (!awakeEnabled || groupMembers.any { !it.awakeCheckEnabled })) {
                                showMessage("Enable Awake Check on all selected alarms first")
                                return@launch
                            }
                            if (old == null) {
                                val used = alarms.map { it.schedulerId }.toSet()
                                var schedulerId: Int
                                do { schedulerId = UUID.randomUUID().hashCode() }
                                while (schedulerId == 0 || schedulerId in used)
                                val fresh = AlarmEntity(
                                    schedulerId = schedulerId, hour = hour, minute = minute,
                                    label = label, repeatDays = repeatDays,
                                    vibrationEnabled = vibrate, snoozeMinutes = snooze,
                                    challengeType = challenge, mathDifficulty = difficulty,
                                    mathQuestionCount = questions, shakeCount = shakes, soundUri = soundUri,
                                    memoryDifficulty = memoryDifficulty, memoryRounds = memoryRounds,
                                    memoryDurationSeconds = memoryDurationSeconds,
                                    awakeCheckEnabled = awakeEnabled, awakeCheckDelayMinutes = awakeDelay, awakeCheckTimeoutSeconds = awakeTimeout,
                                    groupId = linkedGroupId
                                )
                                val id = repository.insertAlarm(fresh)
                                createdId = id
                                val saved = fresh.copy(id = id)
                                createdAlarm = saved
                                if (!AlarmScheduler.schedule(
                                        context, schedulerId, hour, minute, label,
                                        saved.scheduleGeneration, repeatDays
                                    )) {
                                    repository.deleteAlarmById(id)
                                    createdId = null
                                    showMessage("Exact alarm permission required")
                                    requestExactAlarmPermission()
                                    return@launch
                                }


                            } else {
                                val changed = old.hour != hour || old.minute != minute ||
                                        old.label != label || old.repeatDays != repeatDays ||
                                        old.vibrationEnabled != vibrate || old.snoozeMinutes != snooze ||
                                        old.challengeType != challenge || old.mathDifficulty != difficulty ||
                                        old.mathQuestionCount != questions || old.shakeCount != shakes ||
                                        old.memoryDifficulty != memoryDifficulty || old.memoryRounds != memoryRounds ||
                                        old.memoryDurationSeconds != memoryDurationSeconds ||
                                        old.soundUri != soundUri || old.awakeCheckEnabled != awakeEnabled ||
                                        old.awakeCheckDelayMinutes != awakeDelay || old.awakeCheckTimeoutSeconds != awakeTimeout ||
                                        old.groupId != linkedGroupId || !old.isEnabled
                                if (changed) {
                                    val next = old.copy(
                                        hour = hour, minute = minute, label = label,
                                        repeatDays = repeatDays, vibrationEnabled = vibrate,
                                        snoozeMinutes = snooze,
                                        challengeType = challenge, mathDifficulty = difficulty,
                                        mathQuestionCount = questions, shakeCount = shakes, soundUri = soundUri,
                                    memoryDifficulty = memoryDifficulty, memoryRounds = memoryRounds,
                                    memoryDurationSeconds = memoryDurationSeconds,
                                        groupId = linkedGroupId,
                                        awakeCheckEnabled = awakeEnabled,
                                        awakeCheckDelayMinutes = awakeDelay,
                                        awakeCheckTimeoutSeconds = awakeTimeout,
                                        scheduleGeneration = old.scheduleGeneration + 1L,
                                        isEnabled = true
                                    )
                                    repository.updateAlarm(next)
                                    updated = next
                                    if (next.isEnabled && !AlarmScheduler.schedule(
                                            context, next.schedulerId, next.hour, next.minute,
                                            next.label, next.scheduleGeneration, next.repeatDays
                                        )) {
                                        repository.updateAlarm(old)
                                        updated = null
                                        showMessage("Exact alarm permission required")
                                        requestExactAlarmPermission()
                                        return@launch
                                    }
                                }
                            }
                            for (member in groupMembers) {
                                if (member.groupId != linkedGroupId) {
                                    linkedBefore.add(member)
                                    repository.updateAlarm(member.copy(groupId = linkedGroupId))
                                }
                            }
                            if (old != null && updated != null) {
                                AwakeCheckManager.cancel(context, old.schedulerId)
                                AlarmScheduler.cancel(context, old.schedulerId, old.scheduleGeneration)
                            }
                            showEditor = false
                            showMessage("Alarm saved")
                        } catch (e: Exception) {
                            linkedBefore.asReversed().forEach { anchor ->
                                try { repository.updateAlarm(anchor) } catch (_: Exception) { }
                            }
                            createdAlarm?.let { fresh ->
                                AlarmScheduler.cancel(context, fresh.schedulerId, fresh.scheduleGeneration)
                            }
                            createdId?.let { id ->
                                try { repository.deleteAlarmById(id) } catch (_: Exception) { }
                            }
                            updated?.let { next ->
                                try { repository.updateAlarm(old!!) } catch (_: Exception) { }
                                AlarmScheduler.cancel(context, next.schedulerId, next.scheduleGeneration)
                            }
                            showMessage("Could not save alarm")
                        } finally { isBusy = false }
                    }
                }
            }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Background)
            .statusBarsPadding().padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                TextButton(onClick = { selectionMode = false; selectedForDeletion.clear() }) {
                    Text("Cancel", color = Accent)
                }
                Text("${selectedForDeletion.size} selected", color = W.text,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    selectedForDeletion.clear()
                    selectedForDeletion.addAll(alarms.map { it.id })
                }) { Text("Select all", color = Accent) }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("Alarm", color = W.text, fontSize = 34.sp,
                        fontWeight = FontWeight.Bold)
                    Text(nextAlarmSummary, color = Muted, fontSize = 13.sp)
                }
                IconButton(onClick = { showSettings = true }) {
                    Text("⚙", fontSize = 27.sp, color = W.primary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            items(
                items = alarms,
                key = { it.id }
            ) { alarm ->

                AlarmCard(
                    alarm = alarm,
                    onToggle = { enabled ->
                        if (!isBusy) {
                            if (!enabled && (alarm.repeatDays.isNotBlank() || (alarm.groupId != null && alarms.any { it.groupId == alarm.groupId && it.repeatDays.isNotBlank() }))) {
                                alarmToDisable = alarm
                            } else {
                                isBusy = true
                                scope.launch {
                                    try {
                                        val all = repository.getAllAlarms()
                                        val targets = if (alarm.groupId != null) all.filter {
                                            it.groupId == alarm.groupId
                                        } else listOf(alarm)
                                        if (enabled) {
                                            val conflict = targets.firstOrNull { target ->
                                                all.any { other -> other.id != target.id &&
                                                        other !in targets && alarmOccurrencesOverlap(
                                                    other.hour, other.minute, other.repeatDays,
                                                    target.hour, target.minute, target.repeatDays) }
                                            }
                                            if (conflict != null) {
                                                showMessage("An alarm already exists at this time")
                                                return@launch
                                            }
                                            if (!LinkedAlarmManager.setEnabled(context, targets, true)) {
                                                showMessage("Could not enable alarms. Check exact alarm permission.")
                                                requestExactAlarmPermission()
                                                return@launch
                                            }
                                            showMessage("Linked alarms enabled")
                                        } else {
                                            if (!LinkedAlarmManager.setEnabled(context, targets, false)) {
                                                showMessage("Could not disable alarms")
                                                return@launch
                                            }
                                            showMessage("Linked alarms disabled")
                                        }
                                    } catch (e: Exception) {
                                        showMessage("Could not update alarms")
                                    } finally { isBusy = false }
                                }
                            }
                        }
                    },
                    onEdit = {
                        if (!isBusy && !isLoading) {
                            editingAlarm = alarm
                            showEditor = true
                        }
                    },
                    selected = alarm.id in selectedForDeletion,
                    selectionMode = selectionMode,
                    onLongPress = {
                        if (!isBusy && !isLoading) {
                            selectionMode = true
                            if (alarm.id !in selectedForDeletion) selectedForDeletion.add(alarm.id)
                        }
                    },
                    onSelect = {
                        if (alarm.id in selectedForDeletion) selectedForDeletion.remove(alarm.id)
                        else selectedForDeletion.add(alarm.id)
                    },
                    enabled = !isBusy && !isLoading
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        if (selectionMode) {
            Button(onClick = {
                if (selectedForDeletion.isNotEmpty()) {
                    alarmToDelete = alarms.firstOrNull { it.id in selectedForDeletion }
                }
            }, enabled = selectedForDeletion.isNotEmpty() && !isBusy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))) {
                Text("Delete ${selectedForDeletion.size} selected alarms")
            }
        } else {
            Box(Modifier.fillMaxWidth().padding(bottom = 56.dp), contentAlignment = Alignment.Center) {
                FloatingActionButton(onClick = { editingAlarm = null; showEditor = true },
                    containerColor = Accent, shape = CircleShape) {
                    Text("+", fontSize = 34.sp, color = Color.White)
                }
            }
        }
    }

    alarmToDisable?.let { alarm ->
        val next = AlarmScheduler.nextOccurrenceMillis(alarm.hour, alarm.minute,
            alarm.repeatDays, currentTimeMillis)
        val dayName = next?.let { time ->
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply { timeInMillis = time }
            val today = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            when {
                today -> "today"
                tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                        tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> "tomorrow"
                else -> java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(time)
            }
        } ?: "next occurrence"
        AlertDialog(
            onDismissRequest = { if (!isBusy) alarmToDisable = null },
            title = { Text("Turn off recurring alarm?") },
            text = { Text(if (alarm.groupId != null) "This action applies to all linked alarms."
            else "Choose whether to skip the next occurrence or turn off this alarm.") },
            confirmButton = {
                Column {
                    TextButton(enabled = !isBusy, onClick = {
                        if (next != null && !isBusy) {
                            isBusy = true
                            scope.launch {
                                try {
                                    val all = repository.getAllAlarms()
                                    val targets = if (alarm.groupId != null) all.filter {
                                        it.groupId == alarm.groupId
                                    } else listOf(alarm)
                                    if (LinkedAlarmManager.skipDate(context, targets, next)) {
                                        showMessage("Skipped $dayName")
                                        alarmToDisable = null
                                    } else showMessage("Could not skip alarms")
                                } catch (_: Exception) { showMessage("Could not skip alarms") }
                                finally { isBusy = false }
                            }
                        }
                    }) { Text("Skip $dayName") }
                    TextButton(enabled = !isBusy, onClick = {
                        isBusy = true
                        scope.launch {
                            try {
                                val all = repository.getAllAlarms()
                                val targets = if (alarm.groupId != null) all.filter {
                                    it.groupId == alarm.groupId
                                } else listOf(alarm)
                                if (!LinkedAlarmManager.setEnabled(context, targets, false)) {
                                    showMessage("Could not turn off alarms")
                                    return@launch
                                }
                                alarmToDisable = null
                                showMessage("Recurring alarms turned off")
                            } catch (_: Exception) { showMessage("Could not turn off alarms") }
                            finally { isBusy = false }
                        }
                    }) { Text("Turn off recurring alarm") }
                }
            },
            dismissButton = { TextButton(enabled = !isBusy,
                onClick = { alarmToDisable = null }) { Text("Cancel") } }
        )
    }

    alarmToDelete?.let { alarm ->
        AlertDialog(
            onDismissRequest = { if (!isBusy) alarmToDelete = null },
            title = { Text("Delete alarm?") },
            text = {
                Text("Delete ${if (selectionMode) selectedForDeletion.size.toString() + " selected alarms" else formatTime(alarm.hour, alarm.minute)}? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        if (!isBusy) {
                            isBusy = true
                            scope.launch {
                                try {
                                    // Delete from Room first. Do not cancel on DB failure.
                                    val targets = if (selectionMode) alarms.filter { it.id in selectedForDeletion }
                                    else listOf(alarm)
                                    targets.forEach { target ->
                                        repository.deleteAlarmById(target.id)
                                        AwakeCheckManager.cancel(context, target.schedulerId)
                                        AlarmScheduler.cancel(context, target.schedulerId, target.scheduleGeneration)
                                    }
                                    alarms.removeAll { item -> targets.any { it.id == item.id } }
                                    selectedForDeletion.clear()
                                    selectionMode = false
                                    alarmToDelete = null
                                    showMessage("Alarm deleted")
                                } catch (e: Exception) {
                                    // Reload authoritative DB state if an operation fails.
                                    try {
                                        val saved = repository.getAllAlarms()
                                        alarms.clear()
                                        alarms.addAll(saved)
                                    } catch (_: Exception) { }
                                    alarmToDelete = null
                                    showMessage("Could not complete deletion")
                                } finally {
                                    isBusy = false
                                }
                            }
                        }
                    }
                ) { Text("Delete", color = Color(0xFFDC2626)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = { alarmToDelete = null }
                ) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmCard(
    alarm: AlarmEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    selected: Boolean,
    selectionMode: Boolean,
    onLongPress: () -> Unit,
    onSelect: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(CardColor, RoundedCornerShape(16.dp))
            .combinedClickable(enabled = enabled,
                onClick = { if (selectionMode) onSelect() else onEdit() },
                onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(alarm.hour, alarm.minute), fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                color = if (alarm.isEnabled) W.text else W.secondary)
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onSelect() })
            } else {
                Switch(checked = alarm.isEnabled, onCheckedChange = onToggle, enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White))
            }
        }
        Spacer(Modifier.height(4.dp))
        if (alarm.label.isNotBlank() && alarm.label != "New Alarm") {
            Text(alarm.label, color = W.text, fontSize = 14.sp,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
        }
        if (alarm.groupId != null) {
            Text("↗ Linked alarm", color = Accent, fontSize = 12.sp)
            Spacer(Modifier.height(3.dp))
        }
        Text(when (alarm.repeatDays) {
            "" -> "Ring once"
            "1,2,3,4,5,6,7" -> "Every day"
            "2,3,4,5,6" -> "Weekdays"
            "1,7" -> "Weekends"
            else -> "Custom repeat"
        }, color = Muted, fontSize = 13.sp)
    }
}

fun formatTime(hour: Int, minute: Int): String {

    val period = if (hour < 12) "AM" else "PM"

    val displayHour = when (hour % 12) {
        0 -> 12
        else -> hour % 12
    }

    return "%d:%02d %s".format(
        displayHour,
        minute,
        period
    )
}

/**
 * Checks the next seven calendar dates. A repeating alarm can occur on any of
 * its selected weekdays; a one-time alarm only has its next upcoming occurrence.
 * Disabled saved alarms are included by callers to avoid conflicts when re-enabled.
 */
private fun alarmOccurrencesOverlap(
    firstHour: Int, firstMinute: Int, firstRepeatDays: String,
    secondHour: Int, secondMinute: Int, secondRepeatDays: String
): Boolean {
    if (firstHour != secondHour || firstMinute != secondMinute) return false

    val now = Calendar.getInstance()
    val firstDates = nextOccurrenceDates(firstHour, firstMinute, firstRepeatDays, now)
    val secondDates = nextOccurrenceDates(secondHour, secondMinute, secondRepeatDays, now)
    return firstDates.any { it in secondDates }
}

private fun nextOccurrenceDates(
    hour: Int, minute: Int, repeatDays: String, now: Calendar
): Set<Long> {
    val candidate = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
    val weekdays = repeatDays.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
        .toSet()

    if (repeatDays.isBlank()) {
        return setOf(candidate.timeInMillis)
    }

    return buildSet {
        repeat(7) {
            if (candidate.get(Calendar.DAY_OF_WEEK) in weekdays) {
                add(candidate.timeInMillis)
            }
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

/** Next scheduled wall-clock occurrence, respecting the selected weekdays. */
private fun nextAlarmOccurrenceMillis(alarm: AlarmEntity, nowMillis: Long): Long? {
    val selectedDays = alarm.repeatDays.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
        .toSet()
    if (alarm.repeatDays.isNotBlank() && selectedDays.isEmpty()) return null

    val candidate = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, alarm.hour)
        set(Calendar.MINUTE, alarm.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
    }

    if (alarm.repeatDays.isBlank()) return candidate.timeInMillis

    repeat(7) {
        if (candidate.get(Calendar.DAY_OF_WEEK) in selectedDays) {
            return candidate.timeInMillis
        }
        candidate.add(Calendar.DAY_OF_YEAR, 1)
    }
    return null
}
