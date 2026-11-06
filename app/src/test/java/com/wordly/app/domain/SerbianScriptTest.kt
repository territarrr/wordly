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

    @Test
    fun `tone accents become a plain stress mark, length marks go`() {
        assertEquals("u\u0301jak", java.text.Normalizer.normalize(SerbianScript.simplifyAccent("ȕjāk"), java.text.Normalizer.Form.NFD))
        assertEquals("hvála", SerbianScript.simplifyAccent("hvála"))
        assertEquals("óno", SerbianScript.simplifyAccent("òno"))
        assertEquals("ку\u0301ћа", SerbianScript.simplifyAccent("ку\u030Fћа"))
        assertEquals("kúća", SerbianScript.simplifyAccent("kȕća"))
    }

    @Test
    fun `stripAccents keeps letters like c-caron`() {
        assertEquals("kuća", SerbianScript.stripAccents("kȕća"))
        assertEquals("ujak", SerbianScript.stripAccents("ȕjāk"))
        assertEquals("čaša", SerbianScript.stripAccents("čȁša"))
    }
}
