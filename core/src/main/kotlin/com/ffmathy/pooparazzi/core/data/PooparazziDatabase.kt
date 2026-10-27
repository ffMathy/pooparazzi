package com.ffmathy.pooparazzi.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The app's local store. Each device keeps its own copy; sync merges them with last-writer-wins. */
@Database(
    entities = [EntryEntity::class, PurgedEntryEntity::class],
    version = 1,
    exportSchema = true,
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
