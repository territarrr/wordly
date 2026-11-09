package com.wordly.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordly.app.domain.StudyStats
import com.wordly.app.ui.common.container
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
private val MONTHS = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    profileId: Long,
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(key = "stats-$profileId") {
        StatsViewModel(container.repository, container.settings, profileId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Headline numbers as tiles, not charts.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Серия", StudyStats.days(state.streak), Modifier.weight(1f))
                Tile("Лучшая серия", StudyStats.days(state.bestStreak), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Карточек всего", state.totalCards.toString(), Modifier.weight(1f))
                Tile("Верных ответов", state.accuracy?.let { "$it%" } ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Выучено слов", "${state.learned} из ${state.total}", Modifier.weight(1f))
                Tile("Дней с занятиями", state.studiedDays.toString(), Modifier.weight(1f))
            }

            ChartCard("Календарь занятий", "Чем темнее, тем ближе к цели на день (${state.goal} карточек) и выше") {
                CalendarHeatmap(state.calendar, state.goal)
            }
            ChartCard("Карточек в неделю", "Нажмите на столбик, чтобы увидеть число") {
                WeeklyBars(state.calendar, state.weekly)
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/**
 * Sequential ramp, one hue: step 0 is "no study" (a neutral track), 1..4 interpolate from the
 * card surface towards the primary color — lightness changes monotonically in both themes.
 */
@Composable
private fun shade(level: Int): Color {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val ink = MaterialTheme.colorScheme.primary
    return if (level == 0) track else lerp(surface, ink, 0.25f + 0.25f * (level - 1))
}

/**
 * GitHub-style grid: a column per week (Monday on top), a cell per day. With [onClick] (the
 * preview on Home) a tap anywhere opens the full statistics instead of picking a day.
 */
@Composable
internal fun CalendarHeatmap(weeks: List<List<StudyStats.CalendarDay>>, goal: Int, onClick: (() -> Unit)? = null) {
    var selected by remember { mutableStateOf<StudyStats.CalendarDay?>(null) }
    val colors = (0..4).map { shade(it) }
    val ring = MaterialTheme.colorScheme.onSurface
    if (weeks.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonthLabels(weeks)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(weeks.size / 7f)
                .semantics { contentDescription = "Календарь занятий за ${weeks.size} недель" }
                .pointerInput(weeks, onClick) {
                    detectTapGestures { pos ->
                        if (onClick != null) return@detectTapGestures onClick()
                        val cell = size.width / weeks.size.toFloat()
                        val w = (pos.x / cell).toInt()
                        val d = (pos.y / cell).toInt()
                        selected = weeks.getOrNull(w)?.getOrNull(d)
                    }
                },
        ) {
            val cell = size.width / weeks.size
            val gap = 2.dp.toPx() // surface gap between fills
            weeks.forEachIndexed { w, week ->
                week.forEachIndexed { d, day ->
                    val topLeft = Offset(w * cell + gap / 2, d * cell + gap / 2)
                    val cellSize = Size(cell - gap, cell - gap)
                    drawRoundRect(colors[StudyStats.intensity(day.cards, goal)], topLeft, cellSize, CornerRadius(3.dp.toPx()))
                    if (day == selected) {
                        drawRoundRect(ring, topLeft, cellSize, CornerRadius(3.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                selected?.let { "${LocalDate.ofEpochDay(it.epochDay).format(DAY_FORMAT)}: ${cardsLabel(it.cards)}" }
                    ?: if (onClick != null) "Вся статистика ›" else "Нажмите на день",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            // Legend: the ramp from none to "goal doubled".
            Text("меньше ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            colors.forEach { c ->
                Box(
                    Modifier
                        .padding(horizontal = 1.dp)
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .then(Modifier.semantics { }),
                ) { Canvas(Modifier.size(12.dp)) { drawRect(c) } }
            }
            Text(" больше", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeeklyBars(weeks: List<List<StudyStats.CalendarDay>>, totals: List<Int>) {
    var selected by remember { mutableStateOf<Int?>(null) }
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val axis = MaterialTheme.colorScheme.outlineVariant
    val max = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
    if (totals.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            selected?.let { i ->
                val start = weeks[i].first().epochDay
                "Неделя с ${LocalDate.ofEpochDay(start).format(DAY_FORMAT)}: ${cardsLabel(totals[i])}"
            } ?: if (totals.all { it == 0 }) "Пока нет занятий" else "Максимум за неделю: ${cardsLabel(max)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = "Карточек по неделям: " + totals.joinToString(", ") }
                .pointerInput(totals) {
                    detectTapGestures { pos ->
                        val i = (pos.x / (size.width / totals.size.toFloat())).toInt()
                        selected = if (selected == i) null else i.coerceIn(0, totals.lastIndex)
                    }
                },
        ) {
            val slot = size.width / totals.size
            val gap = 2.dp.toPx().coerceAtLeast(slot * 0.25f) // keep bars thin with a surface gap
            val radius = 4.dp.toPx()
            totals.forEachIndexed { i, total ->
                if (total == 0) return@forEachIndexed
                val h = size.height * total / max
                val color = if (selected == null || selected == i) bar else dim
                // Rounded data end at the top, square at the baseline.
                drawRoundRect(color, Offset(i * slot + gap / 2, size.height - h), Size(slot - gap, h), CornerRadius(radius))
                drawRect(color, Offset(i * slot + gap / 2, size.height - minOf(h, radius)), Size(slot - gap, minOf(h, radius)))
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        MonthLabels(weeks)
    }
}

/** Month name over/under the week where it starts — recessive axis labels. */
@Composable
private fun MonthLabels(weeks: List<List<StudyStats.CalendarDay>>) {
    val starts = weeks.indices.filter { i ->
        i == 0 || LocalDate.ofEpochDay(weeks[i - 1].first().epochDay).month !=
            LocalDate.ofEpochDay(weeks[i].first().epochDay).month
    }
    // Like GitHub: a partial first month too close to the next label would collide with it.
    val shown = starts.filterIndexed { n, i -> starts.getOrNull(n + 1)?.let { it - i >= 3 } ?: true }
    Row(Modifier.fillMaxWidth()) {
        weeks.indices.forEach { i ->
            Box(Modifier.weight(1f)) {
                if (i in shown) {
                    Text(
                        MONTHS[LocalDate.ofEpochDay(weeks[i].first().epochDay).monthValue - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                    )
                }
            }
        }
    }
}

private fun cardsLabel(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> "карточек"
        mod10 == 1 -> "карточка"
        mod10 in 2..4 -> "карточки"
        else -> "карточек"
    }
    return "$n $word"
}
