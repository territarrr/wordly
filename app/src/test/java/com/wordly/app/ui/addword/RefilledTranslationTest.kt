package com.wordly.app.ui.addword

import org.junit.Assert.assertEquals
import org.junit.Test

class RefilledTranslationTest {

    @Test
    fun `fills an empty field`() {
        assertEquals("дом", AddWordUiState().refilledTranslation(listOf("дом", "здание")))
    }

    @Test
    fun `replaces what the previous lookup filled in`() {
        val state = AddWordUiState(translation = "привет", autoFilledTranslation = "привет")
        assertEquals("дядя", state.refilledTranslation(listOf("дядя")))
    }

    @Test
    fun `never overwrites a translation typed or picked by hand`() {
        val state = AddWordUiState(translation = "дядя, дядька", autoFilledTranslation = "дядя")
        assertEquals("дядя, дядька", state.refilledTranslation(listOf("дядя по матери")))
        assertEquals("мой", AddWordUiState(translation = "мой").refilledTranslation(listOf("дом")))
    }

    @Test
    fun `keeps the old value when the lookup found nothing`() {
        val state = AddWordUiState(translation = "дом", autoFilledTranslation = "дом")
        assertEquals("дом", state.refilledTranslation(emptyList()))
    }
}
