package com.wakeguard.alarm.ui.ringing.challenges

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat() / target,
        animationSpec = tween(350), label = "shake progress"
    )
    val rotation by animateFloatAsState(
        targetValue = if (progress % 2 == 0) -7f else 7f,
        animationSpec = spring(stiffness = Spring.StiffnessLow), label = "phone tilt"
    )

    ChallengeLayout("Shake challenge", label, snoozeMinutes, previewMode,
        onSnooze, onExitPreview, onDismiss) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(238.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animatedProgress }, modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp, color = Indigo, trackColor = ProgressTrack,
                strokeCap = StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhoneMotionIcon(
                    modifier = Modifier.size(width = 92.dp, height = 95.dp)
                        .graphicsLayer { rotationZ = rotation },
                )
                Spacer(Modifier.height(8.dp))
                Text("$progress / $target", color = Ink, fontSize = 37.sp,
                    fontWeight = FontWeight.Bold)
                Text("movements", color = MutedText, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(30.dp))
        Text(if (sensor == null) "Motion sensor unavailable" else "Move your phone gently",
            color = Ink, fontSize = 23.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(if (sensor == null) "Snooze the alarm if needed."
             else "Move at a comfortable pace. Stop if it feels uncomfortable.",
            color = MutedText, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhoneMotionIcon(modifier: Modifier = Modifier) {
    val accent = Indigo
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 3.dp.toPx()
        drawRoundRect(
            color = accent,
            topLeft = Offset(w * .33f, h * .09f),
            size = Size(w * .34f, h * .76f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(accent, Offset(w * .45f, h * .17f), Offset(w * .55f, h * .17f), stroke, StrokeCap.Round)
        drawCircle(accent, radius = 2.dp.toPx(), center = Offset(w * .5f, h * .77f))
        // Motion lines drawn with Canvas: no placeholder glyph or external icon dependency.
        for (side in listOf(-1f, 1f)) {
            val x = w * (if (side < 0f) .23f else .77f)
            drawArc(accent, startAngle = if (side < 0f) 105f else -75f,
                sweepAngle = 150f, useCenter = false,
                topLeft = Offset(x - w * .11f, h * .28f),
                size = Size(w * .22f, h * .39f),
                style = Stroke(width = stroke * .8f, cap = StrokeCap.Round))
        }
    }
}
