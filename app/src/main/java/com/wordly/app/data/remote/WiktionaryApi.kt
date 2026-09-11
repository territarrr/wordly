package com.wordly.app.data.remote

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * English Wiktionary's MediaWiki API — https://en.wiktionary.org, no key required.
 *
 * Backed by Wikimedia's infrastructure, so it's a far more reliably-available source of IPA
 * transcription (and, as a bonus, real pronunciation audio) than api.dictionaryapi.dev, a much
 * smaller project. A page title mixes every language that uses that spelling (e.g. "chat" is
 * both English and French), so a lookup is two calls: first the section outline, to find the
 * "Pronunciation" subsection nested specifically under the top-level "English" section (see
 * [WiktionaryParsing.findEnglishPronunciationSectionIndex]), then just that section's rendered
 * HTML, from which the transcription/audio are extracted.
 */
interface WiktionaryApi {

    @Headers("User-Agent: WordlyApp/1.0 (Android vocabulary learning app)")
    @GET("w/api.php?action=parse&prop=sections&format=json&formatversion=2&redirects=1")
    suspend fun sections(@Query("page") page: String): WiktionarySectionsResponseDto

    @Headers("User-Agent: WordlyApp/1.0 (Android vocabulary learning app)")
    @GET("w/api.php?action=parse&prop=text&format=json&formatversion=2&redirects=1")
    suspend fun sectionHtml(
        @Query("page") page: String,
        @Query("section") section: String,
    ): WiktionaryTextResponseDto
}

@Serializable
data class WiktionarySectionsResponseDto(val parse: WiktionarySectionsParseDto? = null)

@Serializable
data class WiktionarySectionsParseDto(val sections: List<WiktionarySectionDto> = emptyList())

@Serializable
data class WiktionarySectionDto(
    val toclevel: Int = 0,
    val line: String = "",
    val index: String = "",
)

@Serializable
data class WiktionaryTextResponseDto(val parse: WiktionaryTextParseDto? = null)

@Serializable
data class WiktionaryTextParseDto(val text: String = "")
