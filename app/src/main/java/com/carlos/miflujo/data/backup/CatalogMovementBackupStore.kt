package com.carlos.miflujo.data.backup

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.carlos.miflujo.data.category.CategoryDao
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDao
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.data.movementcatalog.CatalogMovementEntity
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Ownership of the destination store, independent of the current sign-in session. */
internal enum class CatalogRestoreDestination {
    LOCAL_ONLY,
    ACCOUNT_BOUND,
}

/** Experimental replacement of visible content in the 6A candidate. No sync client uses this database. */
internal class CatalogMovementBackupStore(private val database: CatalogMovementDatabase) {
    suspend fun snapshot(createdAt: LocalDateTime): CatalogBackup =
        database.readCatalogMovementSnapshot(database.catalog(), database.movements(), createdAt)

    suspend fun canRestore(): Boolean = database.withTransaction {
        !database.movements().hasSyncEvidence() && !database.catalog().readBackupCatalog().hasSyncEvidence
    }

    suspend fun restore(backup: CatalogBackup, destination: CatalogRestoreDestination) {
        check(destination == CatalogRestoreDestination.LOCAL_ONLY) {
            "Portable backups cannot replace an account-bound database"
        }
        val snapshot = backup.validatedCopy()
        val prepared = snapshot.movements.map { it to it.toCatalogMovementRow() }

        database.withTransaction {
            val movements = database.movements()
            val catalogDao = database.catalog()
            val currentCatalog = catalogDao.readBackupCatalog()
            check(!movements.hasSyncEvidence() && !currentCatalog.hasSyncEvidence) {
                "Replacement would discard local synchronization metadata or tombstones"
            }
            snapshot.requireCompatibleWith(currentCatalog.snapshot(snapshot.createdAt))

            replaceBackupContents(
                database.openHelper.writableDatabase, catalogDao, snapshot, movements::clearForRestore,
            ) { categoryIds, subcategoryIds ->
                for ((movement, row) in prepared) {
                    movements.insert(row.copy(
                        categoryId = movement.categoryUuid?.let(categoryIds::getValue),
                        subcategoryId = movement.subcategoryUuid?.let(subcategoryIds::getValue),
                    ))
                }
            }
        }
    }
}

internal suspend fun RoomDatabase.readCatalogMovementSnapshot(
    categoryDao: CategoryDao,
    movementDao: CatalogMovementDao,
    createdAt: LocalDateTime,
): CatalogBackup = withTransaction {
    val catalog = categoryDao.readBackupCatalog()
    catalog.snapshot(createdAt, movementDao.visibleForBackup().map { row ->
        BackupMovement(
            row.uuid, MovementType.valueOf(row.type), row.amountMinor, Currency.valueOf(row.currency),
            LocalDate.ofEpochDay(row.dateEpochDay), catalog.categoryUuid(row.categoryId),
            catalog.subcategoryUuid(row.subcategoryId), row.detail,
            row.createdAtEpochMillis.toBackupTime(), row.updatedAtEpochMillis.toBackupTime(),
        )
    })
}

/** Prepare the financial row before any write. Restore supplies its resolved references. */
internal fun BackupMovement.toCatalogMovementRow(): CatalogMovementEntity = CatalogMovementEntity(
    uuid = uuid, type = type.name, amountMinor = amountMinor, currency = currency.name,
    dateEpochDay = date.toEpochDay(), categoryId = null, subcategoryId = null, detail = detail,
    createdAtEpochMillis = createdAt.toInstant(ZoneOffset.UTC).toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli(),
)

private fun Long.toBackupTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
