package com.oryareach.core.database

import android.content.Context
import androidx.room.Room
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the encrypted local database.
 *
 * Everything the app stores locally — tasks, cycle records, notes — sits inside this file,
 * so it is encrypted at rest with SQLCipher rather than relying on the app sandbox alone.
 * A rooted device, an offline disk image or an ADB backup yields ciphertext.
 */
object DatabaseFactory {

    fun create(context: Context, passphrase: DatabasePassphrase): OrYareachDatabase {
        System.loadLibrary("sqlcipher")

        val key = passphrase.get()
        // SupportOpenHelperFactory keeps a reference to the array, so it cannot be zeroed
        // here; SQLCipher wipes it once the database is opened.
        val factory = SupportOpenHelperFactory(key)

        return Room.databaseBuilder(context, OrYareachDatabase::class.java, OrYareachDatabase.NAME)
            .openHelperFactory(factory)
            // No fallbackToDestructiveMigration: losing local data on a schema change would
            // discard anything not yet synced. A missing migration must fail loudly instead.
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
            )
            .build()
    }
}
