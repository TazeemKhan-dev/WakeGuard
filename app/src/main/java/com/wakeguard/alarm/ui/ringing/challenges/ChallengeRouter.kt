package com.wakeguard.alarm.ui.ringing.challenges

import androidx.compose.runtime.Composable
import com.wakeguard.alarm.ui.ringing.AlarmRingScreen

@Composable
fun ChallengeRouter(
    challengeType: String,
    mathDifficulty: String,
    mathQuestionCount: Int,
    shakeCount: Int,
    memoryDifficulty: String,
    memoryRounds: Int,
    memoryDurationSeconds: Int,
    previewMode: Boolean,
    label: String,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onExitPreview: () -> Unit
) {
    val displayLabel = label.trim().takeUnless { it == "New Alarm" || it == "Preview alarm" || it == "Alarm" }.orEmpty()
    if (QuranTypingConfig.isTyping(challengeType)) {
        QuranTypingScreen(displayLabel, snoozeMinutes, QuranTypingConfig.parse(challengeType),
            previewMode, onDismiss, onSnooze, onExitPreview)
        return
    }
    when (challengeType) {
        "MATH" -> MathChallengeScreen(displayLabel, snoozeMinutes, mathDifficulty,
            mathQuestionCount, previewMode, onDismiss, onSnooze, onExitPreview)
        "SHAKE" -> ShakeChallengeScreen(displayLabel, snoozeMinutes, shakeCount,
            previewMode, onDismiss, onSnooze, onExitPreview)
        "MEMORY" -> MemoryChallengeScreen(displayLabel, snoozeMinutes, memoryDifficulty,
            memoryRounds, memoryDurationSeconds, previewMode, onDismiss, onSnooze, onExitPreview)
        else -> AlarmRingScreen(
            label = displayLabel,
            snoozeMinutes = snoozeMinutes,
            onDismiss = onDismiss,
            onSnooze = onSnooze
        )
    }
}
