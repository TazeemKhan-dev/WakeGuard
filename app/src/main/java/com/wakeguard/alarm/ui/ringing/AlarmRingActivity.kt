
package com.wakeguard.alarm.ui.ringing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collect
import com.wakeguard.alarm.service.AlarmRingingService
import com.wakeguard.alarm.ui.ringing.challenges.ChallengeRouter

class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        renderAlarm(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun renderAlarm(intent: Intent) {
        val token = intent.getStringExtra("ring_token")
        val label = intent.getStringExtra("alarm_label") ?: ""

        val snoozeMinutes = intent
            .getIntExtra("snooze_minutes", 5)
            .coerceIn(1, 60)

        val preview = intent.getBooleanExtra("preview_mode", false)
        val challenge = intent.getStringExtra("challenge_type") ?: "SWIPE"
        val difficulty = intent.getStringExtra("math_difficulty") ?: "EASY"
        val questionCount = intent.getIntExtra("math_question_count", 3).coerceIn(1, 10)
        val shakeCount = intent.getIntExtra("shake_count", 10).coerceIn(1, 30)

        val memoryDifficulty = intent.getStringExtra("memory_difficulty") ?: "EASY"
        val memoryRounds = intent.getIntExtra("memory_rounds", 3).coerceIn(1, 10)
        val memoryDurationSeconds = intent.getIntExtra("memory_duration_seconds", 10).coerceIn(5, 10)

        setContent {
            LaunchedEffect(token, preview) {
                if (!preview) {
                    AlarmRingingService.activeRingToken.collect { active ->
                        if (active != token || active == null) finish()
                    }
                }
            }
            ChallengeRouter(
                challengeType = challenge,
                mathDifficulty = difficulty,
                mathQuestionCount = questionCount,
                shakeCount = shakeCount,
                memoryDifficulty = memoryDifficulty,
                memoryRounds = memoryRounds,
                memoryDurationSeconds = memoryDurationSeconds,
                previewMode = preview,
                label = label,
                snoozeMinutes = snoozeMinutes,
                onDismiss = {
                    if (preview) finish() else sendAlarmAction(AlarmRingingService.ACTION_DISMISS, token)
                },
                onSnooze = {
                    if (preview) finish() else sendAlarmAction(AlarmRingingService.ACTION_SNOOZE, token)
                },
                onExitPreview = { finish() }
            )
        }
    }

    private fun sendAlarmAction(actionName: String, token: String?) {
        val serviceIntent = Intent(
            this,
            AlarmRingingService::class.java
        ).apply {
            action = actionName
            putExtra("ring_token", token)
        }

        startService(serviceIntent)
        finish()
    }
}
