package com.wordly.app.domain

/**
 * Builds a transcription for what the dictionaries don't list on their own: phrases ("rein in" →
 * /reɪn ɪn/, from each word's transcription) and inflected or derived forms ("muddled" from
 * "muddle" + /d/, "dourly" from "dour" + /li/). Pure — the actual lookup is passed in as `find`,
 * which returns a transcription in any of the usual shapes ("/ˈmʌdəl/", "[ˈmʌdl]") or null.
 */
object PronunciationGuess {

    /** Placeholders that stand for "someone/something" in phrase entries and aren't spoken. */
    private val PLACEHOLDERS = setOf("smth", "smb", "sth", "sb", "smbd", "smthg", "sm1", "smb's", "smth's", "sb's", "sth's", "etc")
    private const val MAX_PHRASE_WORDS = 5
    private val EDGE_PUNCTUATION = Regex("^[\\p{Punct}‘’“”«»…]+|[\\p{Punct}‘’“”«»…]+$")

    /**
     * Short words whose dictionary entries often list the weak form first (/ən/ for "in") — in
     * phrasal verbs and set phrases they're stressed, so the strong form is the one to show.
     * British, like most saved transcriptions.
     */
    private val STRONG_FORMS = mapOf(
        "a" to "ə", "an" to "æn", "the" to "ðə", "and" to "ænd", "or" to "ɔː", "but" to "bʌt",
        "in" to "ɪn", "on" to "ɒn", "at" to "æt", "to" to "tuː", "of" to "ɒv", "for" to "fɔː",
        "from" to "frɒm", "with" to "wɪð", "by" to "baɪ", "as" to "æz", "up" to "ʌp", "out" to "aʊt",
        "off" to "ɒf", "down" to "daʊn", "into" to "ˈɪntuː", "onto" to "ˈɒntuː", "over" to "ˈəʊvə",
        "away" to "əˈweɪ", "back" to "bæk", "about" to "əˈbaʊt", "around" to "əˈraʊnd",
        "through" to "θruː", "upon" to "əˈpɒn", "be" to "biː", "is" to "ɪz", "are" to "ɑː",
        "was" to "wɒz", "have" to "hæv", "has" to "hæz", "do" to "duː", "it" to "ɪt",
        "one" to "wʌn", "one's" to "wʌnz", "oneself" to "wʌnˈsɛlf", "you" to "juː", "me" to "miː",
        "us" to "ʌs", "them" to "ðɛm", "her" to "hɜː", "him" to "hɪm", "your" to "jɔː",
        "some" to "sʌm", "that" to "ðæt", "than" to "ðæn", "not" to "nɒt", "no" to "nəʊ", "so" to "səʊ",
    )

    suspend fun guess(text: String, find: suspend (String) -> String?): String? {
        val tokens = tokens(text) ?: return null
        val cores = mutableListOf<String>()
        for (token in tokens) {
            val ipa = (if (tokens.size > 1) STRONG_FORMS[token] else null)
                ?: find(token)?.let(::core) ?: inflected(token, find, depth = 0) ?: return null
            cores += ipa
        }
        return "/" + cores.joinToString(" ") + "/"
    }

    /**
     * Lower-cased words to look up, or null for text this can't help with (a long sentence, an
     * IPA string pasted as the word). A single word comes back as itself, punctuation trimmed.
     */
    fun tokens(text: String): List<String>? {
        if (text.contains('/') && text.trim().let { it.startsWith("/") || it.startsWith("[") }) return null
        val tokens = text.lowercase()
            .replace('’', '\'')
            .split(Regex("[\\s\\-/]+"))
            .map { it.replace(EDGE_PUNCTUATION, "") }
            .filter { it.isNotEmpty() && it !in PLACEHOLDERS }
        if (tokens.isEmpty() || tokens.size > MAX_PHRASE_WORDS) return null
        if (tokens.any { token -> !token.all { it.isLetter() || it == '\'' } }) return null
        return tokens
    }

    /** The bare transcription inside the first /…/ or […] (several variants are often listed). */
    fun core(ipa: String): String? {
        val trimmed = ipa.trim()
        val inner = Regex("/([^/]+)/").find(trimmed)?.groupValues?.get(1)
            ?: Regex("\\[([^\\]]+)]").find(trimmed)?.groupValues?.get(1)
            ?: trimmed
        return inner.trim().ifEmpty { null }
    }

    enum class Suffix { ED, S, ING, LY, NESS }

    /** A possible base form of [word] and the ending that was added to it. */
    data class Candidate(val base: String, val suffix: Suffix)

    fun candidates(word: String): List<Candidate> {
        val w = word.lowercase()
        if (w.length < 4) return emptyList()
        val out = mutableListOf<Candidate>()
        fun add(base: String, suffix: Suffix) {
            if (base.length >= 2) out += Candidate(base, suffix)
        }
        fun undoubled(stem: String) = stem.length >= 3 && stem.last() == stem[stem.length - 2] && stem.last() !in "aeiouslz"
        when {
            w.endsWith("ily") -> add(w.dropLast(3) + "y", Suffix.LY)
            w.endsWith("ly") -> add(w.dropLast(2), Suffix.LY)
        }
        when {
            w.endsWith("iness") -> add(w.dropLast(5) + "y", Suffix.NESS)
            w.endsWith("ness") -> add(w.dropLast(4), Suffix.NESS)
        }
        when {
            w.endsWith("ied") -> add(w.dropLast(3) + "y", Suffix.ED)
            w.endsWith("ed") -> {
                val stem = w.dropLast(2)
                add(stem, Suffix.ED)
                add(w.dropLast(1), Suffix.ED)
                if (undoubled(stem)) add(stem.dropLast(1), Suffix.ED)
            }
        }
        if (w.endsWith("ing")) {
            val stem = w.dropLast(3)
            add(stem, Suffix.ING)
            add(stem + "e", Suffix.ING)
            if (undoubled(stem)) add(stem.dropLast(1), Suffix.ING)
        }
        when {
            w.endsWith("ies") -> add(w.dropLast(3) + "y", Suffix.S)
            w.endsWith("ss") || w.endsWith("us") || w.endsWith("is") -> Unit
            w.endsWith("es") -> {
                add(w.dropLast(2), Suffix.S)
                add(w.dropLast(1), Suffix.S)
            }
            w.endsWith("s") -> add(w.dropLast(1), Suffix.S)
        }
        return out
    }

    /** The transcription of [base] (spelled [baseSpelling]) with [suffix] added. */
    fun withSuffix(base: String, baseSpelling: String, suffix: Suffix): String {
        // "dɪsˈkləʊʒə(r)" — an optional sound in brackets doesn't decide how the ending sounds.
        val stem = base.replace(Regex("\\((.)\\)$"), "").trimEnd()
        val last = stem.trimEnd('ː', 'ˑ').lastOrNull() ?: return base
        val sibilant = last in "szʃʒ" || stem.endsWith("tʃ") || stem.endsWith("dʒ")
        val voiceless = last in "pktfθ" || stem.endsWith("tʃ")
        val linkingR = baseSpelling.endsWith("r") && last in "əɚ"
        val yBase = baseSpelling.endsWith("y") && last in "iɪ"
        return when (suffix) {
            Suffix.ED -> stem + when {
                last in "td" -> "ɪd"
                voiceless || last == 's' || last == 'ʃ' -> "t"
                else -> "d"
            }
            Suffix.S -> stem + when {
                sibilant -> "ɪz"
                voiceless -> "s"
                else -> "z"
            }
            Suffix.ING -> stem + (if (linkingR) "r" else "") + "ɪŋ"
            Suffix.LY -> if (yBase) stem.dropLast(1) + "ɪli" else stem + "li"
            Suffix.NESS -> if (yBase) stem.dropLast(1) + "inəs" else stem + "nəs"
        }
    }

    /** Transcription of an inflected [word] built from its base form, one level of nesting deep ("grudgingly"). */
    private suspend fun inflected(word: String, find: suspend (String) -> String?, depth: Int): String? {
        val candidates = candidates(word)
        for (candidate in candidates) {
            val base = find(candidate.base)?.let(::core) ?: continue
            return withSuffix(base, candidate.base, candidate.suffix)
        }
        if (depth > 0) return null
        for (candidate in candidates) {
            val base = inflected(candidate.base, find, depth + 1) ?: continue
            return withSuffix(base, candidate.base, candidate.suffix)
        }
        return null
    }
}
