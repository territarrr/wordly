package com.wordly.app.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.data.entity.Word
import com.wordly.app.ui.common.container

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListScreen(
    onBack: () -> Unit,
    onAddWord: () -> Unit,
    viewModel: WordListViewModel = viewModel {
        WordListViewModel(container.repository, container.pronouncer)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои слова (${state.words.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddWord) {
                Icon(Icons.Default.Add, contentDescription = "Добавить слово")
            }
        },
    ) { innerPadding ->
        if (state.words.isEmpty()) {
            EmptyState(Modifier.padding(innerPadding))
            return@Scaffold
        }

        val firstNewIndex = state.words.indexOfFirst { it.orderIndex > state.benchmark }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            if (firstNewIndex == 0) {
                item { BenchmarkDivider() }
            }
            itemsIndexed(state.words, key = { _, word -> word.id }) { index, word ->
                WordRow(
                    word = word,
                    onSpeak = { viewModel.pronounce(word) },
                    onDelete = { viewModel.delete(word) },
                )
                HorizontalDivider()
                if (firstNewIndex > 0 && index == firstNewIndex - 1) {
                    BenchmarkDivider()
                }
            }
        }
    }
}

@Composable
private fun BenchmarkDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            "  выучено до сюда  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WordRow(word: Word, onSpeak: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(word.text, style = MaterialTheme.typography.titleMedium)
            word.transcription?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(word.translation, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onSpeak) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Озвучить")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Пока нет слов", style = MaterialTheme.typography.titleMedium)
        Text("Нажмите + чтобы добавить первое", style = MaterialTheme.typography.bodyMedium)
    }
}
