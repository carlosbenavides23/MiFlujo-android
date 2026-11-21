package com.carlos.miflujo.data.cloud.catalog

fun interface CatalogSession {
    fun currentUid(): String?
}

data class CatalogSnapshot(
    val entries: List<RemoteCatalogEntry>,
    val invalidDocumentIds: Set<String> = emptySet(),
)

interface CatalogRemoteSource {
    /** Both collections must be read from the server. Throws if either query fails. */
    suspend fun fetch(uid: String): CatalogSnapshot

    /** Atomically compare against the stored revision and return the confirmed winner. */
    suspend fun publish(uid: String, entry: RemoteCatalogEntry): RemoteCatalogEntry
}

interface CatalogSyncLocalSource {
    val uid: String
    suspend fun pending(): CatalogSnapshot
    /** Compare with the latest row in one transaction. A reply is conditional on the entry sent. */
    suspend fun apply(
        entry: RemoteCatalogEntry,
        expectedSent: RemoteCatalogEntry? = null,
    ): CatalogApplyResult

    suspend fun markFailed(entry: RemoteCatalogEntry)
}

enum class CatalogApplyResult {
    APPLIED, ACKNOWLEDGED, UNCHANGED, LOCAL_NEWER, LOCAL_CHANGED, CONFLICT, INVALID_LOCAL, MISSING_PARENT,
}

enum class CatalogIssueKind {
    UNAUTHENTICATED, ACCOUNT_CHANGED, PERMISSION_DENIED, PROTOCOL_MISMATCH, TRANSIENT_REMOTE, REMOTE_FAILURE,
    INVALID_REMOTE, INVALID_LOCAL, MISSING_PARENT, REVISION_CONFLICT, LOCAL_CHANGED, LOCAL_PERSISTENCE,
}

class CatalogSyncException(val kind: CatalogIssueKind, cause: Throwable? = null) : Exception(kind.name, cause)

data class CatalogSyncIssue(val kind: CatalogIssueKind, val uuid: String? = null)

data class CatalogSyncResult(
    val applied: Int,
    val acknowledged: Int,
    val issues: List<CatalogSyncIssue>,
    val confirmedUuids: Set<String> = emptySet(),
) {
    val successful: Boolean get() = issues.isEmpty()
}

internal fun isValidCatalogUid(uid: String) =
    uid.isNotBlank() && '/' !in uid && uid != "." && uid != ".."
