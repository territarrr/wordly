package com.wordly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerbianScriptTest {

    @Test
    fun `cyrillic to latin including digraph letters`() {
        assertEquals("prijatelj", SerbianScript.toLatin("пријатељ"))
        assertEquals("Džep njiva", SerbianScript.toLatin("Џеп њива"))
        assertEquals("kuća rođak", SerbianScript.toLatin("кућа рођак"))
    }

    @Test
    fun `latin to cyrillic treats lj nj dz as single letters`() {
        assertEquals("пријатељ", SerbianScript.toCyrillic("prijatelj"))
        assertEquals("Џеп њива", SerbianScript.toCyrillic("Džep njiva"))
        assertEquals("кућа рођак", SerbianScript.toCyrillic("kuća rođak"))
    }

    @Test
    fun `variants put the typed spelling first`() {
        assertEquals(listOf("ujak", "ујак"), SerbianScript.variants("ujak"))
        assertEquals(listOf("здраво", "zdravo"), SerbianScript.variants("здраво"))
        assertTrue(SerbianScript.isCyrillic("здраво"))
        assertFalse(SerbianScript.isCyrillic("zdravo"))
    }
}
