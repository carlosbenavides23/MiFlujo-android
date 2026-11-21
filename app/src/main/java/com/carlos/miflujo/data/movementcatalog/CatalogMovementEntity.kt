package com.carlos.miflujo.data.movementcatalog

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.carlos.miflujo.data.category.CategoryEntity
import com.carlos.miflujo.data.category.SubcategoryEntity
import com.carlos.miflujo.domain.model.SyncStatus

@Entity(
    tableName = "movements",
    indices = [Index("uuid", unique = true), Index("category_id"), Index("subcategory_id")],
    foreignKeys = [
        ForeignKey(CategoryEntity::class, ["id"], ["category_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(SubcategoryEntity::class, ["id"], ["subcategory_id"], onDelete = ForeignKey.RESTRICT),
    ],
)
internal data class CatalogMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uuid: String,
    val type: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    @ColumnInfo(name = "subcategory_id")
    val subcategoryId: Long?,
    val detail: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "sync_status", defaultValue = "'LOCAL_ONLY'")
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(name = "last_synced_at_epoch_millis")
    val lastSyncedAt: Long? = null,
    @ColumnInfo(name = "deleted_at_epoch_millis")
    val deletedAt: Long? = null,
)
