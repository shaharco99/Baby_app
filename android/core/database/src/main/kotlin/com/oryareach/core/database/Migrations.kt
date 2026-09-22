package com.oryareach.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds shopping items and important dates — Phase 3's port of the web app's remaining data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shopping_items` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `estimated_price` INTEGER,
                `actual_price` INTEGER,
                `priority` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `assignee` TEXT,
                `note` TEXT,
                `link` TEXT,
                `alternatives` TEXT NOT NULL,
                `chosen_alternative_id` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_sync_status` ON `shopping_items` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_updated_at` " +
                "ON `shopping_items` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_category` " +
                "ON `shopping_items` (`workspace_id`, `category`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `important_dates` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `wish` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_important_dates_sync_status` ON `important_dates` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_important_dates_workspace_id_updated_at` " +
                "ON `important_dates` (`workspace_id`, `updated_at`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_important_dates_date` ON `important_dates` (`date`)")
    }
}

/** Adds `app_settings` — the couple's shared due date and baby name, for the home dashboard. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `id` TEXT NOT NULL,
                `dueDate` TEXT NOT NULL,
                `babyName` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_app_settings_sync_status` ON `app_settings` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_app_settings_workspace_id_updated_at` " +
                "ON `app_settings` (`workspace_id`, `updated_at`)",
        )
    }
}

/** Adds `folders` — the shared document tree (Phase 4). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `folders` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `parent_id` TEXT,
                `path` TEXT NOT NULL,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_sync_status` ON `folders` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_workspace_id_updated_at` " +
                "ON `folders` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_workspace_id_parent_id` " +
                "ON `folders` (`workspace_id`, `parent_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_folders_workspace_id_path` " +
                "ON `folders` (`workspace_id`, `path`)",
        )
    }
}

/** Adds `documents` — file metadata; the bytes themselves live in Supabase Storage. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `documents` (
                `id` TEXT NOT NULL,
                `folder_id` TEXT,
                `name` TEXT NOT NULL,
                `mime_type` TEXT NOT NULL,
                `size_bytes` INTEGER NOT NULL,
                `sha256` TEXT NOT NULL,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_sync_status` ON `documents` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_updated_at` " +
                "ON `documents` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_folder_id` " +
                "ON `documents` (`workspace_id`, `folder_id`)",
        )
    }
}

/** Adds `task_id` — lets a document be attached to a task independently of folder placement. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `documents` ADD COLUMN `task_id` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_task_id` " +
                "ON `documents` (`workspace_id`, `task_id`)",
        )
    }
}

/** Adds `cycle_entries` (daily flow/symptoms/mood/pain/note logs, distinct from a period's
 * start/end in `menstrual_cycles`) and `documents.cycle_id`, so a document can be attached to
 * a logged period the same way one can already be attached to a task. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cycle_entries` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `flow` TEXT,
                `symptoms` TEXT NOT NULL,
                `mood` TEXT NOT NULL,
                `pain` TEXT,
                `note` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cycle_entries_sync_status` ON `cycle_entries` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_entries_workspace_id_updated_at` " +
                "ON `cycle_entries` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cycle_entries_workspace_id_date` " +
                "ON `cycle_entries` (`workspace_id`, `date`)",
        )

        db.execSQL("ALTER TABLE `documents` ADD COLUMN `cycle_id` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_cycle_id` " +
                "ON `documents` (`workspace_id`, `cycle_id`)",
        )
    }
}

/** Creates the FTS4 search index (see `SearchIndexEntity`'s doc comment for why FTS4, not the
 * FTS5 the ADR names — Room has no `@Fts5` annotation) and backfills it from every
 * already-synced row, so search works immediately rather than only for things edited after
 * the upgrade. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `search_index` USING FTS4(
                `entityType` UNINDEXED,
                `recordId` UNINDEXED,
                `workspaceId` UNINDEXED,
                `title`,
                `body`
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'task', id, workspace_id, title, COALESCE(note, '')
            FROM tasks WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'shopping_item', id, workspace_id, name, COALESCE(note, '')
            FROM shopping_items WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'important_date', id, workspace_id, title, COALESCE(wish, '')
            FROM important_dates WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'folder', id, workspace_id, name, ''
            FROM folders WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'document', id, workspace_id, name, ''
            FROM documents WHERE deleted_at IS NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'cycle', id, workspace_id, '', COALESCE(note, '')
            FROM menstrual_cycles WHERE deleted_at IS NULL AND note IS NOT NULL
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO search_index (entityType, recordId, workspaceId, title, body)
            SELECT 'cycle_entry', id, workspace_id, '', COALESCE(note, '')
            FROM cycle_entries WHERE deleted_at IS NULL AND note IS NOT NULL
            """.trimIndent(),
        )
    }
}

/** Adds recurrence to tasks — `null`/`null` means "does not repeat", the default for every
 * existing row. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrence_frequency` TEXT")
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrence_interval` INTEGER")
    }
}

/** Adds free-form tags to tasks, stored as a JSON array (same pattern as `cycle_entries`'
 * `symptoms`/`mood`) — `'[]'` for every existing row. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `tags` TEXT NOT NULL DEFAULT '[]'")
    }
}

/** Adds a small on-device-generated thumbnail for image documents — see `Document`'s doc
 * comment for why this rides along in the metadata record instead of being fetched from
 * Storage. Null for every existing row and every non-image document; nothing backfills it,
 * since there is no thumbnail to generate without the original bytes in hand (which would
 * mean downloading and decrypting every existing image document during a migration — an
 * unbounded, network-dependent operation a schema migration must never do). */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `documents` ADD COLUMN `thumbnail_base64` TEXT")
    }
}

/** `estimated_price`/`actual_price` were `INTEGER`, so a form entry like "5804.25" got its
 * decimal point stripped before parsing and landed as 580425. Widens both columns (and the
 * JSON-serialized `alternatives.price` field, which needs no migration) to `REAL`. SQLite has
 * no `ALTER COLUMN`, so the table is rebuilt. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `shopping_items_new` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `estimated_price` REAL,
                `actual_price` REAL,
                `priority` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `assignee` TEXT,
                `note` TEXT,
                `link` TEXT,
                `alternatives` TEXT NOT NULL,
                `chosen_alternative_id` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `shopping_items_new`
            SELECT `id`, `name`, `category`, `estimated_price`, `actual_price`, `priority`, `status`,
                `assignee`, `note`, `link`, `alternatives`, `chosen_alternative_id`, `workspace_id`,
                `created_by`, `created_at`, `updated_at`, `deleted_at`, `version`, `sync_status`,
                `client_mutation_id`
            FROM `shopping_items`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `shopping_items`")
        db.execSQL("ALTER TABLE `shopping_items_new` RENAME TO `shopping_items`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_sync_status` ON `shopping_items` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_updated_at` " +
                "ON `shopping_items` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shopping_items_workspace_id_category` " +
                "ON `shopping_items` (`workspace_id`, `category`)",
        )
    }
}

/** Lets a document attach to a shopping item — e.g. a receipt photo — the same way one already
 * attaches to a task or a cycle entry. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `documents` ADD COLUMN `shopping_item_id` TEXT")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_documents_workspace_id_shopping_item_id` " +
                "ON `documents` (`workspace_id`, `shopping_item_id`)",
        )
    }
}

/** Adds `cached_calendar_events` — the Google Calendar read-only integration's local cache
 * (phase 1, docs/specs/03-google-calendar-integration.md). Deliberately not a synced entity: no
 * `sync_status`/`workspace_id`/etc columns, and not part of [RoomSyncStore]'s `EntityType` set —
 * see [CachedCalendarEventEntity]'s doc comment. */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_calendar_events` (
                `id` TEXT NOT NULL,
                `calendar_id` TEXT NOT NULL,
                `event_id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `start_at` TEXT NOT NULL,
                `end_at` TEXT NOT NULL,
                `all_day` INTEGER NOT NULL,
                `fetched_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_calendar_events_calendar_id` " +
                "ON `cached_calendar_events` (`calendar_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_calendar_events_start_at` " +
                "ON `cached_calendar_events` (`start_at`)",
        )
    }
}

/** Lets the couple name themselves (`app_settings`) instead of seeing "Partner 1"/"Partner 2",
 * and lets a shopping item's "other" assignee carry an optional name instead of the generic
 * label (`shopping_items`) — see [Assignee]'s doc comment. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `partner_one_name` TEXT")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `partner_two_name` TEXT")
        db.execSQL("ALTER TABLE `shopping_items` ADD COLUMN `custom_assignee_name` TEXT")
    }
}

/** Lets a shopping item record when it was bought (`purchase_date`) and how many months of
 * warranty it carries (`warranty_months`); the warranty-end date is derived, never stored. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `shopping_items` ADD COLUMN `purchase_date` TEXT")
        db.execSQL("ALTER TABLE `shopping_items` ADD COLUMN `warranty_months` INTEGER")
    }
}

/** Adds `babies` and `feeding_entries`, plus the two `app_settings` columns that point at the
 * active child and set how long after a feed the reminder fires. `app_settings.baby_name` and
 * `due_date` stay where they are: the one-time seed of the first `babies` row reads them, so
 * an existing install's current pregnancy is not lost. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `babies` (
                `id` TEXT NOT NULL,
                `name` TEXT,
                `due_date` TEXT,
                `birth_date` TEXT,
                `birth_time` TEXT,
                `birth_weight_grams` INTEGER,
                `birth_place` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_babies_sync_status` ON `babies` (`sync_status`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_babies_workspace_id_updated_at` " +
                "ON `babies` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_babies_workspace_id_created_at` " +
                "ON `babies` (`workspace_id`, `created_at`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `feeding_entries` (
                `id` TEXT NOT NULL,
                `baby_id` TEXT NOT NULL,
                `fed_at` INTEGER NOT NULL,
                `feed_type` TEXT NOT NULL,
                `amount_ml` INTEGER,
                `had_urine` INTEGER NOT NULL,
                `had_stool` INTEGER NOT NULL,
                `note` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_feeding_entries_sync_status` " +
                "ON `feeding_entries` (`sync_status`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_feeding_entries_workspace_id_updated_at` " +
                "ON `feeding_entries` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_feeding_entries_workspace_id_baby_id_fed_at` " +
                "ON `feeding_entries` (`workspace_id`, `baby_id`, `fed_at`)",
        )

        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `active_baby_id` TEXT")
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `feed_interval_minutes` INTEGER NOT NULL DEFAULT 180")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pump_sessions` (
                `id` TEXT NOT NULL,
                `started_at` INTEGER NOT NULL,
                `ended_at` INTEGER,
                `side` TEXT NOT NULL,
                `amount_ml` INTEGER,
                `note` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pump_sessions_sync_status` " +
                "ON `pump_sessions` (`sync_status`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pump_sessions_workspace_id_updated_at` " +
                "ON `pump_sessions` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pump_sessions_workspace_id_started_at` " +
                "ON `pump_sessions` (`workspace_id`, `started_at`)",
        )

        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `pump_interval_minutes` INTEGER NOT NULL DEFAULT 180")
    }
}

/**
 * Pausing a pump session.
 *
 * Both columns are additive, so nothing existing has to be rewritten: a session recorded before
 * this migration has never been paused, which `0` and `NULL` say exactly.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `pump_sessions` ADD COLUMN `paused_millis` INTEGER NOT NULL DEFAULT 0",
        )
        // No DEFAULT: a column added without one is NULL on every existing row, which is what
        // "has never been paused" means, and Room's schema has no default here to match.
        db.execSQL("ALTER TABLE `pump_sessions` ADD COLUMN `paused_at` INTEGER")
    }
}

/**
 * A feed that was breast *and* formula.
 *
 * Both columns are additive and nullable, so no existing row has to be rewritten to be valid.
 * The backfill moves each old single amount into the column its type says it belonged to, which
 * is what keeps the day totals and the history rows reading the same before and after.
 *
 * `amount_ml` is deliberately left in place rather than dropped. It is now a mirror of the
 * total, and it is the only amount a partner still on a pre-split build can read — dropping it
 * would blank out every feed on the other phone until both are updated.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No DEFAULT: NULL on every existing row is exactly "this source was not used", and
        // Room's schema has no default here to match.
        db.execSQL("ALTER TABLE `feeding_entries` ADD COLUMN `breast_ml` INTEGER")
        db.execSQL("ALTER TABLE `feeding_entries` ADD COLUMN `formula_ml` INTEGER")

        db.execSQL(
            "UPDATE `feeding_entries` SET `breast_ml` = `amount_ml` " +
                "WHERE `feed_type` = 'BREAST_MILK' AND `amount_ml` IS NOT NULL",
        )
        db.execSQL(
            "UPDATE `feeding_entries` SET `formula_ml` = `amount_ml` " +
                "WHERE `feed_type` = 'FORMULA' AND `amount_ml` IS NOT NULL",
        )
        // A SOLID feed keeps its amount in `amount_ml` alone: it is neither breast nor formula,
        // and the total falls back to it.
    }
}

/**
 * The daily supplement dose, and the hour the reminder for it fires.
 *
 * The table is a new one, so nothing existing changes; the settings column is additive and
 * nullable, and null is exactly what "no vitamin reminder set" means, so no row has to be
 * rewritten to be valid.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vitamin_doses` (
                `id` TEXT NOT NULL,
                `baby_id` TEXT NOT NULL,
                `given_at` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `note` TEXT,
                `workspace_id` TEXT NOT NULL,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                `deleted_at` INTEGER,
                `version` INTEGER NOT NULL,
                `sync_status` TEXT NOT NULL,
                `client_mutation_id` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vitamin_doses_sync_status` " +
                "ON `vitamin_doses` (`sync_status`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vitamin_doses_workspace_id_updated_at` " +
                "ON `vitamin_doses` (`workspace_id`, `updated_at`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vitamin_doses_workspace_id_baby_id_given_at` " +
                "ON `vitamin_doses` (`workspace_id`, `baby_id`, `given_at`)",
        )

        // No DEFAULT: NULL on every existing row is "no vitamin reminder set", which is the
        // right starting point and what Room's schema expects to find here.
        db.execSQL("ALTER TABLE `app_settings` ADD COLUMN `vitamin_d_minute_of_day` INTEGER")
    }
}
