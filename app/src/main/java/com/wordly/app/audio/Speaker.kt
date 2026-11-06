package com.wordly.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Thin wrapper over the platform [TextToSpeech] engine — the language is picked per [speak] call. */
class Speaker(context: Context) {

    @Volatile private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }

    /**
     * @param languageTag e.g. `"en-GB"`/`"sr-RS"` — [Profile.ttsLanguageTag][com.wordly.app.domain.model.Profile.ttsLanguageTag].
     *   Silently no-ops if the device has no matching voice, rather than falling back to a
     *   different language and mispronouncing the word.
     */
    fun speak(text: String, languageTag: String) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) return
        var result = engine.setLanguage(Locale.forLanguageTag(languageTag))
        if ((result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) &&
            languageTag == "en-GB"
        ) {
            result = engine.setLanguage(Locale.US)
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordly-tts")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
