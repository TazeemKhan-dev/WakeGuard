package com.wakeguard.alarm.receiver

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wakeguard.alarm.awake.AwakeCheckManager as A
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.linked.LinkedAlarmManager
import com.wakeguard.alarm.service.AlarmRingingService
import com.wakeguard.alarm.ui.ringing.AwakeCheckActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AwakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("alarm_id", 0)
        val token = intent.getStringExtra(A.EXTRA_TOKEN) ?: return
        if (id == 0) return
        A.matches(context, id, token) ?: return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val check = A.matches(app, id, token) ?: return@launch
                val alarm = WakeGuardDatabase.getInstance(app).alarmDao().getAllAlarms()
                    .firstOrNull { it.schedulerId == id }
                if (A.matches(app, id, token) == null) return@launch
                if (alarm == null || alarm.scheduleGeneration != check.generation || !alarm.awakeCheckEnabled) {
                    A.cancelMatching(app, id, token)
                    return@launch
                }
                if (LinkedAlarmManager.isSkipped(app, id, check.occurrence)) {
                    A.cancelMatching(app, id, token)
                    return@launch
                }
                when (intent.action) {
                    A.ACTION_CONFIRM -> {
                        // Reject confirmations arriving after the response deadline.
                        if (System.currentTimeMillis() >= check.deadlineAt) return@launch
                        if (!A.cancelMatching(app, id, token)) return@launch
                        LinkedAlarmManager.confirm(app, alarm, check.occurrence)
                        com.wakeguard.alarm.domain.scheduler.OneTimeAlarmLifecycle.finish(app, id, check.generation)
                    }
                    A.ACTION_PROMPT -> {
                        if (System.currentTimeMillis() >= check.deadlineAt) return@launch
                        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            nm.createNotificationChannel(NotificationChannel("wakeguard_awake_v1", "Awake Check", NotificationManager.IMPORTANCE_HIGH))
                        }
                        val open = PendingIntent.getActivity(app, id, Intent(app, AwakeCheckActivity::class.java).apply {
                            putExtra("alarm_id", id)
                            putExtra(A.EXTRA_TOKEN, token)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        val confirm = PendingIntent.getBroadcast(app, id, Intent(app, AwakeCheckReceiver::class.java).apply {
                            action = A.ACTION_CONFIRM
                            data = android.net.Uri.parse("wakeguard://awake/$id/confirm")
                            putExtra("alarm_id", id)
                            putExtra(A.EXTRA_TOKEN, token)
                        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        synchronized(A) {
                        if (A.matches(app, id, token) == null) return@launch
                        nm.notify(A.notificationId(id), NotificationCompat.Builder(app, "wakeguard_awake_v1")
                            .setSmallIcon(R.drawable.ic_lock_idle_alarm)
                            .setContentTitle("Are you still awake?")
                            .setContentText("Confirm before your timer runs out")
                            .setContentIntent(open)
                            .setFullScreenIntent(open, true)
                            .setCategory(NotificationCompat.CATEGORY_ALARM)
                            .setAutoCancel(false)
                            .setOngoing(true)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .addAction(R.drawable.ic_input_add, "I'm Awake", confirm)
                            .build())
                        }
                    }
                    A.ACTION_TIMEOUT -> {
                        if (System.currentTimeMillis() < check.deadlineAt) return@launch
                        if (!A.cancelMatching(app, id, token)) return@launch
                        // One-time alarms may be disabled after dismissal; rerings are deliberately
                        // dispatched from the validated saved record, not through the normal receiver.
                        val ring = Intent(app, AlarmRingingService::class.java).apply {
                            action = AlarmRingingService.ACTION_START
                            putExtra("alarm_id", alarm.schedulerId)
                            putExtra("alarm_label", alarm.label)
                            putExtra("schedule_generation", alarm.scheduleGeneration)
                            putExtra("occurrence_at", check.occurrence)
                            putExtra("snooze_minutes", alarm.snoozeMinutes)
                            putExtra("vibration_enabled", alarm.vibrationEnabled)
                            putExtra("sound_uri", alarm.soundUri)
                            putExtra("challenge_type", alarm.challengeType)
                            putExtra("math_difficulty", alarm.mathDifficulty)
                            putExtra("math_question_count", alarm.mathQuestionCount)
                            putExtra("shake_count", alarm.shakeCount)
                            putExtra("memory_difficulty", alarm.memoryDifficulty)
                            putExtra("memory_rounds", alarm.memoryRounds)
                            putExtra("memory_duration_seconds", alarm.memoryDurationSeconds)
                        }
                        if (LinkedAlarmManager.isSkipped(app, id, check.occurrence)) return@launch
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(ring)
                        else app.startService(ring)
                    }
                }
            } catch (e: Exception) { Log.e("WakeGuard", "Awake Check delivery failed", e) }
            finally { result.finish() }
        }
    }
}
