package com.carlos.miflujo.data.cloud.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Manual, account-scoped engine. No scheduler or financial sync dependency. */
class CatalogSyncEngine(
    private val local: CatalogSyncLocalSource,
    private val remote: CatalogRemoteSource,
    private val session: CatalogSession,
) {
    private val mutex = Mutex()

    suspend fun sync(): CatalogSyncResult = mutex.withLock {
        val run = Run()
        try {
            run.execute()
        } catch (error: CatalogSyncException) {
            run.issues += CatalogSyncIssue(error.kind)
        }
        CatalogSyncResult(run.applied, run.acknowledged, run.issues.distinct(), run.confirmedUuids())
    }

    private inner class Run {
        var applied = 0
        var acknowledged = 0
        val issues = mutableListOf<CatalogSyncIssue>()
        private val confirmedParents = mutableSetOf<String>()
        private val confirmedChildren = mutableSetOf<RemoteSubcategoryDto>()
        private val blocked = mutableSetOf<String>()

        fun confirmedUuids(): Set<String> =
            (confirmedParents + confirmedChildren.filter { it.categoryUuid in confirmedParents && it.categoryUuid !in blocked }
                .map { it.uuid }) - blocked

        suspend fun execute() {
            checkSession()
            // A failed collection read abandons the snapshot before making any changes.
            val snapshot = remoteCall { remote.fetch(local.uid) }
            checkSession()
            blocked += snapshot.invalidDocumentIds
            blocked += snapshot.entries.groupingBy { it.uuid }.eachCount().filterValues { it > 1 }.keys
            blocked.forEach { issue(CatalogIssueKind.INVALID_REMOTE, it) }

            val categories = snapshot.entries.filterIsInstance<RemoteCategoryDto>()
            val children = snapshot.entries.filterIsInstance<RemoteSubcategoryDto>()
            categories.filterNot { it.uuid in blocked }.forEach { category ->
                val result = apply(category)
                recordParentResult(category.uuid, result)
            }

            publishPending { it is RemoteCategoryDto }

            children.filterNot { it.uuid in blocked }.forEach { child ->
                if (parentAvailable(child)) apply(child)
            }
            publishPending { it is RemoteSubcategoryDto }
        }

        private suspend fun publishPending(select: (RemoteCatalogEntry) -> Boolean) {
            checkSession()
            val pending = localCall { local.pending() }
            checkSession()
            blocked += pending.invalidDocumentIds
            pending.invalidDocumentIds.forEach { issue(CatalogIssueKind.INVALID_LOCAL, it) }
            for (entry in pending.entries.filter(select)) {
                checkSession()
                if (entry.uuid in blocked || entry.uuid in pending.invalidDocumentIds) continue
                if (entry is RemoteSubcategoryDto && !parentAvailable(entry)) continue

                val winner = try {
                    remoteCall { remote.publish(local.uid, entry) }
                } catch (error: CatalogSyncException) {
                    checkSession()
                    if (error.kind == CatalogIssueKind.UNAUTHENTICATED ||
                        error.kind == CatalogIssueKind.ACCOUNT_CHANGED ||
                        error.kind == CatalogIssueKind.PERMISSION_DENIED ||
                        error.kind == CatalogIssueKind.PROTOCOL_MISMATCH
                    ) throw error
                    issue(error.kind, entry.uuid)
                    if (error.kind == CatalogIssueKind.INVALID_REMOTE ||
                        error.kind == CatalogIssueKind.REVISION_CONFLICT
                    ) {
                        blocked += entry.uuid
                        confirmedParents -= entry.uuid
                    }
                    if (error.kind == CatalogIssueKind.TRANSIENT_REMOTE || error.kind == CatalogIssueKind.REMOTE_FAILURE) {
                        try {
                            localCall { local.markFailed(entry) }
                        } catch (localError: CatalogSyncException) {
                            issue(localError.kind, entry.uuid)
                        }
                    }
                    continue
                }
                checkSession()
                when (reconcileCatalog(entry, winner)) {
                    CatalogReconciliation.LOCAL_WINS,
                    CatalogReconciliation.INCOMPATIBLE_STRUCTURE,
                    CatalogReconciliation.REVISION_CONTENT_MISMATCH -> {
                        issue(CatalogIssueKind.REVISION_CONFLICT, entry.uuid)
                        blocked += entry.uuid
                        confirmedParents -= entry.uuid
                    }
                    CatalogReconciliation.REMOTE_WINS, CatalogReconciliation.IDENTICAL -> {
                        val result = apply(winner, entry)
                        if (winner is RemoteCategoryDto) recordParentResult(winner.uuid, result)
                    }
                }
            }
        }

        private fun recordParentResult(uuid: String, result: CatalogApplyResult?) {
            when (result) {
                CatalogApplyResult.APPLIED, CatalogApplyResult.ACKNOWLEDGED, CatalogApplyResult.UNCHANGED,
                CatalogApplyResult.LOCAL_NEWER, CatalogApplyResult.LOCAL_CHANGED -> confirmedParents += uuid
                else -> blocked += uuid
            }
        }

        private fun parentAvailable(child: RemoteSubcategoryDto): Boolean {
            val available = child.categoryUuid in confirmedParents && child.categoryUuid !in blocked
            if (!available) issue(CatalogIssueKind.MISSING_PARENT, child.uuid)
            return available
        }

        private suspend fun apply(
            entry: RemoteCatalogEntry,
            sent: RemoteCatalogEntry? = null,
        ): CatalogApplyResult? {
            checkSession()
            val result = try {
                localCall { local.apply(entry, sent) }
            } catch (error: CatalogSyncException) {
                issue(error.kind, entry.uuid)
                return null
            }
            checkSession()
            if (entry is RemoteSubcategoryDto && result in confirmedResults) confirmedChildren += entry
            when (result) {
                CatalogApplyResult.APPLIED -> applied++
                CatalogApplyResult.ACKNOWLEDGED -> acknowledged++
                CatalogApplyResult.CONFLICT -> {
                    blocked += entry.uuid
                    issue(CatalogIssueKind.REVISION_CONFLICT, entry.uuid)
                }
                CatalogApplyResult.INVALID_LOCAL -> {
                    blocked += entry.uuid
                    issue(CatalogIssueKind.INVALID_LOCAL, entry.uuid)
                }
                CatalogApplyResult.MISSING_PARENT -> issue(CatalogIssueKind.MISSING_PARENT, entry.uuid)
                CatalogApplyResult.LOCAL_CHANGED -> issue(CatalogIssueKind.LOCAL_CHANGED, entry.uuid)
                CatalogApplyResult.LOCAL_NEWER, CatalogApplyResult.UNCHANGED -> Unit
            }
            return result
        }

        private suspend fun checkSession() {
            currentCoroutineContext().ensureActive()
            val uid = session.currentUid()?.takeIf(::isValidCatalogUid)
                ?: throw CatalogSyncException(CatalogIssueKind.UNAUTHENTICATED)
            if (uid != local.uid) throw CatalogSyncException(CatalogIssueKind.ACCOUNT_CHANGED)
        }

        private fun issue(kind: CatalogIssueKind, uuid: String) {
            issues += CatalogSyncIssue(kind, uuid)
        }
    }
    private companion object {
        val confirmedResults = setOf(
            CatalogApplyResult.APPLIED, CatalogApplyResult.ACKNOWLEDGED, CatalogApplyResult.UNCHANGED,
            CatalogApplyResult.LOCAL_NEWER, CatalogApplyResult.LOCAL_CHANGED,
        )
    }
}

private suspend fun <T> remoteCall(block: suspend () -> T): T = mappedCall(CatalogIssueKind.REMOTE_FAILURE, block)
private suspend fun <T> localCall(block: suspend () -> T): T = mappedCall(CatalogIssueKind.LOCAL_PERSISTENCE, block)

private suspend fun <T> mappedCall(fallback: CatalogIssueKind, block: suspend () -> T): T = try {
    block()
} catch (error: CancellationException) {
    throw error
} catch (error: CatalogSyncException) {
    throw error
} catch (error: Exception) {
    throw CatalogSyncException(fallback, error)
}
