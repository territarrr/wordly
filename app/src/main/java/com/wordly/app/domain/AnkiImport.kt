package com.wordly.app.domain

/**
 * Maps notes from an Anki package (.apkg) to importable words. Understands the 2Books reader's
 * export (fields MainWord / MainTranscript / MainExample / MainSource / SecondaryWord /
 * SecondaryExample) — word, transcription, translation, and the book sentence it was saved from
 * (English + Russian) as the note — and falls back to "first field = word, second = translation"
 * for any other deck.
 */
object AnkiImport {

    /** One Anki note: its note type's field names (in order) and the field values (HTML). */
    data class Note(val fieldNames: List<String>, val fields: List<String>)

    fun toWords(notes: List<Note>): CsvParseResult {
        val words = mutableListOf<ImportedWord>()
        var missingWord = 0
        var missingTranslation = 0
        val seen = mutableSetOf<String>()
        for (note in notes) {
            val word = map(note)
            when {
                word.text.isEmpty() -> missingWord++
                word.translation.isEmpty() -> missingTranslation++
                // The same word saved twice (from two books) — keep the first, oldest one.
                !seen.add(LearningEngine.normalize(word.text)) -> Unit
                else -> words += word
            }
        }
        return CsvParseResult(words, missingWord, missingTranslation)
    }

    private fun map(note: Note): ImportedWord {
        fun field(name: String) = note.fieldNames.indexOf(name).takeIf { it >= 0 }?.let { note.fields.getOrNull(it) }?.let(::clean).orEmpty()
        return if ("MainWord" in note.fieldNames && "SecondaryWord" in note.fieldNames) {
            // 2Books exports every word twice — an EN→RU card and a mirrored RU→EN one — so the
            // foreign side is whichever isn't Cyrillic; the mirror then dedupes against the original.
            val (foreign, russian) =
                if (hasCyrillic(field("MainWord")) && !hasCyrillic(field("SecondaryWord"))) "Secondary" to "Main" else "Main" to "Secondary"
            val example = listOf(field("${foreign}Example"), field("${russian}Example")).filter { it.isNotEmpty() }
            val source = field("${foreign}Source")
            ImportedWord(
                text = field("${foreign}Word"),
                transcription = field("${foreign}Transcript").ifEmpty { null },
                translation = field("${russian}Word"),
                note = (example + listOfNotNull(source.ifEmpty { null }?.let { "— $it" })).joinToString("\n").ifEmpty { null },
            )
        } else {
            val first = note.fields.getOrNull(0)?.let(::clean).orEmpty()
            val second = note.fields.getOrNull(1)?.let(::clean).orEmpty()
            // A reversed deck (Russian on the front): the word is the non-Cyrillic side.
            val (word, translation) = if (hasCyrillic(first) && !hasCyrillic(second)) second to first else first to second
            ImportedWord(
                text = word,
                transcription = null,
                // Wordly's translation is one line: "кошка<br>кот" becomes "кошка, кот".
                translation = translation.lines().filter { it.isNotBlank() }.joinToString(", "),
            )
        }
    }

    private fun hasCyrillic(text: String) = text.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

    private val TAG = Regex("<[^>]+>")
    private val BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val ENTITY = Regex("&(#\\d+|#x[0-9a-fA-F]+|amp|lt|gt|quot|apos|nbsp);")
    private val SPACES = Regex("[ \\t\\u00A0]+")

    /** Anki fields are HTML: drop tags (bold highlights etc.), decode entities, tidy spaces. */
    internal fun clean(html: String): String =
        html.replace(BREAK, "\n")
            .replace(TAG, "")
            .replace(ENTITY) { m ->
                when (val e = m.groupValues[1]) {
                    "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                    else -> {
                        val code = if (e.startsWith("#x")) e.drop(2).toInt(16) else e.drop(1).toInt()
                        String(Character.toChars(code))
                    }
                }
            }
            .lines()
            .joinToString("\n") { it.replace(SPACES, " ").trim() }
            .trim()
}
