package com.wakeguard.alarm.ui.ringing.challenges

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

@Composable
fun ShakeChallengeScreen(
    label: String, snoozeMinutes: Int, shakeCount: Int, previewMode: Boolean,
    onDismiss: () -> Unit, onSnooze: () -> Unit, onExitPreview: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember(manager) { manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val target = shakeCount.coerceIn(1, 30)
    var progress by remember { mutableIntStateOf(0) }
    var lastMovement by remember { mutableLongStateOf(0L) }
    val complete by rememberUpdatedState(if (previewMode) onExitPreview else onDismiss)

    DisposableEffect(manager, sensor, target) {
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val magnitude = sqrt(x * x + y * y + z * z)
                val now = SystemClock.elapsedRealtime()
                if (magnitude > 17f && now - lastMovement > 650L && progress < target) {
                    lastMovement = now
                    progress++
                    if (progress == target) complete()
                }
            }
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    ChallengeLayout("Shake challenge", label, snoozeMinutes, previewMode,
        onSnooze, onExitPreview, onDismiss) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(235.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { progress.toFloat() / target },
                modifier = Modifier.fillMaxSize(), strokeWidth = 13.dp,
                color = Lilac, trackColor = Color(0xFF263D75))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("▯", color = Color(0xFFB9A4FF), fontSize = 69.sp)
                Text("$progress / $target", color = Color.White, fontSize = 37.sp,
                    fontWeight = FontWeight.Bold)
                Text("movements", color = MutedText, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(30.dp))
        Text(if (sensor == null) "Motion sensor unavailable" else "Move your phone gently",
            color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(if (sensor == null) "Use the dismissal option below."
             else "Move at a comfortable pace. Stop if it feels uncomfortable.",
            color = MutedText, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
