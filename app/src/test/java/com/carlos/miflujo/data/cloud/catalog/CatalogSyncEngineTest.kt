package com.carlos.miflujo.data.cloud.catalog

import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogSyncEngineTest {
    private val parentId = "11111111-1111-4111-8111-111111111111"
    private val childId = "22222222-2222-4222-8222-222222222222"
    private val lowActor = "33333333-3333-4333-8333-333333333333"
    private val highActor = "44444444-4444-4444-8444-444444444444"
    private val parent = RemoteCategoryDto(
        parentId, "Casa", CategoryScope.EXPENSE, ReportGroup.OTHER, "home", false, 0,
        CatalogRevision(1, lowActor),
    )
    private val child = RemoteSubcategoryDto(childId, parentId, "Agua", "water", false, 0, parent.revision)
    private var uid: String? = "alice"
    private val local = Local("alice")
    private val remote = Remote()
    private fun engine(store: Local = local) = CatalogSyncEngine(store, remote) { uid }

    @Test fun firstSyncUploadsParentBeforeChildAndDownloadsToAnotherDevice() = runBlocking {
        local.edit(child)
        local.edit(parent)
        val result = engine().sync()
        assertTrue(result.successful)
        assertEquals(2, result.acknowledged)
        assertEquals(listOf(parentId, childId), remote.published)
        val anotherDevice = Local("alice")
        assertEquals(2, engine(anotherDevice).sync().applied)
        assertEquals(mapOf(parentId to parent, childId to child), anotherDevice.rows)
        assertTrue(anotherDevice.dirty.isEmpty())
    }

    @Test fun independentOfflineCreationsConvergeAfterReconnect() = runBlocking {
        val anotherDevice = Local("alice")
        val independent = parent.copy(uuid = lowActor, revision = CatalogRevision(1, highActor))
        local.edit(parent)
        anotherDevice.edit(independent)
        remote.fetchError = CatalogSyncException(CatalogIssueKind.TRANSIENT_REMOTE)
        assertEquals(CatalogIssueKind.TRANSIENT_REMOTE, engine().sync().issues.single().kind)
        assertEquals(setOf(parentId), local.dirty)
        assertEquals(emptyList<String>(), remote.published)
        remote.fetchError = null
        assertTrue(engine().sync().successful)
        assertTrue(engine(anotherDevice).sync().successful)
        assertTrue(engine().sync().successful)
        assertEquals(setOf(parentId, independent.uuid), local.rows.keys)
        assertEquals(local.rows, anotherDevice.rows)
    }

    @Test fun responseLostAfterCommitIsRecoveredByNextSnapshotWithoutDuplicateWrite() = runBlocking {
        local.edit(parent)
        remote.afterCommit = { throw CatalogSyncException(CatalogIssueKind.TRANSIENT_REMOTE) }
        assertFalse(engine().sync().successful)
        assertEquals(setOf(parentId), local.dirty)
        assertEquals(setOf(parentId), local.failed)
        remote.afterCommit = {}
        val retry = engine().sync()
        assertTrue(retry.successful)
        assertEquals(1, retry.acknowledged)
        assertEquals(listOf(parentId), remote.published)
        assertEquals(setOf(parentId), remote.docs.keys)
        assertTrue(local.dirty.isEmpty())
        assertTrue(local.failed.isEmpty())
    }

    @Test fun newerLocalEditWhileAcknowledgementIsInFlightStaysPending() = runBlocking {
        local.edit(parent)
        val edited = parent.copy(name = "Nueva", iconKey = "other", sortOrder = 5, revision = parent.revision.nextFor(lowActor))
        remote.afterCommit = { local.edit(edited) }
        assertEquals(CatalogIssueKind.LOCAL_CHANGED, engine().sync().issues.single().kind)
        assertEquals(edited, local.rows[parentId])
        assertEquals(setOf(parentId), local.dirty)
        remote.afterCommit = {}
        assertTrue(engine().sync().successful)
        assertEquals(edited, remote.docs[parentId])
        assertTrue(local.dirty.isEmpty())
    }

    @Test fun archiveWinsOverConcurrentRenameAsOneRecordAndPreservesChild() = runBlocking {
        val rename = parent.copy(name = "Renombrada", revision = CatalogRevision(2, lowActor))
        val archive = parent.copy(archived = true, revision = CatalogRevision(2, highActor))
        local.edit(rename)
        local.edit(child)
        remote.docs[parentId] = archive
        val result = engine().sync()
        assertTrue(result.successful)
        assertEquals(archive, local.rows[parentId])
        assertEquals(child, remote.docs[childId])
        assertEquals(listOf(childId), remote.published)
        assertTrue(local.dirty.isEmpty())
    }

    @Test fun racingServerWinnerIsReconciledInsidePublishAndRetainedLocally() = runBlocking {
        local.edit(parent)
        val newer = parent.copy(name = "Ganadora", revision = CatalogRevision(6, highActor))
        remote.beforePublish = { remote.docs[parentId] = newer }
        assertTrue(engine().sync().successful)
        assertEquals(newer, remote.docs[parentId])
        assertEquals(newer, local.rows[parentId])
        assertTrue(local.dirty.isEmpty())
    }

    @Test fun equalRevisionWithDifferentContentsDoesNotOverwriteEitherSide() = runBlocking {
        local.edit(parent)
        val invalid = parent.copy(name = "Distinta con la misma revisión")
        remote.docs[parentId] = invalid
        assertEquals(CatalogIssueKind.REVISION_CONFLICT, engine().sync().issues.single().kind)
        assertEquals(parent, local.rows[parentId])
        assertEquals(invalid, remote.docs[parentId])
        assertTrue(remote.published.isEmpty())
        assertEquals(setOf(parentId), local.dirty)
    }

    @Test fun remoteChildIsDeferredUntilParentArrivesWithoutPhysicalDeletes() = runBlocking {
        remote.docs[childId] = child
        assertEquals(CatalogSyncIssue(CatalogIssueKind.MISSING_PARENT, childId), engine().sync().issues.single())
        assertTrue(local.rows.isEmpty())
        remote.docs[parentId] = parent.copy(archived = true)
        assertTrue(engine().sync().successful)
        assertEquals(child, local.rows[childId])
        remote.docs.clear()
        assertTrue(engine().sync().successful)
        assertEquals(setOf(parentId, childId), local.rows.keys)
    }

    @Test fun failedParentUploadPreservesBothPendingsAndRetriesInOrder() = runBlocking {
        local.edit(parent)
        local.edit(child)
        remote.beforePublish = { throw CatalogSyncException(CatalogIssueKind.TRANSIENT_REMOTE) }
        val result = engine().sync()
        assertEquals(setOf(CatalogIssueKind.TRANSIENT_REMOTE, CatalogIssueKind.MISSING_PARENT), result.issues.map { it.kind }.toSet())
        assertEquals(listOf(parentId), remote.published)
        assertEquals(setOf(parentId, childId), local.dirty)
        remote.beforePublish = {}
        assertTrue(engine().sync().successful)
        assertEquals(listOf(parentId, parentId, childId), remote.published)
    }

    @Test fun malformedAndCollidingRemoteIdsAreBlockedWhileIndependentRowsCanSync() = runBlocking {
        local.edit(parent)
        local.edit(child)
        remote.invalid = setOf(parentId)
        val independent = parent.copy(uuid = lowActor)
        remote.docs[independent.uuid] = independent
        remote.extra = listOf(independent)
        val valid = parent.copy(uuid = highActor)
        remote.docs[valid.uuid] = valid
        val result = engine().sync()
        assertEquals(setOf(
            CatalogSyncIssue(CatalogIssueKind.INVALID_REMOTE, parentId),
            CatalogSyncIssue(CatalogIssueKind.INVALID_REMOTE, independent.uuid),
            CatalogSyncIssue(CatalogIssueKind.MISSING_PARENT, childId),
        ), result.issues.toSet())
        assertEquals(valid, local.rows[valid.uuid])
        assertFalse(local.rows.containsKey(independent.uuid))
        assertTrue(remote.published.isEmpty())
    }

    @Test fun signOutAndOtherUidRejectOldStoreWithoutRemoteIo() = runBlocking {
        local.edit(parent)
        for ((active, kind) in listOf(null to CatalogIssueKind.UNAUTHENTICATED, "bob" to CatalogIssueKind.ACCOUNT_CHANGED, "a/b" to CatalogIssueKind.UNAUTHENTICATED)) {
            uid = active
            assertEquals(kind, engine().sync().issues.single().kind)
        }
        assertEquals(0, remote.fetches)
        assertTrue(remote.published.isEmpty())
        assertEquals(setOf(parentId), local.dirty)
        uid = "bob"
        assertTrue(engine(Local("bob")).sync().successful)
        assertEquals(listOf("bob"), remote.fetchedUids)
        assertTrue(remote.published.isEmpty())
    }

    @Test fun accountSwitchDuringFetchOrAckDoesNotApplyStaleResponse() = runBlocking {
        remote.docs[parentId] = parent
        remote.afterFetch = { uid = "bob" }
        assertEquals(CatalogIssueKind.ACCOUNT_CHANGED, engine().sync().issues.single().kind)
        assertTrue(local.rows.isEmpty())
        uid = "alice"
        remote.docs.clear()
        remote.afterFetch = {}
        local.edit(parent)
        remote.afterCommit = { uid = "bob" }
        assertEquals(CatalogIssueKind.ACCOUNT_CHANGED, engine().sync().issues.single().kind)
        assertEquals(setOf(parentId), local.dirty)
    }

    @Test fun permissionAndLocalPersistenceFailuresAreReportedSeparately() = runBlocking {
        local.edit(parent)
        remote.beforePublish = { throw CatalogSyncException(CatalogIssueKind.PERMISSION_DENIED) }
        assertEquals(CatalogIssueKind.PERMISSION_DENIED, engine().sync().issues.single().kind)
        assertEquals(setOf(parentId), local.dirty)
        remote.beforePublish = {}
        local.applyError = IllegalStateException("disk error")
        assertEquals(CatalogIssueKind.LOCAL_PERSISTENCE, engine().sync().issues.single().kind)
        assertEquals(setOf(parentId), local.dirty)
        local.applyError = null
        assertTrue(engine().sync().successful)
        assertTrue(local.dirty.isEmpty())
    }

    private class Local(override val uid: String) : CatalogSyncLocalSource {
        val rows = linkedMapOf<String, RemoteCatalogEntry>()
        val dirty = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        var applyError: Exception? = null

        fun edit(entry: RemoteCatalogEntry) {
            rows[entry.uuid] = entry
            dirty += entry.uuid
        }

        override suspend fun pending() = CatalogSnapshot(rows.values.filter { it.uuid in dirty })

        override suspend fun apply(entry: RemoteCatalogEntry, expectedSent: RemoteCatalogEntry?): CatalogApplyResult {
            applyError?.let { throw it }
            val current = rows[entry.uuid]
            if (expectedSent != null && current != expectedSent) return CatalogApplyResult.LOCAL_CHANGED
            if (entry is RemoteSubcategoryDto && rows[entry.categoryUuid] !is RemoteCategoryDto) return CatalogApplyResult.MISSING_PARENT
            return when (current?.let { reconcileCatalog(it, entry) }) {
                CatalogReconciliation.LOCAL_WINS -> CatalogApplyResult.LOCAL_NEWER
                CatalogReconciliation.INCOMPATIBLE_STRUCTURE, CatalogReconciliation.REVISION_CONTENT_MISMATCH ->
                    CatalogApplyResult.CONFLICT
                CatalogReconciliation.IDENTICAL -> {
                    failed -= entry.uuid
                    if (dirty.remove(entry.uuid)) CatalogApplyResult.ACKNOWLEDGED else CatalogApplyResult.UNCHANGED
                }
                else -> {
                    rows[entry.uuid] = entry
                    dirty -= entry.uuid
                    failed -= entry.uuid
                    CatalogApplyResult.APPLIED
                }
            }
        }

        override suspend fun markFailed(entry: RemoteCatalogEntry) {
            if (rows[entry.uuid] == entry && entry.uuid in dirty) failed += entry.uuid
        }
    }

    private class Remote : CatalogRemoteSource {
        val docs = linkedMapOf<String, RemoteCatalogEntry>()
        val published = mutableListOf<String>()
        val fetchedUids = mutableListOf<String>()
        val fetches get() = fetchedUids.size
        var fetchError: CatalogSyncException? = null
        var invalid = emptySet<String>()
        var extra = emptyList<RemoteCatalogEntry>()
        var beforePublish: () -> Unit = {}
        var afterCommit: () -> Unit = {}
        var afterFetch: () -> Unit = {}

        override suspend fun fetch(uid: String): CatalogSnapshot {
            fetchedUids += uid
            fetchError?.let { throw it }
            return CatalogSnapshot(docs.values.toList() + extra, invalid).also { afterFetch() }
        }

        override suspend fun publish(uid: String, entry: RemoteCatalogEntry): RemoteCatalogEntry {
            published += entry.uuid
            beforePublish()
            if (entry is RemoteSubcategoryDto) check(docs[entry.categoryUuid] is RemoteCategoryDto)
            val previous = docs[entry.uuid]
            val winner = when (previous?.let { reconcileCatalog(entry, it) }) {
                CatalogReconciliation.REMOTE_WINS, CatalogReconciliation.IDENTICAL -> checkNotNull(previous)
                CatalogReconciliation.REVISION_CONTENT_MISMATCH, CatalogReconciliation.INCOMPATIBLE_STRUCTURE ->
                    throw CatalogSyncException(CatalogIssueKind.REVISION_CONFLICT)
                else -> entry.also { docs[it.uuid] = it }
            }
            afterCommit()
            return winner
        }
    }
}
