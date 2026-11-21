package com.carlos.miflujo.data.category

import com.carlos.miflujo.domain.category.*
import org.junit.Assert.*
import org.junit.Test

class CatalogMetadataTest {
    private val parent = LegacyCategoryCatalog.categories[1]
    private val child = LegacyCategoryCatalog.subcategories[0]
    private val revision = CatalogRevision(7, newCatalogUuid())

    @Test fun `mappers preserve values revisions and all sync states`() {
        CatalogSyncState.entries.forEach { state ->
            val category = parent.toEntity(revision, state).stored()
            assertEquals(parent, category.value)
            assertEquals(revision, category.revision)
            assertEquals(state, category.syncState)
            val subcategory = child.toEntity(revision, state).stored()
            assertEquals(child, subcategory.value)
            assertEquals(revision, subcategory.revision)
            assertEquals(state, subcategory.syncState)
        }
    }
    @Test fun `local edits increment counter and use installation identity`() {
        val actor = newCatalogUuid()
        val next = revision.nextFor(actor)
        assertEquals(revision.counter + 1, next.counter)
        assertEquals(actor, next.actorUuid)
        assertTrue(next > revision)
    }
    @Test(expected = IllegalArgumentException::class)
    fun `corrupt stored revision is not silently defaulted`() {
        parent.toEntity(revision, CatalogSyncState.PENDING).copy(revisionCounter = -1).stored()
    }
    @Test(expected = IllegalArgumentException::class)
    fun `corrupt stored state is not silently marked synced`() {
        child.toEntity(revision, CatalogSyncState.PENDING).copy(syncState = "UNKNOWN").stored()
    }
}
