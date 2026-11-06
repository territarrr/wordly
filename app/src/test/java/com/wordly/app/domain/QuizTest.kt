package com.wordly.app.domain

import com.wordly.app.data.entity.Word
import com.wordly.app.domain.Quiz.Verdict
import com.wordly.app.domain.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizTest {

    @Test
    fun `accepts any comma-separated variant, ignoring case, ё and punctuation`() {
        assertEquals(Verdict.CORRECT, Quiz.check("  Ёлка! ", "ель, елка"))
        assertEquals(Verdict.CORRECT, Quiz.check("дерево", "ель, дерево"))
    }

    @Test
    fun `ignores the English infinitive prefix`() {
        assertEquals(Verdict.CORRECT, Quiz.check("run", "to run"))
        assertEquals(Verdict.CORRECT, Quiz.check("to run", "run"))
    }

    @Test
    fun `one wrong letter in a long word is a typo, short words must be exact`() {
        assertEquals(Verdict.TYPO, Quiz.check("apricat", "apricot"))
        assertEquals(Verdict.TYPO, Quiz.check("aprico", "apricot"))
        assertEquals(Verdict.WRONG, Quiz.check("cot", "cat"))
        assertEquals(Verdict.WRONG, Quiz.check("apple", "apricot"))
        assertEquals(Verdict.WRONG, Quiz.check("", "cat"))
    }

    private fun word(id: Long, text: String, translation: String) =
        Word(id = id, profileId = 1, orderIndex = id, text = text, normalized = text, translation = translation)

    @Test
    fun `choice options contain the answer and distinct distractors`() {
        val pool = listOf(word(1, "cat", "кошка"), word(2, "dog", "собака"), word(3, "kitty", "кошка"), word(4, "fox", "лиса"))
        val options = Quiz.choiceOptions(pool[0], pool, Direction.EN_TO_RU, seed = 7)
        assertTrue("кошка" in options)
        assertEquals(options.size, options.distinct().size)
        // "kitty" translates the same — must not appear as a second, indistinguishable "кошка".
        assertEquals(3, options.size)
        assertEquals(options, Quiz.choiceOptions(pool[0], pool, Direction.EN_TO_RU, seed = 7))
    }
}
