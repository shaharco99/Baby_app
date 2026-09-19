package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oryareach.core.model.PumpSide

/**
 * One pumping session. Workspace-scoped, with no `baby_id`: unlike a feed this is the mother's
 * record, so it does not belong to a child and does not move with the child switcher.
 *
 * [endedAt] is null while the session is running, which is what makes the timer survive a reboot —
 * there is no in-memory state to lose. A pause is stored the same way, for the same reason: a
 * session paused on one phone reads as paused on the other.
 */
@Entity(
    tableName = "pump_sessions",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
        // Every read is "this workspace's sessions, over this stretch of time".
        Index(value = ["workspace_id", "started_at"]),
    ],
)
data class PumpSessionEntity(
    @PrimaryKey val id: String,
    /** Epoch millis — the countdown to the next pump is arithmetic on an instant. */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** Null while the session is still running. */
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    val side: PumpSide,
    @ColumnInfo(name = "amount_ml") val amountMl: Int?,
    val note: String?,
    /** Paused time already closed off. The duration is wall time minus this. */
    @ColumnInfo(name = "paused_millis", defaultValue = "0") val pausedMillis: Long = 0,
    /** Set only while a pause is open. */
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    @Embedded val sync: SyncMetaEntity,
)
