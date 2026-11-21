package com.carlos.miflujo.data.cloud.catalogmovement

import com.carlos.miflujo.data.cloud.catalog.*
import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CoordinatedCatalogSyncTest {
    private val id = "10000000-0000-4000-8000-000000000001"
    private val parentId = "20000000-0000-4000-8000-000000000001"
    private val childId = "30000000-0000-4000-8000-000000000001"
    private val revision = CatalogRevision(1, "40000000-0000-4000-8000-000000000001")
    private val parent = RemoteCategoryDto(parentId, "Casa", CategoryScope.EXPENSE, ReportGroup.OTHER, "home", true, 0, revision)
    private val child = RemoteSubcategoryDto(childId, parentId, "Agua", "water", true, 0, revision)
    private val content = MovementContent(
        id, CatalogMovementDraft(MovementType.EXPENSE, Long.MAX_VALUE, Currency.DOLLAR, LocalDate.of(2026, 11, 21)),
        1_000L, 2_000L, null,
    )
    private val linked = content.copy(draft = content.draft.copy(selection = CatalogMovementSelection(parentId, childId)))

    @Test fun `transaction advances exactly from observed revision and accepts an exact retry`() {
        val first = nextRemoteMovement(PendingCatalogMovement(content, null), null)
        assertEquals(1L, first.revision)
        val changed = content.copy(updatedAt = 0, draft = content.draft.copy(detail = "offline"))
        val edit = PendingCatalogMovement(changed, first.revision)
        val committed = nextRemoteMovement(edit, first)
        assertEquals(2L, committed.revision)
        assertEquals(committed, nextRemoteMovement(edit, committed))
        conflict { nextRemoteMovement(PendingCatalogMovement(content, 1), committed) }
        conflict { nextRemoteMovement(PendingCatalogMovement(changed.copy(createdAt = 40), 2), committed) }
        conflict { nextRemoteMovement(PendingCatalogMovement(changed, 2), null) }
        conflict { nextRemoteMovement(PendingCatalogMovement(changed, Long.MAX_VALUE), first.copy(revision = Long.MAX_VALUE)) }
    }

    @Test fun `a confirmed tombstone cannot be revived even at the current revision`() {
        val deleted = RemoteCatalogMovement(content.copy(deletedAt = 3000), 4)
        conflict { nextRemoteMovement(PendingCatalogMovement(content, 4), deleted) }
        assertEquals(deleted, nextRemoteMovement(PendingCatalogMovement(deleted.content, 3), deleted))
    }

    @Test fun `confirmed archived parents and children precede dependent moves`() = runBlocking {
        val fixture = Fixture(pending = listOf(PendingCatalogMovement(linked, null)), catalog = listOf(parent, child))
        assertTrue(fixture.sync().successful)
        assertEquals(listOf("protocol", "catalog", "movements", "publish", "apply"), fixture.log)
        assertEquals(linked, fixture.received.single().content)
    }

    @Test fun `missing child or invalid movement defer while an uncategorized move advances`() = runBlocking {
        val first = PendingCatalogMovement(linked, null)
        val second = PendingCatalogMovement(content.copy(uuid = "10000000-0000-4000-8000-000000000002"), null)
        val third = PendingCatalogMovement(content.copy(uuid = "10000000-0000-4000-8000-000000000003"), null)
        val fixture = Fixture(pending = listOf(first, second, third), catalog = listOf(parent))
        fixture.incoming = MovementSyncSnapshot(emptyList(), setOf(third.content.uuid))
        assertEquals(
            setOf(CatalogSyncIssue(CatalogIssueKind.INVALID_REMOTE, third.content.uuid), CatalogSyncIssue(CatalogIssueKind.MISSING_PARENT, id)),
            fixture.sync().issues.toSet(),
        )
        assertEquals(listOf(second.content), fixture.received.map { it.content })
    }

    @Test fun `session change after a write prevents local acknowledgement and further writes`() = runBlocking {
        val fixture = Fixture(pending = listOf(PendingCatalogMovement(content, null)))
        fixture.afterPublish = { fixture.activeUid = "other-account" }
        assertEquals(listOf(CatalogSyncIssue(CatalogIssueKind.ACCOUNT_CHANGED)), fixture.sync().issues)
        assertEquals(listOf("protocol", "catalog", "movements", "publish"), fixture.log)
        assertTrue(fixture.received.isEmpty())
        fixture.log.clear()
        assertEquals(listOf(CatalogSyncIssue(CatalogIssueKind.ACCOUNT_CHANGED)), fixture.sync().issues)
        assertTrue(fixture.log.isEmpty())
    }

    @Test fun `offline transaction keeps a pending entry available on the next run`() = runBlocking {
        val fixture = Fixture(pending = listOf(PendingCatalogMovement(content, null)))
        fixture.publishIssue = CatalogIssueKind.TRANSIENT_REMOTE
        assertEquals(listOf(CatalogSyncIssue(CatalogIssueKind.TRANSIENT_REMOTE, id)), fixture.sync().issues)
        assertTrue(fixture.received.isEmpty())
        fixture.publishIssue = null
        assertTrue(fixture.sync().successful)
        assertEquals(content, fixture.received.single().content)
    }

    @Test fun `incompatible protocol blocks all local and remote work except the preflight read`() = runBlocking {
        val fixture = Fixture(pending = listOf(PendingCatalogMovement(content, null)))
        fixture.protocolIssue = CatalogIssueKind.PROTOCOL_MISMATCH
        assertEquals(listOf(CatalogSyncIssue(CatalogIssueKind.PROTOCOL_MISMATCH)), fixture.sync().issues)
        assertEquals(listOf("protocol"), fixture.log)
    }

    private fun conflict(block: () -> Unit) {
        val error = assertThrows(CatalogSyncException::class.java, block)
        assertEquals(CatalogIssueKind.REVISION_CONFLICT, error.kind)
    }

    private class Fixture(
        private val pending: List<PendingCatalogMovement>,
        private val catalog: List<RemoteCatalogEntry> = emptyList(),
    ) : CatalogMovementSyncLocalSource, CatalogMovementRemoteSource, CatalogSyncLocalSource, CatalogRemoteSource {
        override val uid = "owner"
        var activeUid = uid
        var publishIssue: CatalogIssueKind? = null
        var protocolIssue: CatalogIssueKind? = null
        var afterPublish: () -> Unit = {}
        val log = mutableListOf<String>()
        val received = mutableListOf<RemoteCatalogMovement>()
        var incoming = MovementSyncSnapshot<RemoteCatalogMovement>(emptyList())

        private val session = CatalogSession { activeUid }
        private val engine = CoordinatedCatalogSync(this, this, session, CatalogSyncEngine(this, this, session))

        suspend fun sync() = engine.sync()
        override suspend fun pendingMovements() = MovementSyncSnapshot(pending)
        override suspend fun applyMovement(remote: RemoteCatalogMovement, sent: PendingCatalogMovement?): MovementApplyResult {
            log += "apply"
            received += remote
            return MovementApplyResult(changed = true)
        }
        override suspend fun requireMovementProtocol(uid: String) {
            assertEquals(this.uid, uid)
            log += "protocol"
            protocolIssue?.let { throw CatalogSyncException(it) }
        }
        override suspend fun fetchMovements(uid: String): MovementSyncSnapshot<RemoteCatalogMovement> {
            log += "movements"
            return incoming
        }
        override suspend fun publishMovement(uid: String, movement: PendingCatalogMovement): RemoteCatalogMovement {
            assertEquals(this.uid, uid)
            log += "publish"
            publishIssue?.let { throw CatalogSyncException(it) }
            afterPublish()
            return nextRemoteMovement(movement, null)
        }
        override suspend fun fetch(uid: String): CatalogSnapshot {
            log += "catalog"
            return CatalogSnapshot(catalog)
        }
        override suspend fun publish(uid: String, entry: RemoteCatalogEntry) = error("Unexpected catalog pending")
        override suspend fun pending() = CatalogSnapshot(emptyList())
        override suspend fun apply(entry: RemoteCatalogEntry, expectedSent: RemoteCatalogEntry?) = CatalogApplyResult.APPLIED
        override suspend fun markFailed(entry: RemoteCatalogEntry) = Unit
    }
}
