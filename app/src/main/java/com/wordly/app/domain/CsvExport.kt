package com.wordly.app.domain

import com.wordly.app.data.entity.Word

/**
 * Builds a CSV export in the same shape [CsvImport] reads (word, transcription, translation
 * columns, with a "Слово"/"Word" header row), so exporting and re-importing round-trips.
 */
object CsvExport {

    fun build(words: List<Word>): String {
        val sb = StringBuilder("Слово,Транскрипция,Перевод\r\n")
        for (word in words) {
            sb.append(csvField(word.text))
            sb.append(',')
            sb.append(csvField(word.transcription.orEmpty()))
            sb.append(',')
            sb.append(csvField(word.translation))
            sb.append("\r\n")
        }
        return sb.toString()
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
