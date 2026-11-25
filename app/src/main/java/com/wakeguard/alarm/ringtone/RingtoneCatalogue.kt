package com.wakeguard.alarm.ringtone

import com.wakeguard.alarm.R

object RingtoneCatalogue {
    val sounds = listOf(
        WakeGuardSounds.Sound("Momentum", "Loud", "builtin:momentum", R.raw.wg_momentum, "Energetic instrumental"),
        WakeGuardSounds.Sound("Driving Ambition", "Loud", "builtin:driving_ambition", R.raw.wg_driving_ambition, "Motivational instrumental"),
        WakeGuardSounds.Sound("Urban Pulse", "Loud", "builtin:urban_pulse", R.raw.wg_urban_pulse, "Urban electronic"),
        WakeGuardSounds.Sound("Golden Hour", "Medium", "builtin:golden_hour", R.raw.wg_golden_hour, "Happy acoustic folk"),
        WakeGuardSounds.Sound("Elevate", "Medium", "builtin:elevate", R.raw.wg_elevate, "Uplifting instrumental"),
        WakeGuardSounds.Sound("Fresh Start", "Medium", "builtin:fresh_start", R.raw.wg_fresh_start, "Motivational melody"),
        WakeGuardSounds.Sound("Abstract", "Medium", "builtin:abstract", R.raw.wg_abstract, "Modern instrumental"),
        WakeGuardSounds.Sound("Quiet Dawn", "Low", "builtin:quiet_dawn", R.raw.wg_quiet_dawn, "Inspirational piano"),
        WakeGuardSounds.Sound("Serenity", "Low", "builtin:serenity", R.raw.wg_serenity, "Peaceful instrumental"),
        WakeGuardSounds.Sound("Silent Descent", "Low", "builtin:silent_descent", R.raw.wg_silent_descent, "Atmospheric music")
    )
    val categories = listOf("Loud", "Medium", "Low")
}
