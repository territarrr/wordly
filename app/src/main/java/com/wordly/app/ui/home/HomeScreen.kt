package com.wordly.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.wordly.app.domain.StudyStats
import com.wordly.app.domain.model.QuizType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.domain.model.Direction
import com.wordly.app.domain.model.Profile
import com.wordly.app.domain.model.ReviewScope
import com.wordly.app.domain.model.SessionConfig
import com.wordly.app.ui.common.container

/** Which session-setup bottom sheet is open on Home, if any. */
private enum class HomeSheet { DUE, NEW_WORDS, REVIEW, HARD, FAVORITES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenWords: (profileId: Long) -> Unit,
    onAddWord: (profileId: Long) -> Unit,
    onStartSession: (SessionConfig, profileId: Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: (profileId: Long) -> Unit,
    viewModel: HomeViewModel = viewModel {
        HomeViewModel(container.repository, container.profilePreferences, container.settings)
    },
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val dailyGoal by viewModel.dailyGoal.collectAsStateWithLifecycle()
    val defaultBatch by viewModel.defaultBatch.collectAsStateWithLifecycle()
    val autoPronounce by viewModel.autoPronounce.collectAsStateWithLifecycle()
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()

    celebration?.let { milestone -> CelebrationDialog(milestone) { viewModel.dismissCelebration(milestone) } }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {}
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    // Kept here (not inside the sheets), and saveable, so choices survive closing a sheet,
    // rotation and the process being killed in the background.
    var direction by rememberSaveable { mutableStateOf(Direction.EN_TO_RU) }
    var quiz by rememberSaveable { mutableStateOf(QuizType.CARDS) }
    var batchSize by rememberSaveable(defaultBatch) { mutableStateOf(defaultBatch) }
    var openSheet by rememberSaveable { mutableStateOf<HomeSheet?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wordly", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { onOpenWords(activeProfile.id) }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Мои слова")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddWord(activeProfile.id) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Слово") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            LanguageSwitch(activeProfile, viewModel::switchProfile)
            ProgressHero(stats, dailyGoal, onClick = { onOpenStats(activeProfile.id) })
            Spacer(Modifier.height(8.dp))

            if (stats.learned > 0) {
                ActionCard(
                    icon = Icons.Filled.Today,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "На сегодня",
                    subtitle = if (stats.dueCount > 0) {
                        "${stats.dueCount} ${wordsLabel(stats.dueCount)} пора повторить"
                    } else {
                        "Всё повторено — загляните завтра"
                    },
                    enabled = stats.dueCount > 0,
                    onClick = { openSheet = HomeSheet.DUE },
                )
            }
            ActionCard(
                icon = Icons.Filled.AutoAwesome,
                accent = MaterialTheme.colorScheme.primary,
                title = "Учить новые",
                subtitle = if (stats.newCount > 0) {
                    "${stats.newCount} ждут · пачка по $batchSize"
                } else {
                    "Новых слов нет — добавьте их через «+»"
                },
                enabled = stats.newCount > 0,
                onClick = { openSheet = HomeSheet.NEW_WORDS },
            )
            ActionCard(
                icon = Icons.Filled.Replay,
                accent = MaterialTheme.colorScheme.secondary,
                title = "Повторить",
                subtitle = if (stats.learned > 0) "${stats.learned} выученных · любые на выбор" else "Пока нечего повторять",
                enabled = stats.learned > 0,
                onClick = { openSheet = HomeSheet.REVIEW },
            )
            if (stats.hardCount > 0) {
                ActionCard(
                    icon = Icons.Filled.Bolt,
                    accent = HardColor,
                    title = "Трудные слова",
                    subtitle = "${stats.hardCount} — в них чаще всего ошибки",
                    enabled = true,
                    onClick = { openSheet = HomeSheet.HARD },
                )
            }
            ActionCard(
                icon = Icons.Filled.Star,
                accent = MaterialTheme.colorScheme.tertiary,
                title = "Избранное",
                subtitle = if (stats.favoritesCount > 0) {
                    "${stats.favoritesCount} в избранном"
                } else {
                    "Отмечайте слова звёздочкой"
                },
                enabled = stats.favoritesCount > 0,
                onClick = { openSheet = HomeSheet.FAVORITES },
            )

            Spacer(Modifier.height(96.dp))
        }
    }

    openSheet?.let { sheet ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { openSheet = null }, sheetState = sheetState) {
            val start: (SessionConfig) -> Unit = { config ->
                openSheet = null
                onStartSession(config, activeProfile.id)
            }
            val common: @Composable () -> Unit = {
                DirectionPicker(activeProfile, direction) { direction = it }
                QuizPicker(quiz) { quiz = it }
                // Listening always plays the word — the toggle would be meaningless there.
                if (quiz != QuizType.LISTENING) {
                    AutoPronounceRow(activeProfile, autoPronounce, viewModel::setAutoPronounce)
                }
            }
            when (sheet) {
                HomeSheet.DUE -> DueSheet(
                    due = stats.dueCount,
                    common = common,
                    onStart = { limit ->
                        start(SessionConfig.Due(direction, limit, System.currentTimeMillis(), quiz))
                    },
                )
                HomeSheet.NEW_WORDS -> NewWordsSheet(
                    newCount = stats.newCount,
                    common = common,
                    batchSize = batchSize,
                    onBatchSize = { batchSize = it },
                    onStart = { shuffle ->
                        start(
                            SessionConfig.NewWords(
                                direction = direction,
                                batchSize = batchSize,
                                shuffle = shuffle,
                                seed = System.currentTimeMillis(),
                                quiz = quiz,
                            ),
                        )
                    },
                )
                HomeSheet.REVIEW -> ReviewSheet(
                    learned = stats.learned,
                    common = common,
                    onStart = { shuffle, scope ->
                        start(
                            SessionConfig.Review(
                                direction = direction,
                                shuffle = shuffle,
                                scope = scope,
                                seed = System.currentTimeMillis(),
                                quiz = quiz,
                            ),
                        )
                    },
                )
                HomeSheet.HARD -> SimpleSheet(
                    title = "Трудные слова",
                    subtitle = "Слова, в которых ошибок больше, чем верных ответов: ${stats.hardCount}",
                    common = common,
                    startLabel = "Начать",
                    onStart = { start(SessionConfig.Hard(direction, System.currentTimeMillis(), quiz)) },
                )
                HomeSheet.FAVORITES -> FavoritesSheet(
                    count = stats.favoritesCount,
                    common = common,
                    onStart = { shuffle, scope ->
                        start(
                            SessionConfig.Favorites(
                                direction = direction,
                                shuffle = shuffle,
                                scope = scope,
                                seed = System.currentTimeMillis(),
                                quiz = quiz,
                            ),
                        )
                    },
                )
            }
        }
    }
}

private val HardColor = Color(0xFFE0663A)

/** Russian plural for "слово" after [n]. */
private fun wordsLabel(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "слов"
        mod10 == 1 -> "слово"
        mod10 in 2..4 -> "слова"
        else -> "слов"
    }
}

/**
 * One-tap language switch, always visible at the top of Home — there are only two profiles, so a
 * segmented control beats a dropdown hidden under the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSwitch(active: Profile, onSwitch: (Profile) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Profile.entries.forEachIndexed { i, profile ->
            SegmentedButton(
                selected = profile == active,
                onClick = { onSwitch(profile) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = Profile.entries.size),
            ) { Text(profile.displayName) }
        }
    }
}

@Composable
private fun ProgressHero(stats: HomeStats, dailyGoal: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stats.learned.toString(), style = MaterialTheme.typography.displaySmall)
                Text(
                    "  выучено",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 6.dp),
                )
                if (stats.streak > 0) {
                    Text(
                        "🔥 ${stats.streak}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (stats.total == 0) 0f else stats.learned.toFloat() / stats.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text(
                "${stats.newCount} новых · ${stats.total} всего",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val goalMet = stats.todayCards >= dailyGoal
                Text(
                    if (goalMet) "Цель на сегодня выполнена ✓" else "Сегодня: ${stats.todayCards} из $dailyGoal карточек",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                CircularProgressIndicator(
                    progress = { (stats.todayCards.toFloat() / dailyGoal.coerceAtLeast(1)).coerceAtMost(1f) },
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f),
                    gapSize = 0.dp,
                )
            }
            if (stats.streak == 0 && stats.total > 0) {
                Text(
                    "Позанимайтесь сегодня, чтобы начать серию 🔥",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (stats.total > 0) Milestones(stats.streak, stats.bestStreak)
        }
    }
}

/**
 * The four streak milestones as badges — earned ones (by the best streak ever) filled, the rest
 * outlined — plus how far the current streak is from the next one.
 */
@Composable
private fun Milestones(streak: Int, bestStreak: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudyStats.MILESTONES.forEach { m ->
                val earned = bestStreak >= m.days
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (earned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (earned) "${m.emoji} ${m.days}" else "${m.days}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (earned) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                    )
                }
            }
        }
        StudyStats.nextMilestone(streak)?.let { next ->
            Text(
                "До ${StudyStats.days(next.days)} подряд — ещё ${StudyStats.days(next.days - streak)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Shown once when the streak reaches 10 / 50 / 100 / 365 days. */
@Composable
private fun CelebrationDialog(milestone: StudyStats.Milestone, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(milestone.emoji, style = MaterialTheme.typography.displayMedium) },
        title = { Text(milestone.title, textAlign = TextAlign.Center) },
        text = { Text(milestone.message, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Ура!") } },
    )
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (enabled) 0.14f else 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent.copy(alpha = if (enabled) 1f else 0.4f))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Session-setup sheets ----

@Composable
private fun SheetBody(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

@Composable
private fun DirectionPicker(profile: Profile, direction: Direction, onDirection: (Direction) -> Unit) {
    val code = profile.translateLanguageCode.uppercase()
    OptionLabel("Направление карточек")
    TwoOptions(
        first = "$code → RU",
        second = "RU → $code",
        firstSelected = direction == Direction.EN_TO_RU,
        onSelect = { first -> onDirection(if (first) Direction.EN_TO_RU else Direction.RU_TO_EN) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuizPicker(quiz: QuizType, onQuiz: (QuizType) -> Unit) {
    OptionLabel("Как отвечать")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuizType.entries.forEach { type ->
            FilterChip(selected = quiz == type, onClick = { onQuiz(type) }, label = { Text(type.label) })
        }
    }
}

@Composable
private fun AutoPronounceRow(profile: Profile, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Автоозвучка", style = MaterialTheme.typography.labelLarge)
            Text(
                "Произносить слово (${profile.translateLanguageCode.uppercase()}), как только оно появляется",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun OrderPicker(shuffle: Boolean, onShuffle: (Boolean) -> Unit) {
    OptionLabel("Порядок")
    TwoOptions(
        first = "По порядку",
        second = "Перемешать",
        firstSelected = !shuffle,
        onSelect = { first -> onShuffle(!first) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoOptions(first: String, second: String, firstSelected: Boolean, onSelect: (first: Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = firstSelected,
            onClick = { onSelect(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(first) }
        SegmentedButton(
            selected = !firstSelected,
            onClick = { onSelect(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(second) }
    }
}

@Composable
private fun OptionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DueSheet(due: Int, common: @Composable () -> Unit, onStart: (limit: Int) -> Unit) {
    var limit by rememberSaveable { mutableStateOf(20) }
    SheetBody(
        "На сегодня",
        "Слова, которые пора повторить, чтобы не забылись: $due. " +
            "Верный ответ откладывает слово надолго, ошибка — возвращает на завтра.",
    ) {
        common()
        OptionLabel("Сколько за раз")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 50).filter { it < due }.forEach { n ->
                FilterChip(selected = limit == n, onClick = { limit = n }, label = { Text("$n") })
            }
            FilterChip(selected = limit >= due, onClick = { limit = Int.MAX_VALUE }, label = { Text("Все ($due)") })
        }
        StartButton("Начать") { onStart(limit) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewWordsSheet(
    newCount: Int,
    common: @Composable () -> Unit,
    batchSize: Int,
    onBatchSize: (Int) -> Unit,
    onStart: (shuffle: Boolean) -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf(batchSize !in BATCH_SIZES) }
    // Shuffled by default: in addition order neighbouring words cue each other.
    var shuffle by rememberSaveable { mutableStateOf(true) }
    var customText by rememberSaveable { mutableStateOf(if (custom) batchSize.toString() else "") }

    SheetBody("Учить новые", "Новых слов ждут изучения: $newCount") {
        common()
        OptionLabel("Размер пачки")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BATCH_SIZES.forEach { n ->
                FilterChip(
                    selected = batchSize == n && !custom,
                    onClick = { custom = false; onBatchSize(n) },
                    label = { Text("$n") },
                )
            }
            FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Своё") })
        }
        if (custom) {
            NumberField("Размер пачки", customText) {
                customText = it
                it.toIntOrNull()?.takeIf { n -> n > 0 }?.let(onBatchSize)
            }
        }
        OrderPicker(shuffle) { shuffle = it }
        StartButton("Начать изучение") { onStart(shuffle) }
    }
}

@Composable
private fun ReviewSheet(
    learned: Int,
    common: @Composable () -> Unit,
    onStart: (shuffle: Boolean, scope: ReviewScope) -> Unit,
) {
    var shuffle by rememberSaveable { mutableStateOf(false) }
    val scope = rememberSaveable(saver = ScopeChoice.Saver) { ScopeChoice() }

    SheetBody("Повторить", "Выученных слов: $learned") {
        common()
        OrderPicker(shuffle) { shuffle = it }
        ScopePicker(
            choice = scope,
            counts = listOf(20, 50, 100),
            rangeHint = "Позиция среди выученных слов — 1 это самое первое выученное",
        )
        StartButton("Начать повторение") { onStart(shuffle, scope.toScope()) }
    }
}

@Composable
private fun FavoritesSheet(
    count: Int,
    common: @Composable () -> Unit,
    onStart: (shuffle: Boolean, scope: ReviewScope) -> Unit,
) {
    var shuffle by rememberSaveable { mutableStateOf(false) }
    val scope = rememberSaveable(saver = ScopeChoice.Saver) { ScopeChoice() }

    SheetBody("Избранное", "Слов в избранном: $count") {
        common()
        OrderPicker(shuffle) { shuffle = it }
        ScopePicker(
            choice = scope,
            counts = listOf(10, 20, 50, 100),
            rangeHint = "Позиция среди избранных слов — 1 это самое раннее добавленное",
        )
        StartButton("Учить избранное") { onStart(shuffle, scope.toScope()) }
    }
}

@Composable
private fun SimpleSheet(
    title: String,
    subtitle: String,
    common: @Composable () -> Unit,
    startLabel: String,
    onStart: () -> Unit,
) {
    SheetBody(title, subtitle) {
        common()
        StartButton(startLabel, onStart)
    }
}

private val BATCH_SIZES = listOf(10, 20, 50, 100)

/** UI state behind [ScopePicker]: which chip is picked plus the raw text of its number fields. */
private class ScopeChoice {
    var windowChoice by mutableStateOf<Int?>(null) // null = all
    var windowFromStart by mutableStateOf(false) // ignored when windowChoice == null
    var windowCustom by mutableStateOf(false)
    var windowCustomText by mutableStateOf("")
    var rangeMode by mutableStateOf(false)
    var rangeFromText by mutableStateOf("")
    var rangeToText by mutableStateOf("")

    fun toScope(): ReviewScope = when {
        rangeMode -> {
            val from = rangeFromText.toIntOrNull()?.takeIf { it > 0 } ?: 1
            val to = rangeToText.toIntOrNull()?.takeIf { it > 0 } ?: from
            ReviewScope.Range(from, to)
        }
        windowCustom -> {
            val count = windowCustomText.toIntOrNull()?.takeIf { it > 0 }
            if (count == null) ReviewScope.All else ReviewScope.Window(count, windowFromStart)
        }
        windowChoice != null -> ReviewScope.Window(windowChoice!!, windowFromStart)
        else -> ReviewScope.All
    }

    companion object {
        val Saver: Saver<ScopeChoice, Any> = listSaver(
            save = {
                listOf(
                    it.windowChoice ?: -1, it.windowFromStart, it.windowCustom, it.windowCustomText,
                    it.rangeMode, it.rangeFromText, it.rangeToText,
                )
            },
            restore = {
                ScopeChoice().apply {
                    windowChoice = (it[0] as Int).takeIf { n -> n >= 0 }
                    windowFromStart = it[1] as Boolean
                    windowCustom = it[2] as Boolean
                    windowCustomText = it[3] as String
                    rangeMode = it[4] as Boolean
                    rangeFromText = it[5] as String
                    rangeToText = it[6] as String
                }
            },
        )
    }
}

/** "Какие слова": all / first N / last N for each of [counts] / custom N / a range. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScopePicker(choice: ScopeChoice, counts: List<Int>, rangeHint: String) = with(choice) {
    OptionLabel("Какие слова")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WindowChip("Все", windowChoice == null && !windowCustom && !rangeMode) {
            windowChoice = null; windowCustom = false; rangeMode = false
        }
        counts.forEach { n ->
            WindowChip(
                "Последние $n",
                windowChoice == n && !windowFromStart && !windowCustom && !rangeMode,
            ) {
                windowChoice = n; windowFromStart = false; windowCustom = false; rangeMode = false
            }
        }
        counts.forEach { n ->
            WindowChip(
                "Первые $n",
                windowChoice == n && windowFromStart && !windowCustom && !rangeMode,
            ) {
                windowChoice = n; windowFromStart = true; windowCustom = false; rangeMode = false
            }
        }
        WindowChip("Своё", windowCustom) { windowCustom = true; rangeMode = false }
        WindowChip("Диапазон", rangeMode) { rangeMode = true; windowCustom = false }
    }
    if (windowCustom) {
        NumberField("Сколько слов", windowCustomText) { windowCustomText = it }
        TwoOptions(
            first = "С начала",
            second = "С конца",
            firstSelected = windowFromStart,
            onSelect = { windowFromStart = it },
        )
    }
    if (rangeMode) {
        Text(
            rangeHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                NumberField("От", rangeFromText) { rangeFromText = it }
            }
            Box(Modifier.weight(1f)) {
                NumberField("До", rangeToText) { rangeToText = it }
            }
        }
    }
}

@Composable
private fun StartButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(52.dp),
    ) { Text(text) }
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
