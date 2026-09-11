package com.wordly.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wordly.app.R

/** Posts the "review session finished" notification. */
class Notifier(private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Повторение слов",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Уведомление о завершении повторения" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
    }
}
