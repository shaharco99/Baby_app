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
 * there is no in-memory state to lose.
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
    @Embedded val sync: SyncMetaEntity,
)
