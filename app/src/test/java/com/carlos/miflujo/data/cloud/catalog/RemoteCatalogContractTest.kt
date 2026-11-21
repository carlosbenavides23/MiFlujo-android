package com.carlos.miflujo.data.cloud.catalog

import com.carlos.miflujo.domain.category.*
import com.carlos.miflujo.domain.model.MovementType
import org.junit.Assert.*
import org.junit.Test

class RemoteCatalogContractTest {
    private val parentUuid = "11111111-1111-4111-8111-111111111111"
    private val childUuid = "22222222-2222-4222-8222-222222222222"
    private val actor = "33333333-3333-4333-8333-333333333333"
    private val otherActor = "44444444-4444-4444-8444-444444444444"
    private val revision = CatalogRevision(4, actor)
    private val parent = RemoteCategoryDto(parentUuid, "Hogar", CategoryScope.EXPENSE, ReportGroup.OTHER, "home", true, -2, revision)
    private val child = RemoteSubcategoryDto(childUuid, parentUuid, "Agua", "water", true, 3, revision)

    @Test fun categoryRoundTripUsesOnlyTheContractFields() {
        val fields = RemoteCatalogFields.encode(parent)
        assertEquals(
            setOf("schemaVersion", "uuid", "name", "scope", "reportGroup", "iconKey", "archived", "sortOrder", "revisionCounter", "revisionActorUuid"),
            fields.keys,
        )
        assertEquals(-2L, fields["sortOrder"])
        assertEquals(parent, RemoteCatalogFields.decodeCategory(parentUuid, fields))
    }

    @Test fun childRoundTripResolvesLocalParentToPortableUuid() {
        val localParent = Category(id = 57, uuid = parentUuid, name = "Hogar", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER)
        val localChild = Subcategory(id = 99, uuid = childUuid, categoryId = 57, name = "Agua", iconKey = "water", archived = true, sortOrder = 3)
        val stored = StoredSubcategory(localChild, revision, CatalogSyncState.ERROR)
        val fields = RemoteCatalogFields.encode(stored.toRemoteDto(localParent))
        assertEquals(parentUuid, fields["categoryUuid"])
        assertFalse(fields.keys.any { it in setOf("id", "categoryId", "scope", "syncState") })
        assertEquals(child, RemoteCatalogFields.decodeSubcategory(childUuid, fields))
        rejected { stored.toRemoteDto(localParent.copy(id = 2)) }
        val mappedParent = StoredCategory(localParent, revision, CatalogSyncState.PENDING).toRemoteDto()
        assertEquals(parentUuid, mappedParent.uuid)
    }

    @Test fun malformedIdentityAndOwnerFieldsAreRejected() {
        val fields = RemoteCatalogFields.encode(parent)
        listOf("", parentUuid.uppercase().replaceFirst('1', 'A'), "not-a-uuid", childUuid).forEach { id ->
            rejected { RemoteCatalogFields.decodeCategory(id, fields) }
        }
        rejected { RemoteCatalogFields.decodeCategory(parentUuid, fields + ("ownerUid" to "other")) }
        rejected { child.copy(categoryUuid = childUuid) }
        rejected { child.copy(categoryUuid = "42") }
        rejected { parent.copy(uuid = "11111111-1111-4111-7111-111111111111") }
        assertEquals("users/alice/categories/$parentUuid", CloudCatalogPath.category("alice", parentUuid))
        assertEquals("users/alice/subcategories/$childUuid", CloudCatalogPath.subcategory("alice", childUuid))
        rejected { CloudCatalogPath.category("alice/other", parentUuid) }
    }

    @Test fun malformedPayloadsAreRejectedWithoutCoercion() {
        val fields = RemoteCatalogFields.encode(parent)
        fields.keys.forEach { key ->
            rejected { RemoteCatalogFields.decodeCategory(parentUuid, fields - key) }
        }
        val variants = listOf(
            "schemaVersion" to 2L, "schemaVersion" to 1.0, "archived" to "true",
            "sortOrder" to Int.MAX_VALUE.toLong() + 1, "sortOrder" to 2.5,
            "revisionCounter" to -1L, "revisionActorUuid" to "origin",
            "scope" to "UNKNOWN", "reportGroup" to "INCOME", "name" to "  ",
            "name" to "é".repeat(241), "iconKey" to "i".repeat(129), "iconKey" to null,
        )
        variants.forEach { variant -> rejected { RemoteCatalogFields.decodeCategory(parentUuid, fields + variant) } }
        val edge = parent.copy(name = "é".repeat(240), sortOrder = Int.MIN_VALUE, revision = CatalogRevision(Long.MAX_VALUE, actor))
        assertEquals(edge, RemoteCatalogFields.decodeCategory(parentUuid, RemoteCatalogFields.encode(edge)))
    }

    @Test fun frozenLegacySeedsRetainTheirIdentitiesAndClassification() {
        val stamp = CatalogRevision(0, "00000000-0000-4000-8000-000000000000")
        LegacyCategoryCatalog.categories.forEach { legacy ->
            val dto = StoredCategory(legacy, stamp, CatalogSyncState.PENDING).toRemoteDto()
            assertEquals(dto, RemoteCatalogFields.decodeCategory(legacy.uuid, RemoteCatalogFields.encode(dto)))
            rejected { child.copy(uuid = legacy.uuid) }
        }
        LegacyCategoryCatalog.subcategories.forEach { legacy ->
            val seedParent = LegacyCategoryCatalog.categories.single { it.id == legacy.categoryId }
            val dto = StoredSubcategory(legacy, stamp, CatalogSyncState.PENDING).toRemoteDto(seedParent)
            assertEquals(dto, RemoteCatalogFields.decodeSubcategory(legacy.uuid, RemoteCatalogFields.encode(dto)))
            rejected { dto.copy(categoryUuid = parentUuid) }
            rejected { parent.copy(uuid = legacy.uuid) }
        }
        rejected { parent.copy(uuid = LegacyCategoryCatalog.categories.first().uuid) }
    }

    @Test fun concurrentWholeRecordsConvergeAndStaleWritesLose() {
        val rename = parent.copy(name = "Casa", archived = false)
        val archive = parent.copy(revision = CatalogRevision(4, otherActor))
        assertEquals(CatalogReconciliation.REMOTE_WINS, reconcileCatalog(rename, archive))
        assertEquals(CatalogReconciliation.LOCAL_WINS, reconcileCatalog(archive, rename))
        val newer = rename.copy(revision = CatalogRevision(5, actor))
        assertEquals(CatalogReconciliation.REMOTE_WINS, reconcileCatalog(archive, newer))
        assertEquals(CatalogReconciliation.IDENTICAL, reconcileCatalog(newer, newer))
        assertEquals(CatalogReconciliation.REVISION_CONTENT_MISMATCH, reconcileCatalog(parent, rename))
    }

    @Test fun structuralChangesAreRejectedEvenAtHigherRevisions() {
        val changed = parent.copy(revision = revision.nextFor(otherActor))
        listOf(
            changed.copy(uuid = otherActor),
            changed.copy(scope = CategoryScope.BOTH),
            changed.copy(reportGroup = ReportGroup.MAINTENANCE),
            child.copy(uuid = parentUuid, categoryUuid = otherActor),
        ).forEach { assertEquals(CatalogReconciliation.INCOMPATIBLE_STRUCTURE, reconcileCatalog(parent, it)) }
        assertEquals(
            CatalogReconciliation.INCOMPATIBLE_STRUCTURE,
            reconcileCatalog(child, child.copy(categoryUuid = otherActor, revision = changed.revision)),
        )
    }

    @Test fun acknowledgementIsForTheExactSentAndConfirmedRevisionAndPayload() {
        val newer = parent.copy(revision = revision.nextFor(actor))
        assertTrue(canAcknowledgeCatalogWrite(parent, parent, parent))
        assertFalse(canAcknowledgeCatalogWrite(newer, parent, parent))
        assertFalse(canAcknowledgeCatalogWrite(parent, parent, newer))
        assertFalse(canAcknowledgeCatalogWrite(parent, parent, parent.copy(name = "Otro")))
    }

    @Test fun referencesDistinguishPendingFromInvalidAndAcceptHistoricalArchives() {
        val complete = mapOf(parentUuid to parent, childUuid to child)
        assertEquals(CatalogReferenceResolution.Resolved, resolveRemoteCatalogReferences(null, null, complete::get))
        assertEquals(CatalogReferenceResolution.Resolved, resolveRemoteCatalogReferences(parentUuid, childUuid, complete::get, MovementType.EXPENSE))
        assertEquals(CatalogReferenceResolution.Pending(setOf(parentUuid)), resolveRemoteCatalogReferences(parentUuid, null, { null }))
        assertEquals(CatalogReferenceResolution.Pending(setOf(parentUuid)), resolveRemoteCatalogReferences(parentUuid, childUuid, { if (it == childUuid) child else null }))
        assertEquals(CatalogReferenceResolution.Pending(setOf(childUuid)), resolveRemoteCatalogReferences(parentUuid, childUuid, { if (it == parentUuid) parent else null }))
        assertEquals(CatalogReferenceResolution.Invalid, resolveRemoteCatalogReferences(null, childUuid, complete::get))
        assertEquals(CatalogReferenceResolution.Invalid, resolveRemoteCatalogReferences(childUuid, null, complete::get))
        assertEquals(CatalogReferenceResolution.Invalid, resolveRemoteCatalogReferences(parentUuid, childUuid, complete::get, MovementType.INCOME))
        val wrongParent = complete + (childUuid to child.copy(categoryUuid = otherActor))
        assertEquals(CatalogReferenceResolution.Invalid, resolveRemoteCatalogReferences(parentUuid, childUuid, wrongParent::get))
    }

    private fun rejected(action: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { action() }
    }
}
