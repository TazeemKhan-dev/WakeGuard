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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val Navy = Color(0xFF071329)
internal val Indigo = Color(0xFF4C70F4)
internal val Lilac = Color(0xFFA06AEB)
internal val MutedText = Color(0xFFBAC8E2)

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
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Navy, Color(0xFF102451), Color(0xFF0A1935)))
        ).systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
            fontSize = 59.sp, color = Color.White, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(5.dp))
        Text(label, color = MutedText, fontSize = 17.sp)
        Spacer(Modifier.height(16.dp))
        Surface(color = Color(0xFF283A83), shape = RoundedCornerShape(50.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
        }
        Spacer(Modifier.height(30.dp))
        content()
        Spacer(Modifier.weight(1f))
        if (previewMode) {
            OutlinedButton(onClick = onExitPreview, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(28.dp)) { Text("Exit preview", color = Color.White) }
        } else {
            Button(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Indigo),
                shape = RoundedCornerShape(28.dp)) { Text("Snooze · $snoozeMinutes min") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onFallbackDismiss, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)) { Text("Dismiss without challenge", color = MutedText) }
        }
        Spacer(Modifier.height(18.dp))
    }
}
