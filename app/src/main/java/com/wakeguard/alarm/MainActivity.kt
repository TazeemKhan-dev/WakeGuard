
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

private val Background = Color(0xFF101827)
private val CardColor = Color(0xFF1E293B)
private val Accent = Color(0xFF10B981)
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

    // Create and persist an alarm.
    val timePicker = {
        if (!isBusy && !isLoading) {
            val now = Calendar.getInstance()

            TimePickerDialog(
                context,
                { _, hour, minute ->

                    if (!isBusy) {
                        isBusy = true

                        scope.launch {
                            var savedAlarm: AlarmEntity? = null

                            try {
                                // Preserve the existing Int-based
                                // AlarmManager identity.
                                val usedIds = alarms
                                    .map { it.schedulerId }
                                    .toSet()

                                var schedulerId: Int

                                do {
                                    schedulerId = UUID
                                        .randomUUID()
                                        .hashCode()
                                } while (
                                    schedulerId == 0 ||
                                    schedulerId in usedIds
                                )

                                val newAlarm = AlarmEntity(
                                    schedulerId = schedulerId,
                                    hour = hour,
                                    minute = minute,
                                    label = "New Alarm",
                                    isEnabled = true
                                )

                                val databaseId =
                                    repository.insertAlarm(newAlarm)

                                savedAlarm = newAlarm.copy(
                                    id = databaseId
                                )

                                val scheduled =
                                    AlarmScheduler.schedule(
                                        context = context,
                                        alarmId = schedulerId,
                                        hour = hour,
                                        minute = minute,
                                        label = newAlarm.label
                                    )

                                if (scheduled) {
                                    alarms.add(savedAlarm)
                                    showMessage("Alarm saved and scheduled!")
                                } else {
                                    repository.deleteAlarmById(databaseId)
                                    savedAlarm = null
                                    showMessage(
                                        "Exact alarm permission required"
                                    )
                                    requestExactAlarmPermission()
                                }

                            } catch (e: Exception) {
                                // Remove any partially scheduled alarm.
                                savedAlarm?.let {
                                    AlarmScheduler.cancel(
                                        context,
                                        it.schedulerId
                                    )
                                    try {
                                        repository.deleteAlarmById(it.id)
                                    } catch (_: Exception) {
                                    }
                                }

                                showMessage("Could not save alarm")
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                false
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .padding(20.dp)
    ) {

        Text(
            text = "YOUR MORNING COMPANION",
            color = Muted,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "WakeGuard",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(28.dp))

        // Next alarm card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    CardColor,
                    RoundedCornerShape(20.dp)
                )
                .padding(22.dp)
        ) {

            Text(
                text = "NEXT ALARM",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isLoading) {
                    "Loading..."
                } else {
                    nextAlarm?.let {
                        formatTime(it.hour, it.minute)
                    } ?: "No alarms set"
                },
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (nextAlarm != null) {
                    "Your next scheduled time"
                } else {
                    "Create your first alarm"
                },
                color = Accent,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "Your Alarms",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "${alarms.size} alarms",
                color = Accent,
                fontSize = 13.sp
            )
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

                                        val scheduled =
                                            AlarmScheduler.schedule(
                                                context = context,
                                                alarmId = alarm.schedulerId,
                                                hour = alarm.hour,
                                                minute = alarm.minute,
                                                label = alarm.label
                                            )

                                        if (!scheduled) {
                                            showMessage(
                                                "Exact alarm permission required"
                                            )
                                            requestExactAlarmPermission()
                                        } else {
                                            try {
                                                repository.updateAlarm(
                                                    alarm.copy(
                                                        isEnabled = true
                                                    )
                                                )

                                                val index =
                                                    alarms.indexOfFirst {
                                                        it.id == alarm.id
                                                    }

                                                if (index >= 0) {
                                                    alarms[index] =
                                                        alarm.copy(
                                                            isEnabled = true
                                                        )
                                                }

                                                showMessage("Alarm enabled")
                                            } catch (e: Exception) {
                                                AlarmScheduler.cancel(
                                                    context,
                                                    alarm.schedulerId
                                                )
                                                throw e
                                            }
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
                                            alarm.schedulerId
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
                            TimePickerDialog(
                                context,
                                { _, newHour, newMinute ->
                                    if (!isBusy) {
                                        isBusy = true
                                        scope.launch {
                                            val updated = alarm.copy(
                                                hour = newHour,
                                                minute = newMinute
                                            )
                                            var scheduleChanged = false
                                            try {
                                                if (updated.hour == alarm.hour &&
                                                    updated.minute == alarm.minute
                                                ) {
                                                    showMessage("Time unchanged")
                                                } else {
                                                    if (alarm.isEnabled) {
                                                        // Same schedulerId updates the existing PendingIntent.
                                                        val scheduled = AlarmScheduler.schedule(
                                                            context = context,
                                                            alarmId = alarm.schedulerId,
                                                            hour = updated.hour,
                                                            minute = updated.minute,
                                                            label = updated.label
                                                        )
                                                        if (!scheduled) {
                                                            showMessage("Exact alarm permission required")
                                                            requestExactAlarmPermission()
                                                            return@launch
                                                        }
                                                        scheduleChanged = true
                                                    }

                                                    repository.updateAlarm(updated)
                                                    val index = alarms.indexOfFirst { it.id == alarm.id }
                                                    if (index >= 0) alarms[index] = updated
                                                    showMessage("Alarm updated")
                                                }
                                            } catch (e: Exception) {
                                                if (scheduleChanged) {
                                                    // Best-effort restore of the old pending alarm.
                                                    try {
                                                        AlarmScheduler.schedule(
                                                            context = context,
                                                            alarmId = alarm.schedulerId,
                                                            hour = alarm.hour,
                                                            minute = alarm.minute,
                                                            label = alarm.label
                                                        )
                                                    } catch (_: Exception) { }
                                                }
                                                showMessage("Could not update alarm")
                                            } finally {
                                                isBusy = false
                                            }
                                        }
                                    }
                                },
                                alarm.hour,
                                alarm.minute,
                                false
                            ).show()
                        }
                    },
                    onDelete = {
                        if (!isBusy && !isLoading) alarmToDelete = alarm
                    },
                    enabled = !isBusy && !isLoading
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = timePicker,
            enabled = !isBusy && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent
            ),
            shape = RoundedCornerShape(16.dp)
        ) {

            Text(
                text = if (isLoading) {
                    "Loading..."
                } else {
                    "+  Add New Alarm"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    alarmToDelete?.let { alarm ->
        AlertDialog(
            onDismissRequest = { if (!isBusy) alarmToDelete = null },
            title = { Text("Delete alarm?") },
            text = {
                Text("Delete ${formatTime(alarm.hour, alarm.minute)}? This cannot be undone.")
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
                                    repository.deleteAlarmById(alarm.id)
                                    AlarmScheduler.cancel(context, alarm.schedulerId)
                                    alarms.removeAll { it.id == alarm.id }
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

@Composable
fun AlarmCard(
    alarm: AlarmEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                CardColor,
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Column {

            Text(
                text = formatTime(
                    alarm.hour,
                    alarm.minute
                ),
                color = if (alarm.isEnabled) {
                    Color.White
                } else {
                    Muted
                },
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = alarm.label,
                color = Muted,
                fontSize = 13.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, enabled = enabled) {
                    Text("Edit", color = Accent)
                }
                TextButton(onClick = onDelete, enabled = enabled) {
                    Text("Delete", color = Color(0xFFF87171))
                }
            }
        }

        Switch(
            checked = alarm.isEnabled,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent
            )
        )
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
