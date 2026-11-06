package com.wordly.app.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Fetches a Google Sheets CSV export at an absolute URL built by [com.wordly.app.domain.GoogleSheetsUrl]
 * — a plain unauthenticated GET, so it only succeeds when the sheet is shared as "Anyone with the
 * link". No key needed; `@Url` overrides the Retrofit instance's (otherwise-unused) base URL.
 */
interface GoogleSheetsApi {
    @GET
    suspend fun fetchCsv(@Url url: String): Response<ResponseBody>
}
