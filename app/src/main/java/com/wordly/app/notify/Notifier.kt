package com.wordly.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wordly.app.MainActivity
import com.wordly.app.R

/** Posts the "review session finished" and daily study-reminder notifications. */
class Notifier(private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Повторение слов",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Уведомление о завершении повторения" }
        val reminders = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Напоминания",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Ежедневное напоминание позаниматься" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, reminders))
    }

    fun studyReminder(title: String, text: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text.lines().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    fun reviewFinished(cardCount: Int) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Повторение завершено")
            .setContentText("Вы прошли все карточки — $cardCount шт.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private companion object {
        const val CHANNEL_ID = "review"
        const val NOTIFICATION_ID = 1001
        const val REMINDER_CHANNEL_ID = "reminder"
        const val REMINDER_NOTIFICATION_ID = 1002
    }
}
