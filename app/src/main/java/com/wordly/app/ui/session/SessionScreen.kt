package com.wordly.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.data.entity.Word
import com.wordly.app.domain.model.Direction
import com.wordly.app.ui.common.container

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onExit: () -> Unit,
    viewModel: SessionViewModel = viewModel {
        SessionViewModel(container, createSavedStateHandle())
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isNewWordsMode) "Новые слова" else "Повторение")
                },
                actions = {
                    if (!state.finished && state.cards.isNotEmpty()) {
                        Text(
                            state.progressLabel,
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.cards.isEmpty() -> EmptySession(onExit)
                state.finished -> FinishView(
                    isNewWordsMode = state.isNewWordsMode,
                    committed = state.committed,
                    count = state.cards.size,
                    onConfirm = viewModel::confirmLearned,
                    onRepeat = viewModel::repeatBatch,
                    onExit = onExit,
                )
                else -> CardView(
                    word = state.current!!,
                    direction = state.direction,
                    revealed = state.revealed,
                    index = state.index,
                    total = state.cards.size,
                    onReveal = viewModel::reveal,
                    onSpeak = viewModel::pronounceCurrent,
                    onNext = viewModel::next,
                )
            }
        }
    }
}

@Composable
private fun CardView(
    word: Word,
    direction: Direction,
    revealed: Boolean,
    index: Int,
    total: Int,
    onReveal: () -> Unit,
    onSpeak: () -> Unit,
    onNext: () -> Unit,
) {
    val englishFirst = direction == Direction.EN_TO_RU
    val prompt = if (englishFirst) word.text else word.translation
    val answer = if (englishFirst) word.translation else word.text

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { (index + 1f) / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    prompt,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (englishFirst) {
                    TranscriptionAndSound(word, onSpeak)
                }

                if (revealed) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        answer,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    if (!englishFirst) {
                        TranscriptionAndSound(word, onSpeak)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (revealed) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(if (index + 1 >= total) "Завершить" else "Дальше")
            }
        } else {
            FilledTonalButton(onClick = onReveal, modifier = Modifier.fillMaxWidth()) {
                Text("Показать перевод")
            }
        }
    }
}

@Composable
private fun TranscriptionAndSound(word: Word, onSpeak: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        word.transcription?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onSpeak) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Озвучить")
        }
    }
}

@Composable
private fun FinishView(
    isNewWordsMode: Boolean,
    committed: Boolean,
    count: Int,
    onConfirm: () -> Unit,
    onRepeat: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            !isNewWordsMode -> {
                Text("Повторение завершено", style = MaterialTheme.typography.headlineSmall)
                Text("Карточек пройдено: $count")
                Button(onClick = onExit) { Text("На главную") }
            }
            committed -> {
                Text("Отлично!", style = MaterialTheme.typography.headlineSmall)
                Text("Бенчмарк сдвинут — эти $count слов теперь выучены.")
                Button(onClick = onExit) { Text("На главную") }
            }
            else -> {
                Text("Вы выучили эти $count слов?", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text("Да, выучил — сдвинуть бенчмарк")
                }
                OutlinedButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth()) {
                    Text("Ещё нет — пройти пачку заново")
                }
            }
        }
    }
}

@Composable
private fun EmptySession(onExit: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Нет слов для этого режима", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onExit) { Text("Назад") }
    }
}
