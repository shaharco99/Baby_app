package com.oryareach.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Rings a reminder armed by [ReminderAlarms]. Not exported: only our own PendingIntent reaches it. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ReminderAlarms.onFired(context, intent)
}

/**
 * Puts the reminders back after the system dropped them: a reboot, this app being updated, or the
 * clock or time zone changing under a wall-clock alarm. Exported only because those broadcasts
 * come from the system; all it can do is re-arm what was already stored.
 */
class ReminderRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> ReminderAlarms.rearmAll(context)
        }
    }
}
