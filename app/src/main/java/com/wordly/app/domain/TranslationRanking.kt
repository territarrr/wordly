package com.wordly.app.domain

/**
 * Cleans and ranks raw translation candidates from a translation-*memory* service (MyMemory).
 *
 * Such a service aligns phrases from a corpus of real bilingual sentences rather than looking
 * up dictionary entries, so alongside a clean word-for-word translation it often also returns
 * inflected fragments cut out of a specific example sentence (e.g. for "example" it may offer
 * "личном примере" — "in [a/one's] personal example" — next to the plain "пример"). This
 * ranks short, standalone-looking results first without a real dictionary/grammar dependency.
 */
object TranslationRanking {

    fun rank(rawCandidates: List<String>, limit: Int = 6): List<String> =
        rawCandidates
            .map { it.trim().trim('.', ',', ';', '!', '?', '…') }
            .filter { it.isNotBlank() && !looksLikeSentenceFragment(it) }
            .distinctBy { it.lowercase() }
            .sortedBy { wordCount(it) }
            .take(limit)

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
        "не", "и", "а", "но", "что", "как", "это", "то",
    )
}
