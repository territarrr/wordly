package com.wordly.app.domain

/** A word parsed from an imported CSV row, before duplicate/validity checks against the DB. */
data class ImportedWord(
    val text: String,
    val transcription: String?,
    val translation: String,
    /** Example sentence etc. — only Anki imports carry one. */
    val note: String? = null,
)

/**
 * [CsvImport.parse]'s output: the successfully-parsed rows plus a breakdown of why any others
 * were dropped, so the UI can report e.g. "12 добавлено, 2 нет перевода" instead of one opaque
 * "N invalid" count.
 */
data class CsvParseResult(
    val words: List<ImportedWord>,
    val missingWord: Int,
    val missingTranslation: Int,
)

/**
 * Parses a CSV export (columns: word, transcription, translation — matching what Google Sheets
 * produces via File > Download > CSV). A header row is detected and skipped by its first cell
 * reading "слово"/"word"; rows missing a word or a translation are dropped rather than imported
 * half-empty, and counted separately in the result.
 */
object CsvImport {

    fun parse(csv: String): CsvParseResult {
        val words = mutableListOf<ImportedWord>()
        var missingWord = 0
        var missingTranslation = 0
        splitRows(csv)
            .filterIndexed { index, cells -> index != 0 || !looksLikeHeader(cells) }
            .forEach { cells ->
                val text = cells.getOrNull(0)?.trim().orEmpty()
                val translation = cells.getOrNull(2)?.trim().orEmpty()
                when {
                    text.isEmpty() -> missingWord++
                    translation.isEmpty() -> missingTranslation++
                    else -> words.add(
                        ImportedWord(text, cells.getOrNull(1)?.trim()?.ifEmpty { null }, translation),
                    )
                }
            }
        return CsvParseResult(words, missingWord, missingTranslation)
    }

    private fun looksLikeHeader(cells: List<String>): Boolean =
        cells.getOrNull(0)?.trim()?.lowercase() in setOf("слово", "word")

    /**
     * Minimal RFC4180-style CSV parser: comma-separated fields, `"..."`-quoted fields may
     * contain commas/newlines, and `""` inside a quoted field is an escaped literal quote.
     * Blank lines are dropped.
     */
    private fun splitRows(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i]
            when {
                inQuotes && c == '"' && i + 1 < csv.length && csv[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                inQuotes && c == '"' -> inQuotes = false
                inQuotes -> field.append(c)
                c == '"' -> inQuotes = true
                c == ',' -> {
                    row.add(field.toString())
                    field.clear()
                }
                c == '\r' -> Unit
                c == '\n' -> {
                    row.add(field.toString())
                    field.clear()
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
