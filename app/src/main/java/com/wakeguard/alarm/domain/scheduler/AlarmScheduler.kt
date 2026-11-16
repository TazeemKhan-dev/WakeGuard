package com.wakeguard.alarm.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.wakeguard.alarm.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_ALARM_LABEL = "alarm_label"
    const val EXTRA_GENERATION = "schedule_generation"
    const val EXTRA_TRIGGER_TYPE = "trigger_type"
    const val TYPE_NORMAL = "NORMAL"
    const val TYPE_SNOOZE = "SNOOZE"

    private fun manager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canSchedule(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun alarmIntent(
        context: Context, alarmId: Int, generation: Long, type: String, label: String
    ): Intent = Intent(context, AlarmReceiver::class.java).apply {
        action = "com.wakeguard.alarm.$type"
        data = Uri.parse("wakeguard://alarm/$alarmId/$generation/$type")
        putExtra(EXTRA_ALARM_ID, alarmId)
        putExtra(EXTRA_ALARM_LABEL, label)
        putExtra(EXTRA_GENERATION, generation)
        putExtra(EXTRA_TRIGGER_TYPE, type)
    }

    private fun pendingIntent(
        context: Context, alarmId: Int, generation: Long, type: String, label: String
    ): PendingIntent = PendingIntent.getBroadcast(
        context, alarmId, alarmIntent(context, alarmId, generation, type, label),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(
        context: Context, alarmId: Int, hour: Int, minute: Int,
        label: String, generation: Long = 1L
    ): Boolean {
        val alarmManager = manager(context)
        if (!canSchedule(alarmManager)) return false
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, calendar.timeInMillis,
                pendingIntent(context, alarmId, generation, TYPE_NORMAL, label)
            )
            true
        } catch (_: SecurityException) { false }
    }

    fun snooze(
        context: Context, alarmId: Int, label: String,
        minutes: Int = 5, generation: Long = 1L
    ): Boolean {
        val alarmManager = manager(context)
        if (!canSchedule(alarmManager)) return false
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + minutes * 60_000L,
                pendingIntent(context, alarmId, generation, TYPE_SNOOZE, label)
            )
            true
        } catch (_: SecurityException) { false }
    }

    private fun cancelType(context: Context, alarmId: Int, generation: Long, type: String) {
        val pending = PendingIntent.getBroadcast(
            context, alarmId,
            alarmIntent(context, alarmId, generation, type, ""),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            manager(context).cancel(pending)
            pending.cancel()
        }
    }

    // Cancels the old pre-generation PendingIntent, including legacy snoozes.
    private fun cancelLegacy(context: Context, alarmId: Int) {
        val pending = PendingIntent.getBroadcast(
            context, alarmId, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            manager(context).cancel(pending)
            pending.cancel()
        }
    }

    fun cancel(context: Context, alarmId: Int, generation: Long = 1L) {
        cancelType(context, alarmId, generation, TYPE_NORMAL)
        cancelType(context, alarmId, generation, TYPE_SNOOZE)
        cancelLegacy(context, alarmId)
    }

    // A PendingIntent existing does not prove AlarmManager has a future alarm queued.
    fun isScheduled(context: Context, alarmId: Int, generation: Long = 1L): Boolean {
        val normal = PendingIntent.getBroadcast(
            context, alarmId,
            alarmIntent(context, alarmId, generation, TYPE_NORMAL, ""),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        val legacy = PendingIntent.getBroadcast(
            context, alarmId, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return normal != null || legacy != null
    }
}
