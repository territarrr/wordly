package com.wordly.app.ui.words

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.data.entity.Word
import com.wordly.app.domain.LearningEngine
import com.wordly.app.ui.common.container
import kotlinx.coroutines.launch

private enum class WordTab(val label: String) { ALL("Все"), LEARNED("Выученные"), NEW("Новые") }
private enum class WordSort(val label: String) { NEWEST("Новые сначала"), OLDEST("Старые сначала"), ALPHA("А–Я") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WordListScreen(
    profileId: Long,
    onBack: () -> Unit,
    onAddWord: () -> Unit,
    onEditWord: (Long) -> Unit,
    onImport: () -> Unit,
    viewModel: WordListViewModel = viewModel(key = "words-$profileId") {
        WordListViewModel(container.repository, container.pronouncer, profileId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportMessage by viewModel.exportMessage.collectAsStateWithLifecycle()
    var benchmarkTarget by remember { mutableStateOf<Word?>(null) }
    var tab by rememberSaveable { mutableStateOf(WordTab.ALL) }
    var sort by rememberSaveable { mutableStateOf(WordSort.NEWEST) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val selecting = selectedIds.isNotEmpty()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri, context.contentResolver)
    }

    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeExportMessage()
        }
    }

    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    /** Deletes right away, but offers "Отменить" for a few seconds — no confirmation dialog in the way. */
    fun deleteWithUndo(words: List<Word>) {
        if (words.isEmpty()) return
        viewModel.delete(words)
        selectedIds = emptySet()
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val label = if (words.size == 1) "«${words[0].text}» удалено" else "Удалено слов: ${words.size}"
            val result = snackbarHostState.showSnackbar(label, actionLabel = "Отменить", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    if (showClearDialog) {
        var confirmText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Удалить весь словарь?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Будут безвозвратно удалены все ${state.words.size} слов и прогресс " +
                            "изучения. Отменить это будет нельзя.",
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        label = { Text("Напечатайте УДАЛИТЬ") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllWords()
                        showClearDialog = false
                    },
                    enabled = confirmText.trim().equals("УДАЛИТЬ", ignoreCase = true),
                ) { Text("Удалить всё") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
        )
    }

    benchmarkTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { benchmarkTarget = null },
            title = { Text("Перенести границу выученного?") },
            text = { Text("Считать выученным всё до слова «${target.text}» включительно.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setBenchmarkHere(target)
                        benchmarkTarget = null
                        selectedIds = emptySet()
                    },
                ) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = { benchmarkTarget = null }) { Text("Отмена") }
            },
        )
    }

    val selectedWords = state.words.filter { it.id in selectedIds }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("Выбрано: ${selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = { selectedIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Снять выделение")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleFavorite(selectedWords) }) {
                            Icon(
                                if (selectedWords.all { it.isFavorite }) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = "В избранное",
                            )
                        }
                        if (selectedWords.size == 1) {
                            IconButton(onClick = { benchmarkTarget = selectedWords.first() }) {
                                Icon(Icons.Default.Flag, contentDescription = "Выучено до сюда")
                            }
                        }
                        IconButton(onClick = { deleteWithUndo(selectedWords) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Удалить выбранные")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            } else {
                TopAppBar(
                    title = { Text("Мои слова (${state.words.size})") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = onImport) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Импорт из CSV")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Экспорт в CSV") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    createDocument.launch("wordly_export.csv")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Очистить словарь", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = onAddWord) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить слово")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.words.isEmpty()) {
            EmptyState(Modifier.padding(innerPadding))
            return@Scaffold
        }

        val filteredWords = state.words
            .filter { word ->
                when (tab) {
                    WordTab.ALL -> true
                    WordTab.LEARNED -> word.orderIndex <= state.benchmark
                    WordTab.NEW -> word.orderIndex > state.benchmark
                }
            }
            .let { words ->
                if (searchQuery.isBlank()) {
                    words
                } else {
                    words.filter {
                        it.text.contains(searchQuery, ignoreCase = true) ||
                            it.translation.contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            .let { words -> if (favoritesOnly) words.filter { it.isFavorite } else words }

        val displayWords = when (sort) {
            WordSort.NEWEST -> filteredWords.asReversed()
            WordSort.OLDEST -> filteredWords
            WordSort.ALPHA -> filteredWords.sortedBy { it.normalized }
        }
        // The "выучено до сюда" line only makes sense on the mixed list in addition order.
        val total = filteredWords.size
        val learnedCount = filteredWords.count { it.orderIndex <= state.benchmark }
        val showDivider = tab == WordTab.ALL && sort != WordSort.ALPHA && total > 0 && learnedCount < total
        // Index (in displayWords) after which the divider renders; -1 means before the first item.
        val dividerAfterIndex = (if (sort == WordSort.NEWEST) total - learnedCount else learnedCount) - 1

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск по слову или переводу") },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить поиск")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WordTab.entries.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = tab == t,
                                onClick = { tab = t },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = WordTab.entries.size),
                            ) { Text(t.label) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WordSort.entries.forEach { s ->
                            FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
                        }
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("★ Избранное") },
                        )
                    }
                    Text(
                        "Свайп влево — удалить · долгий тап — выбрать несколько",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (filteredWords.isEmpty()) {
                item {
                    Text(
                        "Ничего не найдено",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (showDivider && dividerAfterIndex == -1) {
                item { BenchmarkDivider() }
            }
            itemsIndexed(displayWords, key = { _, word -> word.id }) { index, word ->
                SwipeToDelete(enabled = !selecting, onDelete = { deleteWithUndo(listOf(word)) }) {
                    WordRow(
                        word = word,
                        selecting = selecting,
                        selected = word.id in selectedIds,
                        onClick = {
                            if (selecting) {
                                selectedIds = if (word.id in selectedIds) selectedIds - word.id else selectedIds + word.id
                            } else {
                                onEditWord(word.id)
                            }
                        },
                        onLongClick = { selectedIds = selectedIds + word.id },
                        onSpeak = { viewModel.pronounce(word) },
                        onToggleFavorite = { viewModel.toggleFavorite(word) },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                if (showDivider && index == dividerAfterIndex) {
                    BenchmarkDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(enabled: Boolean, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            value == SwipeToDismissBoxValue.EndToStart
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = enabled,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { content() }
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
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            "  выучено до сюда  ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordRow(
    word: Word,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSpeak: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = if (selecting) 4.dp else 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.text, style = MaterialTheme.typography.titleMedium)
                if (LearningEngine.isHard(word)) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HardDot),
                    )
                }
            }
            word.transcription?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(word.translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (word.correctCount + word.wrongCount > 0) {
                Text(
                    "верно ${word.correctCount} · ошибок ${word.wrongCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (!selecting) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (word.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (word.isFavorite) "Убрать из избранного" else "В избранное",
                    tint = if (word.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSpeak) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Озвучить", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private val HardDot = Color(0xFFE0663A)

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
