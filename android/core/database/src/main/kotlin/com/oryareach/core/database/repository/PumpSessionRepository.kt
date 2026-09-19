package com.oryareach.core.database.repository

import androidx.room.withTransaction
import com.oryareach.core.database.OrYareachDatabase
import com.oryareach.core.database.SearchIndexer
import com.oryareach.core.database.entity.PumpSessionEntity
import com.oryareach.core.database.entity.SyncMetaEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.mapper.toPumpSession
import com.oryareach.core.model.EntityType
import com.oryareach.core.model.PumpSession
import com.oryareach.core.model.PumpSide
import com.oryareach.core.model.SyncOperationType
import com.oryareach.core.model.SyncStatus
import com.oryareach.core.settings.PumpReminderScheduler
import com.oryareach.core.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The write path for pumping sessions.
 *
 * A session is written twice in the normal case: [start] inserts it with no end, [stop] fills the
 * end in. That is deliberate — the row *is* the timer's state, so a session in progress survives
 * leaving the screen, a force-stop and a reboot, and shows up on the partner's device.
 *
 * Only a finished session moves the reminder: an alarm scheduled at [start] would be right, but a
 * session that is abandoned rather than stopped would leave it standing.
 */
class PumpSessionRepository(
    private val database: OrYareachDatabase,
    private val syncTrigger: SyncTrigger,
    private val reminders: PumpReminderScheduler,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val sessions get() = database.pumpSessionDao()
    private val operations get() = database.syncOperationDao()
    private val search = SearchIndexer(database)

    fun observeInRange(workspaceId: String, start: Long, end: Long): Flow<List<PumpSession>> =
        sessions.observeInRange(workspaceId, start, end)
            .map { list -> list.map { it.toPumpSession() } }

    fun observeLatest(workspaceId: String): Flow<PumpSession?> =
        sessions.observeLatest(workspaceId).map { it?.toPumpSession() }

    fun observeRunning(workspaceId: String): Flow<PumpSession?> =
        sessions.observeRunning(workspaceId).map { it?.toPumpSession() }

    suspend fun findLatest(workspaceId: String): PumpSession? =
        sessions.findLatest(workspaceId)?.toPumpSession()

    suspend fun findRunning(workspaceId: String): PumpSession? =
        sessions.findRunning(workspaceId)?.toPumpSession()

    /**
     * Starts the timer. Returns the existing session's id if one is already running rather than
     * opening a second one: two overlapping sessions would make "how long" meaningless, and the
     * partner's device can press Start too.
     */
    suspend fun start(
        workspaceId: String,
        userId: String,
        side: PumpSide,
        startedAt: Long = now(),
    ): String {
        sessions.findRunning(workspaceId)?.let { return it.id }

        val timestamp = now()
        val entity = PumpSessionEntity(
            id = newId(),
            startedAt = startedAt,
            endedAt = null,
            side = side,
            amountMl = null,
            note = null,
            sync = SyncMetaEntity(
                workspaceId = workspaceId,
                createdBy = userId,
                createdAt = timestamp,
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPLOAD,
                clientMutationId = newId(),
            ),
        )

        database.withTransaction {
            sessions.upsert(entity)
            enqueue(entity.id, SyncOperationType.CREATE, entity.sync.clientMutationId, timestamp)
        }
        syncTrigger.syncNow()
        return entity.id
    }

    /**
     * A session logged after the fact, with no timer involved: the duration is expressed as an end
     * time so a typed session and a timed one are the same row.
     */
    suspend fun logManual(
        workspaceId: String,
        userId: String,
        side: PumpSide,
        startedAt: Long,
        durationMinutes: Int,
        amountMl: Int?,
        note: String?,
        intervalMinutes: Int,
    ): String {
        val timestamp = now()
        val entity = PumpSessionEntity(
            id = newId(),
            startedAt = startedAt,
            endedAt = startedAt + durationMinutes * MILLIS_PER_MINUTE,
            side = side,
            amountMl = amountMl,
            note = note,
            sync = SyncMetaEntity(
                workspaceId = workspaceId,
                createdBy = userId,
                createdAt = timestamp,
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPLOAD,
                clientMutationId = newId(),
            ),
        )

        database.withTransaction {
            sessions.upsert(entity)
            search.index(EntityType.PUMP_SESSION, entity.id, workspaceId, "", entity.note.orEmpty())
            enqueue(entity.id, SyncOperationType.CREATE, entity.sync.clientMutationId, timestamp)
        }
        rescheduleReminder(workspaceId, startedAt, intervalMinutes)
        syncTrigger.syncNow()
        return entity.id
    }

    /**
     * Pauses a running session. Nothing is subtracted yet — the open pause is remembered as the
     * moment it began, and only becomes time when the session resumes or stops. The reminder is
     * left alone: it is scheduled off the *start*, which a pause does not move.
     */
    suspend fun pause(id: String, at: Long = now()) {
        val existing = sessions.findById(id) ?: return
        if (existing.endedAt != null || existing.pausedAt != null) return
        write(existing, pausedAt = at)
    }

    /** Closes an open pause, folding however long it lasted into the time that does not count. */
    suspend fun resume(id: String, at: Long = now()) {
        val existing = sessions.findById(id) ?: return
        val pausedAt = existing.pausedAt ?: return
        if (existing.endedAt != null) return
        write(
            existing,
            pausedMillis = existing.pausedMillis + (at - pausedAt).coerceAtLeast(0),
            pausedAt = null,
        )
    }

    /**
     * Fills in the end of a running session and everything the sheet collected on the way out.
     *
     * A session stopped while paused closes the pause first, so the break between pressing Pause
     * and pressing Stop is counted as paused rather than as pumping.
     */
    suspend fun stop(
        id: String,
        side: PumpSide,
        endedAt: Long = now(),
        amountMl: Int?,
        note: String?,
        intervalMinutes: Int,
    ) {
        val existing = sessions.findById(id) ?: return
        val paused = existing.pausedAt
            ?.let { existing.pausedMillis + (endedAt - it).coerceAtLeast(0) }
            ?: existing.pausedMillis
        write(
            existing,
            endedAt = endedAt,
            side = side,
            amountMl = amountMl,
            note = note,
            pausedMillis = paused,
            pausedAt = null,
        )
        rescheduleReminder(existing.sync.workspaceId, existing.startedAt, intervalMinutes)
    }

    /**
     * Corrects a finished session. [startedAt] is not a parameter on purpose — the sheet shows it
     * read-only, because the whole log is arranged by it and the reminder was scheduled off it.
     * [durationMinutes] moves the *end*.
     */
    suspend fun update(
        id: String,
        side: PumpSide,
        durationMinutes: Int,
        amountMl: Int?,
        note: String?,
    ) {
        val existing = sessions.findById(id) ?: return
        write(
            existing,
            // The paused time is added back on, so the minutes typed in are the minutes the
            // session reads as: the duration is wall time *minus* what was paused.
            endedAt = existing.startedAt + durationMinutes * MILLIS_PER_MINUTE + existing.pausedMillis,
            side = side,
            amountMl = amountMl,
            note = note,
        )
    }

    suspend fun delete(id: String) {
        val timestamp = now()
        database.withTransaction {
            sessions.softDelete(id, timestamp)
            search.remove(id)
            enqueue(id, SyncOperationType.DELETE, newId(), timestamp)
        }
        syncTrigger.syncNow()
    }

    /**
     * The one write path for an existing session. Everything not passed keeps the value it had,
     * which is what lets a pause be a one-field change rather than a re-statement of the row.
     */
    private suspend fun write(
        existing: PumpSessionEntity,
        endedAt: Long? = existing.endedAt,
        side: PumpSide = existing.side,
        amountMl: Int? = existing.amountMl,
        note: String? = existing.note,
        pausedMillis: Long = existing.pausedMillis,
        pausedAt: Long? = existing.pausedAt,
    ) {
        val timestamp = now()
        val entity = existing.copy(
            endedAt = endedAt,
            side = side,
            amountMl = amountMl,
            note = note,
            pausedMillis = pausedMillis,
            pausedAt = pausedAt,
            sync = existing.sync.copy(
                updatedAt = timestamp,
                syncStatus = SyncStatus.PENDING_UPDATE,
                clientMutationId = newId(),
            ),
        )

        database.withTransaction {
            sessions.upsert(entity)
            search.index(
                EntityType.PUMP_SESSION,
                entity.id,
                entity.sync.workspaceId,
                "",
                entity.note.orEmpty(),
            )
            enqueue(entity.id, SyncOperationType.UPDATE, entity.sync.clientMutationId, timestamp)
        }
        syncTrigger.syncNow()
    }

    /**
     * Scheduled off the most recent session on record rather than the one just written: a session
     * entered retroactively, older than the last one, leaves the pending reminder where it is
     * instead of dragging it into the past.
     */
    private suspend fun rescheduleReminder(
        workspaceId: String,
        fallbackStartedAt: Long,
        intervalMinutes: Int,
    ) {
        val latest = sessions.findLatest(workspaceId)
        reminders.scheduleNext(latest?.startedAt ?: fallbackStartedAt, intervalMinutes)
    }

    private suspend fun enqueue(
        recordId: String,
        operation: SyncOperationType,
        clientMutationId: String?,
        timestamp: Long,
    ) {
        val opId = operations.enqueue(
            SyncOperationEntity(
                recordId = recordId,
                entityType = EntityType.PUMP_SESSION,
                operation = operation,
                clientMutationId = clientMutationId.orEmpty(),
                createdAt = timestamp,
            ),
        )
        operations.removeSuperseded(recordId, opId)
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
