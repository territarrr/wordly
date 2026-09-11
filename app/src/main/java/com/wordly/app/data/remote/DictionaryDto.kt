package com.wordly.app.data.remote

import kotlinx.serialization.Serializable

/** Subset of the api.dictionaryapi.dev response we use (top level is a JSON array). */
@Serializable
data class DictionaryEntryDto(
    val word: String = "",
    val phonetic: String? = null,
    val phonetics: List<PhoneticDto> = emptyList(),
)

@Serializable
data class PhoneticDto(
    val text: String? = null,
    val audio: String? = null,
)
