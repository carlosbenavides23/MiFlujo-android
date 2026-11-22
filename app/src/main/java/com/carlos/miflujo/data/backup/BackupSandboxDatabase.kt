package com.carlos.miflujo.data.backup

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carlos.miflujo.data.category.*

/** Staging rows for backup experiments. NOT MovementEntity and never read by financial reports. */
@Entity(
    tableName = "backup_movements",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SubcategoryEntity::class, parentColumns = ["id"], childColumns = ["subcategoryId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["categoryId"]), Index(value = ["subcategoryId"])],
)
data class BackupMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String, val type: String, val amountMinor: Long, val currency: String, val date: String,
    val categoryId: Long?, val subcategoryId: Long?, val detail: String?,
    val createdAt: String, val updatedAt: String, val syncStatus: String = "LOCAL_ONLY",
)

@Dao
interface BackupMovementDao {
    @Insert suspend fun insert(movement: BackupMovementEntity): Long
    @Query("SELECT * FROM backup_movements ORDER BY uuid COLLATE BINARY") suspend fun all(): List<BackupMovementEntity>
    @Query("DELETE FROM backup_movements") suspend fun clear()
}

/** Independent database for atomic catalog + movement backup experiments, no app injection. */
@Database(entities = [CategoryEntity::class, SubcategoryEntity::class, CatalogInstallationEntity::class, BackupMovementEntity::class], version = 2, exportSchema = true)
abstract class BackupSandboxDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao
    abstract fun movements(): BackupMovementDao
    companion object {
        internal val relationshipTriggers = listOf(
            """CREATE TRIGGER backup_relationship_insert BEFORE INSERT ON backup_movements
                WHEN NEW.subcategoryId IS NOT NULL AND (NEW.categoryId IS NULL OR NOT EXISTS
                    (SELECT 1 FROM subcategories WHERE id = NEW.subcategoryId AND categoryId = NEW.categoryId))
                BEGIN SELECT RAISE(ABORT, 'Invalid backup movement relationship'); END""",
            """CREATE TRIGGER backup_relationship_update BEFORE UPDATE ON backup_movements
                WHEN NEW.subcategoryId IS NOT NULL AND (NEW.categoryId IS NULL OR NOT EXISTS
                    (SELECT 1 FROM subcategories WHERE id = NEW.subcategoryId AND categoryId = NEW.categoryId))
                BEGIN SELECT RAISE(ABORT, 'Invalid backup movement relationship'); END""",
        )
        val initializer = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                CategoryCatalogSchema.initialize(db)
                relationshipTriggers.forEach { db.execSQL(it) }
            }
        }
        fun openIsolated(context: Context): BackupSandboxDatabase = Room.databaseBuilder(
            context.applicationContext, BackupSandboxDatabase::class.java, "category-backup-sandbox.db",
        ).addCallback(initializer).addMigrations(CATALOG_ICON_COLOR_1_2).build()
    }
}
