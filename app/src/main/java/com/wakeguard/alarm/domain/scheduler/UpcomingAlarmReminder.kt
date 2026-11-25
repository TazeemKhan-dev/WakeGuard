package com.wakeguard.alarm.domain.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wakeguard.alarm.MainActivity
import com.wakeguard.alarm.R
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.linked.LinkedAlarmManager
import com.wakeguard.alarm.receiver.AlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import android.text.format.DateFormat
import java.util.Date

/** Tracks normal scheduled occurrences only; never changes the actual alarm schedule. */
object UpcomingAlarmReminder {
    private const val CHANNEL = "wakeguard_upcoming_v1"
    private const val SHOW = "com.wakeguard.alarm.UPCOMING_REMINDER"
    private const val SKIP = "com.wakeguard.alarm.SKIP_REMINDER"
    data class Settings(val enabled: Boolean = true, val minutes: Int = 15)
    private fun prefs(c: Context) = c.getSharedPreferences("wakeguard_advance_reminders", Context.MODE_PRIVATE)
    private fun key(id: Int, gen: Long) = "occ:$id:$gen"
    private fun tag(id: Int, gen: Long) = "upcoming:$id:$gen"
    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    fun settings(c: Context) = Settings(prefs(c).getBoolean("enabled", true), prefs(c).getInt("minutes", 15).coerceIn(1,60))
    private fun pending(c: Context, id: Int, gen: Long, at: Long, action: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(c, id, Intent(c, AlarmReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("wakeguard://advance/$id/$gen/$action")
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
            putExtra(AlarmScheduler.EXTRA_GENERATION, gen)
            putExtra(AlarmScheduler.EXTRA_OCCURRENCE, at)
        }, flags or PendingIntent.FLAG_IMMUTABLE)
    private fun clearDelivery(c: Context, id: Int, gen: Long) {
        for (action in listOf(SHOW, SKIP)) {
            pending(c,id,gen,0,action,PendingIntent.FLAG_NO_CREATE)?.let { am(c).cancel(it); it.cancel() }
        }
        nm(c).cancel(tag(id,gen), 1)
    }
    @Synchronized fun record(c: Context, id: Int, gen: Long, label: String, at: Long) {
        try {
            prefs(c).edit().putLong(key(id,gen),at).apply()
            schedule(c,id,gen,at)
        } catch (e: Exception) { Log.w("WakeGuard", "Advance reminder scheduling failed",e) }
    }
    private fun schedule(c: Context,id: Int,gen: Long,at: Long) {
        clearDelivery(c,id,gen)
        val s = settings(c)
        val now = System.currentTimeMillis()
        if (!s.enabled || at <= now) return
        if (Build.VERSION.SDK_INT >= 31 && !am(c).canScheduleExactAlarms()) return
        val pi = pending(c,id,gen,at,SHOW,PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(now+1000,at-s.minutes*60000L),pi)
    }
    @Synchronized fun cancel(c: Context,id: Int,gen: Long) {
        try { prefs(c).edit().remove(key(id,gen)).apply(); clearDelivery(c,id,gen) }
        catch(e: Exception) { Log.w("WakeGuard","Advance reminder cancellation failed",e) }
    }
    @Synchronized fun consume(c: Context,id: Int,gen: Long,at: Long) {
        if (prefs(c).getLong(key(id,gen),0) == at) cancel(c,id,gen)
    }
    @Synchronized fun save(c: Context,s: Settings): Boolean {
        if (!prefs(c).edit().putBoolean("enabled",s.enabled).putInt("minutes",s.minutes.coerceIn(1,60)).commit()) return false
        prefs(c).all.forEach { (k,v) ->
            val parts = k.split(':')
            if (parts.size == 3 && parts[0] == "occ" && v is Long) {
                val id = parts[1].toIntOrNull(); val gen = parts[2].toLongOrNull()
                if (id != null && gen != null) try { schedule(c,id,gen,v) } catch(e: Exception) { Log.w("WakeGuard","Reminder refresh failed",e) }
            }
        }
        return true
    }
    fun handle(receiver: BroadcastReceiver, context: Context, intent: Intent): Boolean {
        val action = intent.action
        if (action != SHOW && action != SKIP) return false
        val c = context.applicationContext
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID,0)
        val gen = intent.getLongExtra(AlarmScheduler.EXTRA_GENERATION,0)
        val at = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE,0)
        val result = receiver.goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!settings(c).enabled || at <= System.currentTimeMillis() || prefs(c).getLong(key(id,gen),0) != at) return@launch
                val alarms = WakeGuardDatabase.getInstance(c).alarmDao().getAllAlarms()
                val alarm = alarms.firstOrNull { it.schedulerId == id && it.scheduleGeneration == gen && it.isEnabled } ?: return@launch
                if (LinkedAlarmManager.isSkipped(c,id,at)) return@launch
                if (action == SKIP) {
                    val members = if (alarm.groupId == null) listOf(alarm) else alarms.filter { it.groupId == alarm.groupId }
                    if (LinkedAlarmManager.skipDate(c,members,at)) nm(c).cancel(tag(id,gen),1)
                } else {
                    if (Build.VERSION.SDK_INT >= 26) nm(c).createNotificationChannel(NotificationChannel(CHANNEL,"Upcoming alarm reminders",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null); enableVibration(false) })
                    val open = PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val skip = pending(c,id,gen,at,SKIP,PendingIntent.FLAG_UPDATE_CURRENT) ?: return@launch
                    val minutes = ((at-System.currentTimeMillis()+59999)/60000).coerceAtLeast(1)
                    val name = alarm.label.takeUnless { it.isBlank() || it == "New Alarm" } ?: "Alarm"
                    nm(c).notify(tag(id,gen),1,NotificationCompat.Builder(c,CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Alarm in $minutes min")
                        .setContentText("$name · ${DateFormat.getTimeFormat(c).format(Date(at))}")
                        .setSilent(true).setPriority(NotificationCompat.PRIORITY_LOW).setAutoCancel(true)
                        .setContentIntent(open).addAction(android.R.drawable.ic_lock_idle_alarm,if(alarm.groupId == null) "Skip alarm" else "Skip linked alarms",skip)
                        .addAction(android.R.drawable.ic_lock_idle_alarm,"Open WakeGuard",open).build())
                }
            } catch(e: Exception) { Log.w("WakeGuard","Advance reminder failed",e) } finally { result.finish() }
        }
        return true
    }
}
