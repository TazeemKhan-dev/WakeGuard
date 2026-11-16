package com.wakeguard.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler
import com.wakeguard.alarm.service.AlarmRingingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, 0)
        if (alarmId == 0) return
        val generation = intent.getLongExtra(AlarmScheduler.EXTRA_GENERATION, 1L)
        val type = intent.getStringExtra(AlarmScheduler.EXTRA_TRIGGER_TYPE)
            ?: AlarmScheduler.TYPE_NORMAL // Legacy alarms from the previous version.
        if (type != AlarmScheduler.TYPE_NORMAL && type != AlarmScheduler.TYPE_SNOOZE) return
        val result = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    val alarm = WakeGuardDatabase.getInstance(appContext)
                        .alarmDao().getAllAlarms()
                        .firstOrNull { it.schedulerId == alarmId }
                    if (alarm == null || !alarm.isEnabled) {
                        Log.w("WakeGuard", "Rejected deleted/disabled alarm $alarmId")
                        return@withTimeout
                    }
                    if (alarm.scheduleGeneration != generation) {
                        Log.w("WakeGuard", "Rejected stale alarm $alarmId generation $generation")
                        return@withTimeout
                    }
                    val serviceIntent = Intent(appContext, AlarmRingingService::class.java).apply {
                        action = AlarmRingingService.ACTION_START
                        putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.schedulerId)
                        putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, alarm.label)
                        putExtra(AlarmScheduler.EXTRA_GENERATION, alarm.scheduleGeneration)
                    }
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
