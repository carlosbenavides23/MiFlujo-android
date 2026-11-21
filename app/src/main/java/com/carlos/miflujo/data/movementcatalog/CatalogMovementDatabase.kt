package com.carlos.miflujo.data.movementcatalog

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carlos.miflujo.data.category.CatalogInstallationEntity
import com.carlos.miflujo.data.category.CategoryCatalogSchema
import com.carlos.miflujo.data.category.CategoryDao
import com.carlos.miflujo.data.category.CategoryEntity
import com.carlos.miflujo.data.category.SubcategoryEntity
import com.carlos.miflujo.data.local.SyncStatusConverters

@Dao
internal interface CatalogMovementDao {
    @Query("SELECT * FROM movements WHERE uuid = :uuid")
    suspend fun find(uuid: String): CatalogMovementEntity?

    @Query("""
        SELECT * FROM movements
        WHERE deleted_at_epoch_millis IS NULL AND sync_status != 'PENDING_DELETE'
        ORDER BY uuid COLLATE BINARY
    """)
    suspend fun visibleForBackup(): List<CatalogMovementEntity>

    @Query("""
        SELECT EXISTS (
            SELECT 1 FROM movements
            WHERE sync_status != 'LOCAL_ONLY' OR last_synced_at_epoch_millis IS NOT NULL
                OR deleted_at_epoch_millis IS NOT NULL
        )
    """)
    suspend fun hasSyncEvidence(): Boolean

    @Query("SELECT * FROM movements WHERE sync_status IN ('PENDING_UPLOAD', 'PENDING_DELETE', 'SYNC_ERROR') ORDER BY uuid")
    suspend fun pendingForSync(): List<CatalogMovementEntity>

    @Query("DELETE FROM movements")
    suspend fun clearForRestore()

    @Insert
    suspend fun insert(movement: CatalogMovementEntity): Long

    @Update
    suspend fun update(movement: CatalogMovementEntity): Int
}

/** Candidate for fresh installs and migration tests. No builder is wired into the app. */
@Database(
    entities = [CatalogMovementEntity::class, CategoryEntity::class, SubcategoryEntity::class, CatalogInstallationEntity::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(SyncStatusConverters::class)
internal abstract class CatalogMovementDatabase : RoomDatabase() {
    abstract fun catalog(): CategoryDao
    abstract fun movements(): CatalogMovementDao

    companion object {
        val initializer = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                CategoryCatalogSchema.initialize(db)
                installMovementRelationshipGuards(db)
            }
        }
    }
}

internal fun installMovementRelationshipGuards(db: SupportSQLiteDatabase) {
    for (operation in listOf("INSERT", "UPDATE")) {
        db.execSQL(
            """CREATE TRIGGER movement_catalog_relationship_${operation.lowercase()} BEFORE $operation ON movements
                WHEN NEW.type NOT IN ('INCOME', 'EXPENSE') OR (NEW.category_id IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM categories WHERE id = NEW.category_id AND scope IN (NEW.type, 'BOTH')
                )) OR (NEW.subcategory_id IS NOT NULL AND (
                    NEW.category_id IS NULL OR NOT EXISTS (
                        SELECT 1 FROM subcategories WHERE id = NEW.subcategory_id AND categoryId = NEW.category_id
                    )
                ))
                BEGIN SELECT RAISE(ABORT, 'Invalid movement category relationship'); END""",
        )
    }
}
