package com.wakeguard.alarm.ui.ringing.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private data class Problem(val expression: String, val answer: Int, val options: List<Int>)

private fun generateProblem(difficulty: String): Problem {
    val range = when (difficulty) {
        "HARD" -> 20..99
        "MEDIUM" -> 10..40
        else -> 1..12
    }
    val a = Random.nextInt(range.first, range.last + 1)
    val b = Random.nextInt(range.first, range.last + 1)
    val sum = Random.nextBoolean()
    val result = if (sum) a + b else kotlin.math.abs(a - b)
    val expression = if (sum) "$a + $b" else "${maxOf(a,b)} − ${minOf(a,b)}"
    val options = mutableSetOf(result)
    while (options.size < 4) {
        val delta = Random.nextInt(-10, 11)
        val candidate = result + delta
        if (candidate >= 0) options.add(candidate)
    }
    return Problem(expression, result, options.shuffled())
}

@Composable
fun MathChallengeScreen(
    label: String, snoozeMinutes: Int, difficulty: String, questionCount: Int,
    previewMode: Boolean, onDismiss: () -> Unit, onSnooze: () -> Unit,
    onExitPreview: () -> Unit
) {
    val total = questionCount.coerceIn(1, 10)
    var solved by remember { mutableIntStateOf(0) }
    var problem by remember(difficulty) { mutableStateOf(generateProblem(difficulty)) }
    var incorrect by remember { mutableStateOf(false) }
    val finish by rememberUpdatedState(if (previewMode) onExitPreview else onDismiss)

    ChallengeLayout("Math challenge", label, snoozeMinutes, previewMode,
        onSnooze, onExitPreview, onDismiss) {
        Text("QUESTION ${solved + 1} OF $total", color = MutedText, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { solved.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(5.dp),
            color = Lilac, trackColor = Color(0xFF27385E))
        Spacer(Modifier.height(26.dp))
        Box(Modifier.fillMaxWidth().height(155.dp)
            .background(Color(0xFF162B54), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Solve this", color = MutedText, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Text("${problem.expression} = ?", color = Color.White,
                    fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        problem.options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { option ->
                    Box(Modifier.weight(1f).height(76.dp)
                        .background(Color(0xFF192E55), RoundedCornerShape(18.dp))
                        .clickable {
                            if (option == problem.answer) {
                                incorrect = false
                                if (solved + 1 == total) finish()
                                else { solved++; problem = generateProblem(difficulty) }
                            } else incorrect = true
                        }, contentAlignment = Alignment.Center) {
                        Text("$option", color = Color.White, fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (incorrect) Text("Not quite — try another answer", color = Color(0xFFFFB7B7))
    }
}
