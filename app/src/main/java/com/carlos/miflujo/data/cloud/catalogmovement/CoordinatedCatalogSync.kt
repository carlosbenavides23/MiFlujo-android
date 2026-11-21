package com.carlos.miflujo.data.cloud.catalogmovement

import com.carlos.miflujo.data.cloud.catalog.CatalogIssueKind
import com.carlos.miflujo.data.cloud.catalog.CatalogSession
import com.carlos.miflujo.data.cloud.catalog.CatalogSyncEngine
import com.carlos.miflujo.data.cloud.catalog.CatalogSyncException
import com.carlos.miflujo.data.cloud.catalog.CatalogSyncIssue
import com.carlos.miflujo.data.cloud.catalog.CatalogSyncResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class MovementSyncSnapshot<T>(val entries: List<T>, val invalidIds: Set<String> = emptySet())

internal interface CatalogMovementRemoteSource {
    suspend fun requireMovementProtocol(uid: String)
    suspend fun fetchMovements(uid: String): MovementSyncSnapshot<RemoteCatalogMovement>
    suspend fun publishMovement(uid: String, movement: PendingCatalogMovement): RemoteCatalogMovement
}

internal data class MovementApplyResult(val changed: Boolean = false, val issue: CatalogIssueKind? = null)

internal interface CatalogMovementSyncLocalSource {
    val uid: String
    suspend fun pendingMovements(): MovementSyncSnapshot<PendingCatalogMovement>
    suspend fun applyMovement(
        remote: RemoteCatalogMovement,
        sent: PendingCatalogMovement? = null,
    ): MovementApplyResult
}

/** Reuses the catalog engine. Movement publication requires confirmed dependencies and a server CAS. */
internal class CoordinatedCatalogSync(
    private val local: CatalogMovementSyncLocalSource,
    private val remote: CatalogMovementRemoteSource,
    private val session: CatalogSession,
    private val catalog: CatalogSyncEngine,
) {
    private val lock = Mutex()

    suspend fun sync(): CatalogSyncResult = lock.withLock {
        val issues = mutableListOf<CatalogSyncIssue>()
        var applied = 0
        var acknowledged = 0

        suspend fun requireSession() {
            currentCoroutineContext().ensureActive()
            val uid = session.currentUid() ?: throw CatalogSyncException(CatalogIssueKind.UNAUTHENTICATED)
            if (uid != local.uid) throw CatalogSyncException(CatalogIssueKind.ACCOUNT_CHANGED)
        }

        try {
            requireSession()
            remote.requireMovementProtocol(local.uid)
            requireSession()
            val result = catalog.sync()
            issues += result.issues
            applied += result.applied
            acknowledged += result.acknowledged
            requireSession()
            if (result.issues.any { it.uuid == null }) return@withLock CatalogSyncResult(applied, acknowledged, issues)

            fun hasDependencies(content: MovementContent): Boolean {
                val selection = content.draft.selection
                return listOfNotNull(selection.categoryUuid, selection.subcategoryUuid).all { it in result.confirmedUuids }
            }

            val readings = remote.fetchMovements(local.uid)
            requireSession()
            val blocked = readings.invalidIds.toMutableSet()
            blocked += readings.entries.groupingBy { it.content.uuid }.eachCount().filterValues { it > 1 }.keys
            blocked.forEach { issues += CatalogSyncIssue(CatalogIssueKind.INVALID_REMOTE, it) }
            for (entry in readings.entries) {
                requireSession()
                val id = entry.content.uuid
                if (id in blocked) continue
                val appliedEntry = if (hasDependencies(entry.content)) local.applyMovement(entry)
                else MovementApplyResult(issue = CatalogIssueKind.MISSING_PARENT)
                if (appliedEntry.changed) applied++
                appliedEntry.issue?.let { issue ->
                    issues += CatalogSyncIssue(issue, id)
                    if (issue != CatalogIssueKind.LOCAL_CHANGED) blocked += id
                }
            }

            requireSession()
            val pending = local.pendingMovements()
            pending.invalidIds.forEach { issues += CatalogSyncIssue(CatalogIssueKind.INVALID_LOCAL, it) }
            for (entry in pending.entries) {
                requireSession()
                val id = entry.content.uuid
                if (id in blocked) continue
                if (!hasDependencies(entry.content)) {
                    issues += CatalogSyncIssue(CatalogIssueKind.MISSING_PARENT, id)
                    continue
                }
                val receipt = try {
                    remote.publishMovement(local.uid, entry)
                } catch (error: CatalogSyncException) {
                    if (error.kind in stopIssues) throw error
                    issues += CatalogSyncIssue(error.kind, id)
                    continue
                }
                requireSession()
                check(receipt.content == entry.content)
                val acknowledgedEntry = local.applyMovement(receipt, entry)
                if (acknowledgedEntry.changed) acknowledged++
                acknowledgedEntry.issue?.let { issues += CatalogSyncIssue(it, id) }
            }
            requireSession()
        } catch (error: CatalogSyncException) {
            issues += CatalogSyncIssue(error.kind)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            issues += CatalogSyncIssue(CatalogIssueKind.LOCAL_PERSISTENCE)
        }
        CatalogSyncResult(applied, acknowledged, issues.distinct())
    }

    private companion object {
        val stopIssues = setOf(
            CatalogIssueKind.UNAUTHENTICATED, CatalogIssueKind.ACCOUNT_CHANGED, CatalogIssueKind.PERMISSION_DENIED,
            CatalogIssueKind.PROTOCOL_MISMATCH,
        )
    }
}
