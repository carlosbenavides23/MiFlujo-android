package com.carlos.miflujo.data.movementcatalog

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.carlos.miflujo.data.category.CategoryDao
import com.carlos.miflujo.data.category.toDomain
import com.carlos.miflujo.domain.category.CategorySelection
import com.carlos.miflujo.domain.model.CatalogMovement
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.domain.model.generateMovementUuid
import java.time.LocalDate

/** Candidate read and local write boundary. Accepts only the candidate database. */
internal class CatalogMovementRepository(
    private val database: RoomDatabase,
    private val movements: CatalogMovementDao,
    private val catalog: CategoryDao,
    private val newStatus: SyncStatus,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(database: CatalogMovementDatabase, nowMillis: () -> Long = System::currentTimeMillis) :
        this(database, database.movements(), database.catalog(), SyncStatus.LOCAL_ONLY, nowMillis)

    suspend fun find(uuid: String): CatalogMovement? = database.withTransaction {
        movements.find(uuid)?.snapshot()
    }

    suspend fun create(draft: CatalogMovementDraft): CatalogMovement = database.withTransaction {
        val selection = resolve(draft)
        val time = nowMillis()
        val row = CatalogMovementEntity(
            uuid = generateMovementUuid(), type = draft.type.name, amountMinor = draft.amountMinor,
            currency = draft.currency.name, dateEpochDay = draft.date.toEpochDay(),
            categoryId = selection.categoryId, subcategoryId = selection.subcategoryId, detail = draft.detail,
            createdAtEpochMillis = time, updatedAtEpochMillis = time, syncStatus = newStatus,
        )
        movements.insert(row)
        row.snapshot()
    }

    suspend fun edit(expected: CatalogMovement, draft: CatalogMovementDraft): CatalogMovement = database.withTransaction {
        val current = requireNotNull(movements.find(expected.uuid)) { "Unknown movement" }
        check(current.deletedAt == null && current.syncStatus != SyncStatus.PENDING_DELETE) { "Deleted movement" }
        val stored = current.snapshot()
        check(stored == expected) { "Movement changed since it was read" }
        val selection = resolve(draft, current)
        if (stored.draft == draft) return@withTransaction stored
        val updated = current.copy(
            type = draft.type.name, amountMinor = draft.amountMinor, currency = draft.currency.name,
            dateEpochDay = draft.date.toEpochDay(), detail = draft.detail,
            categoryId = selection.categoryId, subcategoryId = selection.subcategoryId,
            updatedAtEpochMillis = maxOf(nowMillis(), Math.addExact(current.updatedAtEpochMillis, 1L)),
            syncStatus = if (current.syncStatus == SyncStatus.LOCAL_ONLY) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING_UPLOAD,
        )
        check(movements.update(updated) == 1)
        updated.snapshot()
    }

    suspend fun tombstone(expected: CatalogMovement): Unit = database.withTransaction {
        check(newStatus == SyncStatus.PENDING_UPLOAD) { "Tombstone requires an account-scoped repository" }
        val current = checkNotNull(movements.find(expected.uuid))
        check(current.snapshot() == expected && current.deletedAt == null) { "Movement has changed or was deleted" }
        val deletion = maxOf(nowMillis(), Math.addExact(current.updatedAtEpochMillis, 1L))
        check(movements.update(current.copy(
            updatedAtEpochMillis = deletion, deletedAt = deletion, syncStatus = SyncStatus.PENDING_DELETE,
        )) == 1)
    }

    private suspend fun resolve(draft: CatalogMovementDraft, previous: CatalogMovementEntity? = null): CategorySelection {
        val category = draft.selection.categoryUuid?.let {
            requireNotNull(catalog.category(it)) { "Unknown category UUID" }.toDomain()
        }
        val child = draft.selection.subcategoryUuid?.let {
            requireNotNull(catalog.subcategory(it)) { "Unknown subcategory UUID" }.toDomain()
        }
        val selection = CategorySelection(category?.id, child?.id)
        val errors = selection.validate(draft.type, category, child,
            previous?.let { CategorySelection(it.categoryId, it.subcategoryId) },
            previous?.let { MovementType.valueOf(it.type) })
        require(errors.isEmpty()) { "Invalid category selection: $errors" }
        return selection
    }

    private suspend fun CatalogMovementEntity.snapshot(): CatalogMovement {
        val type = MovementType.valueOf(type)
        val category = categoryId?.let { checkNotNull(catalog.categoryById(it)).toDomain() }
        val child = subcategoryId?.let { checkNotNull(catalog.subcategoryById(it)).toDomain() }
        val local = CategorySelection(categoryId, subcategoryId)
        check(local.validate(type, category, child, local, type).isEmpty()) { "Invalid stored category relationship" }
        return CatalogMovement(
            uuid, CatalogMovementDraft(type, amountMinor, Currency.valueOf(currency), LocalDate.ofEpochDay(dateEpochDay),
                CatalogMovementSelection(category?.uuid, child?.uuid), detail),
            createdAtEpochMillis, updatedAtEpochMillis, syncStatus, lastSyncedAt, deletedAt,
        )
    }
}
