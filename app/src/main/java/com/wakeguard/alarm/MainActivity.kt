
package com.wakeguard.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
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
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
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
import java.util.Calendar
import java.util.UUID

private val Background = Color(0xFF101827)
private val CardColor = Color(0xFF1E293B)
private val Accent = Color(0xFF10B981)
private val Muted = Color(0xFF94A3B8)

data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val label: String = "New Alarm",
    val enabled: Boolean = true
)

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

            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            )

            context.startActivity(settingsIntent)
        }
    }
}


    // Notification permission for Android 13+
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                Toast.makeText(
                    context,
                    "Allow notifications for alarm alerts",
                    Toast.LENGTH_LONG
                ).show()
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

    // Temporary in-memory storage.
    // Room database will be added in the next milestone.
    val alarms = remember {
        mutableStateListOf<AlarmItem>()
    }

    val nextAlarm = alarms
        .filter { it.enabled }
        .minByOrNull { alarm ->
            val now = Calendar.getInstance()

            val currentMinutes =
                now.get(Calendar.HOUR_OF_DAY) * 60 +
                    now.get(Calendar.MINUTE)

            val alarmMinutes =
                alarm.hour * 60 + alarm.minute

            (alarmMinutes - currentMinutes + 1440) % 1440
        }

    // Schedule a new alarm
    val timePicker = {
        val now = Calendar.getInstance()

        TimePickerDialog(
            context,
            { _, hour, minute ->

                val newAlarm = AlarmItem(
                    hour = hour,
                    minute = minute
                )

                val alarmId = newAlarm.id.hashCode()

                val scheduled = AlarmScheduler.schedule(
                    context = context,
                    alarmId = alarmId,
                    hour = hour,
                    minute = minute,
                    label = newAlarm.label
                )

                if (scheduled) {
                    alarms.add(newAlarm)

                    Toast.makeText(
                        context,
                        "Alarm scheduled!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Please allow exact alarms, then try again",
                        Toast.LENGTH_LONG
                    ).show()

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.S
                    ) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                            )
                        )
                    }
                }
            },
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            false
        ).show()
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

        // Next Alarm Card
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
                text = nextAlarm?.let {
                    formatTime(it.hour, it.minute)
                } ?: "No alarms set",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (nextAlarm != null)
                    "Your next scheduled time"
                else
                    "Create your first alarm",
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

        // Alarm List
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

                        val index = alarms.indexOfFirst {
                            it.id == alarm.id
                        }

                        if (index != -1) {

                            val alarmId = alarm.id.hashCode()

                            if (enabled) {

                                val scheduled =
                                    AlarmScheduler.schedule(
                                        context = context,
                                        alarmId = alarmId,
                                        hour = alarm.hour,
                                        minute = alarm.minute,
                                        label = alarm.label
                                    )

                                if (scheduled) {
                                    alarms[index] =
                                        alarm.copy(enabled = true)

                                    Toast.makeText(
                                        context,
                                        "Alarm enabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Exact alarm permission required",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    if (
                                        Build.VERSION.SDK_INT >=
                                        Build.VERSION_CODES.S
                                    ) {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                            )
                                        )
                                    }
                                }

                            } else {

                                AlarmScheduler.cancel(
                                    context,
                                    alarmId
                                )

                                alarms[index] =
                                    alarm.copy(enabled = false)

                                Toast.makeText(
                                    context,
                                    "Alarm disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Add Alarm Button
        Button(
            onClick = timePicker,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent
            ),
            shape = RoundedCornerShape(16.dp)
        ) {

            Text(
                text = "+  Add New Alarm",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun AlarmCard(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit
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
                color = if (alarm.enabled)
                    Color.White else Muted,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = alarm.label,
                color = Muted,
                fontSize = 13.sp
            )
        }

        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
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
