package com.wakeguard.alarm.ui.ringing.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.ui.theme.alarmScreenIsDark
import com.wakeguard.alarm.ui.theme.AlarmPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Navy: Color @Composable get() = if (alarmScreenIsDark()) AlarmPalette.backgroundDark else AlarmPalette.backgroundLight
internal val Indigo: Color @Composable get() = AlarmPalette.accent(alarmScreenIsDark())
internal val Lilac: Color @Composable get() = AlarmPalette.accent(alarmScreenIsDark())
internal val MutedText: Color @Composable get() = AlarmPalette.muted(alarmScreenIsDark())
internal val Ink: Color @Composable get() = AlarmPalette.text(alarmScreenIsDark())
internal val Panel: Color @Composable get() = AlarmPalette.panel(alarmScreenIsDark())
internal val ProgressTrack: Color @Composable get() = AlarmPalette.track(alarmScreenIsDark())

@Composable
internal fun ChallengeLayout(
    title: String,
    label: String,
    snoozeMinutes: Int,
    previewMode: Boolean,
    onSnooze: () -> Unit,
    onExitPreview: () -> Unit,
    onFallbackDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = alarmScreenIsDark()
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(if (dark) listOf(Color(0xFF090A0E), Color(0xFF151827), Color(0xFF24283A)) else listOf(Color(0xFFF7F9FE), Color.White, Color(0xFFEAF0FF)))
        ).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
            fontSize = 55.sp, color = Ink, fontWeight = FontWeight.Light)
        if (label.isNotBlank()) {
            Text(label, color = MutedText, fontSize = 17.sp)
        }
        Spacer(Modifier.height(18.dp))
        Surface(color = if (dark) Color(0xFF302B46) else Color(0xFFEAF0FF), shape = RoundedCornerShape(50.dp)) {
            Text(title, color = Indigo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
        }
        Spacer(Modifier.height(30.dp))
        content()
        Spacer(Modifier.weight(1f))
        if (previewMode) {
            OutlinedButton(onClick = onExitPreview, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp)) { Text("Exit preview", color = Indigo) }
        } else {
            Button(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                shape = RoundedCornerShape(28.dp)) { Text("Snooze · $snoozeMinutes min", color = if (dark) Color(0xFF101018) else Color.White) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onFallbackDismiss, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)) { Text("Dismiss without challenge", color = Indigo) }
        }
        Spacer(Modifier.height(12.dp))
    }
}
