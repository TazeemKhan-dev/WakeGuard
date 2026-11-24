package com.wakeguard.alarm.service

import android.content.Context
import android.content.Intent
import org.json.JSONArray

/** Ordered journal: first entry is active (or awaiting validation). */
object RingingSessionStore {
    private fun prefs(context: Context) = context.getSharedPreferences("ringing_sessions_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun read(context: Context): List<Intent> {
        val raw = prefs(context).getString("queue", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                runCatching { Intent.parseUri(array.getString(index), Intent.URI_INTENT_SCHEME) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun write(context: Context, entries: List<Intent>): Boolean {
        val array = JSONArray()
        entries.forEach { array.put(it.toUri(Intent.URI_INTENT_SCHEME)) }
        return prefs(context).edit().putString("queue", array.toString()).commit()
    }
}
