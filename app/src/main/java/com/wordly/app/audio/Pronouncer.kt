package com.wordly.app.audio

import com.wordly.app.data.entity.Word

/**
 * Pronounces a word: plays the dictionary mp3 when one was saved, otherwise falls back to
 * on-device text-to-speech in the given profile's language.
 */
class Pronouncer(
    private val speaker: Speaker,
    private val audioPlayer: AudioPlayer,
) {
    fun pronounce(word: Word, languageTag: String) = pronounce(word.text, word.audioUrl, languageTag)

    fun pronounce(text: String, audioUrl: String?, languageTag: String) {
        val url = audioUrl?.takeIf { it.isNotBlank() }
        if (url != null) audioPlayer.play(url) else speaker.speak(text, languageTag)
    }
}
