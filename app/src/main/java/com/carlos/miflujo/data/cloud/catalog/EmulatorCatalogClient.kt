package com.carlos.miflujo.data.cloud.catalog

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.carlos.miflujo.data.cloud.catalogmovement.AccountMovementSyncStore
import com.carlos.miflujo.data.cloud.catalogmovement.CatalogMovementFields
import com.carlos.miflujo.data.cloud.catalogmovement.CatalogMovementRemoteSource
import com.carlos.miflujo.data.cloud.catalogmovement.CoordinatedCatalogSync
import com.carlos.miflujo.data.cloud.catalogmovement.MovementSyncSnapshot
import com.carlos.miflujo.data.cloud.catalogmovement.PendingCatalogMovement
import com.carlos.miflujo.data.cloud.catalogmovement.RemoteCatalogMovement
import com.carlos.miflujo.data.cloud.catalogmovement.nextRemoteMovement
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A separate demo app whose SDK instances can only be configured for local emulators here. */
class EmulatorCatalogClient private constructor(
    private val app: FirebaseApp,
    private val firestore: FirebaseFirestore,
    val auth: FirebaseAuth,
    private val boundUser: FirebaseUser? = null,
) : CatalogRemoteSource, CatalogSession {
    private val revoked = AtomicBoolean()

    override fun currentUid(): String? {
        val current = auth.currentUser
        if (boundUser != null && current !== boundUser) revoked.set(true)
        return current?.uid?.takeUnless { revoked.get() }
    }

    internal fun coordinatedSync(context: Context): EmulatorMovementAccount {
        val user = checkNotNull(auth.currentUser)
        val bound = EmulatorCatalogClient(app, firestore, auth, user)
        val local = AccountMovementSyncStore.open(context, bound)
        return EmulatorMovementAccount(local, CoordinatedCatalogSync(
            local, bound.movementRemote(), bound, CatalogSyncEngine(local.catalogSync, bound, bound),
        )) { bound.revoked.set(true) }
    }

    override suspend fun fetch(uid: String): CatalogSnapshot = firestoreCall {
        requireSession(uid)
        val categories = firestore.collection("users/$uid/categories").get(Source.SERVER).awaitCatalogTask()
        requireSession(uid)
        val children = firestore.collection("users/$uid/subcategories").get(Source.SERVER).awaitCatalogTask()
        requireSession(uid)
        if (categories.metadata.isFromCache || children.metadata.isFromCache ||
            categories.metadata.hasPendingWrites() || children.metadata.hasPendingWrites()
        ) throw CatalogSyncException(CatalogIssueKind.TRANSIENT_REMOTE)

        val entries = mutableListOf<RemoteCatalogEntry>()
        val invalid = mutableSetOf<String>()
        fun add(document: DocumentSnapshot, category: Boolean) {
            try {
                entries += decode(document, category)
            } catch (error: CatalogSyncException) {
                if (error.kind != CatalogIssueKind.INVALID_REMOTE) throw error
                invalid += document.id
            }
        }
        categories.documents.forEach { add(it, category = true) }
        children.documents.forEach { add(it, category = false) }
        CatalogSnapshot(entries, invalid)
    }

    override suspend fun publish(uid: String, entry: RemoteCatalogEntry): RemoteCatalogEntry = firestoreCall {
        requireSession(uid)
        val isCategory = entry is RemoteCategoryDto
        val path = if (isCategory) CloudCatalogPath.category(uid, entry.uuid)
        else CloudCatalogPath.subcategory(uid, entry.uuid)
        val reference = firestore.document(path)
        val winner = firestore.runTransaction { transaction ->
            requireSession(uid)
            if (boundUser != null) requireProtocol(transaction.get(protocol(uid)))
            if (entry is RemoteSubcategoryDto) {
                val parent = transaction.get(firestore.document(CloudCatalogPath.category(uid, entry.categoryUuid)))
                if (!parent.exists()) throw CatalogSyncException(CatalogIssueKind.MISSING_PARENT)
                decode(parent, category = true)
            }
            val snapshot = transaction.get(reference)
            val remote = if (snapshot.exists()) decode(snapshot, isCategory) else null
            val decision = remote?.let { reconcileCatalog(entry, it) }
            requireSession(uid)
            when (decision) {
                null, CatalogReconciliation.LOCAL_WINS -> {
                    transaction.set(reference, RemoteCatalogFields.encode(entry))
                    entry
                }
                CatalogReconciliation.IDENTICAL, CatalogReconciliation.REMOTE_WINS -> checkNotNull(remote)
                CatalogReconciliation.INCOMPATIBLE_STRUCTURE, CatalogReconciliation.REVISION_CONTENT_MISMATCH ->
                    throw CatalogSyncException(CatalogIssueKind.REVISION_CONFLICT)
            }
        }.awaitCatalogTask()
        requireSession(uid)
        winner
    }

    private fun protocol(uid: String) = firestore.document("users/$uid/metadata/movementProtocol")

    private fun requireProtocol(snapshot: DocumentSnapshot) {
        if (snapshot.data != mapOf("version" to 2L)) {
            throw CatalogSyncException(CatalogIssueKind.PROTOCOL_MISMATCH)
        }
    }

    private fun movementRemote(): CatalogMovementRemoteSource = object : CatalogMovementRemoteSource {
        override suspend fun requireMovementProtocol(uid: String) = firestoreCall {
            requireSession(uid)
            val marker = protocol(uid).get(Source.SERVER).awaitCatalogTask()
            requireSession(uid)
            if (marker.metadata.isFromCache || marker.metadata.hasPendingWrites()) {
                throw CatalogSyncException(CatalogIssueKind.TRANSIENT_REMOTE)
            }
            requireProtocol(marker)
        }

        override suspend fun fetchMovements(uid: String): MovementSyncSnapshot<RemoteCatalogMovement> = firestoreCall {
            requireMovementProtocol(uid)
            val snapshot = firestore.collection("users/$uid/movements").get(Source.SERVER).awaitCatalogTask()
            requireSession(uid)
            if (snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) {
                throw CatalogSyncException(CatalogIssueKind.TRANSIENT_REMOTE)
            }
            requireMovementProtocol(uid)
            val invalid = mutableSetOf<String>()
            val entries = snapshot.documents.mapNotNull { document ->
                try {
                    decodeMovement(document)
                } catch (error: CatalogSyncException) {
                    if (error.kind != CatalogIssueKind.INVALID_REMOTE) throw error
                    invalid += document.id
                    null
                }
            }
            MovementSyncSnapshot(entries, invalid)
        }

        override suspend fun publishMovement(uid: String, movement: PendingCatalogMovement): RemoteCatalogMovement = firestoreCall {
            requireSession(uid)
            val path = firestore.document("users/$uid/movements/${movement.content.uuid}")
            val receipt = firestore.runTransaction { transaction ->
                requireSession(uid)
                requireProtocol(transaction.get(protocol(uid)))
                val document = transaction.get(path)
                val previous = if (document.exists()) decodeMovement(document) else null
                val next = nextRemoteMovement(movement, previous)
                requireRemoteSelection(transaction, uid, movement)
                requireSession(uid)
                if (next != previous) transaction.set(path, CatalogMovementFields.encode(next))
                next
            }.awaitCatalogTask()
            requireSession(uid)
            receipt
        }
    }

    private fun requireRemoteSelection(transaction: Transaction, uid: String, pending: PendingCatalogMovement) {
        val draft = pending.content.draft
        val parentId = draft.selection.categoryUuid ?: return
        val parent = transaction.get(firestore.document(CloudCatalogPath.category(uid, parentId)))
        if (!parent.exists()) throw CatalogSyncException(CatalogIssueKind.MISSING_PARENT)
        val category = decode(parent, category = true) as RemoteCategoryDto
        if (!category.scope.supports(draft.type)) throw CatalogSyncException(CatalogIssueKind.INVALID_LOCAL)
        draft.selection.subcategoryUuid?.let { childId ->
            val child = transaction.get(firestore.document(CloudCatalogPath.subcategory(uid, childId)))
            if (!child.exists()) throw CatalogSyncException(CatalogIssueKind.MISSING_PARENT)
            val subcategory = decode(child, category = false) as RemoteSubcategoryDto
            if (subcategory.categoryUuid != parentId) throw CatalogSyncException(CatalogIssueKind.INVALID_LOCAL)
        }
    }

    suspend fun close() {
        try {
            firestore.terminate().awaitCatalogTask()
        } finally {
            auth.signOut()
            app.delete()
        }
    }

    private fun requireSession(uid: String) {
        val current = currentUid()?.takeIf(::isValidCatalogUid)
            ?: throw CatalogSyncException(CatalogIssueKind.UNAUTHENTICATED)
        if (uid != current) throw CatalogSyncException(CatalogIssueKind.ACCOUNT_CHANGED)
    }

    companion object {
        const val PROJECT_ID = "demo-miflujo-catalog"

        internal fun validateHost(host: String) {
            require(host == "10.0.2.2" || host == "127.0.0.1") {
                "Invalid Firebase emulator host. Set catalogMovementEmulatorHost to 127.0.0.1 for USB or 10.0.2.2 for AVD."
            }
        }

        fun open(context: Context, host: String = "10.0.2.2", firestorePort: Int = 8080, authPort: Int = 9099): EmulatorCatalogClient {
            validateHost(host)
            require(firestorePort in 1..65535 && authPort in 1..65535)
            val options = FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApplicationId("1:1234567890:android:1234567890abcdef")
                .setApiKey("fake-demo-api-key")
                .build()
            val app = FirebaseApp.initializeApp(context.applicationContext, options, "catalog-emulator-${UUID.randomUUID()}")
            val auth = FirebaseAuth.getInstance(app).apply { useEmulator(host, authPort) }
            val firestore = FirebaseFirestore.getInstance(app).apply {
                useEmulator(host, firestorePort)
                firestoreSettings = FirebaseFirestoreSettings.Builder(firestoreSettings)
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            }
            return EmulatorCatalogClient(app, firestore, auth)
        }
    }
}

internal class EmulatorMovementAccount(
    val local: AccountMovementSyncStore,
    val sync: CoordinatedCatalogSync,
    private val invalidate: () -> Unit,
) : AutoCloseable {
    override fun close() {
        invalidate()
        local.close()
    }
}

private fun decodeMovement(document: DocumentSnapshot): RemoteCatalogMovement = try {
    CatalogMovementFields.decode(document.id, requireNotNull(document.data))
} catch (error: IllegalArgumentException) {
    throw CatalogSyncException(CatalogIssueKind.INVALID_REMOTE, error)
} catch (error: java.time.DateTimeException) {
    throw CatalogSyncException(CatalogIssueKind.INVALID_REMOTE, error)
}

private fun decode(document: DocumentSnapshot, category: Boolean): RemoteCatalogEntry = try {
    val data = requireNotNull(document.data)
    if (category) RemoteCatalogFields.decodeCategory(document.id, data)
    else RemoteCatalogFields.decodeSubcategory(document.id, data)
} catch (error: IllegalArgumentException) {
    throw CatalogSyncException(CatalogIssueKind.INVALID_REMOTE, error)
}

private suspend fun <T> firestoreCall(action: suspend () -> T): T = try {
    action()
} catch (error: FirebaseFirestoreException) {
    // The SDK may wrap exceptions thrown by the transaction callback.
    val original = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<CatalogSyncException>().firstOrNull()
    if (original != null) throw original
    val kind = when (error.code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> CatalogIssueKind.UNAUTHENTICATED
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CatalogIssueKind.PERMISSION_DENIED
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED, FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            CatalogIssueKind.TRANSIENT_REMOTE
        else -> CatalogIssueKind.REMOTE_FAILURE
    }
    throw CatalogSyncException(kind, error)
}

internal suspend fun <T> Task<T>.awaitCatalogTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed"))
        }
    }
}
