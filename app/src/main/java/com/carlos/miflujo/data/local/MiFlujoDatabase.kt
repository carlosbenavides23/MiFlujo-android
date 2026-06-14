package com.carlos.miflujo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.carlos.miflujo.data.model.MovementEntity

@Database(
    entities = [MovementEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(SyncStatusConverters::class)
abstract class MiFlujoDatabase : RoomDatabase() {
    abstract fun movementDao(): MovementDao
}
