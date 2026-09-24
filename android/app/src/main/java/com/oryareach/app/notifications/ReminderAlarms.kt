package com.oryareach.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.oryareach.app.widget.FeedWidget
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The exact reminders. The daily nag from [ReminderWorker] is not one of them.
 *
 * [FEEDING] and [PUMP] are one-shots that move with every logged entry. [VITAMIN_D] is the odd
 * one out: it is the same alarm every day at the same wall-clock time, so [repeatsDaily] marks
 * it as re-arming itself once it has rung, and as being worth nothing at all if it is late.
 */
enum class ReminderKind(
    internal val requestCode: Int,
    internal val prefKey: String,
    internal val repeatsDaily: Boolean = false,
) {
    FEEDING(requestCode = 2, prefKey = "feeding-due-at"),
    PUMP(requestCode = 3, prefKey = "pump-due-at"),
    VITAMIN_D(requestCode = 4, prefKey = "vitamin-d-due-at", repeatsDaily = true),
    ;

    internal fun show(context: Context) = when (this) {
        FEEDING -> FeedingReminderNotifier.show(context)
        PUMP -> PumpReminderNotifier.show(context)
        VITAMIN_D -> VitaminReminderNotifier.show(context)
    }

    internal fun ensureChannel(context: Context) = when (this) {
        FEEDING -> FeedingReminderNotifier.ensureChannel(context)
        PUMP -> PumpReminderNotifier.ensureChannel(context)
        VITAMIN_D -> VitaminReminderNotifier.ensureChannel(context)
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
     * The vitamin reminder's hour, as minutes past local midnight.
     *
     * Kept here, in plain preferences, and not only in the workspace row it comes from: after the
     * alarm rings, the next day's has to be armed from a broadcast receiver that may be running
     * with the app locked and the database key unavailable. Only the time of day is stored — no
     * child, no dose, nothing from the workspace.
     */
    private const val KEY_VITAMIN_MINUTE_OF_DAY = "vitamin-d-minute-of-day"

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

    /**
     * Arms the daily vitamin reminder for [dueAtEpochMillis] and remembers [minuteOfDay], which
     * is what lets the next day's alarm be set from the receiver without the database.
     */
    fun scheduleDaily(context: Context, dueAtEpochMillis: Long, minuteOfDay: Int, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putInt(KEY_VITAMIN_MINUTE_OF_DAY, minuteOfDay).apply()
        schedule(context, ReminderKind.VITAMIN_D, dueAtEpochMillis, now)
    }

    /**
     * Turns the daily vitamin reminder off for good, rather than just dropping the pending
     * alarm: the stored hour goes too, so nothing re-arms it. Plain [cancel] must not do this —
     * [schedule] falls back to it for a time already past, and that would break the chain.
     */
    fun cancelDaily(context: Context) {
        prefs(context).edit().remove(KEY_VITAMIN_MINUTE_OF_DAY).apply()
        cancel(context, ReminderKind.VITAMIN_D)
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
        // Same broadcasts the widget needs: after a reboot its chronometer base is meaningless.
        FeedWidget.refresh(context)
        for (kind in ReminderKind.entries) {
            val dueAt = prefs(context).getLong(kind.prefKey, 0L).takeIf { it > 0L } ?: continue
            if (kind.repeatsDaily) {
                // A daily reminder that was missed is not worth ringing late: "give the vitamin"
                // delivered at 14:10 for an 08:00 dose is noise, and the card on screen already
                // says whether today's was given. The next occurrence is armed instead.
                armNextDaily(context, now)
                continue
            }
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
        // The feed is due this moment: the widget's "next feed in" has to become "overdue by".
        if (kind == ReminderKind.FEEDING) FeedWidget.refresh(context)
        // A daily reminder arms the next one the moment it rings, so the chain survives even if
        // the app is never opened again. Whatever the app works out later — today's dose already
        // given, the time moved, the reminder turned off — replaces this.
        if (kind.repeatsDaily) armNextDaily(context)
    }

    /**
     * Arms the next occurrence of the stored vitamin hour, in the phone's own time zone.
     *
     * Deliberately not "the last due time plus 24 hours": across a daylight-saving change that
     * would walk the reminder an hour off the time that was actually chosen.
     */
    private fun armNextDaily(context: Context, now: Long = System.currentTimeMillis()) {
        val minuteOfDay = prefs(context).getInt(KEY_VITAMIN_MINUTE_OF_DAY, -1).takeIf { it >= 0 } ?: return
        schedule(context, ReminderKind.VITAMIN_D, nextOccurrence(minuteOfDay, now), now)
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

    /** The next moment the clock reads [minuteOfDay] locally: today if it is still ahead, else tomorrow. */
    fun nextOccurrence(
        minuteOfDay: Int,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long {
        val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
        val time = LocalTime(minuteOfDay / 60, minuteOfDay % 60)
        val todayAt = today.atTime(time).toInstant(zone).toEpochMilliseconds()
        return if (todayAt > now) {
            todayAt
        } else {
            today.plus(1, DateTimeUnit.DAY).atTime(time).toInstant(zone).toEpochMilliseconds()
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
