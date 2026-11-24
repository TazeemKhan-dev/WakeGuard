package com.wakeguard.alarm.ringtone

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wakeguard.alarm.ui.theme.WakeGuardColors as W

/** Display names for custom documents; keeps existing built-in sound naming unchanged. */
fun ringtoneDisplayName(context: Context, key: String?): String {
    if (key == null || key == "silent" || !key.startsWith("content://")) {
        return WakeGuardSounds.title(context, key)
    }
    return try {
        val uri = Uri.parse(key)
        var filename: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) filename = cursor.getString(index)
            }
        }
        filename?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Custom ringtone"
    } catch (_: Exception) {
        "Custom ringtone"
    }
}

@Composable
fun RingtoneScreen(selected: String?, onSelect: (String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf<String?>("Loud Alarms") }
    var preview by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewKey by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }

    fun stopPreview() {
        val player = preview
        preview = null
        previewKey = null
        if (player != null) {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    fun playOrStop(key: String) {
        if (previewKey == key) {
            stopPreview()
            return
        }
        stopPreview()
        previewError = null
        if (key.startsWith("content://")) {
            try {
                val player = MediaPlayer()
                preview = player
                previewKey = key
                player.setDataSource(context, Uri.parse(key))
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { stopPreview() }
                player.setOnErrorListener { _, _, _ ->
                    previewError = "Couldn't play this file. Try another audio file."
                    stopPreview()
                    true
                }
                player.prepareAsync()
            } catch (_: Exception) {
                previewError = "Couldn't open this audio file."
                stopPreview()
            }
        } else {
            preview = runCatching { WakeGuardSounds.play(context, key, false) }.getOrNull()
            if (preview == null) {
                previewError = "Couldn't preview this ringtone."
            } else {
                previewKey = key
                preview?.setOnCompletionListener { stopPreview() }
            }
        }
    }

    DisposableEffect(preview) {
        val currentPlayer = preview
        onDispose { currentPlayer?.let { runCatching { it.release() } } }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                stopPreview()
                previewError = null
                onSelect(uri.toString())
            } catch (_: SecurityException) {
                previewError = "Can't keep access to that file. Please choose another."
            }
        }
    }

    val customSelected = selected?.startsWith("content://") == true
    Column(Modifier.fillMaxSize().background(W.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { stopPreview(); onBack() }) { Text("‹ Back") }
            Text("WakeGuard Ringtones", color = W.text, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.background(W.card, RoundedCornerShape(16.dp))) {
                listOf("Default ringtone" to null, "Silent" to "silent").forEach { (name, key) ->
                    Row(Modifier.fillMaxWidth().clickable { stopPreview(); onSelect(key) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f), color = W.text)
                        RadioButton(selected = selected == key, onClick = { stopPreview(); onSelect(key) })
                    }
                    if (key == null) HorizontalDivider(color = W.outline)
                }
            }
            WakeGuardSounds.builtIns.groupBy { it.category }.forEach { (category, sounds) ->
                Column(Modifier.background(W.card, RoundedCornerShape(16.dp))) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = if (expanded == category) null else category }.padding(16.dp)) {
                        Text(category, Modifier.weight(1f), color = W.text)
                        Text(if (expanded == category) "⌃" else "⌄", color = W.secondary)
                    }
                    if (expanded == category) sounds.forEach { sound ->
                        HorizontalDivider(color = W.outline)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { playOrStop(sound.key) }) {
                                Text(if (previewKey == sound.key) "■" else "▶")
                            }
                            Text(sound.name, Modifier.weight(1f).clickable { stopPreview(); onSelect(sound.key) }, color = W.text)
                            RadioButton(selected = selected == sound.key, onClick = { stopPreview(); onSelect(sound.key) })
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(W.card, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pick Custom Ringtone", Modifier.weight(1f), color = W.text)
                    Button(onClick = { stopPreview(); audioPicker.launch(arrayOf("audio/*")) }) {
                        Text(if (customSelected) "Change" else "Choose file")
                    }
                }
                if (customSelected) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = true, onClick = null)
                        Text(
                            ringtoneDisplayName(context, selected),
                            Modifier.weight(1f),
                            color = W.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { selected?.let { playOrStop(it) } }) {
                            Text(if (previewKey == selected) "Stop ■" else "Play ▶")
                        }
                    }
                    Text("Selected custom ringtone", color = W.secondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Choose a song or sound from your phone", color = W.secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Button(onClick = { stopPreview(); onBack() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Done")
        }
    }
}
