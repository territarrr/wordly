package com.wordly.app.domain

import com.wordly.app.data.entity.Word
import kotlin.random.Random

/**
 * Pure selection/ordering logic for study sessions and the moving benchmark.
 *
 * The app keeps words in a single list ordered by [Word.orderIndex] (order of addition).
 * A **benchmark** ([Word.orderIndex] value) splits that list: words with
 * `orderIndex <= benchmark` are considered learned, the rest are new.
 *
 * All functions here are deterministic and free of Android/DB dependencies so they can be
 * unit-tested directly.
 */
object LearningEngine {

    /** Canonical form used for duplicate detection: trimmed, lower-cased, collapsed whitespace. */
    fun normalize(raw: String): String =
        raw.trim().lowercase().replace(WHITESPACE, " ")

    /** True if [candidate] (after [normalize]) already exists in [existingNormalized]. */
    fun isDuplicate(existingNormalized: Set<String>, candidate: String): Boolean =
        normalize(candidate) in existingNormalized

    /**
     * The learned slice to review: words with `orderIndex <= benchmark`, oldest first,
     * optionally trimmed to [window] of them (`null` = all learned) — either the very first
     * ones learned ([fromStart] true) or the most recently learned ([fromStart] false, the
     * default), so you can go back to the beginning as easily as staying near the benchmark.
     */
    fun learnedWindow(all: List<Word>, benchmark: Long, window: Int?, fromStart: Boolean = false): List<Word> {
        val learned = all.filter { it.orderIndex <= benchmark }.sortedBy { it.orderIndex }
        return when {
            window == null || window >= learned.size -> learned
            window <= 0 -> emptyList()
            fromStart -> learned.take(window)
            else -> learned.takeLast(window)
        }
    }

    /** The next batch to learn: first [batchSize] words with `orderIndex > benchmark`, oldest first. */
    fun newBatch(all: List<Word>, benchmark: Long, batchSize: Int): List<Word> =
        all.filter { it.orderIndex > benchmark }
            .sortedBy { it.orderIndex }
            .take(batchSize.coerceAtLeast(0))

    /**
     * Orders a pre-selected slice for presentation. When [shuffle] is true the order is
     * randomised from [seed] so the same session can be rebuilt identically (e.g. across
     * process death); otherwise it is order of addition.
     */
    fun orderSession(slice: List<Word>, shuffle: Boolean, seed: Long): List<Word> =
        if (shuffle) slice.shuffled(Random(seed)) else slice.sortedBy { it.orderIndex }

    /**
     * The benchmark after a confirmed batch of new words: the highest [Word.orderIndex] in
     * [batch], or [current] unchanged if the batch was empty.
     */
    fun nextBenchmark(batch: List<Word>, current: Long): Long =
        batch.maxOfOrNull { it.orderIndex } ?: current

    private val WHITESPACE = Regex("\\s+")
}
