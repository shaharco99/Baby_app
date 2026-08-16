package com.oryareach.core.calendar

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.entity.CachedCalendarEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit

/**
 * Bridges [CalendarEventSource] (network) and [CachedCalendarEventDao] (Room) so
 * `:feature:calendar` only ever reads from Room on render — per
 * `docs/architecture/001-android-architecture.md`'s "network never on render path" rule — and a
 * separate, explicit [refresh] call is the only thing that touches the network.
 *
 * Not part of `:core:sync`/`SyncEngine`: that engine solves two-way, conflict-aware sync against
 * the couple's shared Supabase workspace. This is a one-way, single-account, on-demand read
 * cache with none of that machinery's concerns (see the spec's "Why one-way only for now").
 */
class GoogleCalendarSyncRepository(
    private val database: OrYareachDatabase,
    private val source: CalendarEventSource,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.cachedCalendarEventDao()

    fun observeCachedEvents(calendarIds: List<String>): Flow<List<GoogleCalendarEvent>> {
        if (calendarIds.isEmpty()) return flowOf(emptyList())
        return dao.observeForCalendars(calendarIds).map { list -> list.map { it.toDomain() } }
    }

    suspend fun fetchCalendarList(): Result<List<GoogleCalendarListEntry>> = source.fetchCalendarList()

    /** Re-fetches events for [calendarIds] over a fixed window around today and replaces the
     * cached rows for exactly those calendars — pull-to-refresh on the Calendar screen calls
     * this, same trigger shape as other screens' `SyncEngine.sync()`, though unrelated to it. */
    suspend fun refresh(calendarIds: List<String>): Result<Unit> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val from = today.minus(CACHE_WINDOW_PAST_DAYS, DateTimeUnit.DAY)
        val to = today.plus(CACHE_WINDOW_FUTURE_DAYS, DateTimeUnit.DAY)

        if (calendarIds.isEmpty()) {
            // `NOT IN ()` is invalid SQL — Room's collection-parameter expansion needs at least
            // one element — so an empty selection just clears everything instead.
            database.withTransaction { dao.clearAll() }
            return Result.success(Unit)
        }
        database.withTransaction { dao.deleteForCalendarsNotIn(calendarIds) }

        return source.fetchEvents(calendarIds, from, to).map { events ->
            val fetchedAt = now()
            database.withTransaction {
                calendarIds.forEach { dao.deleteForCalendar(it) }
                dao.insertAll(events.map { it.toEntity(fetchedAt) })
            }
        }
    }

    private companion object {
        const val CACHE_WINDOW_PAST_DAYS = 30
        const val CACHE_WINDOW_FUTURE_DAYS = 180
    }
}

private fun CachedCalendarEventEntity.toDomain(): GoogleCalendarEvent = GoogleCalendarEvent(
    id = eventId,
    calendarId = calendarId,
    title = title,
    startAt = LocalDateTime.parse(startAt),
    endAt = LocalDateTime.parse(endAt),
    allDay = allDay,
)

private fun GoogleCalendarEvent.toEntity(fetchedAt: Long): CachedCalendarEventEntity = CachedCalendarEventEntity(
    id = "$calendarId::$id",
    calendarId = calendarId,
    eventId = id,
    title = title,
    startAt = startAt.toString(),
    endAt = endAt.toString(),
    allDay = allDay,
    fetchedAt = fetchedAt,
)
