package com.wordly.app.domain

/**
 * Serbian is written in both Cyrillic and Latin with a 1:1 letter mapping, but dictionaries file
 * each spelling as a separate page ("кућа" and "kuća"), so a lookup should try both.
 */
object SerbianScript {

    private val CYR_TO_LAT = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e",
        'ж' to "ž", 'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj",
        'м' to "m", 'н' to "n", 'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'ћ' to "ć", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č",
        'џ' to "dž", 'ш' to "š",
    )

    /** Digraphs first, so "lj"/"nj"/"dž" become one letter rather than two. */
    private val LAT_TO_CYR: List<Pair<String, Char>> =
        CYR_TO_LAT.entries.map { (cyr, lat) -> lat to cyr }.sortedByDescending { it.first.length }

    fun isCyrillic(text: String): Boolean = text.any { it.lowercaseChar() in CYR_TO_LAT }

    fun toLatin(text: String): String = buildString {
        for (ch in text) {
            val lat = CYR_TO_LAT[ch.lowercaseChar()]
            when {
                lat == null -> append(ch)
                ch.isUpperCase() -> append(lat.replaceFirstChar { it.uppercaseChar() })
                else -> append(lat)
            }
        }
    }

    fun toCyrillic(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val match = LAT_TO_CYR.firstOrNull { (lat, _) -> text.regionMatches(i, lat, 0, lat.length, ignoreCase = true) }
            if (match == null) {
                append(text[i])
                i++
            } else {
                append(if (text[i].isUpperCase()) match.second.uppercaseChar() else match.second)
                i += match.first.length
            }
        }
    }

    private const val ACUTE = '\u0301'

    /** Serbo-Croatian accent marks, all of which sit on the stressed vowel (or syllabic r). */
    private val TONE_MARKS = setOf('\u0300', '\u0301', '\u030F', '\u0311')

    /**
     * Turns a dictionary's tonal accent spelling into a plain stress mark, the way Russian
     * dictionaries mark stress: the four Serbo-Croatian accents (`ò ó ȍ ȏ`) all become an acute on
     * the stressed vowel, and post-accent length (`ā`) is dropped. "ȕjāk" → "újak", "hvála" stays.
     */
    fun simplifyAccent(accented: String): String {
        val out = StringBuilder()
        var base = ' '
        for (ch in java.text.Normalizer.normalize(accented, java.text.Normalizer.Form.NFD)) {
            val isMark = Character.getType(ch) == Character.NON_SPACING_MARK.toInt()
            when {
                !isMark -> { out.append(ch); base = ch.lowercaseChar() }
                // Over a consonant a mark is part of the letter itself: ć (c + ´), č š ž (+ ˇ).
                base !in ACCENT_BEARERS -> out.append(ch)
                ch in TONE_MARKS -> out.append(ACUTE)
                else -> Unit // post-accent length (ā) and the like
            }
        }
        return java.text.Normalizer.normalize(out.toString(), java.text.Normalizer.Form.NFC)
    }

    /** Vowels and syllabic r — the only letters Serbo-Croatian accent marks go on. */
    private val ACCENT_BEARERS = "aeiourаеиоур".toSet()

    /** [text] without any accent marks — but keeping č ć đ š ž, which are letters, not accents. */
    fun stripAccents(text: String): String {
        val out = StringBuilder()
        var base = ' '
        for (ch in java.text.Normalizer.normalize(simplifyAccent(text), java.text.Normalizer.Form.NFD)) {
            if (ch == ACUTE && base in ACCENT_BEARERS) continue
            out.append(ch)
            if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) base = ch.lowercaseChar()
        }
        return java.text.Normalizer.normalize(out.toString(), java.text.Normalizer.Form.NFC)
    }

    /** [word] as typed first, then in the other script (if that differs). */
    fun variants(word: String): List<String> {
        val other = if (isCyrillic(word)) toLatin(word) else toCyrillic(word)
        return listOf(word, other).distinct()
    }
}
