package com.wordly.app.domain

/**
 * Turns a Google Sheets share URL (e.g. `.../spreadsheets/d/ID/edit?gid=123#gid=123`) into its
 * CSV export URL. Only works over a plain unauthenticated request if the sheet is shared as
 * "Anyone with the link" — Google redirects to a login page otherwise.
 */
object GoogleSheetsUrl {

    private val SPREADSHEET_ID = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)")
    private val GID = Regex("[#&?]gid=(\\d+)")

    /** `null` if [input] doesn't look like a Google Sheets URL. */
    fun toCsvExportUrl(input: String): String? {
        val id = SPREADSHEET_ID.find(input)?.groupValues?.get(1) ?: return null
        val gid = GID.find(input)?.groupValues?.get(1)
        val base = "https://docs.google.com/spreadsheets/d/$id/export?format=csv"
        return if (gid != null) "$base&gid=$gid" else base
    }
}
