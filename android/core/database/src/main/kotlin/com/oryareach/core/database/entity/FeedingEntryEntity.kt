package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oryareach.core.model.FeedType

/**
 * One logged feed. [babyId] is a plain column, not an `@ForeignKey`: this schema keeps no
 * DB-level foreign keys anywhere, because rows arrive from sync in no guaranteed order and a
 * constraint would reject a child's feed that landed before the child did.
 */
@Entity(
    tableName = "feeding_entries",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
        // Every read is "this child's feeds, over this stretch of time".
        Index(value = ["workspace_id", "baby_id", "fed_at"]),
    ],
)
data class FeedingEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "baby_id") val babyId: String,
    /** Epoch millis — the countdown is arithmetic on an instant, not on a calendar day. */
    @ColumnInfo(name = "fed_at") val fedAt: Long,
    @ColumnInfo(name = "feed_type") val feedType: FeedType,
    /** Null when no breast milk was given, or when it was given but not measured. */
    @ColumnInfo(name = "breast_ml") val breastMl: Int?,
    /** Null when no formula was given. */
    @ColumnInfo(name = "formula_ml") val formulaMl: Int?,
    /** Mirror of the total, kept only so a partner on an older build still reads a number. */
    @ColumnInfo(name = "amount_ml") val amountMl: Int?,
    @ColumnInfo(name = "had_urine") val hadUrine: Boolean,
    @ColumnInfo(name = "had_stool") val hadStool: Boolean,
    /** See [com.oryareach.core.model.FeedingEntry.diaperChanged]. */
    @ColumnInfo(name = "diaper_changed", defaultValue = "1") val diaperChanged: Boolean = true,
    val note: String?,
    @Embedded val sync: SyncMetaEntity,
)
