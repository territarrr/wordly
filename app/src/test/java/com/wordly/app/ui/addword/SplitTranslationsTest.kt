package com.wordly.app.ui.addword

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitTranslationsTest {

    @Test
    fun `splits on commas and trims whitespace`() {
        assertEquals(listOf("пример", "образец"), splitTranslations("пример,  образец"))
    }

    @Test
    fun `drops blank parts from stray commas`() {
        assertEquals(listOf("пример"), splitTranslations("пример,,  "))
    }

    @Test
    fun `single value with no comma`() {
        assertEquals(listOf("пример"), splitTranslations("пример"))
    }

    @Test
    fun `blank field splits to an empty list`() {
        assertEquals(emptyList<String>(), splitTranslations("   "))
    }
}
