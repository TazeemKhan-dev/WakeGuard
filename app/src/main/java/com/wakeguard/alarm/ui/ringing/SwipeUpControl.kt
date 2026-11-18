
package com.wakeguard.alarm.ui.ringing.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

@Composable
fun SwipeUpControl(
    onSwipeComplete: () -> Unit
) {
    val density = LocalDensity.current

    val thresholdPx = with(density) {
        100.dp.toPx()
    }

    var dragDistance by remember {
        mutableFloatStateOf(0f)
    }

    var completed by remember {
        mutableStateOf(false)
    }

    val transition = rememberInfiniteTransition(
        label = "wakeguard_swipe_wave"
    )

    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2100,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(thresholdPx) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragDistance = 0f
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()

                        dragDistance = (
                                dragDistance - amount
                                ).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (
                            dragDistance >= thresholdPx &&
                            !completed
                        ) {
                            completed = true
                            onSwipeComplete()
                        }

                        dragDistance = 0f
                    },
                    onDragCancel = {
                        dragDistance = 0f
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // Three chevrons with staggered fading.
        Column(
            modifier = Modifier.height(68.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            repeat(3) { index ->

                val phase = (
                        progress + (2 - index) * 0.16f
                        ) % 1f

                val opacity = when {
                    phase < 0.18f -> phase / 0.18f
                    phase < 0.42f -> 1f
                    phase < 0.82f ->
                        (0.82f - phase) / 0.40f
                    else -> 0f
                }.coerceIn(0f, 1f)

                val movement = FastOutSlowInEasing
                    .transform(phase.coerceIn(0f, 1f))

                Chevron(
                    modifier = Modifier.graphicsLayer {
                        alpha = opacity * 0.95f
                        translationY = -movement * 10.dp.toPx()
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Swipe up to stop alarm",
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Chevron(
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .width(28.dp)
            .height(16.dp)
    ) {
        val centerX = size.width / 2f
        val topY = size.height * 0.25f
        val bottomY = size.height * 0.75f

        val color = Color.White

        drawLine(
            color = color,
            start = Offset(
                size.width * 0.18f,
                bottomY
            ),
            end = Offset(
                centerX,
                topY
            ),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        drawLine(
            color = color,
            start = Offset(
                centerX,
                topY
            ),
            end = Offset(
                size.width * 0.82f,
                bottomY
            ),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
