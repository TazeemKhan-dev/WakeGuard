package com.wakeguard.alarm.linked

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wakeguard.alarm.awake.AwakeCheckManager
import com.wakeguard.alarm.data.local.AlarmEntity
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler
import java.util.Calendar

/** Persist skipped local calendar days; never change a recurring alarm's enabled flag. */
object LinkedAlarmManager {
    private const val PREF = "wakeguard_linked_skips"
    private fun key(id: Int) = "skip_$id"
    private fun day(at: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = at }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }
    private fun skippedDays(context: Context, id: Int): Set<String> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(key(id), null)?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun isSkipped(context: Context, schedulerId: Int, at: Long = System.currentTimeMillis()): Boolean =
        day(at) in skippedDays(context, schedulerId)

    private val groupMutex = Mutex()

    /** Stage new schedules before committing the group state in one Room transaction. */
    suspend fun setEnabled(context: Context, members: List<AlarmEntity>, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            groupMutex.withLock {
                val app = context.applicationContext
                val db = WakeGuardDatabase.getInstance(app)
                val dao = db.alarmDao()
                val originals = members.distinctBy { it.id }.map { member ->
                    dao.getAlarmById(member.id) ?: return@withLock false
                }
                val updates = originals.map {
                    it.copy(isEnabled = enabled, scheduleGeneration = it.scheduleGeneration + 1)
                }
                val staged = mutableListOf<AlarmEntity>()
                var committed = false
                try {
                    if (enabled) {
                        for (alarm in updates) {
                            staged.add(alarm)
                            if (!AlarmScheduler.schedule(app, alarm.schedulerId, alarm.hour,
                                    alarm.minute, alarm.label, alarm.scheduleGeneration, alarm.repeatDays)) {
                                return@withLock false
                            }
                        }
                    }
                    db.withTransaction {
                        for ((index, alarm) in updates.withIndex()) {
                            check(dao.getAlarmById(alarm.id) == originals[index])
                            check(dao.updateAlarm(alarm) == 1)
                        }
                    }
                    committed = true
                    for (alarm in originals) {
                        AwakeCheckManager.cancel(app, alarm.schedulerId)
                        AlarmScheduler.cancel(app, alarm.schedulerId, alarm.scheduleGeneration)
                    }
                    true
                } finally {
                    if (!committed) {
                        for (alarm in staged) {
                            AlarmScheduler.cancel(app, alarm.schedulerId, alarm.scheduleGeneration)
                        }
                    }
                }
            }
        }

    /** Skip a particular local calendar day for a whole group (or one unlinked alarm). */
    suspend fun skipDate(context: Context, members: List<AlarmEntity>, dateMillis: Long): Boolean =
        withContext(Dispatchers.IO) {
            groupMutex.withLock {
                val app = context.applicationContext
                val db = WakeGuardDatabase.getInstance(app)
                val dao = db.alarmDao()
                val target = Calendar.getInstance().apply { timeInMillis = dateMillis }
                val end = (target.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                val now = System.currentTimeMillis()
                val originals = members.distinctBy { it.id }.map { member ->
                    dao.getAlarmById(member.id) ?: return@withLock false
                }.filter { alarm ->
                    alarm.isEnabled && if (alarm.repeatDays.isBlank()) {
                        AlarmScheduler.nextOccurrenceMillis(alarm.hour, alarm.minute, "", now)
                            ?.let { day(it) == day(dateMillis) } == true
                    } else {
                        target.get(Calendar.DAY_OF_WEEK) in alarm.repeatDays.split(',')
                            .mapNotNull { it.trim().toIntOrNull() }
                    }
                }
                val updates = originals.map {
                    it.copy(isEnabled = it.repeatDays.isNotBlank(),
                        scheduleGeneration = it.scheduleGeneration + 1)
                }
                val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                val previous = originals.associate { key(it.schedulerId) to prefs.getString(key(it.schedulerId), null) }
                val staged = mutableListOf<AlarmEntity>()
                var markersWritten = false
                var committed = false
                try {
                    for (alarm in updates.filter { it.isEnabled }) {
                        // Keep an earlier occurrence when skipping a later calendar day.
                        val next = AlarmScheduler.nextOccurrenceMillis(alarm.hour, alarm.minute, alarm.repeatDays, now)
                            ?: return@withLock false
                        val after = if (day(next) == day(dateMillis)) end else now
                        staged.add(alarm)
                        if (!AlarmScheduler.scheduleAfter(app, alarm.schedulerId, alarm.hour,
                                alarm.minute, alarm.label, alarm.scheduleGeneration, alarm.repeatDays, after)) {
                            return@withLock false
                        }
                    }
                    val edit = prefs.edit()
                    originals.forEach { edit.putString(key(it.schedulerId), (skippedDays(app, it.schedulerId) + day(dateMillis)).joinToString(",")) }
                    markersWritten = true
                    if (!edit.commit()) return@withLock false
                    db.withTransaction {
                        for ((index, alarm) in updates.withIndex()) {
                            check(dao.getAlarmById(alarm.id) == originals[index])
                            check(dao.updateAlarm(alarm) == 1)
                        }
                    }
                    committed = true
                    for (alarm in originals) {
                        AwakeCheckManager.cancel(app, alarm.schedulerId)
                        AlarmScheduler.cancel(app, alarm.schedulerId, alarm.scheduleGeneration)
                    }
                    true
                } finally {
                    if (!committed) {
                        staged.forEach { AlarmScheduler.cancel(app, it.schedulerId, it.scheduleGeneration) }
                        if (markersWritten) {
                            val restore = prefs.edit()
                            previous.forEach { (k, v) -> if (v == null) restore.remove(k) else restore.putString(k, v) }
                            restore.commit()
                        }
                    }
                }
            }
        }

    private fun occursToday(alarm: AlarmEntity, now: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val scheduled = (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (scheduled.timeInMillis <= now) return false
        if (alarm.repeatDays.isBlank()) return true
        val days = alarm.repeatDays.split(',').mapNotNull { it.trim().toIntOrNull() }
        return c.get(Calendar.DAY_OF_WEEK) in days
    }

    /** Called only after a validated Awake Check confirmation. */
    suspend fun confirm(context: Context, confirmed: AlarmEntity, occurrence: Long = System.currentTimeMillis()) {
        val group = confirmed.groupId ?: return
        val dao = WakeGuardDatabase.getInstance(context).alarmDao()
        val now = System.currentTimeMillis()
        val others = dao.getAllAlarms().filter {
            it.id != confirmed.id && it.groupId == group &&
                (it.isEnabled || it.repeatDays.isBlank())
        }
        for (other in others) {
            // Commit first: if a pending broadcast races cancellation it can still reject the skip.
            val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(key(other.schedulerId), (skippedDays(context, other.schedulerId) + day(occurrence)).joinToString(",")).commit()
            if (!saved) continue
            AwakeCheckManager.cancel(context, other.schedulerId)
            AlarmScheduler.cancel(context, other.schedulerId, other.scheduleGeneration)
            if (other.repeatDays.isBlank()) {
                // An already-completed one-time alarm stays disabled; a future one is skipped.
                if (other.isEnabled && day(now) == day(occurrence) && occursToday(other, now)) {
                    dao.updateAlarm(other.copy(isEnabled = false))
                } else if (other.isEnabled) {
                    // A one-time alarm for a later day must keep its original schedule.
                    AlarmScheduler.schedule(context, other.schedulerId, other.hour,
                        other.minute, other.label, other.scheduleGeneration, other.repeatDays)
                }
            } else {
                // Schedule the next occurrence after today, without changing the enabled state.
                AlarmScheduler.scheduleAfter(context, other.schedulerId, other.hour, other.minute,
                    other.label, other.scheduleGeneration, other.repeatDays, endOfToday(occurrence))
            }
        }
    }

    private fun endOfToday(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
