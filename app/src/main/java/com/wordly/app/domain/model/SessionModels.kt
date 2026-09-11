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
     * @param window number of learned words to include, or `null` for all.
     * @param fromStart when [window] is set, take the first/earliest-learned [window] words
     *   instead of the most-recently-learned ones. Ignored when [window] is `null`.
     * @param shuffle randomise card order (deterministically via [seed]).
     */
    data class Review(
        override val direction: Direction,
        val shuffle: Boolean,
        val window: Int?,
        val fromStart: Boolean = false,
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
