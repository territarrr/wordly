package com.wordly.app.domain

import com.wordly.app.data.entity.Word

/**
 * Leitner-style spaced repetition for **learned** words (benchmark model still decides what's
 * learned; this only decides *when* a learned word is due again).
 *
 * A right answer moves a word up one box (longer wait), a wrong one drops it back to box 1
 * (due tomorrow). Box 0 means "never scheduled" — e.g. learned before this feature existed —
 * and such words count as due so they enter the schedule.
 */
object Srs {

    /** Wait (days) after landing in box `i`; index 0 is unused (unscheduled). */
    val INTERVAL_DAYS = intArrayOf(0, 1, 3, 7, 14, 30, 60, 120)
    private const val DAY_MS = 24L * 60 * 60 * 1000

    val MAX_BOX = INTERVAL_DAYS.lastIndex

    /** [word] after one answer at time [now]. */
    fun onAnswer(word: Word, correct: Boolean, now: Long): Word {
        val box = if (correct) (word.srsBox + 1).coerceAtMost(MAX_BOX) else 1
        return word.copy(srsBox = box, dueAt = now + INTERVAL_DAYS[box] * DAY_MS)
    }

    /** First scheduling of a word that just became learned: box 1, due tomorrow. */
    fun onLearned(word: Word, now: Long): Word =
        if (word.srsBox > 0) word else word.copy(srsBox = 1, dueAt = now + INTERVAL_DAYS[1] * DAY_MS)

    /**
     * Gives learned-but-never-scheduled words (learned before spaced repetition existed, or by
     * moving the boundary by hand) a schedule spread evenly over the next [days] days in order of
     * addition, so a big backlog doesn't all land on "today" at once. They start in box 2 —
     * they're already learned, just not recently checked.
     */
    fun spreadBacklog(all: List<Word>, benchmark: Long, now: Long, days: Int = 30): List<Word> =
        all.filter { it.orderIndex <= benchmark && it.srsBox == 0 }
            .sortedBy { it.orderIndex }
            .mapIndexed { i, w -> w.copy(srsBox = 2, dueAt = now + (i % days) * DAY_MS) }

    fun isDue(word: Word, now: Long): Boolean = word.srsBox == 0 || (word.dueAt ?: 0L) <= now

    /**
     * Learned words due for review at [now], most overdue first (never-scheduled ones after the
     * dated ones, oldest first), capped at [limit].
     */
    fun dueSlice(all: List<Word>, benchmark: Long, now: Long, limit: Int): List<Word> =
        all.asSequence()
            .filter { it.orderIndex <= benchmark && isDue(it, now) }
            .sortedWith(compareBy<Word>({ it.srsBox == 0 }, { it.dueAt ?: 0L }, { it.orderIndex }))
            .take(limit.coerceAtLeast(0))
            .toList()

    fun dueCount(all: List<Word>, benchmark: Long, now: Long): Int =
        all.count { it.orderIndex <= benchmark && isDue(it, now) }
}
