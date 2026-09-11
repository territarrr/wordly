package com.wordly.app.domain.model

/** Which slice of the learned words a review session covers. */
sealed interface ReviewScope {

    /** Every learned word. */
    data object All : ReviewScope

    /** The [count] most recently learned words, or the [count] earliest ones if [fromStart]. */
    data class Window(val count: Int, val fromStart: Boolean) : ReviewScope

    /**
     * A specific slice by position among learned words, 1-indexed and inclusive on both ends
     * (position 1 is the earliest-learned word) — e.g. `Range(50, 100)` is "words 50 through
     * 100 that I learned". Out-of-bounds values are clamped rather than treated as empty.
     */
    data class Range(val from: Int, val to: Int) : ReviewScope
}
