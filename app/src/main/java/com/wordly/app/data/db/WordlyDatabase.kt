package com.wordly.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wordly.app.data.entity.Progress
import com.wordly.app.data.entity.Word

@Database(
    entities = [Word::class, Progress::class],
    version = 1,
    exportSchema = false,
)
abstract class WordlyDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun progressDao(): ProgressDao
}
