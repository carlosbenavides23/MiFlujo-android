package com.carlos.miflujo.data.local

import androidx.room.TypeConverter
import com.carlos.miflujo.domain.model.SyncStatus

class SyncStatusConverters {
    @TypeConverter
    fun toStoredValue(value: SyncStatus): String = value.name

    @TypeConverter
    fun fromStoredValue(value: String): SyncStatus = SyncStatus.valueOf(value)
}
