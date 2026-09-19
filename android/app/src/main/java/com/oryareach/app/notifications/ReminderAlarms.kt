package com.oryareach.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.WorkManager

/** The one-shot reminders that move with every logged entry. The daily nag is not one of them. */
enum class ReminderKind(internal val requestCode: Int, internal val prefKey: String) {
    FEEDING(requestCode = 2, prefKey = "feeding-due-at"),
    PUMP(requestCode = 3, prefKey = "pump-due-at"),
    ;

    internal fun show(context: Context) = when (this) {
        FEEDING -> FeedingReminderNotifier.show(context)
        PUMP -> PumpReminderNotifier.show(context)
    }

    internal fun ensureChannel(context: Context) = when (this) {
        FEEDING -> FeedingReminderNotifier.ensureChannel(context)
        PUMP -> PumpReminderNotifier.ensureChannel(context)
    }
}

/**
 * Arms the feed and pump reminders as exact `AlarmManager` alarms.
 *
 * Not WorkManager: a delayed job is inexact by design, Doze pushes it back by tens of minutes to
 * hours, and on MIUI it simply never ran once the app was out of recents — a 3am feed reminder
 * that turns up at 4:10 is no reminder. `setExactAndAllowWhileIdle` fires on time through Doze.
 *
 * The pending due time is kept in plain preferences, and only the time — nothing from the
 * workspace. That is what lets [ReminderRearmReceiver] put the alarm back after a reboot, when the
 * workspace key is still locked away and the database can't be asked.
 */
object ReminderAlarms {

    private const val PREFS = "reminder-alarms"
    private const val EXTRA_KIND = "kind"

    /**
     * Arms [kind] for [dueAtEpochMillis], replacing whatever was pending. A time already past is
     * not rung: the app's own countdown already reads "overdue", and a notification the moment a
     * late feed is logged would only be noise.
     */
    fun schedule(context: Context, kind: ReminderKind, dueAtEpochMillis: Long, now: Long = System.currentTimeMillis()) {
        if (dueAtEpochMillis <= now) {
            cancel(context, kind)
            return
        }
        prefs(context).edit().putLong(kind.prefKey, dueAtEpochMillis).apply()
        arm(context, kind, dueAtEpochMillis)
    }

    fun cancel(context: Context, kind: ReminderKind) {
        prefs(context).edit().remove(kind.prefKey).apply()
        alarmManager(context)?.cancel(pendingIntent(context, kind))
    }

    /**
     * After a reboot, an update or a clock change: the system dropped every alarm, so arm again
     * from the stored due times. One that came due while the phone was off is rung now — it was
     * armed and never got the chance.
     */
    fun rearmAll(context: Context, now: Long = System.currentTimeMillis()) {
        for (kind in ReminderKind.entries) {
            val dueAt = prefs(context).getLong(kind.prefKey, 0L).takeIf { it > 0L } ?: continue
            if (dueAt <= now) {
                prefs(context).edit().remove(kind.prefKey).apply()
                kind.ensureChannel(context)
                kind.show(context)
            } else {
                arm(context, kind, dueAt)
            }
        }
    }

    /** Called by [ReminderAlarmReceiver] when an alarm rings. */
    internal fun onFired(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_KIND)
            ?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            ?: return
        prefs(context).edit().remove(kind.prefKey).apply()
        kind.ensureChannel(context)
        kind.show(context)
    }

    /**
     * Releases before this one queued these reminders in WorkManager. Left alone, an update would
     * ring the old job and the new alarm for the same feed.
     */
    fun dropLegacyWork(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork("feeding-reminder-next")
            cancelUniqueWork("pump-reminder-next")
        }
    }

    private fun arm(context: Context, kind: ReminderKind, dueAtEpochMillis: Long) {
        val alarms = alarmManager(context) ?: return
        kind.ensureChannel(context)
        val operation = pendingIntent(context, kind)
        // USE_EXACT_ALARM is granted at install on 33+, and SCHEDULE_EXACT_ALARM by default on
        // 31–32; the check is for a user who revoked it. Inexact-but-Doze-proof is the fallback.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtEpochMillis, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtEpochMillis, operation)
        }
    }

    private fun pendingIntent(context: Context, kind: ReminderKind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            kind.requestCode,
            Intent(context, ReminderAlarmReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun alarmManager(context: Context) = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
