package com.wakeguard.alarm.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wakeguard.alarm.ui.ringing.AlarmRingActivity
import com.wakeguard.alarm.domain.scheduler.AlarmScheduler

class AlarmRingingService : Service() {

    companion object {
        const val ACTION_START = "wakeguard.START"
        const val ACTION_DISMISS = "wakeguard.DISMISS"
        const val ACTION_SNOOZE = "wakeguard.SNOOZE"

        const val CHANNEL_ID = "wakeguard_ringing_v1"
        const val NOTIFICATION_ID = 7001

        const val TIMEOUT_MS = 15 * 60 * 1000L
        const val SNOOZE_MINUTES = 5
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val handler = Handler(Looper.getMainLooper())

    private var currentGeneration = 1L
    private var currentAlarmId = 0
    private var currentLabel = "Wake up!"
    private var currentSnoozeMinutes = 5
    private var currentVibrationEnabled = true
    private var isRinging = false

    private val timeoutRunnable = Runnable {
        Log.d("WakeGuard", "Alarm timeout reached")
        stopRinging()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {

            ACTION_DISMISS -> {
                stopRinging()
            }

            ACTION_SNOOZE -> {
                if (isRinging) {
                    val scheduled = AlarmScheduler.snooze(
                        context = this,
                        alarmId = currentAlarmId,
                        label = currentLabel,
                        minutes = currentSnoozeMinutes,
                        generation = currentGeneration
                    )

                    if (scheduled) {
                        stopRinging()
                    } else {
                        Log.e(
                            "WakeGuard",
                            "Snooze could not be scheduled"
                        )
                    }
                }
            }

            ACTION_START -> {
                val alarmId = intent.getIntExtra(
                    "alarm_id",
                    0
                )

                val label = intent.getStringExtra(
                    "alarm_label"
                ) ?: "Wake up!"

                if (!isRinging) {
                    currentGeneration = intent.getLongExtra(
                        AlarmScheduler.EXTRA_GENERATION, 1L
                    )
                    currentAlarmId = alarmId
                    currentLabel = label
                    currentSnoozeMinutes = intent.getIntExtra("snooze_minutes", 5).coerceIn(1, 60)
                    currentVibrationEnabled = intent.getBooleanExtra("vibration_enabled", true)

                    startRinging()
                } else {
                    Log.w(
                        "WakeGuard",
                        "Another alarm is already ringing"
                    )
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startRinging() {
        isRinging = true

        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }

        startSound()
        if (currentVibrationEnabled) startVibration()

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(
            timeoutRunnable,
            TIMEOUT_MS
        )
    }

    private fun startSound() {
        try {
            val alarmUri =
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ) ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                )

            if (alarmUri == null) {
                Log.e("WakeGuard", "No alarm sound available")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_ALARM
                        )
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )

                setDataSource(
                    applicationContext,
                    alarmUri
                )

                isLooping = true
                prepare()
                start()
            }

        } catch (e: Exception) {
            Log.e(
                "WakeGuard",
                "Alarm audio failed",
                e
            )
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }


private fun startVibration() {
    try {
        vibrator = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            val manager = getSystemService(
                VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(
                VIBRATOR_SERVICE
            ) as Vibrator
        }

        val pattern = longArrayOf(
            0, 600, 400, 600, 400
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

    } catch (e: Exception) {
        Log.e("WakeGuard", "Vibration unavailable", e)
    }
}


    private fun buildNotification(): Notification {

        val fullScreenIntent = Intent(
            this,
            AlarmRingActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra("alarm_label", currentLabel)
        }

        val fullScreenPendingIntent =
            PendingIntent.getActivity(
                this,
                100,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val dismissIntent = Intent(
            this,
            AlarmRingingService::class.java
        ).apply {
            action = ACTION_DISMISS
        }

        val dismissPendingIntent =
            PendingIntent.getService(
                this,
                101,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val snoozeIntent = Intent(
            this,
            AlarmRingingService::class.java
        ).apply {
            action = ACTION_SNOOZE
        }

        val snoozePendingIntent =
            PendingIntent.getService(
                this,
                102,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable.ic_lock_idle_alarm
            )
            .setContentTitle("WakeGuard Alarm")
            .setContentText(currentLabel)
            .setCategory(
                NotificationCompat.CATEGORY_ALARM
            )
            .setPriority(
                NotificationCompat.PRIORITY_MAX
            )
            .setVisibility(
                NotificationCompat.VISIBILITY_PUBLIC
            )
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(
                fullScreenPendingIntent,
                true
            )
            .addAction(
                R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                dismissPendingIntent
            )
            .addAction(
                R.drawable.ic_lock_idle_alarm,
                "Snooze",
                snoozePendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WakeGuard Ringing Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active WakeGuard alarms"
                lockscreenVisibility =
                    Notification.VISIBILITY_PUBLIC

                // Audio is managed by MediaPlayer.
                setSound(null, null)
                enableVibration(false)
            }

            val manager = getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun stopRinging() {
        handler.removeCallbacks(timeoutRunnable)

        isRinging = false

        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            Log.e("WakeGuard", "Audio stop failed", e)
        }

        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        val manager = getSystemService(
            NOTIFICATION_SERVICE
        ) as NotificationManager

        manager.cancel(NOTIFICATION_ID)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)

        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }

        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null

        super.onDestroy()
    }
}