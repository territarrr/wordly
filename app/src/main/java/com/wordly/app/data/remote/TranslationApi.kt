package com.wordly.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** MyMemory Translation API — https://mymemory.translated.net, no key required. */
interface TranslationApi {

    @GET("get")
    suspend fun translate(
        @Query("q") text: String,
        @Query("langpair") langPair: String = "en|ru",
    ): MyMemoryResponseDto
}

@Serializable
data class MyMemoryResponseDto(
    val responseData: MyMemoryTranslationDto? = null,
    val matches: List<MyMemoryMatchDto> = emptyList(),
)

@Serializable
data class MyMemoryTranslationDto(val translatedText: String? = null)

@Serializable
data class MyMemoryMatchDto(val translation: String? = null)
