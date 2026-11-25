package com.wakeguard.alarm.ui.ringing.challenges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun normalized(value: String): String = value.lowercase()
    .replace(Regex("[.,!?;:'’\\-]"), "")
    .trim().replace(Regex("\\s+"), " ")

internal fun selectAyahs(config: QuranTypingConfig): List<Int> {
    val pool = quranAyahs.indices.filter {
        val short = quranAyahs[it].meaning.length <= 65
        when (config.length) { "SHORT" -> short; "MEDIUM" -> !short; else -> true }
    }
    return pool.shuffled().take(config.rounds)
}

@Composable
internal fun QuranTypingScreen(
    label: String, snoozeMinutes: Int, config: QuranTypingConfig,
    previewMode: Boolean, onDismiss: () -> Unit, onSnooze: () -> Unit, onExitPreview: () -> Unit
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val dark = com.wakeguard.alarm.ui.theme.alarmScreenIsDark()
    DisposableEffect(view, dark) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousColor = window?.navigationBarColor
        val previousLight = controller?.isAppearanceLightNavigationBars
        val previousContrast = if (android.os.Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        window?.navigationBarColor = (if (dark) Color(0xFF24283A) else Color(0xFFEAF0FF)).toArgb()
        controller?.isAppearanceLightNavigationBars = !dark
        if (android.os.Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        onDispose {
            if (previousColor != null) window?.navigationBarColor = previousColor
            if (previousLight != null) controller?.isAppearanceLightNavigationBars = previousLight
            if (android.os.Build.VERSION.SDK_INT >= 29 && previousContrast != null) window?.isNavigationBarContrastEnforced = previousContrast
        }
    }
    val total = config.rounds.coerceIn(1, 5)
    val previewIds = rememberSaveable(config.length, config.rounds, previewMode) {
        if (previewMode) selectAyahs(config).toIntArray() else intArrayOf()
    }
    val roundCount = if (previewMode) previewIds.size else total
    var round by rememberSaveable { mutableIntStateOf(0) }
    var typed by rememberSaveable { mutableStateOf("") }
    var finished by rememberSaveable { mutableStateOf(false) }
    var references by rememberSaveable(config.length, config.rounds, previewMode) {
        mutableStateOf(arrayListOf<String>())
    }
    LaunchedEffect(round, config, previewMode) {
        if (!previewMode && references.size <= round) {
            val next = QuranReminderRotation.next(context, config, references.toSet())
            references = ArrayList(references).apply { add(next) }
        }
    }
    val ayah = if (previewMode) quranAyahs[previewIds[round]] else
        quranAyahs.firstOrNull { it.reference == references.getOrNull(round) } ?: return
    val expected = normalized(ayah.meaning)
    val actual = normalized(typed)
    val correct = actual == expected
    val prefix = actual.zip(expected).takeWhile { it.first == it.second }.size
    val accent = Indigo
    val ink = Ink
    val error = Color(0xFFE36B72)
    val feedback = buildAnnotatedString {
        ayah.meaning.forEachIndexed { index, c ->
            val before = normalized(ayah.meaning.substring(0, index))
            val through = normalized(ayah.meaning.substring(0, index + 1))
            val position = through.length - 1
            val optional = c in ".,!?;:'’ -" || c.isWhitespace()
            val color = when {
                optional -> {
                    val passed = actual.length > before.length && prefix >= before.length
                    val explicitlyTyped = prefix >= before.length &&
                        typed.trimEnd().lastOrNull() == c && actual.length == before.length
                    if (correct || passed || explicitlyTyped) accent else ink
                }
                position < 0 || position >= actual.length || position >= expected.length -> ink
                actual[position] == expected[position] -> accent
                else -> error
            }
            withStyle(SpanStyle(color = color)) { append(c) }
        }
    }
    val submit: () -> Unit = {
        if (correct && !finished) {
            if (round == roundCount - 1) {
                finished = true
                keyboard?.hide()
                if (previewMode) onExitPreview() else onDismiss()
            } else { round++; typed = "" }
        }
    }
    Box(Modifier.fillMaxSize().background(if (dark) Color(0xFF24283A) else Color(0xFFEAF0FF)).imePadding()) {
    ChallengeLayout("Quran Typing", label, snoozeMinutes, previewMode, onSnooze,
        onExitPreview, onDismiss, compact = true) {
        Text("REMINDER ${round + 1} OF ${roundCount}", color = MutedText, fontSize = 13.sp)
        LinearProgressIndicator(progress = { (round + prefix.toFloat() / expected.length) / roundCount },
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), color = accent, trackColor = ProgressTrack)
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ayah.arabic, color = ink, fontSize = 28.sp, lineHeight = 42.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("Surah ${ayah.reference}", color = MutedText, fontSize = 13.sp)
            HorizontalDivider(color = ProgressTrack)
            Text("ENGLISH MEANING", color = MutedText, fontSize = 11.sp)
            Text(feedback, fontSize = 20.sp, lineHeight = 29.sp)
        }
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Type the English meaning", color = ink, fontSize = 14.sp,
                fontWeight = FontWeight.Medium)
            OutlinedTextField(value = typed, onValueChange = { next ->
                // Reject bulk insertion (including typical paste); IME behavior varies.
                if ('\n' !in next && '\r' !in next && next.length <= ayah.meaning.length + 20 && next.length - typed.length <= 1) typed = next
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), minLines = 2, maxLines = 4,
                placeholder = { Text("Start typing here…", color = MutedText) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ink, unfocusedTextColor = ink,
                    focusedBorderColor = accent, unfocusedBorderColor = ProgressTrack, cursorColor = accent))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("$prefix / ${expected.length} characters", color = MutedText, fontSize = 12.sp)
                if (correct) Text("Correct ✓", color = accent, fontSize = 12.sp)
            }
            if (!correct && actual.isNotEmpty() && prefix < actual.length) {
                Text("Check the highlighted letters", color = error, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(enabled = correct && !finished, onClick = submit, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent,
                contentColor = if (com.wakeguard.alarm.ui.theme.alarmScreenIsDark()) Color(0xFF101018) else Color.White,
                disabledContainerColor = ProgressTrack, disabledContentColor = MutedText)) {
            Text(if (round == roundCount - 1) "Complete challenge" else "Next reminder", fontWeight = FontWeight.SemiBold)
        }
    }
}
}
