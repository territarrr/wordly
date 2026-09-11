package com.wordly.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.wordly.app.audio.AudioPlayer
import com.wordly.app.audio.Pronouncer
import com.wordly.app.audio.Speaker
import com.wordly.app.data.WordRepository
import com.wordly.app.data.db.WordlyDatabase
import com.wordly.app.data.remote.DictionaryApi
import com.wordly.app.notify.Notifier
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
    }
}

/** Hand-rolled dependency container (no DI framework). Lives for the process lifetime. */
class AppContainer(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val database: WordlyDatabase = Room
        .databaseBuilder(context, WordlyDatabase::class.java, "wordly.db")
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

    val repository = WordRepository(database.wordDao(), database.progressDao(), dictionaryApi)

    private val speaker = Speaker(context)
    private val audioPlayer = AudioPlayer()
    val pronouncer = Pronouncer(speaker, audioPlayer)

    val notifier = Notifier(context)
}
