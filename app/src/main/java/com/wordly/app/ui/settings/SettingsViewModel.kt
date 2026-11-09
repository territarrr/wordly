package com.wordly.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.wordly.app.data.AppSettings
import com.wordly.app.data.ProfilePreferences
import com.wordly.app.data.ThemeMode
import com.wordly.app.domain.model.Profile
import com.wordly.app.notify.ReminderScheduler
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.wordly.app.data.WordRepository
import com.wordly.app.audio.Pronouncer
import com.wordly.app.audio.VoiceStatus
import com.wordly.app.data.backup.BackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val profilePreferences: ProfilePreferences,
    private val settings: AppSettings,
    private val appContext: Context,
    private val repository: WordRepository,
    private val pronouncer: Pronouncer,
) : ViewModel() {

    /** Serbian TTS voice on this device — polled by the screen until the engine has started. */
    fun serbianVoice(): VoiceStatus? = pronouncer.voiceStatus(Profile.SERBIAN.ttsLanguageTag)

    fun testSerbianVoice() = pronouncer.pronounce("hvala", null, Profile.SERBIAN.ttsLanguageTag)

    /** A picked backup, parsed and waiting for "replace everything?" confirmation. */
    private val _pendingRestore = MutableStateFlow<BackupFile?>(null)
    val pendingRestore: StateFlow<BackupFile?> = _pendingRestore.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() { _message.value = null }

    fun exportBackup(uri: Uri, resolver: ContentResolver) = viewModelScope.launch(Dispatchers.IO) {
        _message.value = runCatching {
            val json = repository.exportBackup()
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("no output stream")
            "Резервная копия сохранена"
        }.getOrElse { "Не удалось сохранить копию" }
    }

    fun pickRestore(uri: Uri, resolver: ContentResolver) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val json = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Не удалось прочитать файл")
            repository.readBackup(json)
        }.fold(
            onSuccess = { _pendingRestore.value = it },
            onFailure = { _message.value = it.message ?: "Не удалось прочитать копию" },
        )
    }

    fun cancelRestore() { _pendingRestore.value = null }

    fun confirmRestore() = viewModelScope.launch {
        val file = _pendingRestore.value ?: return@launch
        _pendingRestore.value = null
        _message.value = runCatching {
            repository.restoreBackup(file)
            "Восстановлено слов: ${file.words.size}"
        }.getOrElse { "Не удалось восстановить — текущий словарь не изменён" }
    }

    val activeProfile: StateFlow<Profile> = profilePreferences.activeProfile
    val defaultProfile: StateFlow<Profile> = profilePreferences.defaultProfile
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
    val defaultBatch: StateFlow<Int> = settings.defaultBatch
    val dailyGoal: StateFlow<Int> = settings.dailyGoal
    val dailyReview: StateFlow<Int> = settings.dailyReview
    val autoPronounce: StateFlow<Boolean> = settings.autoPronounce
    val reminder: StateFlow<AppSettings.Reminder> = settings.reminder

    fun selectActiveProfile(profile: Profile) = profilePreferences.setActiveProfile(profile)
    fun selectDefaultProfile(profile: Profile) = profilePreferences.setDefaultProfile(profile)
    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDefaultBatch(size: Int) = settings.setDefaultBatch(size)
    fun setDailyGoal(goal: Int) = settings.setDailyGoal(goal)
    fun setDailyReview(count: Int) = settings.setDailyReview(count)
    fun setAutoPronounce(enabled: Boolean) = settings.setAutoPronounce(enabled)

    fun setReminder(reminder: AppSettings.Reminder) {
        settings.setReminder(reminder)
        ReminderScheduler.apply(appContext, reminder)
    }
}
