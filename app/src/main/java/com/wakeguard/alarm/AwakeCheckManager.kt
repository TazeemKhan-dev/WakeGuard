package com.wakeguard.alarm.awake

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.wakeguard.alarm.receiver.AwakeCheckReceiver
import java.util.UUID

object AwakeCheckManager {
    const val ACTION_PROMPT = "com.wakeguard.alarm.AWAKE_PROMPT"
    const val ACTION_TIMEOUT = "com.wakeguard.alarm.AWAKE_TIMEOUT"
    const val ACTION_CONFIRM = "com.wakeguard.alarm.AWAKE_CONFIRM"
    const val EXTRA_TOKEN = "awake_token"
    private const val PREFS = "wakeguard_awake_check"
    private const val PREFIX = "check_"

    data class Check(val alarmId: Int, val generation: Long, val token: String,
                     val promptAt: Long, val deadlineAt: Long, val occurrence: Long)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(id: Int) = PREFIX + id
    private fun serialize(c: Check) = listOf(c.generation, c.token, c.promptAt, c.deadlineAt, c.occurrence).joinToString("|")
    fun read(context: Context, id: Int): Check? {
        val parts = prefs(context).getString(key(id), null)?.split('|') ?: return null
        if (parts.size !in 4..5) return null
        return Check(id, parts[0].toLongOrNull() ?: return null, parts[1],
            parts[2].toLongOrNull() ?: return null, parts[3].toLongOrNull() ?: return null, parts.getOrNull(4)?.toLongOrNull() ?: (parts[2].toLongOrNull() ?: return null))
    }
    fun all(context: Context): List<Check> = prefs(context).all.keys.mapNotNull { name ->
        if (!name.startsWith(PREFIX)) null else name.removePrefix(PREFIX).toIntOrNull()?.let { read(context, it) }
    }
    fun matches(context: Context, id: Int, token: String): Check? =
        read(context, id)?.takeIf { it.token == token }

    private fun pending(context: Context, c: Check, action: String): PendingIntent {
        val intent = Intent(context, AwakeCheckReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("wakeguard://awake/${c.alarmId}/$action")
            putExtra("alarm_id", c.alarmId)
            putExtra(EXTRA_TOKEN, c.token)
        }
        return PendingIntent.getBroadcast(context, c.alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun manager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun exact(context: Context, c: Check, action: String, at: Long): Boolean {
        val alarmManager = manager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) return false
        return try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, c, action))
            true
        } catch (e: SecurityException) { Log.e("WakeGuard", "Awake Check exact alarm denied", e); false }
    }
    fun start(context: Context, alarmId: Int, generation: Long, delayMinutes: Int, timeoutSeconds: Int, occurrence: Long = System.currentTimeMillis()): Boolean {
        cancel(context, alarmId)
        val prompt = System.currentTimeMillis() + delayMinutes.coerceIn(1, 1440) * 60_000L
        val c = Check(alarmId, generation, UUID.randomUUID().toString(), prompt,
            prompt + timeoutSeconds.coerceIn(1, 3600) * 1000L, occurrence)
        // Persist before scheduling; reboot can restore the same token.
        if (!prefs(context).edit().putString(key(alarmId), serialize(c)).commit()) return false
        if (exact(context, c, ACTION_PROMPT, c.promptAt) && exact(context, c, ACTION_TIMEOUT, c.deadlineAt)) return true
        cancel(context, alarmId)
        return false
    }
    fun cancel(context: Context, alarmId: Int) {
        val c = read(context, alarmId) ?: return
        listOf(ACTION_PROMPT, ACTION_TIMEOUT).forEach { action ->
            val pi = pending(context, c, action)
            manager(context).cancel(pi)
            pi.cancel()
        }
        prefs(context).edit().remove(key(alarmId)).commit()
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notifications.cancel(notificationId(alarmId))
    }
    fun notificationId(id: Int) = 800000 + (id and 0x7fffffff) % 100000
    fun restore(context: Context) {
        all(context).forEach { c ->
            val now = System.currentTimeMillis()
            if (now >= c.deadlineAt) {
                // Process overdue timeouts immediately on boot.
                context.sendBroadcast(Intent(context, AwakeCheckReceiver::class.java).apply {
                    action = ACTION_TIMEOUT
                    putExtra("alarm_id", c.alarmId)
                    putExtra(EXTRA_TOKEN, c.token)
                })
            } else {
                if (now < c.promptAt) exact(context, c, ACTION_PROMPT, c.promptAt)
                else context.sendBroadcast(Intent(context, AwakeCheckReceiver::class.java).apply {
                    action = ACTION_PROMPT
                    putExtra("alarm_id", c.alarmId)
                    putExtra(EXTRA_TOKEN, c.token)
                })
                exact(context, c, ACTION_TIMEOUT, c.deadlineAt)
            }
        }
    }
}
