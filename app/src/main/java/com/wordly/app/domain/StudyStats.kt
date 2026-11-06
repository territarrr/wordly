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
