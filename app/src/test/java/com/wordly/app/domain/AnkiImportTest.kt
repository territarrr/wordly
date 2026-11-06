package com.wordly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnkiImportTest {

    private val twoBooksFields = listOf(
        "MainWord", "MainTranscript", "MainExample", "MainSource",
        "SecondaryWord", "SecondaryTranscript", "SecondaryExample", "SecondarySource",
    )

    private fun twoBooks(word: String, ipa: String, tr: String) = AnkiImport.Note(
        twoBooksFields,
        listOf(
            word, ipa, "Strike had a <b>hunch</b> that it&#39;s part ...", "The Running Grave",
            tr, "", "Страйк <b>догадался</b>, что ...", "Бегущая могила",
        ),
    )

    @Test
    fun `2Books notes map to word, transcription, translation and an example note`() {
        val result = AnkiImport.toWords(listOf(twoBooks("hunch", "[hʌnʧ]", "предчувствие")))
        val w = result.words.single()
        assertEquals("hunch", w.text)
        assertEquals("[hʌnʧ]", w.transcription)
        assertEquals("предчувствие", w.translation)
        assertEquals("Strike had a hunch that it's part ...\nСтрайк догадался, что ...\n— The Running Grave", w.note)
    }

    @Test
    fun `duplicates inside the deck keep the first, empty transcription becomes null`() {
        val result = AnkiImport.toWords(listOf(twoBooks("clash", "", "столкновение"), twoBooks("Clash", "", "стычка")))
        assertEquals(listOf("столкновение"), result.words.map { it.translation })
        assertNull(result.words.single().transcription)
    }

    @Test
    fun `generic decks use the first two fields and count missing parts`() {
        val notes = listOf(
            AnkiImport.Note(listOf("Front", "Back"), listOf("cat", "кошка&nbsp;<br>кот")),
            AnkiImport.Note(listOf("Front", "Back"), listOf("dog", "")),
            AnkiImport.Note(listOf("Front", "Back"), listOf("", "мышь")),
        )
        val result = AnkiImport.toWords(notes)
        assertEquals("кошка, кот", result.words.single().translation)
        assertEquals(1, result.missingTranslation)
        assertEquals(1, result.missingWord)
    }

    @Test
    fun `2Books mirrored RU-EN notes are flipped back and merge with the EN-RU original`() {
        val mirrored = AnkiImport.Note(
            twoBooksFields,
            listOf(
                "одуванчик", "", "... придавая ей вид <b>одуванчика</b> ...", "Бегущая могила",
                "dandelion", "[ˈdændɪlaɪən]", "... like a <b>dandelion</b> clock ...", "The Running Grave",
            ),
        )
        val w = AnkiImport.toWords(listOf(mirrored, twoBooks("dandelion", "[ˈdændɪlaɪən]", "одуванчик"))).words.single()
        assertEquals("dandelion", w.text)
        assertEquals("одуванчик", w.translation)
        assertEquals("[ˈdændɪlaɪən]", w.transcription)
        assertEquals("... like a dandelion clock ...\n... придавая ей вид одуванчика ...\n— The Running Grave", w.note)
    }

    @Test
    fun `reversed generic deck is flipped`() {
        val w = AnkiImport.toWords(listOf(AnkiImport.Note(listOf("Front", "Back"), listOf("кошка", "cat")))).words.single()
        assertEquals("cat" to "кошка", w.text to w.translation)
    }
}
