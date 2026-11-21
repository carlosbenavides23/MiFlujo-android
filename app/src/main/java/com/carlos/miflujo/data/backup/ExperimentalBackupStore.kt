package com.carlos.miflujo.data.backup

import androidx.room.withTransaction
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.time.LocalDateTime

/** No constructor accepting the production database. All access is confined to the sandbox. */
class ExperimentalBackupStore(private val database: BackupSandboxDatabase) {
    suspend fun snapshot(createdAt: LocalDateTime): CatalogBackup = database.withTransaction {
        snapshotInTransaction(createdAt)
    }

    private suspend fun snapshotInTransaction(createdAt: LocalDateTime): CatalogBackup {
        val catalog = database.categories().readBackupCatalog()
        return catalog.snapshot(createdAt, database.movements().all().map { row ->
            BackupMovement(
                row.uuid, MovementType.valueOf(row.type), row.amountMinor, Currency.valueOf(row.currency),
                LocalDate.parse(row.date), catalog.categoryUuid(row.categoryId), catalog.subcategoryUuid(row.subcategoryId),
                row.detail, LocalDateTime.parse(row.createdAt), LocalDateTime.parse(row.updatedAt),
            )
        })
    }

    suspend fun restore(backup: CatalogBackup) {
        val snapshot = backup.validatedCopy()
        database.withTransaction {
            snapshot.requireCompatibleWith(snapshotInTransaction(snapshot.createdAt))
            val movements = database.movements()
            replaceBackupContents(
                database.openHelper.writableDatabase, database.categories(), snapshot, movements::clear,
            ) { categoryIds, subcategoryIds ->
                snapshot.movements.forEach { movement ->
                    movements.insert(BackupMovementEntity(
                        uuid = movement.uuid, type = movement.type.name, amountMinor = movement.amountMinor,
                        currency = movement.currency.name, date = movement.date.toString(), detail = movement.detail,
                        categoryId = movement.categoryUuid?.let(categoryIds::getValue),
                        subcategoryId = movement.subcategoryUuid?.let(subcategoryIds::getValue),
                        createdAt = movement.createdAt.toString(), updatedAt = movement.updatedAt.toString(),
                    ))
                }
            }
        }
    }
}
