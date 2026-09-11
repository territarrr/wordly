package com.wordly.app.domain

import com.wordly.app.data.entity.Word
import com.wordly.app.domain.model.ReviewScope
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
     * narrowed by [scope] — see [ReviewScope] for what each variant means. [ReviewScope.Range]
     * positions are 1-indexed among the learned words and clamped to bounds rather than
     * producing an empty result for a slightly-too-large `to`.
     */
    fun learnedSlice(all: List<Word>, benchmark: Long, scope: ReviewScope): List<Word> {
        val learned = all.filter { it.orderIndex <= benchmark }.sortedBy { it.orderIndex }
        if (learned.isEmpty()) return emptyList()
        return when (scope) {
            ReviewScope.All -> learned
            is ReviewScope.Window -> when {
                scope.count <= 0 -> emptyList()
                scope.count >= learned.size -> learned
                scope.fromStart -> learned.take(scope.count)
                else -> learned.takeLast(scope.count)
            }
            is ReviewScope.Range -> {
                val from = scope.from.coerceIn(1, learned.size)
                val to = scope.to.coerceIn(from, learned.size)
                learned.subList(from - 1, to).toList()
            }
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
