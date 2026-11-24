package com.wakeguard.alarm.ui.ringing.challenges

import kotlin.random.Random

internal enum class MemoryDifficulty(val gridSize: Int, val highlighted: Int, val defaultSeconds: Int) {
    EASY(3, 3, 10), MEDIUM(4, 5, 7), HARD(5, 8, 5);
    companion object {
        fun parse(value: String) = values().firstOrNull { it.name.equals(value, true) } ?: EASY
    }
}

internal enum class MemoryPhase { MEMORIZE, RECALL, CORRECT, INCORRECT, COMPLETE }

internal data class MemoryChallengeState(
    val difficulty: MemoryDifficulty,
    val totalRounds: Int,
    val durationSeconds: Int,
    val round: Int,
    val pattern: Set<Int>,
    val selected: Set<Int> = emptySet(),
    val phase: MemoryPhase = MemoryPhase.MEMORIZE
) {
    fun recall() = if (phase == MemoryPhase.MEMORIZE) copy(phase = MemoryPhase.RECALL) else this

    fun toggle(index: Int): MemoryChallengeState {
        if (phase != MemoryPhase.RECALL || index !in 0 until difficulty.gridSize * difficulty.gridSize) return this
        return copy(selected = if (index in selected) selected - index else selected + index)
    }

    fun submit(): MemoryChallengeState {
        if (phase != MemoryPhase.RECALL) return this
        return copy(phase = if (selected == pattern) MemoryPhase.CORRECT else MemoryPhase.INCORRECT)
    }

    fun advance(random: Random = Random.Default): MemoryChallengeState {
        if (phase != MemoryPhase.CORRECT && phase != MemoryPhase.INCORRECT) return this
        if (phase == MemoryPhase.CORRECT && round == totalRounds) return copy(phase = MemoryPhase.COMPLETE)
        return copy(round = round + if (phase == MemoryPhase.CORRECT) 1 else 0,
            pattern = generate(difficulty, random, pattern), selected = emptySet(), phase = MemoryPhase.MEMORIZE)
    }

    companion object {
        fun start(difficulty: MemoryDifficulty, rounds: Int, seconds: Int, random: Random = Random.Default) =
            MemoryChallengeState(difficulty, rounds.coerceIn(1, 10), seconds.coerceIn(5, 10), 1, generate(difficulty, random))

        private fun generate(difficulty: MemoryDifficulty, random: Random, previous: Set<Int> = emptySet()): Set<Int> {
            val cells = 0 until difficulty.gridSize * difficulty.gridSize
            val next = cells.shuffled(random).take(difficulty.highlighted).toSet()
            return if (next != previous) next else (next - next.first()) + cells.filterNot { it in next }.random(random)
        }
    }
}
