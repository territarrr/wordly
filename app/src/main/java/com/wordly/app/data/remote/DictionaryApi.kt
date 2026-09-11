package com.wordly.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

/** Free Dictionary API — https://dictionaryapi.dev, no key required. */
interface DictionaryApi {

    /** Throws `retrofit2.HttpException` (404) when the word is unknown. */
    @GET("api/v2/entries/en/{word}")
    suspend fun lookup(@Path("word") word: String): List<DictionaryEntryDto>
}

/** Result of a dictionary lookup used to pre-fill the add-word form. */
data class WordLookup(
    val transcription: String?,
    val audioUrl: String?,
)
