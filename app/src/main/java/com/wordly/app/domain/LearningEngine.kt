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
    fun learnedSlice(all: List<Word>, benchmark: Long, scope: ReviewScope): List<Word> =
        applyScope(all.filter { it.orderIndex <= benchmark }.sortedBy { it.orderIndex }, scope)

    /**
     * Favorited words, oldest first, regardless of learned/new status, narrowed by [scope] —
     * positions are counted among the favorites themselves, same rules as [learnedSlice].
     */
    fun favoritesSlice(all: List<Word>, scope: ReviewScope = ReviewScope.All): List<Word> =
        applyScope(all.filter { it.isFavorite }.sortedBy { it.orderIndex }, scope)

    /** Narrows an already-ordered [words] list by [scope]; see [learnedSlice] for the rules. */
    private fun applyScope(words: List<Word>, scope: ReviewScope): List<Word> {
        if (words.isEmpty()) return emptyList()
        return when (scope) {
            ReviewScope.All -> words
            is ReviewScope.Window -> when {
                scope.count <= 0 -> emptyList()
                scope.count >= words.size -> words
                scope.fromStart -> words.take(scope.count)
                else -> words.takeLast(scope.count)
            }
            is ReviewScope.Range -> {
                val from = scope.from.coerceIn(1, words.size)
                val to = scope.to.coerceIn(from, words.size)
                words.subList(from - 1, to).toList()
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

    /**
     * Reverse dictionary search: words whose (comma-separated) [Word.translation] matches
     * [query], for finding the original word when only the Russian is known. [query] may itself
     * be comma-separated; each part shorter than 2 chars is ignored to avoid matching everything
     * on the first keystroke. Ranked exact match → prefix → substring, then by order of addition.
     */
    fun searchByTranslation(all: List<Word>, query: String, limit: Int = 5): List<Word> {
        val queryParts = query.split(",").map(::normalize).filter { it.length >= 2 }
        if (queryParts.isEmpty()) return emptyList()
        return all.mapNotNull { word ->
            val wordParts = word.translation.split(",").map(::normalize)
            val rank = queryParts.minOf { q ->
                wordParts.minOf { p ->
                    when {
                        p == q -> 0
                        p.startsWith(q) -> 1
                        q in p -> 2
                        else -> Int.MAX_VALUE
                    }
                }
            }
            if (rank == Int.MAX_VALUE) null else rank to word
        }
            .sortedWith(compareBy({ it.first }, { it.second.orderIndex }))
            .take(limit)
            .map { it.second }
    }

    /**
     * Words answered wrong often enough to drill separately: at least one miss, and misses
     * outweighing hits (`2 × wrong > correct`). Worst first, capped at [limit].
     */
    fun hardSlice(all: List<Word>, limit: Int = 30): List<Word> =
        all.filter(::isHard)
            .sortedWith(compareByDescending<Word> { it.wrongCount * 2 - it.correctCount }.thenBy { it.orderIndex })
            .take(limit)

    fun isHard(word: Word): Boolean = word.wrongCount > 0 && word.wrongCount * 2 > word.correctCount

    /**
     * Per-word confirmation of a new-words [batch]: only [knownIds] become learned. The benchmark
     * is a single cut in addition order, so the batch's own `orderIndex` values are permuted —
     * known words take the lowest ones, the rest the highest — and the benchmark lands after the
     * last known word. Unknown words therefore stay first in line for the next batch, and nothing
     * outside the batch moves.
     *
     * @return the words whose `orderIndex` changed (already updated) and the new benchmark.
     */
    fun partialCommit(batch: List<Word>, knownIds: Set<Long>, current: Long): Pair<List<Word>, Long> {
        if (batch.isEmpty()) return emptyList<Word>() to current
        val slots = batch.map { it.orderIndex }.sorted()
        val ordered = batch.sortedBy { it.orderIndex }
        val known = ordered.filter { it.id in knownIds }
        val unknown = ordered.filter { it.id !in knownIds }
        val reindexed = (known + unknown).mapIndexed { i, w -> w.copy(orderIndex = slots[i]) }
        val changed = reindexed.filter { new -> batch.first { it.id == new.id }.orderIndex != new.orderIndex }
        val benchmark = if (known.isEmpty()) current else slots[known.size - 1]
        return changed to benchmark
    }

    private val WHITESPACE = Regex("\\s+")
}
