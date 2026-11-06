package com.wordly.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleSheetsUrlTest {

    @Test
    fun `builds a csv export url with gid from a full edit url`() {
        val url = "https://docs.google.com/spreadsheets/d/19wR67jTIev6atmU6aqerKUIb5I1vnO7XuD14iUj-Hvg/edit?gid=0#gid=0"
        assertEquals(
            "https://docs.google.com/spreadsheets/d/19wR67jTIev6atmU6aqerKUIb5I1vnO7XuD14iUj-Hvg/export?format=csv&gid=0",
            GoogleSheetsUrl.toCsvExportUrl(url),
        )
    }

    @Test
    fun `builds a csv export url without gid when none is present`() {
        val url = "https://docs.google.com/spreadsheets/d/abc123/edit"
        assertEquals(
            "https://docs.google.com/spreadsheets/d/abc123/export?format=csv",
            GoogleSheetsUrl.toCsvExportUrl(url),
        )
    }

    @Test
    fun `returns null for a url that is not a Google Sheets link`() {
        assertNull(GoogleSheetsUrl.toCsvExportUrl("https://example.com/not-a-sheet"))
    }
}
