package com.wordly.app.domain.model

/**
 * The two fixed language profiles Wordly supports. Not a DB table — there is no profile
 * CRUD, just a compile-time choice of which word list/benchmark [Word.profileId] points at.
 */
enum class Profile(
    val id: Long,
    val displayName: String,
    val ttsLanguageTag: String,
    val translateLanguageCode: String,
    val hasTranscriptionLookup: Boolean,
) {
    ENGLISH(
        id = 1L,
        displayName = "Английский",
        ttsLanguageTag = "en-GB",
        translateLanguageCode = "en",
        hasTranscriptionLookup = true,
    ),
    SERBIAN(
        id = 2L,
        displayName = "Сербский",
        ttsLanguageTag = "sr-RS",
        translateLanguageCode = "sr",
        hasTranscriptionLookup = false,
    ),
    ;

    companion object {
        fun fromId(id: Long): Profile = entries.first { it.id == id }
    }
}
