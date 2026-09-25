package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A nappy change logged on the nappy screen. Child-scoped like a feed. Changes marked on a feed
 * live on the feed's row, not here — see [com.oryareach.core.model.DiaperChange].
 */
@Entity(
    tableName = "diaper_changes",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
        Index(value = ["workspace_id", "baby_id", "changed_at"]),
    ],
)
data class DiaperChangeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "baby_id") val babyId: String,
    @ColumnInfo(name = "changed_at") val changedAt: Long,
    @ColumnInfo(name = "had_urine") val hadUrine: Boolean,
    @ColumnInfo(name = "had_stool") val hadStool: Boolean,
    val note: String?,
    @Embedded val sync: SyncMetaEntity,
)
