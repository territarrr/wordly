package com.wordly.app.ui.recheck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.ui.common.container

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecheckScreen(
    profileId: Long,
    onDone: () -> Unit,
    viewModel: RecheckViewModel = viewModel(key = "recheck-$profileId") {
        RecheckViewModel(container.repository, profileId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.appliedCount) { if (state.appliedCount != null) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Перепроверить переводы") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Button(
                onClick = viewModel::apply,
                enabled = state.changeCount > 0 && !state.applying,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(52.dp),
            ) { Text(if (state.changeCount > 0) "Применить изменения (${state.changeCount})" else "Нечего применять") }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.running) {
                        LinearProgressIndicator(
                            progress = { if (state.total == 0) 0f else state.checked.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Проверено ${state.checked} из ${state.total}…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            if (state.items.isEmpty()) "Всё в порядке — словарь согласен с сохранёнными переводами" else "Проверено ${state.total} слов",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        "Текущий перевод остаётся, пока не выберете вариант. Ударение добавляется, если галочка стоит.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.items, key = { it.word.id }) { item ->
                RecheckCard(
                    item = item,
                    onChoose = { viewModel.choose(item.word.id, it) },
                    onToggleStress = { viewModel.toggleStress(item.word.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecheckCard(item: RecheckItem, onChoose: (String?) -> Unit, onToggleStress: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.word.text, style = MaterialTheme.typography.titleLarge)
            Text(
                "Сейчас: ${item.word.translation}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.options.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = item.choice == null, onClick = { onChoose(null) }, label = { Text("Оставить") })
                    item.options.forEach { option ->
                        FilterChip(selected = item.choice == option, onClick = { onChoose(option) }, label = { Text(option) })
                    }
                }
            }
            item.stressed?.let { stressed ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleStress),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = item.applyStress, onCheckedChange = { onToggleStress() })
                    Text("Ударение: $stressed", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
