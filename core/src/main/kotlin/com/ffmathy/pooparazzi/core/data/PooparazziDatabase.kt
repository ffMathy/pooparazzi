package com.ffmathy.pooparazzi.core.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's local store. Each device keeps its own copy; sync merges them with last-writer-wins.
 *
 * Versions (schemas in `core/schemas`): 1, the first release; 2, several babies and the backup (an entry's
 * [EntryEntity.babyId] and [EntryEntity.cloudPending], migrated automatically: old rows are the first baby's and not
 * pending for the backend).
 */
@Database(
    entities = [EntryEntity::class, PurgedEntryEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class PooparazziDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        const val NAME = "pooparazzi.db"

        fun build(context: Context): PooparazziDatabase =
            Room.databaseBuilder(context.applicationContext, PooparazziDatabase::class.java, NAME).build()

        /** In-memory database for tests and previews. */
        fun inMemory(context: Context): PooparazziDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, PooparazziDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
