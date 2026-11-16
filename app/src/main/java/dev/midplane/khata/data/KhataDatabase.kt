// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Org::class, Client::class, Due::class, Payment::class, DismissedSms::class],
    version = 2,
    exportSchema = true,
)
abstract class KhataDatabase : RoomDatabase() {
    abstract fun dao(): KhataDao

    companion object {
        fun build(context: Context): KhataDatabase =
            Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: organisations, and an optional org on each due. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `orgs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `dues` ADD COLUMN `orgId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dues_orgId` ON `dues` (`orgId`)")
            }
        }
    }
}
