package com.wordly.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wordly.app.WordlyApp
import com.wordly.app.data.AppSettings
import com.wordly.app.domain.Srs
import com.wordly.app.domain.StudyStats
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * The once-a-day nudge: "let's learn some new words" plus the current streak (of the profile the
 * app opens to), with the counts of new and due words. Text is built by [StudyStats.reminderText].
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as WordlyApp).container
        val profile = container.profilePreferences.defaultProfile.value
        val repository = container.repository
        val today = LocalDate.now().toEpochDay()
        val studied = repository.observeStudyDays(profile.id).first().filter { it.cards > 0 }.map { it.epochDay }.toSet()

        val words = repository.getAllOrdered(profile.id)
        val benchmark = repository.currentBenchmark(profile.id)
        val (title, text) = StudyStats.reminderText(
            streak = StudyStats.streak(studied, today),
            studiedToday = today in studied,
            newCount = words.count { it.orderIndex > benchmark },
            dueCount = Srs.dueCount(words, benchmark, System.currentTimeMillis()),
        )
        container.notifier.studyReminder(title, text)
        return Result.success()
    }
}

object ReminderScheduler {

    private const val WORK_NAME = "daily_reminder"

    /**
     * (Re)schedules — or cancels — the daily reminder to match [reminder]. [replace] = false (app
     * start) keeps an already-scheduled job as is, so launching the app doesn't shift its timing;
     * changing the time in Settings replaces it.
     */
    fun apply(context: Context, reminder: AppSettings.Reminder, replace: Boolean = true) {
        val workManager = WorkManager.getInstance(context)
        if (!reminder.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(reminder.hour, reminder.minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).seconds, TimeUnit.SECONDS)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }
}
