package com.oryareach.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per child. Same outbox/sync machinery as every other synced table. */
@Entity(
    tableName = "babies",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["workspace_id", "updated_at"]),
        // The switcher lists children newest first; creation order doubles as birth order,
        // which holds even for a child added before their birth date is known.
        Index(value = ["workspace_id", "created_at"]),
    ],
)
data class BabyEntity(
    @PrimaryKey val id: String,
    val name: String?,
    /** ISO-8601 `yyyy-MM-dd`, null once the due date stops mattering. */
    @ColumnInfo(name = "due_date") val dueDate: String?,
    /** ISO-8601 `yyyy-MM-dd`. Null means "not born yet" — the home page branches on it. */
    @ColumnInfo(name = "birth_date") val birthDate: String?,
    /** ISO-8601 `HH:mm[:ss]`, local time at the place of birth. */
    @ColumnInfo(name = "birth_time") val birthTime: String?,
    @ColumnInfo(name = "birth_weight_grams") val birthWeightGrams: Int?,
    @ColumnInfo(name = "birth_place") val birthPlace: String?,
    @Embedded val sync: SyncMetaEntity,
)
