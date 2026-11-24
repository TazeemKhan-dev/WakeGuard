package com.wakeguard.alarm.ui.ringing.challenges

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wakeguard.alarm.ui.theme.alarmScreenIsDark
import kotlinx.coroutines.delay

@Composable
fun MemoryChallengeScreen(
    label: String, snoozeMinutes: Int, difficulty: String, rounds: Int,
    durationSeconds: Int, previewMode: Boolean, onDismiss: () -> Unit,
    onSnooze: () -> Unit, onExitPreview: () -> Unit
) {
    val level = MemoryDifficulty.parse(difficulty)
    var game by remember(level, rounds, durationSeconds) {
        mutableStateOf(MemoryChallengeState.start(level, rounds, durationSeconds))
    }
    var remaining by remember { mutableIntStateOf(game.durationSeconds) }
    var finished by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(if (previewMode) onExitPreview else onDismiss)
    LaunchedEffect(game.phase, game.pattern, game.round) {
        when (game.phase) {
            MemoryPhase.MEMORIZE -> {
                val deadline = SystemClock.elapsedRealtime() + game.durationSeconds * 1000L
                while (true) {
                    val left = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                    remaining = ((left + 999) / 1000).toInt()
                    if (left == 0L) break
                    delay(minOf(left, 100L))
                }
                game = game.recall()
            }
            MemoryPhase.CORRECT, MemoryPhase.INCORRECT -> {
                delay(850)
                game = game.advance()
            }
            MemoryPhase.COMPLETE -> if (!finished) { finished = true; finish() }
            MemoryPhase.RECALL -> Unit
        }
    }
    val dark = alarmScreenIsDark()
    val accent = Indigo
    val success = if (dark) Color(0xFF86EFAC) else Color(0xFF166534)
    val error = if (dark) Color(0xFFFDA4AF) else Color(0xFFB91C1C)
    val memorizing = game.phase == MemoryPhase.MEMORIZE
    val recalling = game.phase == MemoryPhase.RECALL
    val status = when (game.phase) {
        MemoryPhase.MEMORIZE -> "Memorize"
        MemoryPhase.RECALL -> "Recall"
        MemoryPhase.CORRECT -> "Correct!"
        MemoryPhase.INCORRECT -> "Try again"
        MemoryPhase.COMPLETE -> "Completed"
    }
    ChallengeLayout(title = "Memory challenge", label = label, snoozeMinutes = snoozeMinutes,
        previewMode = previewMode, onSnooze = onSnooze, onExitPreview = onExitPreview,
        onFallbackDismiss = {}, allowFallbackDismiss = false, compact = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Round ${game.round}/${game.totalRounds}", color = MutedText, fontWeight = FontWeight.SemiBold)
            Text(if (memorizing) "$remaining s" else "${game.selected.size}/${level.highlighted} selected", color = accent)
        }
        Spacer(Modifier.height(8.dp))
        val progress by animateFloatAsState(if (memorizing) remaining.toFloat() / game.durationSeconds else
            (game.round - if (game.phase == MemoryPhase.CORRECT || game.phase == MemoryPhase.COMPLETE) 0 else 1).toFloat() / game.totalRounds,
            animationSpec = tween(200), label = "memory progress")
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp), color = accent, trackColor = ProgressTrack)
        Spacer(Modifier.height(14.dp))
        Text(status, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = when (game.phase) {
            MemoryPhase.CORRECT, MemoryPhase.COMPLETE -> success
            MemoryPhase.INCORRECT -> error
            else -> Ink
        })
        Text(when (game.phase) {
            MemoryPhase.MEMORIZE -> "Remember the highlighted positions."
            MemoryPhase.RECALL -> "Select the blocks you remember."
            MemoryPhase.CORRECT -> "Pattern matched."
            MemoryPhase.INCORRECT -> "A new pattern is coming."
            MemoryPhase.COMPLETE -> "All rounds completed."
        }, color = MutedText, textAlign = TextAlign.Center, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp))
        Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(level.gridSize) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(level.gridSize) { col ->
                        val index = row * level.gridSize + col
                        val selected = !memorizing && index in game.selected
                        val highlighted = memorizing && index in game.pattern
                        val target = when {
                            selected && game.phase == MemoryPhase.CORRECT -> success
                            selected && game.phase == MemoryPhase.INCORRECT -> error
                            highlighted || selected -> accent
                            else -> Panel
                        }
                        val color by animateColorAsState(target, tween(180), label = "block color")
                        val scale by animateFloatAsState(if (selected || highlighted) .94f else 1f, tween(180), label = "block scale")
                        val shape = RoundedCornerShape(12.dp)
                        Box(Modifier.weight(1f).aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(shape).background(if (recalling && !selected) Panel else color)
                            .border(1.dp, if (selected || highlighted) target else ProgressTrack, shape)
                            .semantics { contentDescription = "Row ${row + 1}, column ${col + 1}" + if (highlighted) ", highlighted" else "" }
                            .toggleable(value = selected, enabled = recalling, role = Role.Checkbox, onValueChange = { game = game.toggle(index) }),
                            contentAlignment = Alignment.Center) {}
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = { game = game.submit() }, enabled = recalling,
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (dark) Color(0xFF101018) else Color.White)) {
            Text(if (memorizing) "Memorizing…" else "Submit pattern")
        }
    }
}
