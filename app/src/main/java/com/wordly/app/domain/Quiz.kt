package com.wordly.app.domain

import com.wordly.app.data.entity.Word
import com.wordly.app.domain.model.Direction
import kotlin.random.Random

/** Answer checking and option generation for the typing / multiple-choice / listening modes. */
object Quiz {

    enum class Verdict { CORRECT, TYPO, WRONG }

    /**
     * Checks a typed [input] against [expected], which may hold several comma-separated variants
     * (any one counts). Case, ё/е, surrounding punctuation and an English "to " infinitive prefix
     * are ignored; one wrong letter in a word of 5+ letters is accepted as [Verdict.TYPO].
     */
    fun check(input: String, expected: String): Verdict {
        val given = canonical(input)
        if (given.isEmpty()) return Verdict.WRONG
        val variants = expected.split(",").map(::canonical).filter { it.isNotEmpty() }
        if (variants.any { it == given }) return Verdict.CORRECT
        if (variants.any { it.length >= 5 && editDistanceAtMostOne(it, given) }) return Verdict.TYPO
        return Verdict.WRONG
    }

    /** Text shown as the answer for [word] in [direction]. */
    fun answerOf(word: Word, direction: Direction): String =
        if (direction == Direction.EN_TO_RU) word.translation else word.text

    /**
     * Four (or fewer, if [pool] is small) answer options for [target]: its own answer plus
     * distinct distractors drawn from [pool], shuffled deterministically from [seed].
     */
    fun choiceOptions(target: Word, pool: List<Word>, direction: Direction, seed: Long, count: Int = 4): List<String> {
        val right = answerOf(target, direction)
        val random = Random(seed)
        val distractors = pool.asSequence()
            .filter { it.id != target.id }
            .map { answerOf(it, direction) }
            .filter { canonical(it) != canonical(right) }
            .distinctBy(::canonical)
            .toList()
            .shuffled(random)
            .take(count - 1)
        return (distractors + right).shuffled(random)
    }

    private fun canonical(raw: String): String =
        LearningEngine.normalize(raw)
            .replace('ё', 'е')
            .trim { !it.isLetterOrDigit() }
            .removePrefix("to ")

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }
}
