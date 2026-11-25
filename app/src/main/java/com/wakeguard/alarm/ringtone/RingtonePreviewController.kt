package com.wakeguard.alarm.ringtone

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.*

internal class RingtonePreviewController(private val context: Context) {
    private var player: MediaPlayer? = null
    var key by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun stop() {
        val previous = player
        player = null
        key = null
        playing = false
        loading = false
        previous?.let { runCatching { it.release() } }
    }

    fun toggle(id: String) {
        if (id == "silent") return
        error = null
        try {
            if (key == id && player != null) {
                if (loading) { stop(); return }
                val current = requireNotNull(player)
                if (playing) current.pause() else current.start()
                playing = !playing
                return
            }
            stop()
            val current = MediaPlayer()
            player = current
            key = id
            loading = true
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            WakeGuardSounds.setSource(context, current, id.ifEmpty { null })
            current.setOnPreparedListener {
                if (player === it) {
                    try { it.start(); loading = false; playing = true }
                    catch (_: Exception) { stop(); error = "Couldn't play this ringtone." }
                }
            }
            current.setOnCompletionListener { if (player === it) stop() }
            current.setOnErrorListener { mp, _, _ ->
                if (player === mp) { stop(); error = "Couldn't play this ringtone." }
                true
            }
            current.prepareAsync()
        } catch (_: Exception) {
            stop()
            error = "Couldn't open this ringtone."
        }
    }
}
