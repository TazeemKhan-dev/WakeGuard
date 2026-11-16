
package com.wakeguard.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import com.wakeguard.alarm.service.AlarmRingingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val alarmId = intent.getIntExtra("alarm_id", 0)

        if (alarmId == 0) {
            Log.w("WakeGuard", "Ignoring invalid alarm ID")
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Bound the work performed while the broadcast is active.
                withTimeout(8_000L) {
                    val dao = WakeGuardDatabase
                        .getInstance(appContext)
                        .alarmDao()

                    // The current DAO has getAllAlarms().
                    // Match the stable AlarmManager scheduler ID.
                    val alarm = dao.getAllAlarms()
                        .firstOrNull {
                            it.schedulerId == alarmId
                        }

                    if (alarm == null) {
                        Log.w(
                            "WakeGuard",
                            "Ignoring trigger for deleted alarm: $alarmId"
                        )
                        return@withTimeout
                    }

                    if (!alarm.isEnabled) {
                        Log.w(
                            "WakeGuard",
                            "Ignoring trigger for disabled alarm: $alarmId"
                        )
                        return@withTimeout
                    }

                    val serviceIntent = Intent(
                        appContext,
                        AlarmRingingService::class.java
                    ).apply {
                        action = AlarmRingingService.ACTION_START
                        putExtra("alarm_id", alarm.schedulerId)
                        putExtra("alarm_label", alarm.label)
                    }

                    try {
                        if (Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.O
                        ) {
                            appContext.startForegroundService(
                                serviceIntent
                            )
                        } else {
                            appContext.startService(
                                serviceIntent
                            )
                        }

                        Log.d(
                            "WakeGuard",
                            "Validated alarm trigger: $alarmId"
                        )
                    } catch (e: Exception) {
                        Log.e(
                            "WakeGuard",
                            "Unable to start ringing service",
                            e
                        )
                    }
                }
            } catch (e: CancellationException) {
                Log.w(
                    "WakeGuard",
                    "Alarm validation timed out: $alarmId"
                )
            } catch (e: Exception) {
                Log.e(
                    "WakeGuard",
                    "Alarm validation failed: $alarmId",
                    e
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
