package com.wordly.app.translate

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.wordly.app.domain.LearningEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline English→Russian dictionary lookup, backed by a SQLite asset built from FreeDict's
 * eng-rus.tei (CC BY-SA 3.0, WikDict/DBnary/Wiktionary-sourced, https://freedict.org — see
 * scripts/build_freedict_db.py for how `app/src/main/assets/freedict_eng_rus.db` was built).
 * Unlike [MlKitTranslator]'s single NMT guess, this returns real dictionary senses (e.g. several
 * distinct Russian verbs for a phrasal verb like "look after"), which both MlKit and Wiktionary's
 * Translations table often lack for multi-word expressions.
 */
class FreeDictTranslator(private val context: Context) {

    private val db: SQLiteDatabase by lazy { openDatabase() }

    private fun openDatabase(): SQLiteDatabase {
        val dbFile = context.getDatabasePath(ASSET_NAME)
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(ASSET_NAME).use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    suspend fun translate(word: String): List<String> = withContext(Dispatchers.IO) {
        db.query(
            "translations",
            arrayOf("translations"),
            "word = ?",
            arrayOf(LearningEngine.normalize(word)),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).split("\t") else emptyList()
        }
    }

    private companion object {
        const val ASSET_NAME = "freedict_eng_rus.db"
    }
}
