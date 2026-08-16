package com.oryareach.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oryareach.core.database.entity.CachedCalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedCalendarEventDao {

    @Query("SELECT * FROM cached_calendar_events WHERE calendar_id IN (:calendarIds) ORDER BY start_at ASC")
    fun observeForCalendars(calendarIds: List<String>): Flow<List<CachedCalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CachedCalendarEventEntity>)

    @Query("DELETE FROM cached_calendar_events WHERE calendar_id = :calendarId")
    suspend fun deleteForCalendar(calendarId: String)

    @Query("DELETE FROM cached_calendar_events")
    suspend fun clearAll()

    /** Drops cached rows for calendars the user has since deselected, so an unselected
     * calendar's events do not linger in the cache forever. */
    @Query("DELETE FROM cached_calendar_events WHERE calendar_id NOT IN (:keepCalendarIds)")
    suspend fun deleteForCalendarsNotIn(keepCalendarIds: List<String>)
}
