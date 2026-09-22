package com.oryareach.core.database.reminder

import com.oryareach.core.database.repository.AppSettingsRepository
import com.oryareach.core.database.repository.BabyRepository
import com.oryareach.core.database.repository.VitaminDoseRepository
import com.oryareach.core.settings.VitaminReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Re-derives the pending vitamin reminder from what is actually in the database, for the same
 * reasons as [FeedingReminderRefresher] — and one more of its own: the dose may have been ticked
 * off on the *partner's* phone, which arrives by sync and should silence this phone's reminder
 * for the day without anyone touching it.
 *
 * The rule is short: no child born, or no hour set, means no reminder. Today's dose already
 * given means tomorrow. Otherwise the next time the clock reads the chosen hour.
 */
class VitaminReminderRefresher(
    private val babies: BabyRepository,
    private val vitamins: VitaminDoseRepository,
    private val settings: AppSettingsRepository,
    private val scheduler: VitaminReminderScheduler,
    private val workspaceId: () -> String?,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend fun refresh() {
        val workspace = workspaceId() ?: return
        val minuteOfDay = settings.observe(workspace).first()?.vitaminDMinuteOfDay
        val baby = babies.observeActive(workspace).first()

        if (minuteOfDay == null || baby == null || !baby.isBorn) {
            scheduler.cancel()
            return
        }

        val zone = timeZone()
        val today = Instant.fromEpochMilliseconds(now()).toLocalDateTime(zone).date
        val dayStart = today.atStartOfDayIn(zone).toEpochMilliseconds()
        val dayEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds() - 1
        val givenToday = vitamins.findInRange(workspace, baby.id, dayStart, dayEnd) != null

        val due = if (givenToday) {
            // Skip today's slot outright: today is done, and `nextOccurrence` would happily hand
            // back an hour that has not come round yet.
            tomorrowAt(minuteOfDay, today, zone)
        } else {
            scheduler.nextOccurrence(minuteOfDay)
        }
        scheduler.scheduleAt(due, minuteOfDay)
    }

    private fun tomorrowAt(
        minuteOfDay: Int,
        today: kotlinx.datetime.LocalDate,
        zone: TimeZone,
    ): Long = today.plus(1, DateTimeUnit.DAY)
        .atTime(kotlinx.datetime.LocalTime(minuteOfDay / 60, minuteOfDay % 60))
        .toInstant(zone)
        .toEpochMilliseconds()
}
