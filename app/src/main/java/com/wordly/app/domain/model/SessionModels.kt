package com.wordly.app.domain.model

/** Which face of a card is shown first. */
enum class Direction {
    /** Show the English word first, reveal the Russian translation. */
    EN_TO_RU,

    /** Show the Russian translation first, reveal the English word. */
    RU_TO_EN,
}

/** How each card is answered. */
enum class QuizType(val label: String) {
    /** Flip the card, then say yourself "Знаю"/"Не знаю". */
    CARDS("Карточки"),

    /** Type the answer. */
    TYPING("Ввод"),

    /** Pick the answer out of four. */
    CHOICE("Выбор из 4"),

    /** Hear the word, type it — [Direction] is ignored. */
    LISTENING("На слух"),
}

/** A learning/review session, reconstructable from primitive nav arguments. */
sealed interface SessionConfig {
    val direction: Direction
    val quiz: QuizType

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
        override val quiz: QuizType = QuizType.CARDS,
    ) : SessionConfig

    /**
     * Learn the next [batchSize] words that come after the benchmark — always the oldest new ones,
     * but presented shuffled (from [seed]) unless [shuffle] is off.
     */
    data class NewWords(
        override val direction: Direction,
        val batchSize: Int,
        val shuffle: Boolean = true,
        val seed: Long = 0,
        override val quiz: QuizType = QuizType.CARDS,
    ) : SessionConfig

    /**
     * Favorited words, regardless of learned/new status. [scope] positions count among the
     * favorites themselves (not relative to the benchmark). Like [Review], finishing never moves it.
     */
    data class Favorites(
        override val direction: Direction,
        val shuffle: Boolean,
        val scope: ReviewScope,
        val seed: Long,
        override val quiz: QuizType = QuizType.CARDS,
    ) : SessionConfig

    /** Learned words the spaced-repetition schedule says are due today (see `Srs`), at most [limit]. */
    data class Due(
        override val direction: Direction,
        val limit: Int,
        val seed: Long,
        override val quiz: QuizType = QuizType.CARDS,
    ) : SessionConfig

    /** Words with more misses than hits (see `LearningEngine.hardSlice`), shuffled. */
    data class Hard(
        override val direction: Direction,
        val seed: Long,
        override val quiz: QuizType = QuizType.CARDS,
    ) : SessionConfig
}
