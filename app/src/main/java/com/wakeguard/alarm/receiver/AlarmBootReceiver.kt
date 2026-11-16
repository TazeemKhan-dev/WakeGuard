package com.wakeguard.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WakeGuardDatabase.getInstance(app).alarmDao().getEnabledAlarms()
                    .filter { it.repeatDays.isNotBlank() }
                    .forEach { alarm ->
                        AlarmScheduler.schedule(
                            app, alarm.schedulerId, alarm.hour, alarm.minute,
                            alarm.label, alarm.scheduleGeneration, alarm.repeatDays
                        )
                    }
            } catch (e: Exception) {
                Log.e("WakeGuard", "Reboot repeat recovery failed", e)
            } finally {
                result.finish()
            }
        }
    }
}
