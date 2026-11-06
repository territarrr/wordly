package com.wordly.app.domain

/**
 * Cleans and ranks raw translation candidates: trims stray punctuation, drops anything that
 * looks like a fragment cut out of a longer sentence rather than a standalone word-for-word
 * translation (more than three words, or starting with a preposition/conjunction), dedupes,
 * and puts short results first.
 */
object TranslationRanking {

    /**
     * @param sourceWord when it starts lower-case, candidates are too — MT engines like to
     *   capitalise a lone word as if it began a sentence ("groznica" → "Лихорадка").
     * @param keepOrder keep the callers' source priority instead of putting short results first.
     */
    fun rank(
        rawCandidates: List<String>,
        limit: Int = 6,
        sourceWord: String? = null,
        keepOrder: Boolean = false,
    ): List<String> =
        rawCandidates
            .map { it.trim().trim('.', ',', ';', '!', '?', '…') }
            .map { if (sourceWord?.firstOrNull()?.isLowerCase() == true) decapitalize(it) else it }
            .filter { it.isNotBlank() && !looksLikeMarkup(it) && !looksLikeSentenceFragment(it) }
            .distinctBy { it.lowercase() }
            .let { if (keepOrder) it else it.sortedBy(::wordCount) }
            .take(limit)

    /** "Лихорадка" → "лихорадка", but acronyms like "США" stay as they are. */
    private fun decapitalize(text: String): String =
        if (text.length > 1 && text[1].isUpperCase()) text else text.replaceFirstChar { it.lowercaseChar() }

    /**
     * MyMemory's translation-memory matches sometimes carry raw alignment markup like
     * `<g id="1">добрый день.</g>` straight through — never a real word-for-word translation.
     */
    private fun looksLikeMarkup(text: String): Boolean =
        text.any { it == '<' || it == '>' || it == '=' }

    private fun looksLikeSentenceFragment(text: String): Boolean {
        val words = text.split(WHITESPACE).filter { it.isNotBlank() }
        if (words.size > 3) return true
        return words.firstOrNull()?.lowercase() in FRAGMENT_STARTERS
    }

    private fun wordCount(text: String) = text.split(WHITESPACE).count { it.isNotBlank() }

    private val WHITESPACE = Regex("\\s+")

    // Common Russian prepositions/conjunctions that essentially never start a standalone
    // word-for-word translation — their presence signals a fragment cut from a longer sentence.
    private val FRAGMENT_STARTERS = setOf(
        "в", "во", "на", "для", "с", "со", "из", "от", "о", "об", "к", "ко", "у", "по", "за",
        "не", "и", "а", "но", "что", "чтобы", "как", "это", "то",
    )
}
