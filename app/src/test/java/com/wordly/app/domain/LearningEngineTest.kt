package com.wordly.app.domain

import com.wordly.app.data.entity.Word
import com.wordly.app.domain.model.ReviewScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEngineTest {

    private fun word(order: Long, text: String = "w$order") =
        Word(id = order, orderIndex = order, text = text, normalized = text, translation = "п$order")

    private val all = (1L..10L).map { word(it) }

    @Test
    fun `normalize trims lowercases and collapses whitespace`() {
        assertEquals("new york", LearningEngine.normalize("  New   York "))
    }

    @Test
    fun `isDuplicate ignores case and surrounding space`() {
        val existing = setOf("apple", "new york")
        assertTrue(LearningEngine.isDuplicate(existing, " Apple "))
        assertTrue(LearningEngine.isDuplicate(existing, "new  YORK"))
        assertFalse(LearningEngine.isDuplicate(existing, "banana"))
    }

    @Test
    fun `learnedSlice All returns every learned word`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 6, scope = ReviewScope.All)
        assertEquals(listOf(1L, 2, 3, 4, 5, 6), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Window returns the last N learned in addition order`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 8, scope = ReviewScope.Window(3, fromStart = false))
        assertEquals(listOf(6L, 7, 8), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Window returns the first N learned when fromStart is true`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 8, scope = ReviewScope.Window(3, fromStart = true))
        assertEquals(listOf(1L, 2, 3), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Window fromStart still respects the benchmark`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 4, scope = ReviewScope.Window(10, fromStart = true))
        assertEquals(listOf(1L, 2, 3, 4), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Window never returns words past the benchmark`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 4, scope = ReviewScope.Window(100, fromStart = false))
        assertEquals(listOf(1L, 2, 3, 4), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Range returns the given 1-indexed inclusive positions`() {
        // 10 words, benchmark 10 (all learned): positions 3..5 are orderIndex 3,4,5.
        val result = LearningEngine.learnedSlice(all, benchmark = 10, scope = ReviewScope.Range(3, 5))
        assertEquals(listOf(3L, 4, 5), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Range clamps an out-of-bounds end instead of going empty`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 10, scope = ReviewScope.Range(8, 100))
        assertEquals(listOf(8L, 9, 10), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Range clamps an out-of-bounds start`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 10, scope = ReviewScope.Range(-5, 2))
        assertEquals(listOf(1L, 2), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice Range clamps to the last learned word when entirely past the benchmark`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 4, scope = ReviewScope.Range(50, 60))
        assertEquals(listOf(4L), result.map { it.orderIndex })
    }

    @Test
    fun `learnedSlice is empty when nothing is learned yet`() {
        val result = LearningEngine.learnedSlice(all, benchmark = 0, scope = ReviewScope.Range(1, 5))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `newBatch takes the next words after the benchmark oldest first`() {
        val result = LearningEngine.newBatch(all, benchmark = 4, batchSize = 3)
        assertEquals(listOf(5L, 6, 7), result.map { it.orderIndex })
    }

    @Test
    fun `newBatch is empty when nothing is left to learn`() {
        assertTrue(LearningEngine.newBatch(all, benchmark = 10, batchSize = 5).isEmpty())
    }

    @Test
    fun `orderSession keeps addition order when not shuffled`() {
        val slice = listOf(word(3), word(1), word(2))
        assertEquals(listOf(1L, 2, 3), LearningEngine.orderSession(slice, shuffle = false, seed = 0).map { it.orderIndex })
    }

    @Test
    fun `orderSession shuffle is deterministic for a seed and preserves the set`() {
        val slice = all
        val a = LearningEngine.orderSession(slice, shuffle = true, seed = 42)
        val b = LearningEngine.orderSession(slice, shuffle = true, seed = 42)
        assertEquals(a.map { it.orderIndex }, b.map { it.orderIndex })
        assertEquals(slice.map { it.orderIndex }.toSet(), a.map { it.orderIndex }.toSet())
    }

    @Test
    fun `nextBenchmark advances to the last orderIndex of the batch`() {
        val batch = listOf(word(5), word(6), word(7))
        assertEquals(7L, LearningEngine.nextBenchmark(batch, current = 4))
    }

    @Test
    fun `nextBenchmark keeps current benchmark for an empty batch`() {
        assertEquals(4L, LearningEngine.nextBenchmark(emptyList(), current = 4))
    }
}
