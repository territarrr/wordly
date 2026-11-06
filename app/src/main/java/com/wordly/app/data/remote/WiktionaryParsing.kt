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
    fun findEnglishPronunciationSectionIndex(sections: List<WiktionarySectionDto>): String? =
        findEnglishSubsectionIndex(sections, "Pronunciation")

    /**
     * The MediaWiki section index of the first "Translations" subsection nested under the
     * top-level "English" section (there is one per part-of-speech sense; the first one is
     * generally the primary sense), or `null` if none exists. Same cross-language pitfall as
     * [findEnglishPronunciationSectionIndex].
     */
    fun findEnglishTranslationsSectionIndex(sections: List<WiktionarySectionDto>): String? =
        findEnglishSubsectionIndex(sections, "Translations")

    private fun findEnglishSubsectionIndex(
        sections: List<WiktionarySectionDto>,
        headingPrefix: String,
    ): String? = findSubsectionIndex(sections, setOf("English"), headingPrefix)

    /**
     * The index of the first subsection starting with [headingPrefix] inside the first top-level
     * section whose heading is one of [languages] — the generalisation of the English-only lookups
     * above, used for Serbian on both en.wiktionary and ru.wiktionary.
     */
    fun findSubsectionIndex(
        sections: List<WiktionarySectionDto>,
        languages: Set<String>,
        headingPrefix: String,
    ): String? {
        val languageIndex = sections.indexOfFirst { it.toclevel == 1 && it.line in languages }
        if (languageIndex == -1) return null
        for (i in languageIndex + 1 until sections.size) {
            val section = sections[i]
            if (section.toclevel == 1) return null // next top-level language section: stop
            if (section.line.startsWith(headingPrefix)) return section.index
        }
        return null
    }

    /** The index of the top-level section for [language] (e.g. "Serbo-Croatian"), or `null`. */
    fun findLanguageSectionIndex(sections: List<WiktionarySectionDto>, language: String): String? =
        sections.firstOrNull { it.toclevel == 1 && it.line == language }?.index

    private val OL = Regex("<ol>(.*?)</ol>", RegexOption.DOT_MATCHES_ALL)
    private val LI = Regex("<li>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
    private val NESTED_LIST = Regex("<(ul|dl|ol)\\b.*", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("<[^>]+>")
    private val PARENTHESES = Regex("\\([^)]*\\)")
    private val SPLIT = Regex("[,;]")

    /**
     * Short English glosses from an en.wiktionary language section (e.g. "Serbo-Croatian"):
     * every definition line across its part-of-speech lists, minus usage examples, notes in
     * parentheses and "form of" lines (e.g. "neuter nominative singular of onaj"), split on
     * commas. "hvala" → ["praise", "thank you", "thanks"].
     */
    fun extractEnglishGlosses(html: String, limit: Int = 4): List<String> =
        OL.findAll(html)
            .flatMap { LI.findAll(it.groupValues[1]) }
            .map { it.groupValues[1].replace(NESTED_LIST, "") }
            // A link to another Serbo-Croatian headword means "inflection of X", not a meaning.
            .filterNot { "lang=\"sh\"" in it }
            .map { li -> cleanText(li).replace(PARENTHESES, "").split(SPLIT).map { it.trim().trim('!', '.', '?', ' ') } }
            // Same rule as the Russian meanings: a line with a long part is a description
            // ("anything resembling the state of fever; … ; a bug"), not a list of equivalents.
            .filter { parts -> parts.none { it.split(' ').size > 3 } }
            .flatMap { it }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(limit)
            .toList()

    /**
     * Russian meanings from a ru.wiktionary "Значение" section: each numbered line, cut before
     * its "◆" usage example, without grammatical/style labels ("этнолог.", "разг."), split on
     * commas; lines that are descriptive definitions (any part of 4+ words) are dropped whole.
     */
    fun extractRussianMeanings(html: String, limit: Int = 6): List<String> =
        OL.find(html)?.groupValues?.get(1).orEmpty()
            .let { LI.findAll(it) }
            .map { it.groupValues[1].substringBefore("<span class=\"example-fullblock") }
            // A line with any long part is a descriptive definition ("в фам. или ирон. обращении…"),
            // not a list of synonyms — drop it whole. Measured *with* labels, since labels sit
            // mid-sentence and removing them first would leave short-looking shards.
            .filter { li -> meaningParts(li).none { it.split(' ').size > 3 } }
            .flatMap { li -> meaningParts(li.replace(LABEL, "")) }
            .filter { it.isNotEmpty() && it.none(Char::isDigit) }
            .distinct()
            .take(limit)
            .toList()

    private fun meaningParts(liHtml: String): List<String> =
        cleanText(liHtml).substringBefore('◆').replace(PARENTHESES, "").split(SPLIT)
            .map { it.trim().trim('.', ' ').replace(STRESS, "") }

    private val SH_HEADWORD = Regex("<strong class=\"(Latn|Cyrl) headword\" lang=\"sh\">(.*?)</strong>", RegexOption.DOT_MATCHES_ALL)

    /**
     * The accented headword of an en.wiktionary "Serbo-Croatian" section in the requested script —
     * the dictionary spelling with its tone marks, e.g. "hvála", "ȕjāk" (see
     * [com.wordly.app.domain.SerbianScript.simplifyAccent]).
     */
    fun extractSerboCroatianHeadword(html: String, cyrillic: Boolean): String? =
        SH_HEADWORD.findAll(html)
            .firstOrNull { (it.groupValues[1] == "Cyrl") == cyrillic }
            ?.let { cleanText(it.groupValues[2]) }
            ?.takeIf { it.isNotEmpty() }

    private val SERBIAN_TRANSLATION = Regex("<span lang=\"(sr|sr-Latn|sh)\">(.*?)</span></li>", RegexOption.DOT_MATCHES_ALL)

    /**
     * Serbian words from the "Перевод" table of a ru.wiktionary Russian entry ("дом" → "кућа"):
     * the Serbian (Cyrillic or Latin) and Serbo-Croatian rows, comma-split, stress marks removed.
     */
    fun extractSerbianTranslations(html: String): List<String> =
        SERBIAN_TRANSLATION.findAll(html)
            .flatMap { cleanText(it.groupValues[2]).split(SPLIT) }
            .map { it.trim().replace(STRESS, "") }
            .filter { it.isNotEmpty() && it.split(' ').size <= 3 }
            .distinct()
            .toList()

    private val LABEL = Regex("<span class=\"label[^\"]*\"[^>]*>.*?</span>", RegexOption.DOT_MATCHES_ALL)
    private const val STRESS = "\u0301"

    private fun cleanText(html: String): String =
        html.replace(TAG, "")
            .replace("&#160;", " ").replace("&nbsp;", " ").replace("&#9670;", "◆")
            .replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()

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

    private val RUSSIAN_TRANSLATION_SPAN = Regex("<span class=\"Cyrl\" lang=\"ru\">([\\s\\S]*?)</span>")
    private val HTML_TAG = Regex("<[^>]+>")
    private const val COMBINING_ACUTE_ACCENT = "́" // Wiktionary marks stress with this

    /**
     * Every Russian headword listed in a "Translations" section's rendered HTML (there can be
     * several — one per gloss/near-synonym in that table), stripped of the inner `<a>` markup
     * and stress-mark diacritics Wiktionary adds to the Cyrillic text.
     */
    fun extractRussianTranslations(html: String): List<String> =
        RUSSIAN_TRANSLATION_SPAN.findAll(html)
            .map {
                it.groupValues[1]
                    .replace(HTML_TAG, "")
                    .replace(COMBINING_ACUTE_ACCENT, "")
                    .trim()
            }
            .filter(String::isNotBlank)
            .distinct()
            .toList()
}
