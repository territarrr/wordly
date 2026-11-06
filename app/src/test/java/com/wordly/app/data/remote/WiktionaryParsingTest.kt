package com.wordly.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WiktionaryParsingTest {

    @Test
    fun `finds Pronunciation nested directly under the top-level English section`() {
        val sections = listOf(
            WiktionarySectionDto(toclevel = 1, line = "English", index = "1"),
            WiktionarySectionDto(toclevel = 2, line = "Pronunciation", index = "2"),
            WiktionarySectionDto(toclevel = 2, line = "Etymology 1", index = "3"),
        )
        assertEquals("2", WiktionaryParsing.findEnglishPronunciationSectionIndex(sections))
    }

    @Test
    fun `ignores a Pronunciation section belonging to a different language`() {
        // "chat" is French too - its Pronunciation section must not be picked for the
        // English word, regardless of which language section comes first on the page.
        val sections = listOf(
            WiktionarySectionDto(toclevel = 1, line = "English", index = "1"),
            WiktionarySectionDto(toclevel = 2, line = "Pronunciation", index = "2"),
            WiktionarySectionDto(toclevel = 1, line = "French", index = "10"),
            WiktionarySectionDto(toclevel = 2, line = "Pronunciation", index = "11"),
        )
        assertEquals("2", WiktionaryParsing.findEnglishPronunciationSectionIndex(sections))
    }

    @Test
    fun `returns null when the page has no English section`() {
        val sections = listOf(WiktionarySectionDto(toclevel = 1, line = "French", index = "1"))
        assertNull(WiktionaryParsing.findEnglishPronunciationSectionIndex(sections))
    }

    @Test
    fun `returns null when English has no Pronunciation subsection`() {
        val sections = listOf(
            WiktionarySectionDto(toclevel = 1, line = "English", index = "1"),
            WiktionarySectionDto(toclevel = 2, line = "Etymology", index = "2"),
            WiktionarySectionDto(toclevel = 1, line = "German", index = "5"),
        )
        assertNull(WiktionaryParsing.findEnglishPronunciationSectionIndex(sections))
    }

    @Test
    fun `finds Translations nested under English, even several levels deep under a POS`() {
        // Real shape: Translations sits under a Noun/Verb sense, not directly under English.
        val sections = listOf(
            WiktionarySectionDto(toclevel = 1, line = "English", index = "1"),
            WiktionarySectionDto(toclevel = 2, line = "Noun", index = "5"),
            WiktionarySectionDto(toclevel = 3, line = "Translations", index = "8"),
            WiktionarySectionDto(toclevel = 2, line = "Verb", index = "10"),
        )
        assertEquals("8", WiktionaryParsing.findEnglishTranslationsSectionIndex(sections))
    }

    @Test
    fun `ignores a Translations section belonging to a different language`() {
        val sections = listOf(
            WiktionarySectionDto(toclevel = 1, line = "French", index = "1"),
            WiktionarySectionDto(toclevel = 2, line = "Translations", index = "2"),
        )
        assertNull(WiktionaryParsing.findEnglishTranslationsSectionIndex(sections))
    }

    @Test
    fun `extracts the first slash-delimited IPA span, skipping bare rhyme codes`() {
        // Real rendered shape for "chat" - a bare "-æt" rhyme also carries the IPA class.
        val html = """
            <span class="IPA nowrap">/ˈt͡ʃæt/</span>
            <span class="IPA">-æt</span>
        """.trimIndent()
        assertEquals("/ˈt͡ʃæt/", WiktionaryParsing.extractFirstIpa(html))
    }

    @Test
    fun `extracts bracket-delimited IPA too`() {
        val html = """<span class="IPA nowrap">[ˈæpəl]</span>"""
        assertEquals("[ˈæpəl]", WiktionaryParsing.extractFirstIpa(html))
    }

    @Test
    fun `returns null when no IPA span is present`() {
        assertNull(WiktionaryParsing.extractFirstIpa("<p>no pronunciation here</p>"))
    }

    @Test
    fun `prefers the transcoded mp3 audio source over the raw ogg`() {
        val html = """
            <source src="//upload.wikimedia.org/wikipedia/commons/4/48/En-uk-hello.ogg?utm_source=en.wiktionary.org&amp;utm_campaign=api">
            <source src="//upload.wikimedia.org/wikipedia/commons/transcoded/4/48/En-uk-hello.ogg/En-uk-hello.ogg.mp3">
        """.trimIndent()
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/4/48/En-uk-hello.ogg/En-uk-hello.ogg.mp3",
            WiktionaryParsing.extractFirstAudioUrl(html),
        )
    }

    @Test
    fun `falls back to the ogg source and unescapes html entities when no mp3 is present`() {
        val html = """<source src="//upload.wikimedia.org/x.ogg?a=1&amp;b=2">"""
        assertEquals(
            "https://upload.wikimedia.org/x.ogg?a=1&b=2",
            WiktionaryParsing.extractFirstAudioUrl(html),
        )
    }

    @Test
    fun `returns null when no audio source is present`() {
        assertNull(WiktionaryParsing.extractFirstAudioUrl("<p>no audio</p>"))
    }

    @Test
    fun `extracts a Russian headword and strips its stress-mark diacritic`() {
        // Real rendered shape for "example"'s Translations table.
        val html = """
            <span class="Cyrl" lang="ru"><a href="/wiki/%D0%BF%D1%80%D0%B8%D0%BC%D0%B5%D1%80#Russian"
            title="пример">при́мер</a></span><span class="tpos"> <a
            href="https://ru.wiktionary.org/wiki/%D0%BF%D1%80%D0%B8%D0%BC%D0%B5%D1%80"
            class="extiw" title="ru:пример">(ru)</a></span>
        """.trimIndent()
        assertEquals(listOf("пример"), WiktionaryParsing.extractRussianTranslations(html))
    }

    @Test
    fun `extracts multiple Russian headwords from separate glosses and dedupes`() {
        val html = """
            <li><span class="Cyrl" lang="ru">пример</span></li>
            <li><span class="Cyrl" lang="ru">образец</span></li>
            <li><span class="Cyrl" lang="ru">пример</span></li>
        """.trimIndent()
        assertEquals(listOf("пример", "образец"), WiktionaryParsing.extractRussianTranslations(html))
    }

    @Test
    fun `returns an empty list when no Russian entry is present`() {
        val html = """<span class="Cyrl" lang="uk">приклад</span>"""
        assertEquals(emptyList<String>(), WiktionaryParsing.extractRussianTranslations(html))
    }

    /** Real (trimmed) MediaWiki `prop=sections` response, captured for the word "garden". */
    @Test
    fun `deserializes and locates the section index in a real sections response`() {
        val json = """
            {
              "warnings": {"main": {"warnings": "deprecated notice"}},
              "parse": {
                "title": "garden",
                "pageid": 19485,
                "sections": [
                  {"toclevel": 1, "level": "2", "line": "English", "number": "1", "index": "1"},
                  {"toclevel": 2, "level": "3", "line": "Etymology", "number": "1.1", "index": "2"},
                  {"toclevel": 2, "level": "3", "line": "Pronunciation", "number": "1.2", "index": "3"},
                  {"toclevel": 2, "level": "3", "line": "Noun", "number": "1.3", "index": "4"}
                ]
              }
            }
        """.trimIndent()

        val json2 = Json { ignoreUnknownKeys = true }
        val response = json2.decodeFromString<WiktionarySectionsResponseDto>(json)

        assertEquals(
            "3",
            WiktionaryParsing.findEnglishPronunciationSectionIndex(response.parse!!.sections),
        )
    }

    /** Real (trimmed) MediaWiki `prop=text` response for "hello"'s Pronunciation section. */
    @Test
    fun `deserializes and extracts from a real section text response`() {
        val json = """
            {
              "parse": {
                "title": "hello",
                "text": "<span class=\"IPA nowrap\">/hɛˈloʊ/</span> <audio><source src=\"//upload.wikimedia.org/wikipedia/commons/transcoded/4/48/En-uk-hello.ogg/En-uk-hello.ogg.mp3\"></audio>"
              }
            }
        """.trimIndent()

        val json2 = Json { ignoreUnknownKeys = true }
        val response = json2.decodeFromString<WiktionaryTextResponseDto>(json)
        val text = response.parse!!.text

        assertEquals("/hɛˈloʊ/", WiktionaryParsing.extractFirstIpa(text))
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/transcoded/4/48/En-uk-hello.ogg/En-uk-hello.ogg.mp3",
            WiktionaryParsing.extractFirstAudioUrl(text),
        )
    }
}
