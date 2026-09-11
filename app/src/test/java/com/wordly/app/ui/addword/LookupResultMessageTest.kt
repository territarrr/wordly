package com.wordly.app.ui.addword

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test: a real dictionary-API response can have audio without an IPA transcription
 * (or vice versa) for a given word — the message must never claim a part loaded when it didn't.
 */
class LookupResultMessageTest {

    @Test
    fun `all three found`() {
        assertEquals(
            "Загружено: перевод, транскрипция, озвучка",
            lookupResultMessage(gotTranslations = true, gotTranscription = true, gotAudio = true),
        )
    }

    @Test
    fun `audio found without transcription is not reported as transcription loaded`() {
        assertEquals(
            "Загружено: перевод, озвучка. Не найдено: транскрипция",
            lookupResultMessage(gotTranslations = true, gotTranscription = false, gotAudio = true),
        )
    }

    @Test
    fun `transcription found without audio`() {
        assertEquals(
            "Загружено: транскрипция. Не найдено: перевод, озвучка",
            lookupResultMessage(gotTranslations = false, gotTranscription = true, gotAudio = false),
        )
    }

    @Test
    fun `nothing found`() {
        assertEquals(
            "Не удалось получить данные — введите вручную",
            lookupResultMessage(gotTranslations = false, gotTranscription = false, gotAudio = false),
        )
    }
}
