package com.wordly.app.domain

/** Streak math over the set of days (epoch days) on which at least one card was answered. */
object StudyStats {

    /** A streak length worth celebrating, with how it's shown on Home and in the congratulation. */
    data class Milestone(val days: Int, val emoji: String, val title: String, val message: String)

    val MILESTONES = listOf(
        Milestone(10, "🔥", "10 дней подряд", "Привычка появилась. Самое сложное — первые дни — позади."),
        Milestone(50, "⚡", "50 дней подряд", "Полсотни дней без пропусков — это уже не случайность."),
        Milestone(100, "💯", "100 дней подряд", "Сотня! Слова, выученные в первые дни, давно в долгой памяти."),
        Milestone(365, "🏆", "Год подряд", "365 дней без единого пропуска. Это редкость — гордитесь собой."),
    )

    /**
     * Consecutive study days ending today — or ending yesterday, so the streak doesn't show as
     * broken in the morning before today's session.
     */
    fun streak(studiedDays: Set<Long>, today: Long): Int {
        var day = if (today in studiedDays) today else today - 1
        var count = 0
        while (day in studiedDays) {
            count++
            day--
        }
        return count
    }

    /** The longest run of consecutive study days ever — milestones stay earned after a break. */
    fun longestStreak(studiedDays: Set<Long>): Int {
        var best = 0
        for (day in studiedDays) {
            if (day - 1 in studiedDays) continue // not the start of a run
            var length = 1
            while (day + length in studiedDays) length++
            best = maxOf(best, length)
        }
        return best
    }

    /** The next milestone above [streak], or `null` past the last one. */
    fun nextMilestone(streak: Int): Milestone? = MILESTONES.firstOrNull { it.days > streak }

    /**
     * The milestone to congratulate on now: the highest one [streak] has reached that is above
     * [lastCelebrated] (so each is shown once, and a jump past two at once shows only the top one).
     */
    fun milestoneToCelebrate(streak: Int, lastCelebrated: Int): Milestone? =
        MILESTONES.lastOrNull { it.days in (lastCelebrated + 1)..streak }

    /**
     * Title and body of the daily reminder. [streak] is as of now (still alive from yesterday if
     * today isn't studied yet); the text nudges towards new words and calls out a milestone that
     * today's session would reach.
     */
    fun reminderText(streak: Int, studiedToday: Boolean, newCount: Int, dueCount: Int): Pair<String, String> {
        val title = if (newCount > 0) "Давай поучим новые слова" else "Давай повторим слова"
        val streakPart = when {
            studiedToday -> "Серия: ${days(streak)} 🔥 Сегодня уже было — можно ещё пару слов."
            streak == 0 -> "Начни серию сегодня 🔥"
            nextMilestone(streak)?.days == streak + 1 -> "Сегодня будет ${days(streak + 1)} подряд — не пропусти! 🔥"
            else -> "Серия: ${days(streak)} 🔥 Не прерывай её."
        }
        val wordsPart = buildList {
            if (newCount > 0) add("новых слов ждут: $newCount")
            if (dueCount > 0) add("пора повторить: $dueCount")
        }.joinToString(", ").replaceFirstChar { it.uppercaseChar() }
        return title to listOf(streakPart, wordsPart).filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** One cell of the study calendar. */
    data class CalendarDay(val epochDay: Long, val cards: Int)

    /**
     * The last [weeks] weeks as columns of 7 days (Monday first), oldest week first, ending with
     * the week that contains [today]; days after today are left out of the last column.
     */
    fun calendar(cardsByDay: Map<Long, Int>, today: Long, weeks: Int): List<List<CalendarDay>> {
        // 1970-01-01 (epoch day 0) was a Thursday → Monday-based day of week = (day + 3) mod 7.
        val mondayThisWeek = today - Math.floorMod(today + 3, 7L)
        val firstMonday = mondayThisWeek - 7L * (weeks - 1)
        return (0 until weeks).map { w ->
            (0 until 7).map { d -> firstMonday + 7L * w + d }
                .filter { it <= today }
                .map { CalendarDay(it, cardsByDay[it] ?: 0) }
        }
    }

    /** Cards answered per week for the same weeks as [calendar], oldest first. */
    fun weeklyTotals(calendar: List<List<CalendarDay>>): List<Int> = calendar.map { week -> week.sumOf { it.cards } }

    /**
     * Calendar shade 0..4 for a day relative to the daily [goal]: none, under half, under the
     * goal, met, doubled — a goal-relative scale reads better than raw counts.
     */
    fun intensity(cards: Int, goal: Int): Int = when {
        cards <= 0 -> 0
        cards * 2 < goal -> 1
        cards < goal -> 2
        cards < goal * 2 -> 3
        else -> 4
    }

    /** "1 день", "3 дня", "5 дней". */
    fun days(n: Int): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "дней"
            mod10 == 1 -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
        return "$n $word"
    }
}
