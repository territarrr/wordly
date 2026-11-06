package com.wordly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvImportTest {

    private fun wordsOf(csv: String) = CsvImport.parse(csv).words

    @Test
    fun `parses word, transcription and translation columns`() {
        val csv = "gather,/ˈɡæðə/,собирать"
        assertEquals(listOf(ImportedWord("gather", "/ˈɡæðə/", "собирать")), wordsOf(csv))
    }

    @Test
    fun `skips a header row named Слово or word`() {
        val csv = "Слово,Транскрипция,Перевод\ngather,/ˈɡæðə/,собирать"
        assertEquals(listOf(ImportedWord("gather", "/ˈɡæðə/", "собирать")), wordsOf(csv))
    }

    @Test
    fun `does not skip a data row that merely starts with a plausible word`() {
        val csv = "wordly,/ˈwɜːdli/,словли"
        assertEquals(listOf(ImportedWord("wordly", "/ˈwɜːdli/", "словли")), wordsOf(csv))
    }

    @Test
    fun `handles quoted fields containing commas`() {
        val csv = """gather,/ˈɡæðə/,"собирать, собрать""""
        assertEquals(listOf(ImportedWord("gather", "/ˈɡæðə/", "собирать, собрать")), wordsOf(csv))
    }

    @Test
    fun `unescapes doubled quotes inside a quoted field`() {
        val csv = "greeting,,\"she said \"\"hi\"\"\""
        assertEquals(listOf(ImportedWord("greeting", null, "she said \"hi\"")), wordsOf(csv))
    }

    @Test
    fun `treats a blank transcription cell as null`() {
        val csv = "gather up,,перевод"
        assertEquals(listOf(ImportedWord("gather up", null, "перевод")), wordsOf(csv))
    }

    @Test
    fun `counts rows missing a word separately from rows missing a translation`() {
        val csv = "\n,/ipa/,перевод\nword,/ipa/,\nvalid,/ipa/,перевод"
        val result = CsvImport.parse(csv)
        assertEquals(listOf(ImportedWord("valid", "/ipa/", "перевод")), result.words)
        assertEquals(1, result.missingWord)
        assertEquals(1, result.missingTranslation)
    }

    @Test
    fun `parses multiple rows separated by newlines, ignoring trailing blank lines`() {
        val csv = "a,,1\nb,,2\n\n"
        assertEquals(listOf(ImportedWord("a", null, "1"), ImportedWord("b", null, "2")), wordsOf(csv))
    }

    @Test
    fun `returns an empty result with zero counts for blank input`() {
        val result = CsvImport.parse("")
        assertEquals(emptyList<ImportedWord>(), result.words)
        assertEquals(0, result.missingWord)
        assertEquals(0, result.missingTranslation)
    }
}
