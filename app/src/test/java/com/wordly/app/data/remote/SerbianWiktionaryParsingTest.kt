package com.wordly.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/** Runs the Serbian parsers against real (saved) en/ru.wiktionary section HTML. */
class SerbianWiktionaryParsingTest {

    private fun fixture(name: String): String =
        javaClass.getResource("/wiktionary/$name.html")!!.readText()

    @Test
    fun `english glosses span every part of speech and drop punctuation`() {
        assertEquals(listOf("praise", "thank you", "thanks"), WiktionaryParsing.extractEnglishGlosses(fixture("en_hvala")))
    }

    @Test
    fun `english glosses skip form-of lines`() {
        assertEquals(listOf("it"), WiktionaryParsing.extractEnglishGlosses(fixture("en_ono")))
    }

    @Test
    fun `english glosses drop parenthetical notes`() {
        assertEquals(listOf("maternal uncle"), WiktionaryParsing.extractEnglishGlosses(fixture("en_ujak")))
    }

    @Test
    fun `russian meanings drop labels, examples and long descriptions`() {
        assertEquals(
            listOf("приятель", "друг", "преданный", "защитник что-то"),
            WiktionaryParsing.extractRussianMeanings(fixture("ru_prijatelj_lat")),
        )
        assertEquals(listOf("друг"), WiktionaryParsing.extractRussianMeanings(fixture("ru_prijatelj_cyr")))
    }

    @Test
    fun `finds a subsection under any of several language headings`() {
        val sections = listOf(
            WiktionarySectionDto(1, "Русский", "1"),
            WiktionarySectionDto(3, "Значение", "2"),
            WiktionarySectionDto(1, "Хорватский", "3"),
            WiktionarySectionDto(2, "Семантические свойства", "4"),
            WiktionarySectionDto(3, "Значение", "5"),
        )
        assertEquals("5", WiktionaryParsing.findSubsectionIndex(sections, setOf("Сербский", "Хорватский"), "Значение"))
        assertEquals("3", WiktionaryParsing.findLanguageSectionIndex(sections, "Хорватский"))
    }

    @Test
    fun `accented headword in the requested script`() {
        assertEquals("hvála", WiktionaryParsing.extractSerboCroatianHeadword(fixture("en_hvala"), cyrillic = false))
        assertEquals("ȕjāk", WiktionaryParsing.extractSerboCroatianHeadword(fixture("en_ujak"), cyrillic = false))
        assertEquals("òno", WiktionaryParsing.extractSerboCroatianHeadword(fixture("en_ono"), cyrillic = false))
    }

    @Test
    fun `serbian rows from a russian entry's translation table`() {
        assertEquals(listOf("кућа"), WiktionaryParsing.extractSerbianTranslations(fixture("ru_dom_perevod")))
    }
}
