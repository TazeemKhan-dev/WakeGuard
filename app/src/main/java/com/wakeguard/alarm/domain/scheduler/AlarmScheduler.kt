package com.wakeguard.alarm.domain.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.wakeguard.alarm.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    private fun pendingIntent(
        context: Context,
        alarmId: Int,
        label: String
    ): PendingIntent {
        val intent = Intent(
            context,
            AlarmReceiver::class.java
        ).apply {
            putExtra("alarm_id", alarmId)
            putExtra("alarm_label", label)
        }

        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canSchedule(manager: AlarmManager): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
    }

    fun schedule(
        context: Context,
        alarmId: Int,
        hour: Int,
        minute: Int,
        label: String
    ): Boolean {
        val manager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        if (!canSchedule(manager)) return false

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        return try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent(context, alarmId, label)
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun snooze(
        context: Context,
        alarmId: Int,
        label: String,
        minutes: Int = 5
    ): Boolean {
        val manager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        if (!canSchedule(manager)) return false

        return try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() +
                    minutes * 60_000L,
                pendingIntent(context, alarmId, label)
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context, alarmId: Int) {
        val intent = Intent(
            context,
            AlarmReceiver::class.java
        )

        val pending = PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_NO_CREATE or
                PendingIntent.FLAG_IMMUTABLE
        )

        if (pending != null) {
            val manager = context.getSystemService(
                Context.ALARM_SERVICE
            ) as AlarmManager

            manager.cancel(pending)
            pending.cancel()
        }
    }
}