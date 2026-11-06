package com.wordly.app.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.data.ThemeMode
import com.wordly.app.audio.VoiceStatus
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wordly.app.domain.model.Profile
import com.wordly.app.ui.common.container

private val SIZES = listOf(10, 20, 50, 100)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.profilePreferences, container.settings, container.appContext, container.repository, container.pronouncer)
    },
) {
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val defaultProfile by viewModel.defaultProfile.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val defaultBatch by viewModel.defaultBatch.collectAsStateWithLifecycle()
    val dailyGoal by viewModel.dailyGoal.collectAsStateWithLifecycle()
    val autoPronounce by viewModel.autoPronounce.collectAsStateWithLifecycle()
    val reminder by viewModel.reminder.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri, context.contentResolver)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.pickRestore(uri, context.contentResolver)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    pendingRestore?.let { backup ->
        val date = remember(backup) {
            java.text.SimpleDateFormat("d MMMM yyyy, HH:mm", java.util.Locale("ru")).format(java.util.Date(backup.exportedAt))
        }
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Восстановить из копии?") },
            text = {
                Text(
                    "Копия от $date: слов ${backup.words.size}. Весь текущий словарь, прогресс и статистика " +
                        "будут заменены содержимым копии.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Отмена") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Текущий язык", "Что показывать прямо сейчас. Также переключается прямо на главном экране.") {
                Profile.entries.forEach { profile ->
                    FilterChip(
                        selected = activeProfile == profile,
                        onClick = { viewModel.selectActiveProfile(profile) },
                        label = { Text(profile.displayName) },
                    )
                }
            }
            Section("Язык по умолчанию", "Этот язык будет выбран при следующем открытии приложения.") {
                Profile.entries.forEach { profile ->
                    FilterChip(
                        selected = defaultProfile == profile,
                        onClick = { viewModel.selectDefaultProfile(profile) },
                        label = { Text(profile.displayName) },
                    )
                }
            }
            Section("Тема") {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = themeMode == mode, onClick = { viewModel.setThemeMode(mode) }, label = { Text(mode.label) })
                }
            }
            Section("Размер пачки новых слов", "Сколько слов предлагать по умолчанию в «Учить новые».") {
                SIZES.forEach { n ->
                    FilterChip(selected = defaultBatch == n, onClick = { viewModel.setDefaultBatch(n) }, label = { Text("$n") })
                }
            }
            Section("Цель на день", "Сколько карточек в день считать выполненной нормой.") {
                SIZES.forEach { n ->
                    FilterChip(selected = dailyGoal == n, onClick = { viewModel.setDailyGoal(n) }, label = { Text("$n") })
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SwitchRow(
                title = "Автоозвучка",
                subtitle = "Произносить слово, как только оно появляется на карточке",
                checked = autoPronounce,
                onChange = viewModel::setAutoPronounce,
            )
            SwitchRow(
                title = "Ежедневное напоминание",
                subtitle = if (reminder.enabled) "Каждый день в ${reminder.timeLabel}: новые слова и текущая серия" else "Выключено",
                checked = reminder.enabled,
                onChange = { viewModel.setReminder(reminder.copy(enabled = it)) },
            )
            if (reminder.enabled) {
                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> viewModel.setReminder(reminder.copy(hour = hour, minute = minute)) },
                            reminder.hour,
                            reminder.minute,
                            true,
                        ).show()
                    },
                ) { Text("Изменить время (${reminder.timeLabel})") }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SerbianVoiceSection(viewModel)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Резервная копия", style = MaterialTheme.typography.titleMedium)
            Text(
                "Все слова обоих языков, что выучено, расписание повторений и статистика — в один файл. " +
                    "Пригодится при смене телефона или перед экспериментами.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    saveBackup.launch("wordly-backup-${java.time.LocalDate.now()}.json")
                }) { Text("Сохранить копию") }
                OutlinedButton(onClick = { openBackup.launch("*/*") }) { Text("Восстановить") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String, subtitle: String? = null, chips: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Whether Serbian words without a dictionary recording can be spoken at all: the platform TTS
 * needs a Serbian (or, as a stand-in, Croatian) voice, which many phones don't have installed.
 */
@Composable
private fun SerbianVoiceSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<VoiceStatus?>(null) }
    // TTS starts asynchronously; re-check after returning from the voice installer too.
    LifecycleResumeEffect(Unit) {
        status = null
        onPauseOrDispose {}
    }
    LaunchedEffect(status) {
        while (status == null) {
            status = viewModel.serbianVoice()
            if (status == null) kotlinx.coroutines.delay(300)
        }
    }

    Text("Озвучка сербского", style = MaterialTheme.typography.titleMedium)
    Text(
        when (status) {
            null -> "Проверяю голоса синтеза речи…"
            VoiceStatus.NATIVE -> "Сербский голос установлен ✓"
            VoiceStatus.FALLBACK -> "Сербского голоса нет — слова читает хорватский (звучит почти так же). Можно установить сербский."
            VoiceStatus.MISSING -> "Голоса для сербского нет — кнопка 🔊 у сербских слов молчит. Установите голос «Српски» в синтезе речи."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (status == VoiceStatus.NATIVE || status == VoiceStatus.FALLBACK) {
            OutlinedButton(onClick = viewModel::testSerbianVoice) { Text("Проверить: hvala") }
        }
        if (status == VoiceStatus.FALLBACK || status == VoiceStatus.MISSING) {
            OutlinedButton(onClick = {
                val install = android.content.Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(install) }.onFailure {
                    runCatching { context.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }) { Text("Установить голос") }
        }
    }
}
