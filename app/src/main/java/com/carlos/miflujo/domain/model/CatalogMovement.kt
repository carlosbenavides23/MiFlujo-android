package com.carlos.miflujo.domain.model

import com.carlos.miflujo.domain.category.isCanonicalCatalogUuid
import java.time.LocalDate

/** Portable selection used by the candidate repository. Local row IDs stay in Room. */
data class CatalogMovementSelection(
    val categoryUuid: String? = null,
    val subcategoryUuid: String? = null,
) {
    init {
        require(categoryUuid == null || isCanonicalCatalogUuid(categoryUuid))
        require(subcategoryUuid == null || (categoryUuid != null && isCanonicalCatalogUuid(subcategoryUuid)))
    }
}

data class CatalogMovementDraft(
    val type: MovementType,
    val amountMinor: Long,
    val currency: Currency,
    val date: LocalDate,
    val selection: CatalogMovementSelection = CatalogMovementSelection(),
    val detail: String? = null,
) {
    init { require(amountMinor > 0) }
}

/** Snapshot for the isolated candidate. The backup and wire contracts still define their own metadata. */
data class CatalogMovement(
    val uuid: String,
    val draft: CatalogMovementDraft,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val syncStatus: SyncStatus,
    val lastSyncedAtEpochMillis: Long?,
    val deletedAtEpochMillis: Long?,
)
