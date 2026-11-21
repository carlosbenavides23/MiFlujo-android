package com.carlos.miflujo.data.cloud.catalog

import com.carlos.miflujo.domain.category.isCanonicalCatalogUuid
import com.carlos.miflujo.domain.model.MovementType

enum class CatalogReconciliation {
    LOCAL_WINS, REMOTE_WINS, IDENTICAL, INCOMPATIBLE_STRUCTURE, REVISION_CONTENT_MISMATCH,
}

fun reconcileCatalog(local: RemoteCatalogEntry, remote: RemoteCatalogEntry): CatalogReconciliation {
    val sameStructure = local.uuid == remote.uuid && when {
        local is RemoteCategoryDto && remote is RemoteCategoryDto ->
            local.scope == remote.scope && local.reportGroup == remote.reportGroup
        local is RemoteSubcategoryDto && remote is RemoteSubcategoryDto ->
            local.categoryUuid == remote.categoryUuid
        else -> false
    }
    return when {
        !sameStructure -> CatalogReconciliation.INCOMPATIBLE_STRUCTURE
        local.revision > remote.revision -> CatalogReconciliation.LOCAL_WINS
        local.revision < remote.revision -> CatalogReconciliation.REMOTE_WINS
        local == remote -> CatalogReconciliation.IDENTICAL
        else -> CatalogReconciliation.REVISION_CONTENT_MISMATCH
    }
}

/** The local writer must still check the revision atomically when persisting the ACK. */
fun canAcknowledgeCatalogWrite(
    current: RemoteCatalogEntry,
    sent: RemoteCatalogEntry,
    confirmed: RemoteCatalogEntry,
): Boolean = current == sent && sent == confirmed

sealed interface CatalogReferenceResolution {
    data object Resolved : CatalogReferenceResolution
    data object Invalid : CatalogReferenceResolution
    data class Pending(val uuids: Set<String>) : CatalogReferenceResolution
}

/** For historical references, archived entries remain valid. New assignment uses the domain guard. */
fun resolveRemoteCatalogReferences(
    categoryUuid: String?,
    subcategoryUuid: String?,
    lookup: (String) -> RemoteCatalogEntry?,
    movementType: MovementType? = null,
): CatalogReferenceResolution {
    if (categoryUuid == null) {
        return if (subcategoryUuid == null) CatalogReferenceResolution.Resolved else CatalogReferenceResolution.Invalid
    }
    if (!isCanonicalCatalogUuid(categoryUuid) ||
        (subcategoryUuid != null && (!isCanonicalCatalogUuid(subcategoryUuid) || subcategoryUuid == categoryUuid))
    ) return CatalogReferenceResolution.Invalid

    val parent = lookup(categoryUuid)
    if (parent != null && (parent !is RemoteCategoryDto || parent.uuid != categoryUuid ||
            (movementType != null && !parent.scope.supports(movementType)))
    ) return CatalogReferenceResolution.Invalid

    val child = subcategoryUuid?.let(lookup)
    if (child != null && (child !is RemoteSubcategoryDto || child.uuid != subcategoryUuid || child.categoryUuid != categoryUuid)) {
        return CatalogReferenceResolution.Invalid
    }
    val missing = buildSet {
        if (parent == null) add(categoryUuid)
        if (subcategoryUuid != null && child == null) add(subcategoryUuid)
    }
    return if (missing.isEmpty()) CatalogReferenceResolution.Resolved else CatalogReferenceResolution.Pending(missing)
}
