package com.wakeguard.alarm.ui.ringing.challenges

// Stored in the existing challengeType string; no Room schema change.
internal data class QuranTypingConfig(val length: String = "MIXED", val rounds: Int = 2) {
    fun encode() = "QURAN_TYPING:$length:${rounds.coerceIn(1, 5)}"
    companion object {
        fun isTyping(value: String) = value == "QURAN_TYPING" || value.startsWith("QURAN_TYPING:")
        fun parse(value: String): QuranTypingConfig {
            val parts = value.split(':')
            return QuranTypingConfig(parts.getOrNull(1)?.takeIf { it in listOf("SHORT", "MEDIUM", "MIXED") } ?: "MIXED",
                parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 5) ?: 2)
        }
    }
}
