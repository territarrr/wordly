package com.wordly.app.domain

import com.wordly.app.data.entity.Word
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportTest {

    private fun word(text: String, transcription: String?, translation: String, orderIndex: Long = 1) =
        Word(profileId = 1L, orderIndex = orderIndex, text = text, normalized = text.lowercase(), translation = translation, transcription = transcription)

    @Test
    fun `writes a header row followed by one row per word`() {
        val csv = CsvExport.build(listOf(word("gather", "/ˈɡæðə/", "собирать")))
        assertEquals("Слово,Транскрипция,Перевод\r\ngather,/ˈɡæðə/,собирать\r\n", csv)
    }

    @Test
    fun `writes an empty transcription cell when null`() {
        val csv = CsvExport.build(listOf(word("gather up", null, "поднять")))
        assertEquals("Слово,Транскрипция,Перевод\r\ngather up,,поднять\r\n", csv)
    }

    @Test
    fun `quotes a field containing a comma`() {
        val csv = CsvExport.build(listOf(word("indignant", "/x/", "возмущённый, негодующий")))
        assertEquals("Слово,Транскрипция,Перевод\r\nindignant,/x/,\"возмущённый, негодующий\"\r\n", csv)
    }

    @Test
    fun `escapes a quote inside a field by doubling it`() {
        val csv = CsvExport.build(listOf(word("greeting", null, "she said \"hi\"")))
        assertEquals("Слово,Транскрипция,Перевод\r\ngreeting,,\"she said \"\"hi\"\"\"\r\n", csv)
    }

    @Test
    fun `round-trips through CsvImport`() {
        val words = listOf(
            word("gather", "/ˈɡæðə/", "собирать, собрать"),
            word("gather up", null, "поднять"),
        )
        val csv = CsvExport.build(words)
        val parsed = CsvImport.parse(csv).words
        assertEquals(
            listOf(
                ImportedWord("gather", "/ˈɡæðə/", "собирать, собрать"),
                ImportedWord("gather up", null, "поднять"),
            ),
            parsed,
        )
    }

    @Test
    fun `produces only the header for an empty word list`() {
        assertEquals("Слово,Транскрипция,Перевод\r\n", CsvExport.build(emptyList()))
    }
}
