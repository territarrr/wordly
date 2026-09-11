package com.wordly.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.domain.model.Direction
import com.wordly.app.domain.model.SessionConfig
import com.wordly.app.ui.common.container

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenWords: () -> Unit,
    onStartSession: (SessionConfig) -> Unit,
    viewModel: HomeViewModel = viewModel { HomeViewModel(container.repository) },
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    var direction by remember { mutableStateOf(Direction.EN_TO_RU) }
    var shuffle by remember { mutableStateOf(false) }
    var windowChoice by remember { mutableStateOf<Int?>(null) } // null = all learned
    var windowFromStart by remember { mutableStateOf(false) } // ignored when windowChoice == null
    var windowCustom by remember { mutableStateOf(false) }
    var windowCustomText by remember { mutableStateOf("") }
    var batchChoice by remember { mutableStateOf(20) }
    var batchCustom by remember { mutableStateOf(false) }
    var batchCustomText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wordly") },
                actions = {
                    IconButton(onClick = onOpenWords) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Мои слова")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            StatsCard(stats)

            SectionTitle("Направление карточек")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = direction == Direction.EN_TO_RU,
                    onClick = { direction = Direction.EN_TO_RU },
                    label = { Text("EN → RU") },
                )
                FilterChip(
                    selected = direction == Direction.RU_TO_EN,
                    onClick = { direction = Direction.RU_TO_EN },
                    label = { Text("RU → EN") },
                )
            }

            SectionTitle("Повторить выученное")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !shuffle,
                    onClick = { shuffle = false },
                    label = { Text("По порядку") },
                )
                FilterChip(
                    selected = shuffle,
                    onClick = { shuffle = true },
                    label = { Text("Перемешать") },
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WindowChip("Все", windowChoice == null && !windowCustom) {
                    windowChoice = null; windowCustom = false
                }
                listOf(20, 50, 100).forEach { n ->
                    WindowChip("Первые $n", windowChoice == n && windowFromStart && !windowCustom) {
                        windowChoice = n; windowFromStart = true; windowCustom = false
                    }
                    WindowChip("Последние $n", windowChoice == n && !windowFromStart && !windowCustom) {
                        windowChoice = n; windowFromStart = false; windowCustom = false
                    }
                }
                WindowChip("Своё", windowCustom) { windowCustom = true }
            }
            if (windowCustom) {
                NumberField("Сколько слов", windowCustomText) { windowCustomText = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindowChip("С начала", windowFromStart) { windowFromStart = true }
                    WindowChip("С конца (последние)", !windowFromStart) { windowFromStart = false }
                }
            }
            Button(
                onClick = {
                    val window = when {
                        windowCustom -> windowCustomText.toIntOrNull()?.takeIf { it > 0 }
                        else -> windowChoice
                    }
                    onStartSession(
                        SessionConfig.Review(
                            direction = direction,
                            shuffle = shuffle,
                            window = window,
                            fromStart = windowFromStart,
                            seed = System.currentTimeMillis(),
                        ),
                    )
                },
                enabled = stats.learned > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Начать повторение (${stats.learned})") }

            SectionTitle("Учить новые слова")
            Text(
                "Новых слов после бенчмарка: ${stats.newCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30).forEach { n ->
                    WindowChip("$n", batchChoice == n && !batchCustom) {
                        batchChoice = n; batchCustom = false
                    }
                }
                WindowChip("Своё", batchCustom) { batchCustom = true }
            }
            if (batchCustom) {
                NumberField("Размер пачки", batchCustomText) { batchCustomText = it }
            }
            Button(
                onClick = {
                    val size = when {
                        batchCustom -> batchCustomText.toIntOrNull()?.takeIf { it > 0 } ?: 20
                        else -> batchChoice
                    }
                    onStartSession(SessionConfig.NewWords(direction = direction, batchSize = size))
                },
                enabled = stats.newCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Начать изучение") }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatsCard(stats: HomeStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatItem(stats.learned.toString(), "выучено")
            StatItem(stats.newCount.toString(), "новых")
            StatItem(stats.total.toString(), "всего")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
