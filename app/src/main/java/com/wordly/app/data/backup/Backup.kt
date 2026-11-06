package com.wordly.app.data.backup

import com.wordly.app.data.entity.Progress
import com.wordly.app.data.entity.StudyDay
import com.wordly.app.data.entity.Word
import kotlinx.serialization.Serializable

/**
 * A full backup file: every word with all its fields (stats and spaced-repetition state
 * included), each profile's learned/new boundary and the per-day study log — everything a CSV
 * export loses. Plain JSON, versioned so a future format change can still read old files.
 */
@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val version: Int = VERSION,
    val exportedAt: Long,
    val words: List<BackupWord>,
    val progress: List<BackupProgress>,
    val studyDays: List<BackupStudyDay>,
) {
    companion object {
        const val APP_ID = "wordly"
        const val VERSION = 1
    }
}

@Serializable
data class BackupWord(
    val id: Long,
    val profileId: Long,
    val orderIndex: Long,
    val text: String,
    val normalized: String,
    val translation: String,
    val transcription: String? = null,
    val audioUrl: String? = null,
    val createdAt: Long,
    val isFavorite: Boolean = false,
    val note: String? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastReviewedAt: Long? = null,
    val srsBox: Int = 0,
    val dueAt: Long? = null,
)

@Serializable
data class BackupProgress(val profileId: Long, val benchmarkOrderIndex: Long)

@Serializable
data class BackupStudyDay(val profileId: Long, val epochDay: Long, val cards: Int)

fun Word.toBackup() = BackupWord(
    id, profileId, orderIndex, text, normalized, translation, transcription, audioUrl, createdAt,
    isFavorite, note, correctCount, wrongCount, lastReviewedAt, srsBox, dueAt,
)

fun BackupWord.toEntity() = Word(
    id = id, profileId = profileId, orderIndex = orderIndex, text = text, normalized = normalized,
    translation = translation, transcription = transcription, audioUrl = audioUrl, createdAt = createdAt,
    isFavorite = isFavorite, note = note, correctCount = correctCount, wrongCount = wrongCount,
    lastReviewedAt = lastReviewedAt, srsBox = srsBox, dueAt = dueAt,
)

fun Progress.toBackup() = BackupProgress(profileId, benchmarkOrderIndex)
fun BackupProgress.toEntity() = Progress(profileId, benchmarkOrderIndex)
fun StudyDay.toBackup() = BackupStudyDay(profileId, epochDay, cards)
fun BackupStudyDay.toEntity() = StudyDay(profileId, epochDay, cards)
