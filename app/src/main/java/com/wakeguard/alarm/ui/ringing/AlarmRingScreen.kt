package com.wakeguard.alarm.ui.ringing

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000L) }
    }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    val travel = with(LocalDensity.current) { 132.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val dismiss by rememberUpdatedState(onDismiss)
    var dragDistance by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val progress = (dragDistance / travel).coerceIn(0f, 1f)
    val lift by animateFloatAsState(dragDistance, animationSpec = if (dragging) snap() else spring(stiffness = Spring.StiffnessMedium), label = "handle")
    val infinite = rememberInfiniteTransition(label = "swipe chevrons")
    val pulse by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "wave")
    LaunchedEffect(completed) {
        if (completed) { delay(180L); dismiss() }
    }

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
        Box(
            Modifier.width(94.dp).height(218.dp)
                .background(panel, RoundedCornerShape(48.dp))
                .border(1.dp, blue.copy(alpha = 0.22f), RoundedCornerShape(48.dp))
                .pointerInput(travel) {
                    detectVerticalDragGestures(
                        onDragStart = { if (!completed) { dragging = true; dragDistance = 0f } },
                        onDragEnd = {
                            dragging = false
                            if (!completed && dragDistance >= travel * 0.78f) {
                                completed = true
                                dragDistance = travel
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else if (!completed) dragDistance = 0f
                        },
                        onDragCancel = { dragging = false; if (!completed) dragDistance = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            if (!completed) dragDistance = (dragDistance - amount).coerceIn(0f, travel)
                        }
                    )
                }, contentAlignment = Alignment.BottomCenter
        ) {
            Column(Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { index ->
                    val phase = (pulse + index * 0.24f) % 1f
                    Chevron(blue.copy(alpha = (0.25f + 0.75f * (1f - phase)) * (1f - progress)), Modifier.size(24.dp, 14.dp))
                }
            }
            Box(Modifier.padding(bottom = 8.dp).graphicsLayer { translationY = -lift }
                .size(70.dp).background(blue, CircleShape), contentAlignment = Alignment.Center) {
                if (completed) Text("✓", color = if (dark) Color(0xFF101018) else Color.White, fontSize = 30.sp)
                else Chevron(if (dark) Color(0xFF101018) else Color.White, Modifier.size(30.dp, 20.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(if (completed) "Alarm dismissed" else if (progress >= 0.78f) "Release to dismiss" else "Swipe up to dismiss",
            color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Slide the handle upward", color = muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun Chevron(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val top = Offset(size.width / 2f, size.height * 0.2f)
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.8f), top, 3.dp.toPx(), StrokeCap.Round)
        drawLine(color, top, Offset(size.width * 0.85f, size.height * 0.8f), 3.dp.toPx(), StrokeCap.Round)
    }
}
