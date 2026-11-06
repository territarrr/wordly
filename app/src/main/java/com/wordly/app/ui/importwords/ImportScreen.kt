package com.wordly.app.ui.importwords

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.ui.common.container

/**
 * Bulk-adds words either from a CSV file (word, transcription, translation columns — e.g. one
 * exported from a Google Sheet via File > Download > Comma-separated values) or straight from a
 * pasted Google Sheets link, provided that sheet is shared as "Anyone with the link".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    profileId: Long,
    onDone: () -> Unit,
    viewModel: ImportViewModel = viewModel(key = "import-$profileId") {
        ImportViewModel(container.repository, profileId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetUrl by remember { mutableStateOf("") }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text != null) viewModel.importCsv(text) else viewModel.reportReadError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Импорт слов") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Колонки (в этом порядке): слово, транскрипция, перевод. Строку-заголовок " +
                    "(\"Слово\"/\"Word\") можно оставить — распознаётся и пропускается сама.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = sheetUrl,
                onValueChange = { sheetUrl = it },
                label = { Text("Ссылка на Google Таблицу") },
                singleLine = true,
                supportingText = { Text("Таблица должна быть доступна \"всем, у кого есть ссылка\"") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.importFromUrl(sheetUrl) },
                enabled = !state.importing && sheetUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Импортировать по ссылке") }

            HorizontalDivider()

            Text("Или выберите CSV-файл, скачанный на устройство:", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { pickFile.launch("*/*") },
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Выбрать файл") }

            if (state.importing) {
                CircularProgressIndicator()
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            state.result?.let { result ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Добавлено новых слов: ${result.imported}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Пропущено (уже есть в словаре): ${result.duplicates}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (result.missingWord > 0) {
                        Text(
                            "Пропущено (не указано слово): ${result.missingWord}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (result.missingTranslation > 0) {
                        Text(
                            "Пропущено (не указан перевод): ${result.missingTranslation}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
            }
        }
    }
}
