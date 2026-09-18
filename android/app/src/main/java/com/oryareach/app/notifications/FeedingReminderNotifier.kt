package com.oryareach.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oryareach.app.MainActivity
import com.oryareach.app.R

private const val CHANNEL_ID = "feeding-reminders"
private const val NOTIFICATION_ID = 2

/**
 * Posts the "time for the next feed" reminder, deliberately generic for the same reason as
 * [ReminderNotifier]: never the baby's name, the amount, or the feed type. The notification
 * tray is outside this app's encryption model — any app, and the lock screen, can read it.
 *
 * Its own channel rather than sharing "reminders": this one can arrive at 3am, and it should be
 * possible to silence the daily nag without silencing this.
 */
object FeedingReminderNotifier {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.feeding_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun show(context: Context) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.feeding_reminder_title))
            .setContentText(context.getString(R.string.feeding_reminder_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
