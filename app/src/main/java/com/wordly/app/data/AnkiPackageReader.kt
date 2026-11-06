package com.wordly.app.data

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.wordly.app.domain.AnkiImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Reads the notes out of an Anki package (.apkg): a zip holding the collection as an SQLite file
 * ("collection.anki21", or "collection.anki2" in older/compatible exports like 2Books'). Only that
 * one entry is extracted, to a fixed name in the cache dir, and deleted afterwards.
 */
class AnkiPackageReader(private val context: Context) {

    suspend fun read(uri: Uri, resolver: ContentResolver): List<AnkiImport.Note> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apkg-import").apply { deleteRecursively(); mkdirs() }
        try {
            val collection = extractCollection(uri, resolver, dir)
            SQLiteDatabase.openDatabase(collection.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val fieldNames = readFieldNames(db)
                db.rawQuery("SELECT mid, flds FROM notes ORDER BY id", null).use { c ->
                    buildList {
                        while (c.moveToNext()) {
                            add(AnkiImport.Note(fieldNames[c.getLong(0)].orEmpty(), c.getString(1).split('\u001F')))
                        }
                    }
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun extractCollection(uri: Uri, resolver: ContentResolver, dir: File): File {
        val found = mutableMapOf<String, File>()
        val input = resolver.openInputStream(uri) ?: error("Не удалось открыть файл")
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name in COLLECTION_NAMES) {
                    // Fixed target name — entry paths from the archive are never used as file paths.
                    val target = File(dir, entry.name)
                    target.outputStream().use { zip.copyTo(it) }
                    found[entry.name] = target
                }
            }
        }
        return found["collection.anki21"] ?: found["collection.anki2"]
            ?: if ("collection.anki21b" in found) {
                error("Этот файл в новом формате Anki. Экспортируйте колоду с галочкой «Поддержка старых версий Anki»")
            } else {
                error("Это не похоже на файл Anki (.apkg)")
            }
    }

    /** Note type id → its field names in order; old collections keep them as JSON in `col.models`, new ones in `fields`. */
    private fun readFieldNames(db: SQLiteDatabase): Map<Long, List<String>> {
        val fromJson = runCatching {
            db.rawQuery("SELECT models FROM col", null).use { c ->
                if (!c.moveToFirst()) return@use emptyMap()
                val models = JSONObject(c.getString(0))
                models.keys().asSequence().associate { id ->
                    val flds = models.getJSONObject(id).getJSONArray("flds")
                    id.toLong() to (0 until flds.length()).map { flds.getJSONObject(it) }
                        .sortedBy { it.getInt("ord") }
                        .map { it.getString("name") }
                }
            }
        }.getOrDefault(emptyMap())
        if (fromJson.isNotEmpty()) return fromJson
        return runCatching {
            db.rawQuery("SELECT ntid, name FROM fields ORDER BY ntid, ord", null).use { c ->
                buildMap<Long, MutableList<String>> {
                    while (c.moveToNext()) getOrPut(c.getLong(0)) { mutableListOf() } += c.getString(1)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        val COLLECTION_NAMES = setOf("collection.anki21", "collection.anki2", "collection.anki21b")
    }
}
