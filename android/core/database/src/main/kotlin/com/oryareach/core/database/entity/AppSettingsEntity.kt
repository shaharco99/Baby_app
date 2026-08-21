package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val babyName: String?,
    @ColumnInfo(name = "partner_one_name") val partnerOneName: String?,
    @ColumnInfo(name = "partner_two_name") val partnerTwoName: String?,
    @Embedded val sync: SyncMetaEntity,
)
