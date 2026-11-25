package com.wakeguard.alarm.ringtone

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.wakeguard.alarm.R

object WakeGuardSounds {
    data class Sound(val name: String, val category: String, val key: String, val resId: Int, val description: String = "")
    val builtIns: List<Sound> get() = RingtoneCatalogue.sounds
    private val aliases = mapOf(
        "builtin:emergency_pulse" to "builtin:momentum",
        "builtin:digital_siren" to "builtin:urban_pulse",
        "builtin:heavy_buzzer" to "builtin:driving_ambition",
        "builtin:rapid_beep" to "builtin:momentum",
        "builtin:high_alert" to "builtin:driving_ambition",
        "builtin:rising_alarm" to "builtin:momentum",
        "builtin:radar_alert" to "builtin:urban_pulse",
        "builtin:power_bell" to "builtin:driving_ambition",
        "builtin:twin_bell" to "builtin:elevate",
        "builtin:mechanical_clock" to "builtin:fresh_start",
        "builtin:classic_buzzer" to "builtin:elevate",
        "builtin:fast_chime" to "builtin:golden_hour",
        "builtin:retro_alarm" to "builtin:abstract",
        "builtin:morning_bell" to "builtin:fresh_start",
        "builtin:soft_sunrise" to "builtin:serenity",
        "builtin:morning_birds" to "builtin:serenity",
        "builtin:ocean_waves" to "builtin:silent_descent",
        "builtin:gentle_piano" to "builtin:quiet_dawn",
        "builtin:rainfall" to "builtin:silent_descent",
        "builtin:wind_chimes" to "builtin:serenity",
        "builtin:digital_pulse" to "builtin:abstract",
        "builtin:neon_alert" to "builtin:abstract",
        "builtin:circuit_beep" to "builtin:fresh_start",
        "builtin:space_signal" to "builtin:abstract",
        "builtin:synth_wake" to "builtin:elevate",
        "builtin:ascending_tones" to "builtin:fresh_start"
    )
    fun canonicalKey(key: String?): String? = aliases[key] ?: key
    fun find(key: String?): Sound? = builtIns.firstOrNull { it.key == canonicalKey(key) }

    internal fun setSource(context: Context, player: MediaPlayer, key: String?) {
        val sound = find(key)
        if (sound != null) {
            context.resources.openRawResourceFd(sound.resId).use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
        } else if (key?.startsWith("builtin:") == true) {
            context.resources.openRawResourceFd(R.raw.wg_momentum).use { fd ->
                player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
        } else {
            val uri = if (key.isNullOrBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else Uri.parse(key.removePrefix("device:"))
            player.setDataSource(context, requireNotNull(uri))
        }
    }
    fun title(context: Context, key: String?): String = when {
        key.isNullOrBlank() -> "Default ringtone"
        key == "silent" -> "Silent"
        key.startsWith("builtin:") -> find(key)?.name ?: "Momentum"
        else -> try { RingtoneManager.getRingtone(context, Uri.parse(key.removePrefix("device:")))?.getTitle(context) ?: if (key.startsWith("device:")) "Device ringtone" else "Custom audio" } catch (_: Exception) { if (key.startsWith("device:")) "Device ringtone" else "Custom audio" }
    }
    fun play(context: Context, key: String?, loop: Boolean): MediaPlayer? {
        if (key == "silent") return null
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            setSource(context, player, key)
            player.isLooping = loop
            player.prepare()
            player.start()
            return player
        } catch (e: Exception) {
            player.release()
            throw e
        }
    }
}
