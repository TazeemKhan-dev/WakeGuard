package com.wakeguard.alarm.upcoming

import android.content.Context
import androidx.compose.runtime.*
import com.wakeguard.alarm.awake.AwakeCheckManager as A
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler as S
import com.wakeguard.alarm.linked.LinkedAlarmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

sealed class UpcomingAlert(val name: String, val scheduledAt: Long) {
    class Snooze(name: String, val record: S.PendingSnooze) : UpcomingAlert(name, record.deadline)
    class Awake(name: String, val record: A.Check) : UpcomingAlert(name, record.promptAt)
    val key: String get() = when (this) {
        is Snooze -> "s:${record.alarmId}:${record.generation}:${record.deadline}"
        is Awake -> "a:${record.token}"
    }
}

object UpcomingAlertsManager {
    suspend fun list(context: Context): List<UpcomingAlert> = withContext(Dispatchers.IO) {
        val alarms = WakeGuardDatabase.getInstance(context).alarmDao().getAllAlarms().associateBy { it.schedulerId }
        val result = mutableListOf<UpcomingAlert>()
        S.pendingSnoozes(context).forEach { s ->
            val alarm = alarms[s.alarmId]
            if (alarm != null && alarm.scheduleGeneration == s.generation &&
                (alarm.isEnabled || alarm.repeatDays.isBlank()) &&
                !LinkedAlarmManager.isSkipped(context, s.alarmId, s.occurrence)) {
                result.add(UpcomingAlert.Snooze(alarm.label.ifBlank { "Alarm" }, s))
            }
        }
        A.all(context).forEach { c ->
            val alarm = alarms[c.alarmId]
            if (alarm != null && alarm.scheduleGeneration == c.generation && alarm.awakeCheckEnabled &&
                !LinkedAlarmManager.isSkipped(context, c.alarmId, c.occurrence)) {
                result.add(UpcomingAlert.Awake(alarm.label.ifBlank { "Alarm" }, c))
            }
        }
        result.sortedBy { it.scheduledAt }
    }

    suspend fun resolve(context: Context, alert: UpcomingAlert): Boolean = withContext(Dispatchers.IO) {
        when (alert) {
            is UpcomingAlert.Snooze -> {
                val s = alert.record
                val dao = WakeGuardDatabase.getInstance(context).alarmDao()
                val alarm = dao.getAllAlarms().firstOrNull {
                    it.schedulerId == s.alarmId && it.scheduleGeneration == s.generation
                } ?: return@withContext false
                if (!S.cancelSnooze(context, s)) return@withContext false
                val check = A.read(context, s.alarmId)
                if (check != null && check.generation == s.generation && check.occurrence == s.occurrence) {
                    A.cancelMatching(context, check.alarmId, check.token)
                }
                if (alarm.repeatDays.isBlank()) {
                    val current = dao.getAllAlarms().firstOrNull {
                        it.id == alarm.id && it.scheduleGeneration == s.generation
                    }
                    if (current != null && current.isEnabled && current.repeatDays.isBlank()) {
                        dao.updateAlarm(current.copy(isEnabled = false))
                    }
                }
                true
            }
            is UpcomingAlert.Awake -> {
                val c = alert.record
                val alarm = WakeGuardDatabase.getInstance(context).alarmDao().getAllAlarms()
                    .firstOrNull { it.schedulerId == c.alarmId && it.scheduleGeneration == c.generation && it.awakeCheckEnabled }
                    ?: return@withContext false
                if (!A.cancelMatching(context, c.alarmId, c.token)) return@withContext false
                LinkedAlarmManager.confirm(context, alarm, c.occurrence)
                com.wakeguard.alarm.domain.scheduler.OneTimeAlarmLifecycle.finish(context, c.alarmId, c.generation)
                true
            }
        }
    }
}

@Composable
fun rememberUpcomingAlerts(context: Context): State<List<UpcomingAlert>> = produceState(emptyList(), context) {
    while (true) {
        value = UpcomingAlertsManager.list(context.applicationContext)
        delay(1000L)
    }
}
