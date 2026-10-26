package com.ffmathy.xaternity.core.data

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
abstract class XaternityDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        const val NAME = "xaternity.db"

        fun build(context: Context): XaternityDatabase =
            Room.databaseBuilder(context.applicationContext, XaternityDatabase::class.java, NAME).build()

        /** In-memory database for tests and previews. */
        fun inMemory(context: Context): XaternityDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, XaternityDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
