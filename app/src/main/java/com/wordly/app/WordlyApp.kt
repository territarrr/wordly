package com.wordly.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.wordly.app.audio.AudioPlayer
import com.wordly.app.audio.Pronouncer
import com.wordly.app.audio.Speaker
import com.wordly.app.data.AnkiPackageReader
import com.wordly.app.data.AppSettings
import com.wordly.app.data.ProfilePreferences
import com.wordly.app.data.WordRepository
import com.wordly.app.data.db.MIGRATION_1_2
import com.wordly.app.data.db.MIGRATION_2_3
import com.wordly.app.data.db.MIGRATION_3_4
import com.wordly.app.data.db.WordlyDatabase
import com.wordly.app.data.remote.DictionaryApi
import com.wordly.app.data.remote.GoogleSheetsApi
import com.wordly.app.data.remote.TranslationApi
import com.wordly.app.data.remote.WiktionaryApi
import com.wordly.app.notify.Notifier
import com.wordly.app.notify.ReminderScheduler
import com.wordly.app.translate.FreeDictTranslator
import com.wordly.app.translate.MlKitTranslator
import com.wordly.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class WordlyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannel()
        // The reminder is on by default, so it must be scheduled without a visit to Settings.
        ReminderScheduler.apply(this, container.settings.reminder.value, replace = false)
    }
}

/** Hand-rolled dependency container (no DI framework). Lives for the process lifetime. */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val json = Json { ignoreUnknownKeys = true }

    private val database: WordlyDatabase = Room
        .databaseBuilder(context, WordlyDatabase::class.java, "wordly.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    private val dictionaryApi: DictionaryApi = Retrofit.Builder()
        .baseUrl("https://api.dictionaryapi.dev/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(DictionaryApi::class.java)

    private val wiktionaryApi: WiktionaryApi = Retrofit.Builder()
        .baseUrl("https://en.wiktionary.org/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(WiktionaryApi::class.java)

    /** Russian Wiktionary — same MediaWiki API shape as [wiktionaryApi]; used for Serbian → Russian. */
    private val ruWiktionaryApi: WiktionaryApi = Retrofit.Builder()
        .baseUrl("https://ru.wiktionary.org/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(WiktionaryApi::class.java)

    private val googleSheetsApi: GoogleSheetsApi = Retrofit.Builder()
        .baseUrl("https://docs.google.com/") // unused: GoogleSheetsApi.fetchCsv takes an absolute @Url
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GoogleSheetsApi::class.java)

    private val translationApi: TranslationApi = Retrofit.Builder()
        .baseUrl("https://api.mymemory.translated.net/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TranslationApi::class.java)

    private val mlKitTranslator = MlKitTranslator()
    private val freeDictTranslator = FreeDictTranslator(context)

    val repository = WordRepository(
        database.wordDao(),
        database.progressDao(),
        database.studyDayDao(),
        database.backupDao(),
        dictionaryApi,
        wiktionaryApi,
        ruWiktionaryApi,
        mlKitTranslator,
        freeDictTranslator,
        translationApi,
        googleSheetsApi,
    )

    private val speaker = Speaker(context)
    private val audioPlayer = AudioPlayer()
    val pronouncer = Pronouncer(speaker, audioPlayer)

    val notifier = Notifier(context)

    val profilePreferences = ProfilePreferences(context)

    val settings = AppSettings(context)

    val ankiReader = AnkiPackageReader(context)
}
