package com.wordly.app.translate

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * On-device translation via ML Kit, for whichever source/target language pair a call asks for
 * (see [com.wordly.app.domain.model.Profile.translateLanguageCode]) — one client per (source,
 * target) pair, built lazily and kept for the process lifetime. Each language model is downloaded
 * once (over whatever network is available) and every translation after that runs locally on the
 * phone, so unlike a cloud translation API this has no per-request quota, API key, or billing to
 * worry about — see [com.wordly.app.data.WordRepository].
 */
class MlKitTranslator {

    private val clients = mutableMapOf<Pair<String, String>, Translator>()

    /** `null` if ML Kit has no model for [sourceLanguageCode] or [targetLanguageCode]. */
    private fun clientFor(sourceLanguageCode: String, targetLanguageCode: String): Translator? {
        val source = TranslateLanguage.fromLanguageTag(sourceLanguageCode) ?: return null
        val target = TranslateLanguage.fromLanguageTag(targetLanguageCode) ?: return null
        return clients.getOrPut(source to target) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build(),
            )
        }
    }

    suspend fun translate(sourceLanguageCode: String, targetLanguageCode: String, word: String): String? =
        runCatching {
            val client = clientFor(sourceLanguageCode, targetLanguageCode) ?: return@runCatching null
            client.downloadModelIfNeeded().await()
            client.translate(word).await()
        }.getOrNull()
}
