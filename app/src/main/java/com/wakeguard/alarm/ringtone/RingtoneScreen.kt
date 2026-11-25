package com.wakeguard.alarm.ringtone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.RingtoneManager
import android.app.Activity
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wakeguard.alarm.ui.theme.WakeGuardColors as W

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.clip
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Display names for custom documents; keeps existing built-in sound naming unchanged. */
fun ringtoneDisplayName(context: Context, key: String?): String {
    if (key?.startsWith("device:") == true) return WakeGuardSounds.title(context, key)
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = remember(context) { RingtonePreviewController(context) }
    var category by remember { mutableStateOf(WakeGuardSounds.find(selected)?.category ?: "Loud") }
    var query by remember { mutableStateOf("") }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val canonical = WakeGuardSounds.canonicalKey(selected)
    val device = selected?.startsWith("device:") == true
    val custom = selected?.startsWith("content://") == true
    val close = { preview.stop(); onBack() }
    BackHandler { close() }
    DisposableEffect(lifecycle, preview) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) preview.stop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); preview.stop() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                pickerError = null
                onSelect(uri.toString())
            } catch (_: SecurityException) { pickerError = "Can't keep access to this file." }
        }
    }
    val devicePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                pickerError = null
                onSelect("device:$uri")
            }
        }
    }
    val sounds = WakeGuardSounds.builtIns.filter {
        if (query.isBlank()) it.category == category else
            (it.name + " " + it.description + " " + it.category).contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().background(W.background).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("‹ Back") }
            Text("Ringtones", color = W.text, style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search ringtones or instruments") }, singleLine = true,
            shape = RoundedCornerShape(14.dp), trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } })
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().background(W.card, RoundedCornerShape(14.dp)).padding(4.dp)) {
            RingtoneCatalogue.categories.forEach { item ->
                val active = category == item
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else W.card)
                    .clickable { category = item; query = ""; preview.stop() }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text("$item (${WakeGuardSounds.builtIns.count { it.category == item }})", color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else W.secondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item { Text(if (query.isBlank()) "$category ringtones" else "Search results", color = W.secondary, style = MaterialTheme.typography.labelMedium) }
            items(sounds, key = { it.key }) { sound ->
                RingtoneRow(sound.name, sound.description, sound.key, canonical == sound.key, preview) { onSelect(sound.key) }
            }
            if (sounds.isEmpty()) item { Text("No matching ringtones", color = W.secondary) }
            item { Spacer(Modifier.height(8.dp)); Text("Other options", color = W.secondary, style = MaterialTheme.typography.labelMedium) }
            item { RingtoneRow("Default ringtone", "System alarm sound", "", selected.isNullOrBlank(), preview) { onSelect(null) } }
            item { RingtoneRow("Silent", "No ringtone", "silent", selected == "silent", preview) { onSelect("silent") } }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(W.card).clickable {
                    preview.stop(); pickerError = null
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Device Ringtones")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (device) Uri.parse(selected!!.removePrefix("device:")) else null as Uri?)
                    }
                    try { devicePicker.launch(intent) }
                    catch (_: android.content.ActivityNotFoundException) { pickerError = "Device ringtone picker is unavailable on this phone." }
                    catch (_: SecurityException) { pickerError = "Device ringtone picker could not be opened." }
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Device Ringtones", color = W.text); Text("Choose installed alarm sounds or ringtones", color = W.secondary, style = MaterialTheme.typography.bodySmall) }
                    Text("›", color = W.secondary)
                }
            }
            if (device) item { RingtoneRow(ringtoneDisplayName(context, selected), "Selected device ringtone", requireNotNull(selected), true, preview) { onSelect(selected) } }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(W.card).clickable {
                    preview.stop(); pickerError = null; picker.launch(arrayOf("audio/*"))
                }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Pick Custom", color = W.text); Text("Choose an audio file", color = W.secondary, style = MaterialTheme.typography.bodySmall) }
                    Text("›", color = W.secondary)
                }
            }
            if (custom) item { RingtoneRow(ringtoneDisplayName(context, selected), "Selected custom ringtone", requireNotNull(selected), true, preview) { onSelect(selected) } }
        }
        (pickerError ?: preview.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Selected", color = W.secondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(12.dp))
            Text(ringtoneDisplayName(context, selected), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = close, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Done") }
    }
}

@Composable
private fun RingtoneRow(name: String, description: String, id: String, selected: Boolean, preview: RingtonePreviewController, onSelect: () -> Unit) {
    val active = preview.key == id
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(if (selected) MaterialTheme.colorScheme.primaryContainer else W.card), verticalAlignment = Alignment.CenterVertically) {
        if (id != "silent") {
            TextButton(onClick = { preview.toggle(id) }, modifier = Modifier.width(54.dp)) {
                Text(if (active && preview.loading) "…" else if (active && preview.playing) "Ⅱ" else "▶", color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
            }
        } else Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).clickable(onClick = onSelect).padding(vertical = 12.dp)) {
            Text(name, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else W.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else W.secondary, style = MaterialTheme.typography.bodySmall)
            if (active && preview.playing) PlaybackBars()
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

@Composable
private fun PlaybackBars() {
    val transition = rememberInfiniteTransition(label = "playback")
    val phase by transition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "bars")
    Row(Modifier.height(14.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(9) { index ->
            val height = 3f + 8f * kotlin.math.abs(kotlin.math.sin(phase * 3.14f + index * 1.2f))
            Box(Modifier.width(3.dp).height(height.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        }
    }
}
