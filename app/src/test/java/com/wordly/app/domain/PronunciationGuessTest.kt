package com.wordly.app.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PronunciationGuessTest {

    private val dictionary = mapOf(
        "rein" to "/ɹeɪn/",
        "in" to "/ɪn/, /ən/",
        "umbrella" to "/ʌmˈbɹɛlə/",
        "term" to "[tɜːm]",
        "muddle" to "/ˈmʌdəl/",
        "dour" to "/ˈdaʊə/",
        "stagger" to "/ˈstæɡə/",
        "beady" to "/ˈbiːdi/",
        "grudge" to "/ɡɹʌdʒ/",
        "wince" to "/wɪns/",
        "snag" to "/snæɡ/",
        "decree" to "/dɪˈkɹiː/",
        "rift" to "/ɹɪft/",
        "feckless" to "/ˈfɛkləs/",
        "exonerate" to "/ɪɡˈzɒnəɹeɪt/",
        "hold" to "/həʊld/",
        "to" to "/tuː/",
        "account" to "/əˈkaʊnt/",
    )

    private fun guess(text: String): String? = runBlocking { PronunciationGuess.guess(text) { dictionary[it] } }

    @Test
    fun `a phrase joins each word's transcription`() {
        assertEquals("/ɹeɪn ɪn/", guess("rein in"))
        assertEquals("/ʌmˈbɹɛlə tɜːm/", guess("umbrella term"))
        assertEquals("/ˈmʌdəl θruː/", guess("muddle through"))
        assertEquals("/tuː həʊld tuː əˈkaʊnt/", guess("to hold to account"))
    }

    @Test
    fun `a phrase with an unknown word gets nothing`() {
        assertNull(guess("rein in smoothly-ish xyzzy"))
    }

    @Test
    fun `placeholders and punctuation are skipped`() {
        assertEquals("/ɹeɪn ɪn/", guess("rein smb in."))
        assertEquals("/ˈmʌdəl/", guess("Muddle!"))
    }

    @Test
    fun `inflected forms are built from the base word`() {
        assertEquals("/ˈmʌdəld/", guess("muddled"))
        assertEquals("/ˈstæɡəd/", guess("staggered"))
        assertEquals("/ˈstæɡərɪŋ/", guess("staggering"))
        assertEquals("/ˈdaʊəli/", guess("dourly"))
        assertEquals("/ˈbiːdɪli/", guess("beadily"))
        assertEquals("/wɪnsɪŋ/", guess("wincing"))
        assertEquals("/snæɡɪŋ/", guess("snagging"))
        assertEquals("/dɪˈkɹiːd/", guess("decreed"))
        assertEquals("/ɹɪfts/", guess("rifts"))
        assertEquals("/ˈfɛkləsnəs/", guess("fecklessness"))
        assertEquals("/ɪɡˈzɒnəɹeɪtɪd/", guess("exonerated"))
    }

    @Test
    fun `two endings deep`() {
        assertEquals("/ɡɹʌdʒɪŋli/", guess("grudgingly"))
    }

    @Test
    fun `endings sound like the base ends`() {
        assertEquals("kɪst", PronunciationGuess.withSuffix("kɪs", "kiss", PronunciationGuess.Suffix.ED))
        assertEquals("wɒtʃɪz", PronunciationGuess.withSuffix("wɒtʃ", "watch", PronunciationGuess.Suffix.S))
        assertEquals("dɒɡz", PronunciationGuess.withSuffix("dɒɡ", "dog", PronunciationGuess.Suffix.S))
        assertEquals("dɪsˈkləʊʒəd", PronunciationGuess.withSuffix("dɪsˈkləʊʒə(r)", "disclosure", PronunciationGuess.Suffix.ED))
    }

    @Test
    fun `sentences and pasted IPA are left alone`() {
        assertNull(PronunciationGuess.tokens("You mustn't speak ill of the dead at all"))
        assertNull(PronunciationGuess.tokens("/dɪsˈkləʊʒə(r)/"))
    }
}
