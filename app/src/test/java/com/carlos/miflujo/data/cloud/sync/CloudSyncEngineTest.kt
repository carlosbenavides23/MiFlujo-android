package com.carlos.miflujo.data.cloud.sync

import android.content.Context
import com.carlos.miflujo.data.cloud.auth.CloudAccount
import com.carlos.miflujo.data.cloud.auth.CloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import com.carlos.miflujo.data.cloud.firestore.CloudMovementRemoteDataSource
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.domain.sync.InvalidRemoteItemReason
import com.carlos.miflujo.domain.sync.MovementRemoteSnapshot
import com.carlos.miflujo.domain.sync.RemoteMovementInput
import com.carlos.miflujo.domain.sync.toRemoteSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncEngineTest {
    @Test
    fun `signed out returns signed out without touching remote`() = runBlocking {
        val remote = FakeRemoteDataSource()
        val engine = engine(
            accountStatus = CloudAccountStatus.SignedOut,
            remote = remote,
        )

        val result = engine.syncNow()

        assertEquals(CloudSyncStatus.SIGNED_OUT, result.status)
        assertEquals(0, remote.fetchCalls)
        assertTrue(remote.visibleWrites.isEmpty())
        assertTrue(remote.tombstoneWrites.isEmpty())
    }

    @Test
    fun `unauthorized returns unauthorized without touching remote`() = runBlocking {
        val remote = FakeRemoteDataSource()
        val engine = engine(
            accountStatus = CloudAccountStatus.Unauthorized(account),
            remote = remote,
        )

        val result = engine.syncNow()

        assertEquals(CloudSyncStatus.UNAUTHORIZED, result.status)
        assertEquals(0, remote.fetchCalls)
    }

    @Test
    fun `local only visible movement uploads and becomes synced`() = runBlocking {
        val original = movement(syncStatus = SyncStatus.LOCAL_ONLY)
        val local = FakeLocalDataSource(original)
        val remote = FakeRemoteDataSource()

        val result = engine(local = local, remote = remote).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals(1, result.uploaded)
        assertEquals(1, result.markedSynced)
        assertEquals(listOf(account.uid), remote.fetchUids)
        assertEquals(listOf(account.uid), remote.writeUids)
        assertEquals(original.toRemoteSnapshot(), remote.visibleWrites.single())
        assertEquals(
            original.copy(
                syncStatus = SyncStatus.SYNCED,
                lastSyncedAt = syncTime,
            ),
            local.singleMovement(),
        )
    }

    @Test
    fun `pending upload movement uploads and becomes synced`() = runBlocking {
        val original = movement(syncStatus = SyncStatus.PENDING_UPLOAD)
        val local = FakeLocalDataSource(original)
        val remote = FakeRemoteDataSource()

        val result = engine(local = local, remote = remote).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals(1, remote.visibleWrites.size)
        assertEquals(SyncStatus.SYNCED, local.singleMovement().syncStatus)
        assertEquals(syncTime, local.singleMovement().lastSyncedAt)
    }

    @Test
    fun `remote only visible movement inserts locally as synced`() = runBlocking {
        val remoteMovement = movement(id = 0L, detail = "Remote").toRemoteSnapshot()
        val local = FakeLocalDataSource()
        val remote = FakeRemoteDataSource(valid(remoteMovement))

        val result = engine(local = local, remote = remote).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals(1, result.downloaded)
        val inserted = local.singleMovement()
        assertTrue(inserted.id > 0L)
        assertEquals("Remote", inserted.detail)
        assertEquals(SyncStatus.SYNCED, inserted.syncStatus)
        assertEquals(syncTime, inserted.lastSyncedAt)
        assertNull(inserted.deletedAt)
    }

    @Test
    fun `remote newer movement updates local and preserves local identity`() = runBlocking {
        val original = movement(
            id = 41L,
            updatedAt = baseTime.plusHours(1),
            detail = "Local older",
        )
        val remoteMovement = movement(
            id = 0L,
            updatedAt = baseTime.plusHours(2),
            detail = "Remote newer",
        ).copy(createdAt = baseTime.minusDays(5)).toRemoteSnapshot()
        val local = FakeLocalDataSource(original)

        val result = engine(
            local = local,
            remote = FakeRemoteDataSource(valid(remoteMovement)),
        ).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals(1, result.updatedLocal)
        val updated = local.singleMovement()
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals("Remote newer", updated.detail)
        assertEquals(syncTime, updated.lastSyncedAt)
    }

    @Test
    fun `local newer movement uploads remote`() = runBlocking {
        val localMovement = movement(
            updatedAt = baseTime.plusHours(3),
            detail = "Local newer",
        )
        val remoteMovement = movement(
            id = 0L,
            updatedAt = baseTime.plusHours(2),
            detail = "Remote older",
        ).toRemoteSnapshot()
        val remote = FakeRemoteDataSource(valid(remoteMovement))

        val result = engine(
            local = FakeLocalDataSource(localMovement),
            remote = remote,
        ).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals("Local newer", remote.visibleWrites.single().detail)
    }

    @Test
    fun `equivalent local and remote movement updates only local sync metadata`() = runBlocking {
        val pending = movement(
            syncStatus = SyncStatus.PENDING_UPLOAD,
            lastSyncedAt = baseTime.minusDays(1),
        )
        val local = FakeLocalDataSource(pending)
        val remote = FakeRemoteDataSource(valid(pending.toRemoteSnapshot()))

        val result = engine(local = local, remote = remote).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals(1, result.markedSynced)
        assertTrue(remote.visibleWrites.isEmpty())
        assertEquals(
            pending.copy(
                syncStatus = SyncStatus.SYNCED,
                lastSyncedAt = syncTime,
            ),
            local.singleMovement(),
        )
    }

    @Test
    fun `remote tombstone marks local tombstone without physical delete`() = runBlocking {
        val localMovement = movement(id = 91L, updatedAt = baseTime.plusHours(1))
        val deletedAt = baseTime.plusHours(2)
        val remoteTombstone = movement(
            id = 0L,
            updatedAt = deletedAt,
            deletedAt = deletedAt,
        ).toRemoteSnapshot()
        val local = FakeLocalDataSource(localMovement)

        val result = engine(
            local = local,
            remote = FakeRemoteDataSource(valid(remoteTombstone)),
        ).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals(1, local.movements.size)
        assertEquals(deletedAt, local.singleMovement().deletedAt)
        assertEquals(SyncStatus.SYNCED, local.singleMovement().syncStatus)
    }

    @Test
    fun `local tombstone uploads and remains a local tombstone`() = runBlocking {
        val deletedAt = baseTime.plusHours(2)
        val tombstone = movement(
            syncStatus = SyncStatus.PENDING_DELETE,
            updatedAt = deletedAt,
            deletedAt = deletedAt,
        )
        val local = FakeLocalDataSource(tombstone)
        val remote = FakeRemoteDataSource()

        val result = engine(local = local, remote = remote).syncNow()

        assertEquals(CloudSyncStatus.SUCCESS, result.status)
        assertEquals(deletedAt, remote.tombstoneWrites.single().deletedAt)
        assertEquals(1, local.movements.size)
        assertEquals(deletedAt, local.singleMovement().deletedAt)
        assertEquals(SyncStatus.SYNCED, local.singleMovement().syncStatus)
    }

    @Test
    fun `invalid remote item produces partial result and skipped count`() = runBlocking {
        val remote = FakeRemoteDataSource(
            RemoteMovementInput.Invalid(
                documentId = testUuid,
                reason = InvalidRemoteItemReason.INVALID_DOCUMENT,
            ),
        )

        val result = engine(remote = remote).syncNow()

        assertEquals(CloudSyncStatus.PARTIAL, result.status)
        assertEquals(1, result.skippedRemote)
        assertEquals(1, result.remoteErrors)
        assertEquals(0, result.localErrors)
    }

    @Test
    fun `remote write failure marks local sync error without changing financial fields`() =
        runBlocking {
            val original = movement(
                syncStatus = SyncStatus.PENDING_UPLOAD,
                lastSyncedAt = baseTime.minusDays(1),
            )
            val local = FakeLocalDataSource(original)
            val remote = FakeRemoteDataSource().apply {
                failingVisibleUuids += original.uuid
            }

            val result = engine(local = local, remote = remote).syncNow()

            assertEquals(CloudSyncStatus.PARTIAL, result.status)
            assertEquals(1, result.remoteErrors)
            assertEquals(1, result.localErrors)
            assertEquals(
                original.copy(syncStatus = SyncStatus.SYNC_ERROR),
                local.singleMovement(),
            )
        }

    @Test
    fun `mark local sync error action changes only sync status`() = runBlocking {
        val invalid = movement(
            syncStatus = SyncStatus.PENDING_DELETE,
            lastSyncedAt = baseTime.minusDays(1),
            deletedAt = null,
        )
        val local = FakeLocalDataSource(invalid)

        val result = engine(local = local).syncNow()

        assertEquals(CloudSyncStatus.PARTIAL, result.status)
        assertEquals(1, result.localErrors)
        assertEquals(
            invalid.copy(syncStatus = SyncStatus.SYNC_ERROR),
            local.singleMovement(),
        )
    }

    @Test
    fun `sync status and last synced at are never sent to remote`() = runBlocking {
        val remote = FakeRemoteDataSource()
        engine(
            local = FakeLocalDataSource(
                movement(
                    syncStatus = SyncStatus.PENDING_UPLOAD,
                    lastSyncedAt = baseTime.minusDays(1),
                ),
            ),
            remote = remote,
        ).syncNow()

        val remoteFields = remote.visibleWrites.single()::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }

        assertFalse("syncStatus" in remoteFields)
        assertFalse("lastSyncedAt" in remoteFields)
    }

    @Test
    fun `Room id is never sent to remote`() = runBlocking {
        val remote = FakeRemoteDataSource()
        engine(
            local = FakeLocalDataSource(movement(id = 987L)),
            remote = remote,
        ).syncNow()

        val payload = remote.visibleWrites.single()
        val remoteFields = payload::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }

        assertFalse("id" in remoteFields)
        assertEquals(testUuid, payload.uuid)
    }

    private fun engine(
        accountStatus: CloudAccountStatus = CloudAccountStatus.Authorized(account),
        local: FakeLocalDataSource = FakeLocalDataSource(),
        remote: FakeRemoteDataSource = FakeRemoteDataSource(),
    ): CloudSyncEngine = CloudSyncEngine(
        cloudAccountRepository = FakeCloudAccountRepository(accountStatus),
        localDataSource = local,
        remoteDataSource = remote,
        syncTimeProvider = { syncTime },
    )

    private fun valid(snapshot: MovementRemoteSnapshot): RemoteMovementInput =
        RemoteMovementInput.Valid(snapshot)

    private fun movement(
        id: Long = 1L,
        uuid: String = testUuid,
        detail: String? = "Pago de luz",
        updatedAt: LocalDateTime = baseTime.plusHours(1),
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        lastSyncedAt: LocalDateTime? = null,
        deletedAt: LocalDateTime? = null,
    ): Movement = Movement(
        id = id,
        uuid = uuid,
        type = MovementType.EXPENSE,
        amountMinor = 180_050L,
        currency = Currency.CORDOBA,
        date = LocalDate.of(2026, 6, 11),
        category = MovementCategory.FIXED_COST,
        subcategory = MovementSubcategory.ELECTRICITY,
        detail = detail,
        createdAt = baseTime,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
        lastSyncedAt = lastSyncedAt,
        deletedAt = deletedAt,
    )

    private companion object {
        const val testUuid = "3f83ad74-77f1-4625-a525-66d860a86e76"

        val account = CloudAccount(
            uid = "authorized-test-uid",
            email = "user@example.com",
            displayName = "Test User",
        )
        val baseTime: LocalDateTime = LocalDateTime.of(2026, 6, 11, 8, 0)
        val syncTime: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)
    }
}

private class FakeCloudAccountRepository(
    private val status: CloudAccountStatus,
) : CloudAccountRepository {
    override suspend fun getCurrentStatus(): CloudAccountStatus = status

    override suspend fun signInWithGoogle(context: Context): CloudAccountStatus {
        error("Not used in sync engine tests.")
    }

    override suspend fun signOut(context: Context) = Unit
}

private class FakeLocalDataSource(
    vararg initialMovements: Movement,
) : CloudSyncLocalDataSource {
    val movements = initialMovements.toMutableList()

    override suspend fun fetchAllIncludingTombstones(): List<Movement> =
        movements.toList()

    override suspend fun insertRemoteMovement(movement: Movement): Long {
        val localId = (movements.maxOfOrNull(Movement::id) ?: 0L) + 1L
        movements += movement.copy(id = localId)
        return localId
    }

    override suspend fun updateRemoteMovement(movement: Movement) {
        val index = movementIndex(movement.id, movement.uuid)
        movements[index] = movement
    }

    override suspend fun markSynced(
        localId: Long,
        uuid: String,
        lastSyncedAt: LocalDateTime,
    ) {
        val index = movementIndex(localId, uuid)
        movements[index] = movements[index].copy(
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = lastSyncedAt,
        )
    }

    override suspend fun markSyncError(
        localId: Long,
        uuid: String,
    ) {
        val index = movementIndex(localId, uuid)
        movements[index] = movements[index].copy(syncStatus = SyncStatus.SYNC_ERROR)
    }

    fun singleMovement(): Movement = movements.single()

    private fun movementIndex(
        localId: Long,
        uuid: String,
    ): Int {
        val index = movements.indexOfFirst { it.id == localId && it.uuid == uuid }
        check(index >= 0) { "Movement not found." }
        return index
    }
}

private class FakeRemoteDataSource(
    vararg initialInputs: RemoteMovementInput,
) : CloudMovementRemoteDataSource {
    val inputs = initialInputs.toMutableList()
    val visibleWrites = mutableListOf<MovementRemoteSnapshot>()
    val tombstoneWrites = mutableListOf<MovementRemoteSnapshot>()
    val fetchUids = mutableListOf<String>()
    val writeUids = mutableListOf<String>()
    val failingVisibleUuids = mutableSetOf<String>()
    var fetchCalls: Int = 0

    override suspend fun fetchAll(uid: String): List<RemoteMovementInput> {
        fetchCalls += 1
        fetchUids += uid
        return inputs.toList()
    }

    override suspend fun upsertVisible(
        uid: String,
        movement: MovementRemoteSnapshot,
    ) {
        writeUids += uid
        if (movement.uuid in failingVisibleUuids) {
            error("Remote write failed.")
        }
        visibleWrites += movement
    }

    override suspend fun upsertTombstone(
        uid: String,
        movement: MovementRemoteSnapshot,
    ) {
        writeUids += uid
        tombstoneWrites += movement
    }
}
