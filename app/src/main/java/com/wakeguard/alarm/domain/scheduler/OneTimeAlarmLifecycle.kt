package com.wakeguard.alarm.domain.scheduler

import android.content.Context
import com.wakeguard.alarm.awake.AwakeCheckManager
import com.wakeguard.alarm.data.local.WakeGuardDatabase

/** Call from an IO coroutine, after the current wake-up session is resolved. */
object OneTimeAlarmLifecycle {
    private fun prefs(context: Context) = context.getSharedPreferences("wakeguard_one_time", Context.MODE_PRIVATE)
    fun autoDelete(context: Context): Boolean = prefs(context).getBoolean("auto_delete", false)
    fun setAutoDelete(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("auto_delete", enabled).commit()

    fun finish(context: Context, id: Int, generation: Long) {
        val db = WakeGuardDatabase.getInstance(context)
        db.runInTransaction(Runnable {
            val dao = db.alarmDao()
            val alarm = dao.getAllAlarms().firstOrNull {
                it.schedulerId == id && it.scheduleGeneration == generation && it.repeatDays.isBlank()
            } ?: return@Runnable
            val pending = AwakeCheckManager.read(context, id)?.generation == generation ||
                AlarmScheduler.pendingSnoozes(context).any { it.alarmId == id && it.generation == generation }
            if (pending) {
                if (alarm.isEnabled) dao.updateAlarm(alarm.copy(isEnabled = false))
                return@Runnable
            }
            AlarmScheduler.cancel(context, id, generation)
            if (autoDelete(context)) dao.deleteAlarm(alarm)
            else if (alarm.isEnabled) dao.updateAlarm(alarm.copy(isEnabled = false))
        })
    }
}
