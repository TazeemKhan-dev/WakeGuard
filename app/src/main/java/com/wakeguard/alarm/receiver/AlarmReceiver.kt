package com.wakeguard.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wakeguard.alarm.service.AlarmRingingService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val alarmId = intent.getIntExtra("alarm_id", 0)
        val label = intent.getStringExtra("alarm_label")
            ?: "Wake up!"

        val serviceIntent = Intent(
            context,
            AlarmRingingService::class.java
        ).apply {
            action = AlarmRingingService.ACTION_START
            putExtra("alarm_id", alarmId)
            putExtra("alarm_label", label)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(
                "WakeGuard",
                "Unable to start ringing service",
                e
            )
        }
    }
}
