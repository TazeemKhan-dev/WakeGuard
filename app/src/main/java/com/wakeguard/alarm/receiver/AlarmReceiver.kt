package com.wakeguard.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wakeguard.alarm.linked.LinkedAlarmManager
import com.wakeguard.alarm.awake.AwakeCheckManager
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler
import com.wakeguard.alarm.service.AlarmRingingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (com.wakeguard.alarm.domain.scheduler.UpcomingAlarmReminder.handle(this, context, intent)) return
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        if (alarmId == 0) return
        val generation = intent.getLongExtra(AlarmScheduler.EXTRA_GENERATION, 1L)
        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TRIGGER_TYPE)
            ?: AlarmScheduler.TYPE_NORMAL // Legacy alarms from the previous version.
        if (type != AlarmScheduler.TYPE_NORMAL && type != AlarmScheduler.TYPE_SNOOZE) return
        val occurrence = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val result = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    val alarm = WakeGuardDatabase.getInstance(appContext)
                        .alarmDao().getAllAlarms()
                        .firstOrNull { it.schedulerId == alarmId }
                    if (alarm == null) {
                        Log.w("WakeGuard", "Rejected deleted alarm $alarmId")
                        return@withTimeout
                    }
                    // A completed one-time alarm is disabled in Room, but its
                    // snoozed wake-up session must remain eligible to ring.
                    val activeOneTimeSnooze = type == AlarmScheduler.TYPE_SNOOZE &&
                        alarm.repeatDays.isBlank() && alarm.awakeCheckEnabled
                    if (!alarm.isEnabled && !activeOneTimeSnooze) {
                        Log.w("WakeGuard", "Rejected deleted/disabled alarm $alarmId")
                        return@withTimeout
                    }
                    if (alarm.scheduleGeneration != generation) {
                        Log.w("WakeGuard", "Rejected stale alarm $alarmId generation $generation")
                        return@withTimeout
                    }
                    if (type == AlarmScheduler.TYPE_NORMAL) {
                        com.wakeguard.alarm.domain.scheduler.UpcomingAlarmReminder.consume(appContext, alarmId, generation, occurrence)
                    }
                    // Re-arm recurring alarms before ringing. One-time alarms remain one-shot.
                    // A generation-aware PendingIntent ensures the next occurrence is distinct.
                    if (type == AlarmScheduler.TYPE_NORMAL && alarm.repeatDays.isNotBlank()) {
                        val rescheduled = AlarmScheduler.schedule(
                            appContext, alarm.schedulerId, alarm.hour, alarm.minute,
                            alarm.label, alarm.scheduleGeneration, alarm.repeatDays
                        )
                        if (!rescheduled) {
                            Log.e("WakeGuard", "Could not schedule next repeat: $alarmId")
                        }
                    }
                    if (LinkedAlarmManager.isSkipped(appContext, alarm.schedulerId, occurrence)) {
                        Log.i("WakeGuard", "Skipped confirmed linked alarm ${alarm.schedulerId}")
                        if (type == AlarmScheduler.TYPE_NORMAL && alarm.repeatDays.isBlank()) {
                            WakeGuardDatabase.getInstance(appContext).alarmDao()
                                .updateAlarm(alarm.copy(isEnabled = false))
                        }
                        return@withTimeout
                    }
                    if (type == AlarmScheduler.TYPE_SNOOZE &&
                        !AlarmScheduler.consumeSnooze(appContext, alarmId, generation)) return@withTimeout
                    AwakeCheckManager.cancel(appContext, alarm.schedulerId)
                    val serviceIntent = Intent(appContext, AlarmRingingService::class.java).apply {
                        action = AlarmRingingService.ACTION_START
                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.schedulerId)
                        putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarm.label)
                        putExtra(AlarmScheduler.EXTRA_GENERATION, alarm.scheduleGeneration)
                        putExtra(AlarmScheduler.EXTRA_OCCURRENCE, occurrence)
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
                    // Recheck after preparing delivery in case group confirmation raced this broadcast.
                    if (LinkedAlarmManager.isSkipped(appContext, alarm.schedulerId, occurrence)) return@withTimeout
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(serviceIntent)
                    } else {
                        appContext.startService(serviceIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("WakeGuard", "Alarm validation/delivery failed: $alarmId", e)
            } finally {
                result.finish()
            }
        }
    }
}
