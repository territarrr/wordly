package com.wordly.app.audio

import com.wordly.app.data.entity.Word

/**
 * Pronounces an English word: plays the dictionary mp3 when one was saved, otherwise falls
 * back to on-device text-to-speech.
 */
class Pronouncer(
    private val speaker: Speaker,
    private val audioPlayer: AudioPlayer,
) {
    fun pronounce(word: Word) = pronounce(word.text, word.audioUrl)

    fun pronounce(text: String, audioUrl: String?) {
        val url = audioUrl?.takeIf { it.isNotBlank() }
        if (url != null) audioPlayer.play(url) else speaker.speak(text)
    }
}
