package com.wordly.app.domain.model

/** Which face of a card is shown first. */
enum class Direction {
    /** Show the English word first, reveal the Russian translation. */
    EN_TO_RU,

    /** Show the Russian translation first, reveal the English word. */
    RU_TO_EN,
}

/** A learning/review session, reconstructable from primitive nav arguments. */
sealed interface SessionConfig {
    val direction: Direction

    /**
     * Repeat words that are already learned (at or before the benchmark).
     *
     * @param scope which learned words to include — see [ReviewScope].
     * @param shuffle randomise card order (deterministically via [seed]).
     */
    data class Review(
        override val direction: Direction,
        val shuffle: Boolean,
        val scope: ReviewScope,
        val seed: Long,
    ) : SessionConfig

    /**
     * Learn the next [batchSize] words that come after the benchmark, in order of addition.
     */
    data class NewWords(
        override val direction: Direction,
        val batchSize: Int,
    ) : SessionConfig
}
