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

    /** [word] as typed first, then in the other script (if that differs). */
    fun variants(word: String): List<String> {
        val other = if (isCyrillic(word)) toLatin(word) else toCyrillic(word)
        return listOf(word, other).distinct()
    }
}
