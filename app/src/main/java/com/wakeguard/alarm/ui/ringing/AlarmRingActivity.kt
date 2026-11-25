
package com.wakeguard.alarm.ui.ringing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.ui.theme.AlarmPalette
import com.wakeguard.alarm.ui.theme.alarmScreenIsDark
import kotlinx.coroutines.flow.collect
import com.wakeguard.alarm.service.AlarmRingingService
import com.wakeguard.alarm.ui.ringing.challenges.ChallengeRouter

class AlarmRingActivity : ComponentActivity() {

    private var challengeStarted by mutableStateOf(false)
    private var challengeToken: String? = null

    private fun updateChallengeSound(active: Boolean) {
        val token = challengeToken ?: return
        if (AlarmRingingService.activeRingToken.value != token) return
        startService(Intent(this, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_CHALLENGE_SOUND
            putExtra("ring_token", token)
            putExtra("challenge_active", active)
        })
    }

    override fun onStop() {
        if (challengeStarted) {
            updateChallengeSound(false)
            challengeStarted = false
        }
        super.onStop()
    }

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
        if (challengeStarted) updateChallengeSound(false)
        challengeStarted = false
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

        val requiresStart = !preview && (challenge in listOf("MATH", "SHAKE", "MEMORY") ||
            challenge == "QURAN_TYPING" || challenge.startsWith("QURAN_TYPING:"))
        challengeToken = if (requiresStart) token else null
        setContent {
            LaunchedEffect(token, preview) {
                if (!preview) {
                    AlarmRingingService.activeRingToken.collect { active ->
                        if (active != token || active == null) finish()
                    }
                }
            }
            BackHandler(enabled = requiresStart && challengeStarted) {
                updateChallengeSound(false)
                challengeStarted = false
            }
            if (requiresStart && !challengeStarted) {
                val dark = alarmScreenIsDark()
                Column(Modifier.fillMaxSize().background(if (dark) AlarmPalette.backgroundDark else AlarmPalette.backgroundLight).systemBarsPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Alarm ringing", color = AlarmPalette.text(dark), fontSize = 32.sp)
                    if (label.isNotBlank()) Text(label, color = AlarmPalette.muted(dark))
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { challengeStarted = true; updateChallengeSound(true) }, modifier = Modifier.fillMaxWidth()) { Text("Start Challenge") }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { sendAlarmAction(AlarmRingingService.ACTION_SNOOZE, token) }, modifier = Modifier.fillMaxWidth()) { Text("Snooze · $snoozeMinutes min") }
                }
            } else ChallengeRouter(
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
