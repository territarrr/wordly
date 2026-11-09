package com.wordly.app.domain

import com.wordly.app.data.entity.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000 * day

    private fun word(order: Long, box: Int = 0, dueAt: Long? = null) = Word(
        id = order, profileId = 1, orderIndex = order, text = "w$order", normalized = "w$order",
        translation = "п$order", srsBox = box, dueAt = dueAt,
    )

    @Test
    fun `right answer moves up a box and waits longer`() {
        val w = Srs.onAnswer(word(1, box = 2), correct = true, now = now)
        assertEquals(3, w.srsBox)
        assertEquals(now + 7 * day, w.dueAt)
    }

    @Test
    fun `wrong answer drops to box 1, due tomorrow`() {
        val w = Srs.onAnswer(word(1, box = 5), correct = false, now = now)
        assertEquals(1, w.srsBox)
        assertEquals(now + day, w.dueAt)
    }

    @Test
    fun `box is capped at the last interval`() {
        assertEquals(Srs.MAX_BOX, Srs.onAnswer(word(1, box = Srs.MAX_BOX), true, now).srsBox)
    }

    @Test
    fun `onLearned schedules only unscheduled words`() {
        assertEquals(1, Srs.onLearned(word(1), now).srsBox)
        assertEquals(4, Srs.onLearned(word(1, box = 4, dueAt = 5), now).srsBox)
    }

    @Test
    fun `dueSlice takes learned due words, most overdue first, unscheduled last`() {
        val all = listOf(
            word(1, box = 0),
            word(2, box = 2, dueAt = now - 2 * day),
            word(3, box = 2, dueAt = now + day), // not due yet
            word(4, box = 1, dueAt = now - 5 * day),
            word(5, box = 0), // new — beyond the benchmark
        )
        assertEquals(listOf(4L, 2, 1), Srs.dueSlice(all, benchmark = 4, now = now, limit = 10).map { it.id })
        assertEquals(listOf(4L), Srs.dueSlice(all, benchmark = 4, now = now, limit = 1).map { it.id })
        assertEquals(3, Srs.dueCount(all, benchmark = 4, now = now))
        assertTrue(Srs.isDue(word(9), now))
        assertFalse(Srs.isDue(word(9, box = 1, dueAt = now + 1), now))
    }

    @Test
    fun `spreadBacklog schedules only unscheduled learned words across the window`() {
        val all = (1L..5L).map { word(it) } + word(6, box = 3, dueAt = now) + word(7)
        val spread = Srs.spreadBacklog(all, benchmark = 6, now = now, days = 2)
        assertEquals(listOf(1L, 2, 3, 4, 5), spread.map { it.id })
        assertEquals(listOf(now, now + day, now, now + day, now), spread.map { it.dueAt })
        assertTrue(spread.all { it.srsBox == 2 })
    }

    @Test
    fun `dailySlice takes a fixed number of random due words, the same for the same seed`() {
        val now = 1_000_000L
        val words = (1L..100L).map { word(it, box = 1, dueAt = now - 1) }
        val a = Srs.dailySlice(words, benchmark = 100, now = now, limit = 50, seed = 7)
        assertEquals(50, a.size)
        assertEquals(a, Srs.dailySlice(words.shuffled(), benchmark = 100, now = now, limit = 50, seed = 7))
        assertTrue(a.map { it.id } != (1L..50L).toList()) // not just the first 50
        assertEquals(30, Srs.dailySlice(words.take(30), benchmark = 100, now = now, limit = 50, seed = 7).size)
    }
}
