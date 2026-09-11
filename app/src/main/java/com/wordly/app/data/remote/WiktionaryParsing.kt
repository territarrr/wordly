package com.wordly.app.data.remote

/**
 * Pure parsing helpers for [WiktionaryApi] responses — no network/Android dependency, so they
 * are unit-tested directly against fixtures shaped like real page fragments.
 */
object WiktionaryParsing {

    /**
     * The MediaWiki section index of the "Pronunciation" subsection nested under the
     * top-level "English" section, or `null` if the page has no English section (or no
     * Pronunciation subsection within it). A page title mixes every language that uses that
     * spelling (e.g. "chat" is both English and French), so this must not just grab the first
     * "Pronunciation" on the page — that could belong to an entirely different language.
     */
    fun findEnglishPronunciationSectionIndex(sections: List<WiktionarySectionDto>): String? {
        val englishIndex = sections.indexOfFirst { it.toclevel == 1 && it.line == "English" }
        if (englishIndex == -1) return null
        for (i in englishIndex + 1 until sections.size) {
            val section = sections[i]
            if (section.toclevel == 1) return null // next top-level language section: stop
            if (section.line.startsWith("Pronunciation")) return section.index
        }
        return null
    }

    private val IPA_SPAN = Regex("class=\"IPA[^\"]*\">([^<]+)<")

    /**
     * The first IPA-delimited (`/…/` or `[…]`) transcription in a Pronunciation section's
     * rendered HTML. Wiktionary also tags bare rhyme codes (e.g. "-æt") with the same `IPA`
     * CSS class, so a bare delimiter check filters those out.
     */
    fun extractFirstIpa(html: String): String? =
        IPA_SPAN.findAll(html)
            .map { it.groupValues[1].trim() }
            .firstOrNull(::looksLikeIpaTranscription)

    private fun looksLikeIpaTranscription(text: String): Boolean =
        text.length >= 2 &&
            ((text.first() == '/' && text.last() == '/') || (text.first() == '[' && text.last() == ']'))

    private val AUDIO_SOURCE = Regex("<source src=\"([^\"]+)\"")

    /**
     * The first playable pronunciation audio URL, preferring an already-transcoded mp3 (always
     * playable on Android) over the raw ogg upload.
     */
    fun extractFirstAudioUrl(html: String): String? {
        val sources = AUDIO_SOURCE.findAll(html)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .toList()
        val chosen = sources.firstOrNull { it.contains(".mp3", ignoreCase = true) } ?: sources.firstOrNull()
        return chosen?.let { if (it.startsWith("//")) "https:$it" else it }
    }
}
