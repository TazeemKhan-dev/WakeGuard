package com.wakeguard.alarm.ui.ringing.challenges

import android.content.Context

/** Shared, offline rotation. Only a reminder selected for display consumes a slot. */
internal object QuranReminderRotation {
    @Synchronized
    fun next(context: Context, config: QuranTypingConfig, excluded: Set<String>): String {
        val eligible = quranAyahs.filter {
            when (config.length) {
                "SHORT" -> it.meaning.length <= 65
                "MEDIUM" -> it.meaning.length > 65
                else -> true
            }
        }.map { it.reference }
        require(eligible.isNotEmpty())
        val prefs = context.applicationContext.getSharedPreferences("quran_reminder_rotation", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet("seen", emptySet())!!.toMutableSet()
        val last = prefs.getString("last", null)
        val allowed = eligible.filterNot { it in excluded }.ifEmpty { eligible }
        var available = allowed.filterNot { it in seen }
        if (available.isEmpty()) {
            // Reset only the exhausted eligible pool; preserve other lengths' history.
            seen.removeAll(eligible.toSet())
            available = allowed
        }
        val choices = available.filterNot { it == last }.ifEmpty { available }
        val selected = choices.random()
        seen.add(selected)
        // Commit synchronously so the selection survives immediate process termination.
        if (!prefs.edit().putStringSet("seen", seen).putString("last", selected).commit()) {
            android.util.Log.w("QuranRotation", "Could not persist reminder history")
        }
        return selected
    }
}
