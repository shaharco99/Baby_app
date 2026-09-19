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

private const val CHANNEL_ID = "pump-reminders"
private const val NOTIFICATION_ID = 3

/**
 * Posts the "time to pump again" reminder. Generic for the same reason as
 * [FeedingReminderNotifier]: never a side, a duration or an amount — the notification tray is
 * outside this app's encryption model, and the lock screen shows it to whoever is holding the
 * phone.
 *
 * Its own channel and its own notification id, so silencing one reminder never silences the other
 * and a pending pump reminder never replaces a feeding one in the tray.
 */
object PumpReminderNotifier {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.pump_reminder_channel_name),
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
            .setContentTitle(context.getString(R.string.pump_reminder_title))
            .setContentText(context.getString(R.string.pump_reminder_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
