package com.wordly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationRankingTest {

    @Test
    fun `single-word translations are ranked before a multi-word example fragment`() {
        // Real MyMemory response shape for "example": a declined sentence fragment
        // ("личном примере" = "in [a] personal example") alongside the plain word.
        val raw = listOf("личном примере", "пример", "образец")
        assertEquals(listOf("пример", "образец", "личном примере"), TranslationRanking.rank(raw))
    }

    @Test
    fun `fragments longer than three words are dropped entirely`() {
        val raw = listOf("это очень длинный пример предложения целиком", "слово")
        assertEquals(listOf("слово"), TranslationRanking.rank(raw))
    }

    @Test
    fun `fragments starting with a preposition are dropped`() {
        val raw = listOf("в качестве примера", "для примера", "образец")
        assertEquals(listOf("образец"), TranslationRanking.rank(raw))
    }

    @Test
    fun `duplicates are removed case-insensitively`() {
        val raw = listOf("Пример", "пример", "ПРИМЕР")
        assertEquals(listOf("Пример"), TranslationRanking.rank(raw))
    }

    @Test
    fun `surrounding punctuation is trimmed`() {
        val raw = listOf("пример.", "образец,")
        assertEquals(listOf("пример", "образец"), TranslationRanking.rank(raw))
    }

    @Test
    fun `blank and empty candidates are ignored`() {
        val raw = listOf("", "   ", "пример")
        assertEquals(listOf("пример"), TranslationRanking.rank(raw))
    }

    @Test
    fun `result is capped at the given limit`() {
        val raw = (1..10).map { "слово$it" }
        assertEquals(4, TranslationRanking.rank(raw, limit = 4).size)
    }
}
