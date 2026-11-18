package com.wakeguard.alarm.ui.ringing

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.ui.theme.AlarmPalette
import com.wakeguard.alarm.ui.theme.alarmScreenIsDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlarmRingScreen(
    label: String,
    snoozeMinutes: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val dark = alarmScreenIsDark()
    val ink = AlarmPalette.text(dark)
    val blue = AlarmPalette.accent(dark)
    val muted = AlarmPalette.muted(dark)
    val panel = AlarmPalette.panel(dark)
    val time = remember { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) }
    val date = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val infinite = rememberInfiniteTransition(label = "swipe chevrons")
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1450, easing = LinearEasing), RepeatMode.Restart),
        label = "upward movement"
    )
    val lift by animateFloatAsState(
        targetValue = (-dragDistance / 5f).coerceIn(0f, 36f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium), label = "swipe lift"
    )

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                if (dark) listOf(Color(0xFF090A0E), Color(0xFF151827), Color(0xFF24283A))
                else listOf(Color(0xFFF7F9FE), Color.White, Color(0xFFEAF0FF))
            )
        ).systemBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.9f))
        Text(time, color = ink, fontSize = 62.sp, fontWeight = FontWeight.Light)
        Text(date, color = muted, fontSize = 16.sp)
        Spacer(Modifier.height(32.dp))
        if (label.isNotBlank()) {
            Text(label, color = ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Button(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = blue)
        ) {
            Text("Snooze · $snoozeMinutes min",
                color = if (dark) Color(0xFF101018) else Color.White,
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1.1f))
        Column(
            modifier = Modifier.fillMaxWidth().height(222.dp)
                .background(panel.copy(alpha = if (dark) 0.98f else 0.88f), RoundedCornerShape(34.dp))
                .pointerInput(onDismiss) {
                    detectVerticalDragGestures(
                        onDragStart = { dragDistance = 0f },
                        onDragEnd = {
                            if (dragDistance < -110f) onDismiss()
                            dragDistance = 0f
                        },
                        onDragCancel = { dragDistance = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragDistance += amount
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.graphicsLayer { translationY = -lift },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((-9).dp)
            ) {
                repeat(3) { index ->
                    val phase = (pulse + index * 0.28f) % 1f
                    val alpha = (0.3f + 0.7f * (1f - phase)).coerceIn(0f, 1f)
                    Text("⌃", color = blue.copy(alpha = alpha), fontSize = 49.sp,
                        lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer { translationY = -phase * 10f })
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Swipe up to stop alarm", color = ink, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(22.dp))
    }
}
