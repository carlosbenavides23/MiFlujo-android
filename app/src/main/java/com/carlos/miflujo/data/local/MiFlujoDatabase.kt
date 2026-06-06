package com.carlos.miflujo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.carlos.miflujo.data.model.MovementEntity

@Database(
    entities = [MovementEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MiFlujoDatabase : RoomDatabase() {
    abstract fun movementDao(): MovementDao
}
