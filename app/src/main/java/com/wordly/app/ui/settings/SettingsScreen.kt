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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.data.ThemeMode
import com.wordly.app.domain.model.Profile
import com.wordly.app.ui.common.container

private val SIZES = listOf(10, 20, 50, 100)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(container.profilePreferences, container.settings, container.appContext)
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

    Scaffold(
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
