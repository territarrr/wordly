package com.wordly.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.wordly.app.data.entity.Progress
import com.wordly.app.data.entity.StudyDay
import com.wordly.app.data.entity.Word

/** Whole-database reads and the all-or-nothing replace used by backup/restore. */
@Dao
interface BackupDao {

    @Query("SELECT * FROM words ORDER BY profileId, orderIndex")
    suspend fun allWords(): List<Word>

    @Query("SELECT * FROM progress")
    suspend fun allProgress(): List<Progress>

    @Query("SELECT * FROM study_days ORDER BY profileId, epochDay")
    suspend fun allStudyDays(): List<StudyDay>

    @Query("DELETE FROM words")
    suspend fun deleteWords()

    @Query("DELETE FROM progress")
    suspend fun deleteProgress()

    @Query("DELETE FROM study_days")
    suspend fun deleteStudyDays()

    @Insert
    suspend fun insertWords(words: List<Word>)

    @Insert
    suspend fun insertProgress(progress: List<Progress>)

    @Insert
    suspend fun insertStudyDays(days: List<StudyDay>)

    /** One transaction: if anything in the backup fails to insert, the current data stays untouched. */
    @Transaction
    suspend fun replaceAll(words: List<Word>, progress: List<Progress>, days: List<StudyDay>) {
        deleteWords()
        deleteProgress()
        deleteStudyDays()
        insertWords(words)
        insertProgress(progress)
        insertStudyDays(days)
    }
}
