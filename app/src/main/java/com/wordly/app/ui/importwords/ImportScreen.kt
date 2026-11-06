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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
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
        ImportViewModel(container.repository, container.ankiReader, profileId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetUrl by remember { mutableStateOf("") }
    var enrichExisting by remember { mutableStateOf(true) }

    val pickApkg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importApkg(uri, context.contentResolver, enrichExisting)
    }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Из Anki или 2Books", style = MaterialTheme.typography.titleMedium)
            Text(
                "Файл .apkg — например, экспорт словаря из 2Books. Новые слова встанут в очередь «новых» " +
                    "с переводом, транскрипцией и примером из книги в заметке.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { enrichExisting = !enrichExisting },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = enrichExisting, onCheckedChange = { enrichExisting = it })
                Text(
                    "Дописать пример и транскрипцию к словам, которые уже есть (переводы не меняются)",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = { pickApkg.launch("*/*") },
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Выбрать файл .apkg") }

            HorizontalDivider()

            Text("Из таблицы", style = MaterialTheme.typography.titleMedium)
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
                    if (result.enriched > 0) {
                        Text(
                            "Дописан пример или транскрипция: ${result.enriched}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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
