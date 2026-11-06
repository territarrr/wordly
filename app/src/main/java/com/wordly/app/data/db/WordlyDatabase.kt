package com.wordly.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wordly.app.data.entity.Progress
import com.wordly.app.data.entity.StudyDay
import com.wordly.app.data.entity.Word

@Database(
    entities = [Word::class, Progress::class, StudyDay::class],
    version = 4,
    exportSchema = true,
)
abstract class WordlyDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun progressDao(): ProgressDao
    abstract fun studyDayDao(): StudyDayDao
}

/** Adds [Word.isFavorite], defaulting existing rows to not-favorited. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Splits the single word list into per-profile lists (see [com.wordly.app.domain.model.Profile])
 * — existing rows become the English profile's (id 1) data, Serbian starts empty.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN profileId INTEGER NOT NULL DEFAULT 1")
        db.execSQL("DROP INDEX IF EXISTS index_words_normalized")
        db.execSQL("CREATE UNIQUE INDEX index_words_profileId_normalized ON words(profileId, normalized)")

        db.execSQL("CREATE TABLE progress_new (profileId INTEGER NOT NULL PRIMARY KEY, benchmarkOrderIndex INTEGER NOT NULL)")
        db.execSQL("INSERT INTO progress_new (profileId, benchmarkOrderIndex) SELECT 1, benchmarkOrderIndex FROM progress WHERE id = 0")
        db.execSQL("DROP TABLE progress")
        db.execSQL("ALTER TABLE progress_new RENAME TO progress")
    }
}

/** Adds per-word answer stats, spaced-repetition state and a note, plus the per-day study log. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE words ADD COLUMN note TEXT")
        db.execSQL("ALTER TABLE words ADD COLUMN correctCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE words ADD COLUMN wrongCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE words ADD COLUMN lastReviewedAt INTEGER")
        db.execSQL("ALTER TABLE words ADD COLUMN srsBox INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE words ADD COLUMN dueAt INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS study_days (profileId INTEGER NOT NULL, epochDay INTEGER NOT NULL, " +
                "cards INTEGER NOT NULL, PRIMARY KEY(profileId, epochDay))",
        )
    }
}
