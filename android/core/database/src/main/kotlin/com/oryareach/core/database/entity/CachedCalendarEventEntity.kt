package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Device-local cache of Google Calendar events (phase 1,
 * docs/specs/03-google-calendar-integration.md) — date + title only, per that spec's
 * free/busy-level decision.
 *
 * Deliberately outside the synced entity set: this is a read-through cache of another service's
 * data, not something this app's user created, so it has no [SyncMetaEntity], no
 * `sync_status`/`client_mutation_id`, and is never touched by [com.oryareach.core.database.sync.RoomSyncStore]
 * or its `EntityType` — see `docs/architecture/001-android-architecture.md`'s "network never on
 * render path" rule for why it is cached at all rather than fetched inline.
 */
@Entity(
    tableName = "cached_calendar_events",
    indices = [
        Index(value = ["calendar_id"]),
        Index(value = ["start_at"]),
    ],
)
data class CachedCalendarEventEntity(
    /** `"{calendarId}::{googleEventId}"` — Google event ids are only unique within a single
     * calendar, and one device can cache events from more than one selected calendar. */
    @PrimaryKey val id: String,
    @ColumnInfo(name = "calendar_id") val calendarId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    val title: String,
    /** ISO-8601 local date-time (`LocalDateTime.toString()`), no zone — Google's event times are
     * resolved to the device's zone before caching, same as everywhere else in this app. */
    @ColumnInfo(name = "start_at") val startAt: String,
    @ColumnInfo(name = "end_at") val endAt: String,
    @ColumnInfo(name = "all_day") val allDay: Boolean,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
