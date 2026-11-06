package com.wordly.app.data.entity

import androidx.room.Entity

/**
 * How many cards were answered on one calendar day ([epochDay], local time) in a profile —
 * feeds the streak and the daily goal on Home.
 */
@Entity(tableName = "study_days", primaryKeys = ["profileId", "epochDay"])
data class StudyDay(
    val profileId: Long,
    val epochDay: Long,
    val cards: Int,
)
