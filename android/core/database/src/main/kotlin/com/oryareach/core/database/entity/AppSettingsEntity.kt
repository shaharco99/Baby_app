package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oryareach.core.model.AppSettings

/** One row per workspace, same outbox/sync machinery as every other synced table. */
@Entity(
    tableName = "app_settings",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
    ],
)
data class AppSettingsEntity(
    @PrimaryKey val id: String,
    /** ISO-8601 `yyyy-MM-dd`. */
    val dueDate: String,
    /** Kept only as the seed source for the first [BabyEntity]; see [com.oryareach.core.model.AppSettings]. */
    val babyName: String?,
    @ColumnInfo(name = "partner_one_name") val partnerOneName: String?,
    @ColumnInfo(name = "partner_two_name") val partnerTwoName: String?,
    /** `babies.id` of the child everything currently points at. */
    @ColumnInfo(name = "active_baby_id") val activeBabyId: String? = null,
    @ColumnInfo(name = "feed_interval_minutes") val feedIntervalMinutes: Int =
        AppSettings.DEFAULT_FEED_INTERVAL_MINUTES,
    @Embedded val sync: SyncMetaEntity,
)
