package com.wordly.app.ui.addword

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.data.entity.Word
import com.wordly.app.ui.common.container

/**
 * [wordId] `null` adds a new word; a real id opens that word for viewing/editing.
 * [onOpenWord] opens another, already saved word (the duplicate, or a translation match) for editing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddWordScreen(
    profileId: Long,
    onDone: () -> Unit,
    onOpenWord: (wordId: Long) -> Unit,
    wordId: Long? = null,
    viewModel: AddWordViewModel = viewModel(key = "add-$profileId-$wordId") {
        AddWordViewModel(container.repository, container.pronouncer, profileId, wordId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val wordFocus = remember { FocusRequester() }
    val translationFocus = remember { FocusRequester() }

    // A new word starts with the cursor (and keyboard) already in "Слово".
    LaunchedEffect(Unit) { if (!viewModel.isEditing) runCatching { wordFocus.requestFocus() } }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить слово?") },
            text = { Text("«${state.text}» будет удалено без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Изменить слово" else "Новое слово") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить слово")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!viewModel.isEditing) {
                    OutlinedButton(
                        onClick = { viewModel.save(addAnother = true) },
                        enabled = !state.saving,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) { Text("Сохранить и ещё") }
                }
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text(if (viewModel.isEditing) "Сохранить изменения" else "Сохранить") }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::onText,
                label = { Text("Слово") },
                singleLine = true,
                isError = state.textError != null,
                // A duplicate gets its own card below instead of a one-line error.
                supportingText = state.textError?.takeIf { state.duplicate == null }?.let { { Text(it) } },
                // Enter = "I've finished typing the word": look it up and move on to the translation.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (!state.isDuplicate && state.text.isNotBlank()) viewModel.lookup()
                        runCatching { translationFocus.requestFocus() }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(wordFocus),
            )

            state.duplicate?.let { existing ->
                DuplicateCard(
                    word = existing,
                    onSpeak = { viewModel.pronounce(existing) },
                    onOpen = { onOpenWord(existing.id) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = viewModel::lookup, enabled = !state.looking && !state.isDuplicate) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (viewModel.profile.hasTranscriptionLookup) {
                            "Подтянуть перевод и озвучку"
                        } else {
                            "Подтянуть перевод"
                        },
                    )
                }
                if (state.looking) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = state.translation,
                onValueChange = viewModel::onTranslation,
                label = { Text("Перевод (рус.)") },
                singleLine = true,
                isError = state.translationError != null,
                supportingText = state.translationError?.let { { Text(it) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(translationFocus),
            )
            if (state.translationOptions.isNotEmpty()) {
                val selected = splitTranslations(state.translation)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.translationOptions.forEach { option ->
                        FilterChip(
                            selected = option in selected,
                            onClick = { viewModel.pickTranslation(option) },
                            label = { Text(option) },
                        )
                    }
                }
            }

            if (state.translationMatches.isNotEmpty()) {
                TranslationMatchesCard(state.translationMatches, onOpenWord)
            }

            TextButton(
                onClick = viewModel::lookupByTranslation,
                enabled = !state.looking && state.translation.isNotBlank(),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Подобрать слово по переводу (онлайн)")
            }
            if (state.wordOptions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.wordOptions.forEach { option ->
                        FilterChip(
                            selected = option == state.text,
                            onClick = { viewModel.pickWord(option) },
                            label = { Text(option) },
                        )
                    }
                }
            }

            if (viewModel.profile.hasTranscriptionLookup) {
                OutlinedTextField(
                    value = state.transcription,
                    onValueChange = viewModel::onTranscription,
                    label = { Text("Транскрипция (IPA)") },
                    singleLine = true,
                    trailingIcon = {
                        if (state.audioUrl != null || state.text.isNotBlank()) {
                            IconButton(onClick = viewModel::previewAudio) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Прослушать",
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.text.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previewAudio) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать")
                    }
                    Text("Прослушать произношение", style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNote,
                label = { Text("Заметка или пример (необязательно)") },
                placeholder = { Text("I have a cat. — У меня есть кошка.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.lookedUp && viewModel.profile.hasTranscriptionLookup) {
                Text(
                    if (state.audioUrl != null) {
                        "🔊 Озвучка из словаря загружена"
                    } else {
                        "🔊 Кнопка озвучит через синтез речи телефона — из словаря аудио не пришло"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Shown under "Слово" when it's already saved: the existing entry itself, not just "already exists". */
@Composable
private fun DuplicateCard(word: Word, onSpeak: () -> Unit, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Это слово уже есть в словаре", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(word.text, style = MaterialTheme.typography.titleLarge)
                    word.transcription?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(word.translation, style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = onSpeak) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Прослушать")
                }
            }
            TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
                Text("Открыть и изменить")
            }
        }
    }
}

/** Already-saved words whose translation matches what's being typed — a local reverse search. */
@Composable
private fun TranslationMatchesCard(matches: List<Word>, onOpenWord: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(
            "В словаре с таким переводом",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        matches.forEachIndexed { i, word ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenWord(word.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(word.text, style = MaterialTheme.typography.titleMedium)
                    Text(
                        word.translation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Открыть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
