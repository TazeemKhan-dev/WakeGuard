package com.wakeguard.alarm.service

import android.R
import com.wakeguard.alarm.linked.LinkedAlarmManager
import java.util.UUID
import com.wakeguard.alarm.awake.AwakeCheckManager
import com.wakeguard.alarm.ringtone.WakeGuardSounds
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
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
import com.wakeguard.alarm.data.local.WakeGuardDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmRingingService : Service() {

    companion object {
        const val ACTION_RECOVER = "wakeguard.RECOVER_RINGING"
        const val ACTION_START = "wakeguard.START"
        const val ACTION_NOTIFICATION_DISMISS = "wakeguard.NOTIFICATION_DISMISS"
        const val ACTION_DISMISS = "wakeguard.DISMISS"
        const val ACTION_SNOOZE = "wakeguard.SNOOZE"
        const val ACTION_SCREEN_OFF_STOP = "wakeguard.SCREEN_OFF_STOP"

        const val CHANNEL_ID = "wakeguard_ringing_v1"
        const val NOTIFICATION_ID = 7001
        private const val RECOVERY_NOTIFICATION_ID = 7002
        private const val RECOVERY_CHANNEL_ID = "wakeguard_recovery_v1"

        const val TIMEOUT_MS = 15 * 60 * 1000L
        const val SNOOZE_MINUTES = 5
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    private val handler = Handler(Looper.getMainLooper())

    private var currentGeneration = 1L
    private var currentOccurrence = 0L
    private var currentAlarmId = 0
    private var currentLabel = "Wake up!"
    private var currentSnoozeMinutes = 5
    private var currentSoundUri: String? = null
    private var currentVibrationEnabled = true
    private var currentChallengeType = "SWIPE"
    private var currentMathDifficulty = "EASY"
    private var currentMathQuestionCount = 3
    private var currentShakeCount = 10
    private var currentMemoryDifficulty = "EASY"
    private var currentMemoryRounds = 3
    private var currentMemoryDurationSeconds = 10
    private val pending = java.util.ArrayDeque<Intent>()
    private var activeIntent: Intent? = null
    private var validating = false
    private var destroyed = false
    private var ringToken = ""
    private var isRinging = false
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF && isRinging) {
                onStartCommand(
                    Intent(this@AlarmRingingService, AlarmRingingService::class.java).apply {
                        action = ACTION_SCREEN_OFF_STOP
                    },
                    0,
                    0
                )
            }
        }
    }

    private val timeoutRunnable = Runnable {
        Log.d("WakeGuard", "Alarm timeout reached")
        stopRinging()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        pending.addAll(RingingSessionStore.read(this))
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {

            ACTION_DISMISS, ACTION_NOTIFICATION_DISMISS -> {
                if (isRinging && intent.getStringExtra("ring_token") == ringToken &&
                    (intent.action != ACTION_NOTIFICATION_DISMISS || currentChallengeType.equals("SWIPE", ignoreCase = true))) {
                    val dismissedId = currentAlarmId
                    val dismissedGeneration = currentGeneration
                    val dismissedOccurrence = currentOccurrence
                    // Persist the one-shot completion independently of this Service's lifetime.
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val dao = WakeGuardDatabase.getInstance(applicationContext).alarmDao()
                            val alarm = dao.getAllAlarms().firstOrNull {
                                it.schedulerId == dismissedId
                            }
                            if (alarm != null && alarm.isEnabled &&
                                alarm.scheduleGeneration == dismissedGeneration &&
                                alarm.repeatDays.isBlank()
                            ) {
                                dao.updateAlarm(alarm.copy(isEnabled = false))
                            }
                        } catch (e: Exception) {
                            Log.e("WakeGuard", "Could not disable completed one-time alarm", e)
                        }
                    }
                    // Only the challenge completion enters this branch.
                    // Every completed challenge starts a new Awake Check, including re-rings.
                    CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val alarm = WakeGuardDatabase.getInstance(applicationContext).alarmDao()
                                    .getAllAlarms().firstOrNull { it.schedulerId == dismissedId }
                                if (alarm != null && alarm.awakeCheckEnabled &&
                                    alarm.scheduleGeneration == dismissedGeneration) {
                                    AwakeCheckManager.start(applicationContext, dismissedId,
                                        dismissedGeneration, alarm.awakeCheckDelayMinutes,
                                        alarm.awakeCheckTimeoutSeconds, dismissedOccurrence)
                                }
                            } catch (e: Exception) { Log.e("WakeGuard", "Awake Check scheduling failed", e) }
                    }
                    stopRinging()
                }
            }

            ACTION_SCREEN_OFF_STOP -> { /* Screen locking must not dismiss an alarm. */ }

            ACTION_SNOOZE -> {
                if (isRinging && intent.getStringExtra("ring_token") == ringToken) {
                    val scheduled = AlarmScheduler.snooze(
                        context = this,
                        alarmId = currentAlarmId,
                        label = currentLabel,
                        minutes = currentSnoozeMinutes,
                        generation = currentGeneration,
                        occurrence = currentOccurrence
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
                val incoming = Intent(intent)
                if (incoming.getIntExtra("alarm_id", 0) != 0 &&
                    (activeIntent?.let { sameSession(it, incoming) } != true) &&
                    pending.none { sameSession(it, incoming) }) {
                    pending.addLast(incoming)
                    persistQueue()
                }
                ensureForegroundForRecovery()
                advanceQueue()
            }
            ACTION_RECOVER, null -> {
                ensureForegroundForRecovery()
                advanceQueue()
            }
        }
        return START_STICKY
    }

    private fun sameSession(a: Intent, b: Intent): Boolean =
        a.getIntExtra("alarm_id", 0) == b.getIntExtra("alarm_id", 0) &&
            a.getLongExtra(AlarmScheduler.EXTRA_GENERATION, 1L) == b.getLongExtra(AlarmScheduler.EXTRA_GENERATION, 1L) &&
            a.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, 0L) == b.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, 0L)

    private fun persistQueue() {
        if (!RingingSessionStore.write(this, listOfNotNull(activeIntent) + pending.toList())) {
            Log.e("WakeGuard", "Ringing journal write failed")
        }
    }

    private fun ensureForegroundForRecovery() {
        if (isRinging) return
        val notification = NotificationCompat.Builder(this, RECOVERY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock_idle_alarm)
            .setContentTitle("WakeGuard")
            .setContentText("Checking pending alarms")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(RECOVERY_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(RECOVERY_NOTIFICATION_ID, notification)
    }

    private fun activate(intent: Intent) {
        activeIntent = intent
        persistQueue()
        ringToken = UUID.randomUUID().toString()
        currentOccurrence = intent.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
        currentGeneration = intent.getLongExtra(AlarmScheduler.EXTRA_GENERATION, 1L)
        currentAlarmId = intent.getIntExtra("alarm_id", 0)
        currentLabel = intent.getStringExtra("alarm_label") ?: "Wake up!"
        currentSnoozeMinutes = intent.getIntExtra("snooze_minutes", 5).coerceIn(1, 60)
        currentVibrationEnabled = intent.getBooleanExtra("vibration_enabled", true)
        currentSoundUri = intent.getStringExtra("sound_uri")
        currentChallengeType = intent.getStringExtra("challenge_type") ?: "SWIPE"
        currentMathDifficulty = intent.getStringExtra("math_difficulty") ?: "EASY"
        currentMathQuestionCount = intent.getIntExtra("math_question_count", 3).coerceIn(1, 10)
        currentShakeCount = intent.getIntExtra("shake_count", 10).coerceIn(1, 30)
        currentMemoryDifficulty = intent.getStringExtra("memory_difficulty") ?: "EASY"
        currentMemoryRounds = intent.getIntExtra("memory_rounds", 3).coerceIn(1, 10)
        currentMemoryDurationSeconds = intent.getIntExtra("memory_duration_seconds", 10).coerceIn(5, 10)
        startRinging()
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

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(RECOVERY_NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= 34) {
            Log.d("WakeGuard", "Full-screen intent allowed=${notificationManager.canUseFullScreenIntent()}")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("WakeGuard", "Ringing channel importance=${notificationManager.getNotificationChannel(CHANNEL_ID)?.importance}")
        }
        Log.d("WakeGuard", "Notifications enabled=${notificationManager.areNotificationsEnabled()}")

        if (!screenOffReceiverRegistered) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            screenOffReceiverRegistered = true
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
        if (currentSoundUri == "silent") {
            Log.i("WakeGuard", "Alarm ringtone explicitly set to Silent")
            return
        }
        val audio = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        Log.d("WakeGuard", "Alarm stream volume=${audio.getStreamVolume(android.media.AudioManager.STREAM_ALARM)}")
        val candidates = listOf(currentSoundUri, null, "builtin:morning_bell").distinct()
        for (key in candidates) {
            try {
                val player = WakeGuardSounds.play(applicationContext, key, true)
                if (player != null) {
                    mediaPlayer = player
                    Log.d("WakeGuard", "Alarm audio started: ${key ?: "default"}")
                    return
                }
            } catch (e: Exception) {
                Log.e("WakeGuard", "Alarm audio failed: ${key ?: "default"}", e)
            }
        }
        Log.e("WakeGuard", "All alarm audio candidates failed")
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

            putExtra("ring_token", ringToken)
            putExtra("alarm_label", currentLabel)
            putExtra("snooze_minutes", currentSnoozeMinutes)
            putExtra("challenge_type", currentChallengeType)
            putExtra("math_difficulty", currentMathDifficulty)
            putExtra("math_question_count", currentMathQuestionCount)
            putExtra("shake_count", currentShakeCount)
            putExtra("memory_difficulty", currentMemoryDifficulty)
            putExtra("memory_rounds", currentMemoryRounds)
            putExtra("memory_duration_seconds", currentMemoryDurationSeconds)
        }

        val fullScreenPendingIntent =
            PendingIntent.getActivity(
                this,
                100,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val dismissIntent = Intent(this, AlarmRingingService::class.java).apply {
            action = ACTION_NOTIFICATION_DISMISS
            putExtra("ring_token", ringToken)
        }
        val dismissPendingIntent = PendingIntent.getService(
            this, 103, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(
            this,
            AlarmRingingService::class.java
        ).apply {
            action = ACTION_SNOOZE
            putExtra("ring_token", ringToken)
        }

        val snoozePendingIntent =
            PendingIntent.getService(
                this,
                102,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val builder = NotificationCompat.Builder(
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
                R.drawable.ic_lock_idle_alarm,
                "Snooze",
                snoozePendingIntent
            )
        if (currentChallengeType.equals("SWIPE", ignoreCase = true)) {
            builder.addAction(R.drawable.ic_lock_idle_alarm, "Dismiss", dismissPendingIntent)
        }
        return builder.build()
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
            manager.createNotificationChannel(NotificationChannel(
                RECOVERY_CHANNEL_ID, "Alarm recovery", NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null); enableVibration(false) })
        }
    }

    private fun stopRinging() {
        if (screenOffReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffReceiverRegistered = false
        }
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

        // Move foreground ownership away from the ringing notification before removing it.
        ensureForegroundForRecovery()
        manager.cancel(NOTIFICATION_ID)

        activeIntent = null
        persistQueue()
        advanceQueue()
    }

    private fun advanceQueue() {
        if (isRinging || validating || destroyed) return
        val next = pending.peekFirst()
        if (next == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(RECOVERY_NOTIFICATION_ID)
            stopSelf()
            return
        }
        validating = true
        CoroutineScope(Dispatchers.Main).launch {
            val valid = try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    val id = next.getIntExtra("alarm_id", 0)
                    val alarm = WakeGuardDatabase.getInstance(applicationContext).alarmDao()
                        .getAllAlarms().firstOrNull { it.schedulerId == id }
                    alarm != null && alarm.scheduleGeneration == next.getLongExtra(AlarmScheduler.EXTRA_GENERATION, 1L) &&
                        (alarm.isEnabled || (alarm.repeatDays.isBlank() && alarm.awakeCheckEnabled)) &&
                        !LinkedAlarmManager.isSkipped(applicationContext, id, next.getLongExtra(AlarmScheduler.EXTRA_OCCURRENCE, 0L).takeIf { it > 0L } ?: System.currentTimeMillis())
                }
            } catch (e: Exception) {
                Log.e("WakeGuard", "Queued alarm validation failed", e)
                false
            }
            if (destroyed) return@launch
            validating = false
            pending.pollFirst()
            if (valid) activate(next)
            else {
                persistQueue()
                advanceQueue()
            }
        }
    }

    override fun onDestroy() {
        destroyed = true
        if (screenOffReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffReceiverRegistered = false
        }
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