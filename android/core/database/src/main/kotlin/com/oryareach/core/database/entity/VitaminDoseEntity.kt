package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oryareach.core.model.SupplementKind

/**
 * One dose of a daily supplement. Child-scoped like a feed, not workspace-scoped like a pumping
 * session: the dose belongs to whoever swallowed it, and it moves with the child switcher.
 *
 * There is no "missed" row. A day without a dose is a day with no record for it, which is what
 * keeps a partner's late tick from having to undo anything.
 */
@Entity(
    tableName = "vitamin_doses",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
        // Every read is "this child's doses, over this stretch of time".
        Index(value = ["workspace_id", "baby_id", "given_at"]),
    ],
)
data class VitaminDoseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "baby_id") val babyId: String,
    /** Epoch millis; the card reads the local day it falls in. */
    @ColumnInfo(name = "given_at") val givenAt: Long,
    val kind: SupplementKind,
    val note: String?,
    @Embedded val sync: SyncMetaEntity,
)
