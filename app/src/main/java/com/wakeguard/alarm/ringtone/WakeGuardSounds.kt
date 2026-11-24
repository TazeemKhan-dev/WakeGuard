package com.wakeguard.alarm.ringtone

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.wakeguard.alarm.R

object WakeGuardSounds {
    data class Sound(val name: String, val category: String, val key: String, val resId: Int)
    val builtIns = listOf(
        Sound("Emergency Pulse", "Loud Ringtones", "builtin:emergency_pulse", R.raw.wg_emergency_pulse),
        Sound("Digital Siren", "Loud Ringtones", "builtin:digital_siren", R.raw.wg_digital_siren),
        Sound("Heavy Buzzer", "Loud Ringtones", "builtin:heavy_buzzer", R.raw.wg_heavy_buzzer),
        Sound("Rapid Beep", "Loud Ringtones", "builtin:rapid_beep", R.raw.wg_rapid_beep),
        Sound("High Alert", "Loud Ringtones", "builtin:high_alert", R.raw.wg_high_alert),
        Sound("Rising Alarm", "Loud Ringtones", "builtin:rising_alarm", R.raw.wg_rising_alarm),
        Sound("Radar Alert", "Loud Ringtones", "builtin:radar_alert", R.raw.wg_radar_alert),
        Sound("Power Bell", "Loud Ringtones", "builtin:power_bell", R.raw.wg_power_bell),
        Sound("Twin Bell", "Medium Ringtones", "builtin:twin_bell", R.raw.wg_twin_bell),
        Sound("Mechanical Clock", "Medium Ringtones", "builtin:mechanical_clock", R.raw.wg_mechanical_clock),
        Sound("Classic Buzzer", "Medium Ringtones", "builtin:classic_buzzer", R.raw.wg_classic_buzzer),
        Sound("Fast Chime", "Medium Ringtones", "builtin:fast_chime", R.raw.wg_fast_chime),
        Sound("Retro Alarm", "Medium Ringtones", "builtin:retro_alarm", R.raw.wg_retro_alarm),
        Sound("Morning Bell", "Medium Ringtones", "builtin:morning_bell", R.raw.wg_morning_bell),
        Sound("Soft Sunrise", "Low Ringtones", "builtin:soft_sunrise", R.raw.wg_soft_sunrise),
        Sound("Morning Birds", "Low Ringtones", "builtin:morning_birds", R.raw.wg_morning_birds),
        Sound("Ocean Waves", "Low Ringtones", "builtin:ocean_waves", R.raw.wg_ocean_waves),
        Sound("Gentle Piano", "Low Ringtones", "builtin:gentle_piano", R.raw.wg_gentle_piano),
        Sound("Rainfall", "Low Ringtones", "builtin:rainfall", R.raw.wg_rainfall),
        Sound("Wind Chimes", "Low Ringtones", "builtin:wind_chimes", R.raw.wg_wind_chimes),
        Sound("Digital Pulse", "Medium Ringtones", "builtin:digital_pulse", R.raw.wg_digital_pulse),
        Sound("Neon Alert", "Medium Ringtones", "builtin:neon_alert", R.raw.wg_neon_alert),
        Sound("Circuit Beep", "Medium Ringtones", "builtin:circuit_beep", R.raw.wg_circuit_beep),
        Sound("Space Signal", "Medium Ringtones", "builtin:space_signal", R.raw.wg_space_signal),
        Sound("Synth Wake", "Medium Ringtones", "builtin:synth_wake", R.raw.wg_synth_wake),
        Sound("Ascending Tones", "Medium Ringtones", "builtin:ascending_tones", R.raw.wg_ascending_tones)
    )
    fun title(context: Context, key: String?): String = when {
        key.isNullOrBlank() -> "Default ringtone"
        key == "silent" -> "Silent"
        key.startsWith("builtin:") -> builtIns.firstOrNull { it.key == key }?.name ?: "Default ringtone"
        else -> try { RingtoneManager.getRingtone(context, Uri.parse(key))?.getTitle(context) ?: "Custom audio" } catch (_: Exception) { "Custom audio" }
    }
    fun play(context: Context, key: String?, loop: Boolean): MediaPlayer? {
        if (key == "silent") return null
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            val builtIn = builtIns.firstOrNull { it.key == key }
            if (builtIn != null) {
                context.resources.openRawResourceFd(builtIn.resId).use { fd ->
                    player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
            } else {
                val uri = if (key.isNullOrBlank() || key.startsWith("builtin:"))
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                else Uri.parse(key)
                requireNotNull(uri) { "No audio URI available" }
                player.setDataSource(context, uri)
            }
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
