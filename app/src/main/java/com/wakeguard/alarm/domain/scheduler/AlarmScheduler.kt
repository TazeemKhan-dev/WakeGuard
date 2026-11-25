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
    const val EXTRA_OCCURRENCE = "occurrence_at"
    const val EXTRA_GENERATION = "schedule_generation"
    const val EXTRA_TRIGGER_TYPE = "trigger_type"
    const val TYPE_NORMAL = "NORMAL"
    const val TYPE_SNOOZE = "SNOOZE"

    private fun manager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canSchedule(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun alarmIntent(
        context: Context, alarmId: Int, generation: Long, type: String, label: String, occurrence: Long = 0L
    ): Intent = Intent(context, AlarmReceiver::class.java).apply {
        action = "com.wakeguard.alarm.$type"
        data = Uri.parse("wakeguard://alarm/$alarmId/$generation/$type")
        putExtra(EXTRA_ALARM_ID, alarmId)
        putExtra(EXTRA_ALARM_LABEL, label)
        putExtra(EXTRA_GENERATION, generation)
        putExtra(EXTRA_TRIGGER_TYPE, type)
        putExtra(EXTRA_OCCURRENCE, occurrence)
    }

    private fun pendingIntent(
        context: Context, alarmId: Int, generation: Long, type: String, label: String, occurrence: Long = 0L
    ): PendingIntent = PendingIntent.getBroadcast(
        context, alarmId, alarmIntent(context, alarmId, generation, type, label, occurrence),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun nextOccurrenceMillis(hour: Int, minute: Int, repeatDays: String,
                             afterMillis: Long = System.currentTimeMillis()): Long? {
        val c = Calendar.getInstance().apply {
            timeInMillis = afterMillis
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= afterMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (repeatDays.isNotBlank()) {
            val days = repeatDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
            if (days.isEmpty()) return null
            repeat(7) {
                if (c.get(Calendar.DAY_OF_WEEK) in days) return c.timeInMillis
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
            return null
        }
        return c.timeInMillis
    }

    fun schedule(
        context: Context, alarmId: Int, hour: Int, minute: Int,
        label: String, generation: Long = 1L, repeatDays: String = ""
    ): Boolean = scheduleAfter(context, alarmId, hour, minute, label, generation, repeatDays,
        System.currentTimeMillis())

    fun scheduleAfter(
        context: Context, alarmId: Int, hour: Int, minute: Int,
        label: String, generation: Long = 1L, repeatDays: String = "", afterMillis: Long
    ): Boolean {
        val alarmManager = manager(context)
        if (!canSchedule(alarmManager)) return false
        var occurrence = nextOccurrenceMillis(hour, minute, repeatDays, afterMillis) ?: return false
        var attempts = 0
        while (repeatDays.isNotBlank() && com.wakeguard.alarm.linked.LinkedAlarmManager.isSkipped(context, alarmId, occurrence)) {
            if (++attempts > 366) return false
            occurrence = nextOccurrenceMillis(hour, minute, repeatDays, occurrence) ?: return false
        }
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, occurrence,
                pendingIntent(context, alarmId, generation, TYPE_NORMAL, label, occurrence)
            )
            true
        } catch (_: SecurityException) { false }
    }

    @Synchronized
    fun snooze(
        context: Context, alarmId: Int, label: String,
        minutes: Int = 5, generation: Long = 1L, occurrence: Long = System.currentTimeMillis()
    ): Boolean {
        val deadline = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        return persistSnooze(context, alarmId, label, generation, deadline, occurrence)
    }

    private fun snoozePrefs(context: Context) =
        context.getSharedPreferences("wakeguard_snoozes", Context.MODE_PRIVATE)

    private fun snoozeKey(id: Int, generation: Long) = "$id:$generation"

    private fun persistSnooze(context: Context, id: Int, label: String,
                               generation: Long, deadline: Long, occurrence: Long): Boolean {
        val am = manager(context)
        if (!canSchedule(am)) return false
        val prefs = snoozePrefs(context)
        val key = snoozeKey(id, generation)
        if (!prefs.edit().putLong(key, deadline).putLong("occ:$key", occurrence).commit()) return false
        return try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                maxOf(deadline, System.currentTimeMillis() + 1000L),
                pendingIntent(context, id, generation, TYPE_SNOOZE, label, occurrence))
            true
        } catch (_: SecurityException) {
            prefs.edit().remove(key).remove("occ:$key").commit()
            false
        }
    }

    @Synchronized
    fun consumeSnooze(context: Context, id: Int, generation: Long): Boolean {
        val prefs = snoozePrefs(context)
        val key = snoozeKey(id, generation)
        val deadline = prefs.getLong(key, 0L)
        if (deadline == 0L || deadline > System.currentTimeMillis()) return false
        return prefs.edit().remove(key).remove("occ:$key").commit()
    }

    @Synchronized
    fun restoreSnooze(context: Context, id: Int, generation: Long, label: String): Boolean {
        val deadline = snoozePrefs(context).getLong(snoozeKey(id, generation), 0L)
        if (deadline == 0L) return true
        return persistSnooze(context, id, label, generation, deadline, snoozeOccurrence(context, id, generation))
    }

    fun snoozeOccurrence(context: Context, id: Int, generation: Long): Long =
        snoozePrefs(context).getLong("occ:${snoozeKey(id, generation)}", 0L)

    fun hasSnooze(context: Context, id: Int, generation: Long): Boolean =
        snoozePrefs(context).contains(snoozeKey(id, generation))

    data class PendingSnooze(val alarmId: Int, val generation: Long, val deadline: Long, val occurrence: Long)

    @Synchronized
    fun pendingSnoozes(context: Context): List<PendingSnooze> = snoozePrefs(context).all.mapNotNull { (key, value) ->
        val parts = key.split(':')
        if (parts.size != 2 || value !is Long) return@mapNotNull null
        val id = parts[0].toIntOrNull() ?: return@mapNotNull null
        val generation = parts[1].toLongOrNull() ?: return@mapNotNull null
        PendingSnooze(id, generation, value, snoozeOccurrence(context, id, generation))
    }

    @Synchronized
    fun cancelSnooze(context: Context, snooze: PendingSnooze): Boolean {
        val prefs = snoozePrefs(context)
        val key = snoozeKey(snooze.alarmId, snooze.generation)
        if (prefs.getLong(key, 0L) != snooze.deadline ||
            snoozeOccurrence(context, snooze.alarmId, snooze.generation) != snooze.occurrence) return false
        if (!prefs.edit().remove(key).remove("occ:$key").commit()) return false
        cancelType(context, snooze.alarmId, snooze.generation, TYPE_SNOOZE)
        return true
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

    @Synchronized
    fun cancel(context: Context, alarmId: Int, generation: Long = 1L) {
        snoozePrefs(context).edit().remove(snoozeKey(alarmId, generation)).remove("occ:${snoozeKey(alarmId, generation)}").commit()
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
