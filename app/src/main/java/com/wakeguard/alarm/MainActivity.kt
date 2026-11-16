
package com.wakeguard.alarm

import android.Manifest
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
import androidx.compose.material3.*
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
import java.util.Calendar
import java.util.UUID

private val Background = Color(0xFF151515)
private val CardColor = Color(0xFF303030)
private val Accent = Color(0xFF6485FF)
private val Muted = Color(0xFF94A3B8)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                WakeGuardHome()
            }
        }
    }
}

@Composable
fun WakeGuardHome() {

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

    var alarmToDelete by remember { mutableStateOf<AlarmEntity?>(null) }
    val selectedForDeletion = remember { mutableStateListOf<Long>() }
    var selectionMode by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }

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

    // Load persisted alarms when the screen opens.
    LaunchedEffect(repository) {
        try {
            val savedAlarms = repository.getAllAlarms()

            alarms.clear()
            alarms.addAll(savedAlarms)
        } catch (e: Exception) {
            showMessage("Could not load saved alarms")
        } finally {
            isLoading = false
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

    val nextAlarm = alarms
        .filter { it.isEnabled }
        .minByOrNull { alarm ->
            val now = Calendar.getInstance()

            val currentMinutes =
                now.get(Calendar.HOUR_OF_DAY) * 60 +
                        now.get(Calendar.MINUTE)

            val alarmMinutes =
                alarm.hour * 60 + alarm.minute

            (alarmMinutes - currentMinutes + 1440) % 1440
        }

    if (showEditor) {
        AlarmEditorScreen(
            existing = editingAlarm,
            onCancel = { if (!isBusy) showEditor = false },
            onSave = { hour, minute, label, repeatDays, vibrate, snooze ->
                if (!isBusy) {
                    isBusy = true
                    scope.launch {
                        val old = editingAlarm
                        var createdId: Long? = null
                        var updated: AlarmEntity? = null
                        try {
                            if (old == null) {
                                val used = alarms.map { it.schedulerId }.toSet()
                                var schedulerId: Int
                                do { schedulerId = UUID.randomUUID().hashCode() }
                                while (schedulerId == 0 || schedulerId in used)
                                val fresh = AlarmEntity(
                                    schedulerId = schedulerId, hour = hour, minute = minute,
                                    label = label, repeatDays = repeatDays,
                                    vibrationEnabled = vibrate, snoozeMinutes = snooze
                                )
                                val id = repository.insertAlarm(fresh)
                                createdId = id
                                val saved = fresh.copy(id = id)
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
                                alarms.add(saved)
                            } else {
                                val changed = old.hour != hour || old.minute != minute ||
                                    old.label != label || old.repeatDays != repeatDays ||
                                    old.vibrationEnabled != vibrate || old.snoozeMinutes != snooze
                                if (changed) {
                                    val next = old.copy(
                                        hour = hour, minute = minute, label = label,
                                        repeatDays = repeatDays, vibrationEnabled = vibrate,
                                        snoozeMinutes = snooze,
                                        scheduleGeneration = old.scheduleGeneration + 1L
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
                                    AlarmScheduler.cancel(context, old.schedulerId, old.scheduleGeneration)
                                    val index = alarms.indexOfFirst { it.id == old.id }
                                    if (index >= 0) alarms[index] = next
                                }
                            }
                            showEditor = false
                            showMessage("Alarm saved")
                        } catch (e: Exception) {
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
                Text("${selectedForDeletion.size} selected", color = Color.White,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    selectedForDeletion.clear()
                    selectedForDeletion.addAll(alarms.map { it.id })
                }) { Text("Select all", color = Accent) }
            } else {
                Column {
                    Text("Alarm", color = Color.White, fontSize = 34.sp,
                        fontWeight = FontWeight.Bold)
                    Text(nextAlarm?.let { "Next alarm ${formatTime(it.hour, it.minute)}" }
                        ?: "No alarms set", color = Muted, fontSize = 13.sp)
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
                            isBusy = true

                            scope.launch {
                                try {
                                    if (enabled) {
                                        val updated = alarm.copy(
                                            isEnabled = true,
                                            scheduleGeneration = alarm.scheduleGeneration + 1L
                                        )
                                        repository.updateAlarm(updated)
                                        val scheduled = AlarmScheduler.schedule(
                                            context = context,
                                            alarmId = updated.schedulerId,
                                            hour = updated.hour,
                                            minute = updated.minute,
                                            label = updated.label,
                                            generation = updated.scheduleGeneration,
                                            repeatDays = updated.repeatDays
                                        )
                                        if (!scheduled) {
                                            repository.updateAlarm(alarm)
                                            showMessage("Exact alarm permission required")
                                            requestExactAlarmPermission()
                                        } else {
                                            AlarmScheduler.cancel(
                                                context, alarm.schedulerId,
                                                alarm.scheduleGeneration
                                            )
                                            val index = alarms.indexOfFirst { it.id == alarm.id }
                                            if (index >= 0) alarms[index] = updated
                                            showMessage("Alarm enabled")
                                        }
                                    } else {

                                        // Persist disabled state first.
                                        repository.updateAlarm(
                                            alarm.copy(
                                                isEnabled = false
                                            )
                                        )

                                        AlarmScheduler.cancel(
                                            context,
                                            alarm.schedulerId,
                                            alarm.scheduleGeneration
                                        )

                                        val index =
                                            alarms.indexOfFirst {
                                                it.id == alarm.id
                                            }

                                        if (index >= 0) {
                                            alarms[index] =
                                                alarm.copy(
                                                    isEnabled = false
                                                )
                                        }

                                        showMessage("Alarm disabled")
                                    }

                                } catch (e: Exception) {
                                    showMessage(
                                        "Could not update alarm"
                                    )
                                } finally {
                                    isBusy = false
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
            Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                FloatingActionButton(onClick = { editingAlarm = null; showEditor = true },
                    containerColor = Accent) {
                    Text("+", fontSize = 34.sp, color = Color.White)
                }
            }
        }
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
                color = if (alarm.isEnabled) Color.White else Color.Gray)
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onSelect() })
            } else {
                Switch(checked = alarm.isEnabled, onCheckedChange = onToggle, enabled = enabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
        }
        Spacer(Modifier.height(4.dp))
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
