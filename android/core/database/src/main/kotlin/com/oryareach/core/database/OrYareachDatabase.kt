package com.oryareach.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.oryareach.core.database.dao.AppSettingsDao
import com.oryareach.core.database.dao.CachedCalendarEventDao
import com.oryareach.core.database.dao.CycleEntryDao
import com.oryareach.core.database.dao.DocumentDao
import com.oryareach.core.database.dao.FolderDao
import com.oryareach.core.database.dao.ImportantDateDao
import com.oryareach.core.database.dao.MenstrualCycleDao
import com.oryareach.core.database.dao.SearchDao
import com.oryareach.core.database.dao.ShoppingItemDao
import com.oryareach.core.database.dao.SyncOperationDao
import com.oryareach.core.database.dao.SyncStateDao
import com.oryareach.core.database.dao.TaskDao
import com.oryareach.core.database.entity.AppSettingsEntity
import com.oryareach.core.database.entity.CachedCalendarEventEntity
import com.oryareach.core.database.entity.CycleEntryEntity
import com.oryareach.core.database.entity.DocumentEntity
import com.oryareach.core.database.entity.FolderEntity
import com.oryareach.core.database.entity.ImportantDateEntity
import com.oryareach.core.database.entity.MenstrualCycleEntity
import com.oryareach.core.database.entity.SearchIndexEntity
import com.oryareach.core.database.entity.ShoppingItemEntity
import com.oryareach.core.database.entity.SyncConflictEntity
import com.oryareach.core.database.entity.SyncCursorEntity
import com.oryareach.core.database.entity.SyncOperationEntity
import com.oryareach.core.database.entity.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        MenstrualCycleEntity::class,
        ShoppingItemEntity::class,
        ImportantDateEntity::class,
        AppSettingsEntity::class,
        FolderEntity::class,
        DocumentEntity::class,
        CycleEntryEntity::class,
        SearchIndexEntity::class,
        SyncOperationEntity::class,
        SyncCursorEntity::class,
        SyncConflictEntity::class,
        CachedCalendarEventEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class OrYareachDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun menstrualCycleDao(): MenstrualCycleDao
    abstract fun cycleEntryDao(): CycleEntryDao
    abstract fun shoppingItemDao(): ShoppingItemDao
    abstract fun importantDateDao(): ImportantDateDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun searchDao(): SearchDao
    abstract fun syncOperationDao(): SyncOperationDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun cachedCalendarEventDao(): CachedCalendarEventDao

    companion object {
        const val NAME = "or-yareach.db"
    }
}
