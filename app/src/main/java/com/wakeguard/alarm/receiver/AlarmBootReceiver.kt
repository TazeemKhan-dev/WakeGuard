package com.wakeguard.alarm.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import com.wakeguard.alarm.service.AlarmRingingService
import com.wakeguard.alarm.service.RingingSessionStore
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wakeguard.alarm.linked.LinkedAlarmManager
import com.wakeguard.alarm.awake.AwakeCheckManager
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmBootReceiver : BroadcastReceiver() {
    private val recoveryAction = "com.wakeguard.alarm.RECOVER_RINGING_QUEUE"

    private fun scheduleQueueRecovery(context: Context) {
        if (RingingSessionStore.read(context).isEmpty()) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            Log.w("WakeGuard", "Queue recovery requires exact alarm permission")
            return
        }
        val pending = PendingIntent.getBroadcast(context, 8108,
            Intent(context, AlarmBootReceiver::class.java).setAction(recoveryAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 2000L, pending)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == recoveryAction) {
            if (RingingSessionStore.read(context).isEmpty()) return
            try {
                val service = Intent(context, AlarmRingingService::class.java)
                    .setAction(AlarmRingingService.ACTION_RECOVER)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service)
                else context.startService(service)
            } catch (e: Exception) {
                Log.e("WakeGuard", "Queue recovery start failed", e)
            }
            return
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = WakeGuardDatabase.getInstance(app).alarmDao()
                val now = System.currentTimeMillis()
                val queued = RingingSessionStore.read(app)
                for (alarm in dao.getAllAlarms()) {
                    if (!AlarmScheduler.hasSnooze(app, alarm.schedulerId, alarm.scheduleGeneration)) continue
                    if ((!alarm.isEnabled && !(alarm.repeatDays.isBlank() && alarm.awakeCheckEnabled)) ||
                        LinkedAlarmManager.isSkipped(app, alarm.schedulerId, AlarmScheduler.snoozeOccurrence(app, alarm.schedulerId, alarm.scheduleGeneration).takeIf { it > 0L } ?: now)) continue
                    try {
                        if (!AlarmScheduler.restoreSnooze(app, alarm.schedulerId, alarm.scheduleGeneration, alarm.label)) {
                            Log.w("WakeGuard", "Snooze recovery failed: ${alarm.schedulerId}")
                        }
                    } catch (e: Exception) {
                        Log.e("WakeGuard", "Snooze recovery failed: ${alarm.schedulerId}", e)
                    }
                }
                for (alarm in dao.getEnabledAlarms()) {
                    if (alarm.repeatDays.isBlank() && queued.any {
                        it.getIntExtra("alarm_id", 0) == alarm.schedulerId &&
                            it.getLongExtra(AlarmScheduler.EXTRA_GENERATION, 1L) == alarm.scheduleGeneration &&
                            !LinkedAlarmManager.isSkipped(app, alarm.schedulerId, it.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, 0L).takeIf { at -> at > 0L } ?: now)
                    }) continue
                    if (alarm.repeatDays.isBlank() && AlarmScheduler.hasSnooze(app, alarm.schedulerId, alarm.scheduleGeneration)) continue
                    try {
                        var after = now
                        val next = AlarmScheduler.nextOccurrenceMillis(
                            alarm.hour, alarm.minute, alarm.repeatDays, after
                        ) ?: continue
                        if (LinkedAlarmManager.isSkipped(app, alarm.schedulerId, next)) {
                            if (alarm.repeatDays.isBlank()) {
                                dao.updateAlarm(alarm.copy(isEnabled = false))
                                continue
                            }
                            after = Calendar.getInstance().apply {
                                timeInMillis = next
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis
                        }
                        if (!AlarmScheduler.scheduleAfter(app, alarm.schedulerId,
                                alarm.hour, alarm.minute, alarm.label,
                                alarm.scheduleGeneration, alarm.repeatDays, after)) {
                            Log.w("WakeGuard", "Boot scheduling failed: ${alarm.schedulerId}")
                        }
                    } catch (e: Exception) {
                        Log.e("WakeGuard", "Boot recovery failed: ${alarm.schedulerId}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("WakeGuard", "Boot alarm recovery failed", e)
            } finally {
                try {
                    AwakeCheckManager.restore(app)
                } catch (e: Exception) {
                    Log.e("WakeGuard", "Boot Awake Check recovery failed", e)
                } finally {
                    try { scheduleQueueRecovery(app) }
                    catch (e: Exception) { Log.e("WakeGuard", "Queue recovery scheduling failed", e) }
                    result.finish()
                }
            }
        }
    }
}
