package com.wordly.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import com.wordly.app.domain.SerbianScript
import java.util.Locale

/** Which voice a language will actually be spoken with on this device. */
enum class VoiceStatus { NATIVE, FALLBACK, MISSING }

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
     *   English falls back to en-US. Serbian is handed to the engine in Cyrillic (what Serbian
     *   voices are trained on), or, with no Serbian voice installed, to a Croatian one in Latin —
     *   near-identical pronunciation. Otherwise this no-ops rather than mispronounce the word in
     *   another language; [status] tells the UI so the silence isn't a mystery.
     */
    fun speak(text: String, languageTag: String) {
        val engine = tts ?: return
        if (!ready || text.isBlank()) return
        val (locale, spoken) = pick(engine, languageTag, text) ?: return
        engine.language = locale
        engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "wordly-tts")
    }

    /** `null` until the engine has started. */
    fun status(languageTag: String): VoiceStatus? {
        val engine = tts ?: return null
        if (!ready) return null
        val primary = Locale.forLanguageTag(languageTag)
        return when {
            available(engine, primary) -> VoiceStatus.NATIVE
            fallbackFor(languageTag)?.let { available(engine, it) } == true -> VoiceStatus.FALLBACK
            else -> VoiceStatus.MISSING
        }
    }

    private fun pick(engine: TextToSpeech, languageTag: String, text: String): Pair<Locale, String>? {
        val serbian = languageTag.startsWith("sr")
        val primary = Locale.forLanguageTag(languageTag)
        if (available(engine, primary)) return primary to if (serbian) SerbianScript.toCyrillic(text) else text
        val fallback = fallbackFor(languageTag) ?: return null
        if (!available(engine, fallback)) return null
        return fallback to if (serbian) SerbianScript.toLatin(text) else text
    }

    private fun fallbackFor(languageTag: String): Locale? = when {
        languageTag == "en-GB" -> Locale.US
        languageTag.startsWith("sr") -> Locale.forLanguageTag("hr-HR")
        else -> null
    }

    private fun available(engine: TextToSpeech, locale: Locale): Boolean =
        engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
