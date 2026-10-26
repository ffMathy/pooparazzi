package com.ffmathy.xaternity.core

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "spike")
data class SpikeRow(@PrimaryKey val id: String, val value: Long)

@Dao
interface SpikeDao {
    @Query("SELECT * FROM spike")
    fun all(): Flow<List<SpikeRow>>
}

@Database(entities = [SpikeRow::class], version = 1)
abstract class SpikeDb : RoomDatabase() {
    abstract fun dao(): SpikeDao
}

fun spikeGreeting(): String = "hello"
