package com.wakeguard.alarm.ui.ringing.challenges

import androidx.compose.runtime.Composable
import com.wakeguard.alarm.ui.ringing.AlarmRingScreen

@Composable
fun ChallengeRouter(
    challengeType: String,
    mathDifficulty: String,
    mathQuestionCount: Int,
    shakeCount: Int,
    previewMode: Boolean,
    label: String,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onExitPreview: () -> Unit
) {
    when (challengeType) {
        "MATH" -> MathChallengeScreen(label, snoozeMinutes, mathDifficulty,
            mathQuestionCount, previewMode, onDismiss, onSnooze, onExitPreview)
        "SHAKE" -> ShakeChallengeScreen(label, snoozeMinutes, shakeCount,
            previewMode, onDismiss, onSnooze, onExitPreview)
        else -> AlarmRingScreen(
            label = if (previewMode) "Preview: $label" else label,
            snoozeMinutes = snoozeMinutes,
            onDismiss = onDismiss,
            onSnooze = onSnooze
        )
    }
}
