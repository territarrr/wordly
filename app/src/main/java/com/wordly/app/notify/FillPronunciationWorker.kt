package com.wordly.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wordly.app.WordlyApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Fills in a missing transcription (and pronunciation audio) for every word of a profile that has
 * none — e.g. the ~2000 words a 2Books import brings without one — using the same lookup as the
 * add-word screen. Never touches a word that already has them, nor its translation.
 *
 * WorkManager caps one run at ~10 minutes, so each run works for [RUN_BUDGET_MS], then hands
 * over to a fresh run that continues after the last word it reached ([KEY_AFTER]).
 */
class FillPronunciationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as WordlyApp).container.repository
        val profileId = inputData.getLong(KEY_PROFILE, 1L)
        var after = inputData.getLong(KEY_AFTER, 0L)
        var checked = inputData.getInt(KEY_CHECKED, 0)
        var filled = inputData.getInt(KEY_FILLED, 0)
        val total = inputData.getInt(KEY_TOTAL, -1).takeIf { it >= 0 } ?: repository.countMissingPronunciation(profileId, after)
        val started = System.currentTimeMillis()

        while (System.currentTimeMillis() - started < RUN_BUDGET_MS) {
            val batch = repository.wordsMissingPronunciation(profileId, after, BATCH)
            if (batch.isEmpty()) return Result.success(workDataOf(KEY_CHECKED to checked, KEY_FILLED to filled, KEY_TOTAL to total))
            for (word in batch) {
                if (isStopped) return Result.success()
                val found = repository.lookup(word.text).getOrNull()
                if (found != null && repository.fillPronunciation(word.id, found.transcription, found.audioUrl)) filled++
                after = word.orderIndex
                checked++
                setProgress(workDataOf(KEY_CHECKED to checked, KEY_FILLED to filled, KEY_TOTAL to total))
                delay(REQUEST_SPACING_MS) // stay well under Wiktionary's rate limit
            }
        }
        // Out of time for this run: continue in a fresh one from where this one stopped.
        enqueue(applicationContext, profileId, after, checked, filled, total)
        return Result.success()
    }

    /** Progress of a running fill, as shown on the word list. */
    data class Progress(val running: Boolean, val checked: Int, val filled: Int, val total: Int)

    companion object {
        private const val KEY_PROFILE = "profileId"
        private const val KEY_AFTER = "after"
        private const val KEY_CHECKED = "checked"
        private const val KEY_FILLED = "filled"
        private const val KEY_TOTAL = "total"
        private const val BATCH = 25
        private const val REQUEST_SPACING_MS = 400L
        private const val RUN_BUDGET_MS = 8L * 60 * 1000

        private fun workName(profileId: Long) = "fill_pronunciation_$profileId"

        /** Starts filling from the first word of [profileId] (restarting any run already going). */
        fun start(context: Context, profileId: Long) = enqueue(context, profileId, 0L, 0, 0, -1, restart = true)

        fun stop(context: Context, profileId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(profileId))
        }

        private fun enqueue(
            context: Context,
            profileId: Long,
            after: Long,
            checked: Int,
            filled: Int,
            total: Int,
            restart: Boolean = false,
        ) {
            val request = OneTimeWorkRequestBuilder<FillPronunciationWorker>()
                .setInputData(
                    workDataOf(
                        KEY_PROFILE to profileId,
                        KEY_AFTER to after,
                        KEY_CHECKED to checked,
                        KEY_FILLED to filled,
                        KEY_TOTAL to total,
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(profileId),
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }

        fun observe(context: Context, profileId: Long): Flow<Progress?> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(profileId)).map { infos ->
                val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                    ?: infos.lastOrNull { it.state == WorkInfo.State.SUCCEEDED }
                    ?: return@map null
                val data = if (info.state == WorkInfo.State.SUCCEEDED) info.outputData else info.progress
                Progress(
                    running = info.state != WorkInfo.State.SUCCEEDED,
                    checked = data.getInt(KEY_CHECKED, 0),
                    filled = data.getInt(KEY_FILLED, 0),
                    total = data.getInt(KEY_TOTAL, 0),
                )
            }
    }
}
