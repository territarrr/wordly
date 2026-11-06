package com.wordly.app.ui.session

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarOutline
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.data.entity.Word
import com.wordly.app.domain.Quiz
import com.wordly.app.domain.model.Direction
import com.wordly.app.domain.model.QuizType
import com.wordly.app.ui.common.container
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun titleFor(mode: SessionMode): String = when (mode) {
    SessionMode.NEW_WORDS -> "Новые слова"
    SessionMode.FAVORITES -> "Избранное"
    SessionMode.REVIEW -> "Повторение"
    SessionMode.DUE -> "На сегодня"
    SessionMode.HARD -> "Трудные слова"
}

/** How far (px) a card must be dragged before releasing it counts as a swipe. */
private const val SWIPE_THRESHOLD_PX = 160f

/** Success/error tints that read on both light and dark surfaces. */
private val Right = Color(0xFF1E9E6A)
private val Wrong = Color(0xFFD9534F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onExit: () -> Unit,
    onEditWord: (wordId: Long, profileId: Long) -> Unit,
    viewModel: SessionViewModel = viewModel {
        SessionViewModel(container, createSavedStateHandle())
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoPronounce by viewModel.autoPronounce.collectAsStateWithLifecycle()

    // Back from editing a word mid-session: show the fixed version on its card.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshCards()
        onPauseOrDispose {}
    }

    // Don't let the screen dim mid-session.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(state.mode)) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Filled.Close, contentDescription = "Закончить")
                    }
                },
                actions = {
                    if (state.quiz != QuizType.LISTENING && !state.finished && state.cards.isNotEmpty()) {
                        IconButton(onClick = viewModel::toggleAutoPronounce) {
                            Icon(
                                if (autoPronounce) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = if (autoPronounce) "Выключить автоозвучку" else "Включить автоозвучку",
                                tint = if (autoPronounce) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!state.finished && state.cards.isNotEmpty()) {
                        Text(
                            state.progressLabel,
                            modifier = Modifier.padding(end = 16.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.cards.isEmpty() -> EmptySession(onExit)
                state.finished -> FinishView(state, viewModel, onExit)
                else -> SessionBody(state, viewModel) { wordId -> onEditWord(wordId, viewModel.profileId) }
            }
        }
    }
}

@Composable
private fun SessionBody(state: SessionUiState, viewModel: SessionViewModel, onEditWord: (Long) -> Unit) {
    val word = state.current ?: return
    val index = state.index
    val total = state.cards.size
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { (index + 1f) / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(16.dp))

        // Slides in from the side matching the swipe direction: forward → from the right.
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { w -> if (forward) w else -w } + fadeIn()) togetherWith
                    (slideOutHorizontally { w -> if (forward) -w else w } + fadeOut())
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "card",
        ) { _ ->
            SwipeableCard(
                canGoBack = index > 0,
                onSwipeLeft = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.next()
                },
                onSwipeRight = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.previous()
                },
                onTap = viewModel::toggleReveal,
            ) {
                CardFace(
                    word = word,
                    direction = state.direction,
                    quiz = state.quiz,
                    revealed = state.revealed,
                    onSpeak = viewModel::pronounceCurrent,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onEdit = { onEditWord(word.id) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        when (state.quiz) {
            QuizType.CARDS -> CardsControls(state, viewModel)
            QuizType.TYPING, QuizType.LISTENING -> TypingControls(state, viewModel)
            QuizType.CHOICE -> ChoiceControls(state, viewModel)
        }
    }
}

@Composable
private fun CardsControls(state: SessionUiState, viewModel: SessionViewModel) {
    val haptics = LocalHapticFeedback.current
    Text(
        if (state.revealed) "Тап — скрыть · свайп — листать" else "Тап — показать перевод · свайп — листать",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.revealed) {
            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.answer(known = false)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                border = BorderStroke(1.dp, Wrong.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Wrong),
            ) { Text("Не знаю") }
            Button(
                onClick = { viewModel.answer(known = true) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) { Text("Знаю") }
        } else {
            FilledTonalIconButton(
                onClick = viewModel::previous,
                enabled = state.index > 0,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущее слово")
            }
            FilledTonalButton(
                onClick = viewModel::toggleReveal,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            ) { Text("Показать перевод") }
            FilledTonalIconButton(onClick = viewModel::next, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Пропустить")
            }
        }
    }
}

@Composable
private fun TypingControls(state: SessionUiState, viewModel: SessionViewModel) {
    val focus = remember { FocusRequester() }
    val isLast = state.index + 1 >= state.cards.size
    LaunchedEffect(state.index) { runCatching { focus.requestFocus() } }

    state.verdict?.let { verdict ->
        val (text, color) = when (verdict) {
            Quiz.Verdict.CORRECT -> "Верно!" to Right
            Quiz.Verdict.TYPO -> "Почти — опечатка, засчитано" to Right
            Quiz.Verdict.WRONG -> "Неверно" to Wrong
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.height(8.dp))
    }
    OutlinedTextField(
        value = state.input,
        onValueChange = viewModel::onInput,
        label = {
            Text(
                when {
                    state.quiz == QuizType.LISTENING -> "Что прозвучало?"
                    state.direction == Direction.EN_TO_RU -> "Перевод"
                    else -> "Слово"
                },
            )
        },
        singleLine = true,
        readOnly = state.verdict != null,
        trailingIcon = if (state.quiz == QuizType.LISTENING) {
            {
                IconButton(onClick = viewModel::pronounceCurrent) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Послушать ещё", tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { viewModel.submitTyped() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus),
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.verdict == null) {
            OutlinedButton(onClick = viewModel::giveUp, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Не помню")
            }
            Button(
                onClick = viewModel::submitTyped,
                enabled = state.input.isNotBlank(),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Проверить") }
        } else {
            Button(onClick = viewModel::next, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(if (isLast) "Завершить" else "Дальше")
            }
        }
    }
}

@Composable
private fun ChoiceControls(state: SessionUiState, viewModel: SessionViewModel) {
    val word = state.current ?: return
    val right = Quiz.answerOf(word, state.direction)
    val isLast = state.index + 1 >= state.cards.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.options.forEach { option ->
            val color = when {
                state.picked == null -> null
                option == right -> Right
                option == state.picked -> Wrong
                else -> null
            }
            OutlinedButton(
                onClick = { viewModel.pick(option) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                border = BorderStroke(if (color != null) 2.dp else 1.dp, color ?: MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = color ?: MaterialTheme.colorScheme.onSurface,
                    containerColor = color?.copy(alpha = 0.08f) ?: Color.Transparent,
                ),
            ) {
                Text(option, maxLines = 1)
            }
        }
        if (state.picked != null) {
            Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (isLast) "Завершить" else "Дальше")
            }
        }
    }
}

/**
 * Draggable card: release past [SWIPE_THRESHOLD_PX] to the left calls [onSwipeLeft] (next), to the
 * right [onSwipeRight] (previous, only when [canGoBack]); otherwise it springs back. A plain tap
 * calls [onTap].
 */
@Composable
private fun SwipeableCard(
    canGoBack: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Card(
        onClick = onTap,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = offsetX.value / 60f
            }
            .pointerInput(canGoBack) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offsetX.value
                        when {
                            x < -SWIPE_THRESHOLD_PX -> onSwipeLeft()
                            x > SWIPE_THRESHOLD_PX && canGoBack -> onSwipeRight()
                        }
                        scope.launch { offsetX.animateTo(0f, tween(200)) }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, tween(200)) } },
                ) { change, dragAmount ->
                    change.consume()
                    // Resist dragging right on the first card — there's nothing to go back to.
                    val damped = if (!canGoBack && offsetX.value + dragAmount > 0) dragAmount / 4 else dragAmount
                    scope.launch { offsetX.snapTo(offsetX.value + damped) }
                }
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        // Fade a hint of where the release will go.
        val hint = when {
            offsetX.value < -SWIPE_THRESHOLD_PX / 2 -> "дальше →"
            offsetX.value > SWIPE_THRESHOLD_PX / 2 && canGoBack -> "← назад"
            else -> null
        }
        Box(Modifier.fillMaxSize()) {
            content()
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .graphicsLayer { alpha = (abs(offsetX.value) / SWIPE_THRESHOLD_PX).coerceAtMost(1f) },
                )
            }
        }
    }
}

@Composable
private fun CardFace(
    word: Word,
    direction: Direction,
    quiz: QuizType,
    revealed: Boolean,
    onSpeak: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
) {
    val listening = quiz == QuizType.LISTENING
    val englishFirst = direction == Direction.EN_TO_RU || listening
    val prompt = if (englishFirst) word.text else word.translation
    val answer = if (englishFirst) word.translation else word.text

    Box(Modifier.fillMaxSize()) {
        // Fix a typo or a bad translation without leaving the session.
        IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "Изменить слово", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(
                if (word.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (word.isFavorite) "Убрать из избранного" else "В избранное",
                tint = if (word.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (listening && !revealed) {
                FilledTonalIconButton(onClick = onSpeak, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Filled.Headphones, contentDescription = "Послушать ещё", modifier = Modifier.size(32.dp))
                }
                Text(
                    "Тап по карточке — послушать ещё раз",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    prompt,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                if (englishFirst) TranscriptionAndSound(word, onSpeak)
            }

            AnimatedVisibility(visible = revealed, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(48.dp)
                            .padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        answer,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    if (!englishFirst) TranscriptionAndSound(word, onSpeak)
                    word.note?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptionAndSound(word: Word, onSpeak: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        word.transcription?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSpeak) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Озвучить",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun FinishView(state: SessionUiState, viewModel: SessionViewModel, onExit: () -> Unit) {
    val total = state.cards.size
    val answeredCount = state.answers.size
    val mistakes = state.mistakes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(if (mistakes.isEmpty()) "🎉" else "💪", style = MaterialTheme.typography.displaySmall)

        val newWordsPending = state.mode == SessionMode.NEW_WORDS && !state.committed && !state.mistakesRound
        when {
            state.committed -> {
                val known = state.knownSelection.size
                Text("Отлично!", style = MaterialTheme.typography.headlineSmall)
                Text(
                    buildString {
                        append("Выучено слов: $known.")
                        if (known < total) append(" Остальные ${total - known} будут первыми в следующей пачке.")
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            newWordsPending -> {
                Text("Какие слова вы выучили?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(
                    "Отмеченные станут выученными, остальные — первыми в следующей пачке",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Text(
                    if (state.mistakesRound) "Ошибки пройдены" else "Сессия завершена",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        if (answeredCount > 0 && !state.committed) {
            Text(
                "Правильно: ${state.correctCount} из $answeredCount",
                style = MaterialTheme.typography.titleMedium,
                color = if (mistakes.isEmpty()) Right else MaterialTheme.colorScheme.onSurface,
            )
        }

        if (newWordsPending) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                state.cards.forEach { word ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleKnown(word.id) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = word.id in state.knownSelection, onCheckedChange = { viewModel.toggleKnown(word.id) })
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(word.text, style = MaterialTheme.typography.titleMedium)
                            Text(
                                word.translation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.answers[word.id] == false) {
                            Icon(Icons.Filled.Close, contentDescription = "Была ошибка", tint = Wrong, modifier = Modifier.size(18.dp))
                        } else if (state.answers[word.id] == true) {
                            Icon(Icons.Filled.Check, contentDescription = "Верно", tint = Right, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            Button(
                onClick = viewModel::confirmLearned,
                enabled = state.knownSelection.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Отметить выученными (${state.knownSelection.size} из $total)") }
        }

        if (mistakes.isNotEmpty() && !state.committed) {
            FilledTonalButton(onClick = viewModel::repeatMistakes, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Повторить ошибки (${mistakes.size})")
            }
        }
        // Same words the other way round: EN→RU becomes RU→EN and vice versa.
        if (state.quiz != QuizType.LISTENING && !state.mistakesRound) {
            val code = viewModel.profile.translateLanguageCode.uppercase()
            val reversed = if (state.direction == Direction.EN_TO_RU) "RU → $code" else "$code → RU"
            FilledTonalButton(onClick = viewModel::repeatReversed, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Эти же слова: $reversed")
            }
        }
        if (state.mode == SessionMode.NEW_WORDS && !state.committed) {
            OutlinedButton(onClick = viewModel::repeatBatch, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Пройти пачку заново")
            }
        }
        if (state.mode != SessionMode.NEW_WORDS || state.committed) {
            Button(onClick = onExit, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("На главную") }
        } else {
            TextButton(onClick = onExit) { Text("Выйти без изменений") }
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
        Button(onClick = onExit) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text("  Назад")
        }
    }
}
