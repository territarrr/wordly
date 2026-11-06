package com.wordly.app.data.backup

import com.wordly.app.data.entity.Progress
import com.wordly.app.data.entity.StudyDay
import com.wordly.app.data.entity.Word
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupFileTest {

    @Test
    fun `every word field, progress and study log survive a round trip`() {
        val word = Word(
            id = 7, profileId = 2, orderIndex = 3, text = "hvala", normalized = "hvala", translation = "спасибо",
            transcription = "hvála", audioUrl = null, createdAt = 123, isFavorite = true, note = "Hvala lepo!",
            correctCount = 4, wrongCount = 1, lastReviewedAt = 456, srsBox = 3, dueAt = 789,
        )
        val file = BackupFile(
            exportedAt = 1000,
            words = listOf(word.toBackup()),
            progress = listOf(Progress(2, 3).toBackup()),
            studyDays = listOf(StudyDay(2, 20000, 15).toBackup()),
        )
        val json = Json { ignoreUnknownKeys = true }
        val back = json.decodeFromString(BackupFile.serializer(), json.encodeToString(BackupFile.serializer(), file))
        assertEquals(word, back.words.single().toEntity())
        assertEquals(Progress(2, 3), back.progress.single().toEntity())
        assertEquals(StudyDay(2, 20000, 15), back.studyDays.single().toEntity())
        assertEquals("wordly", back.app)
    }
}
