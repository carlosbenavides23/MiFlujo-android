package com.carlos.miflujo.domain.sync

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementSyncReconcilerTest {
    @Test
    fun `local visible movement with remote missing uploads local`() {
        val local = movement(syncStatus = SyncStatus.LOCAL_ONLY)

        val action = reconcile(local = listOf(local)).singleAction()

        val upload = action as SyncReconciliationAction.UploadLocalMovement
        assertEquals(local.toRemoteSnapshot(), upload.payload)
    }

    @Test
    fun `sync error movement with remote missing is retryable`() {
        val local = movement(syncStatus = SyncStatus.SYNC_ERROR)

        val action = reconcile(local = listOf(local)).singleAction()

        assertTrue(action is SyncReconciliationAction.UploadLocalMovement)
    }

    @Test
    fun `local tombstone with remote missing uploads tombstone`() {
        val local = movement(
            syncStatus = SyncStatus.PENDING_DELETE,
            deletedAt = baseTime.plusHours(2),
            updatedAt = baseTime.plusHours(2),
        )

        val action = reconcile(local = listOf(local)).singleAction()

        val upload = action as SyncReconciliationAction.UploadLocalTombstone
        assertEquals(local.deletedAt, upload.payload.deletedAt)
    }

    @Test
    fun `remote visible movement with local missing inserts synced local movement`() {
        val remote = movement(id = 0).toRemoteSnapshot()

        val action = reconcile(remote = listOf(valid(remote))).singleAction()

        val insert = action as SyncReconciliationAction.InsertRemoteLocally
        assertEquals(0L, insert.movement.id)
        assertEquals(remote.uuid, insert.movement.uuid)
        assertEquals(SyncStatus.SYNCED, insert.movement.syncStatus)
        assertEquals(syncTime, insert.movement.lastSyncedAt)
        assertNull(insert.movement.deletedAt)
    }

    @Test
    fun `remote tombstone with local missing records hidden local tombstone`() {
        val deletedAt = baseTime.plusHours(3)
        val remote = movement(
            id = 0,
            deletedAt = deletedAt,
            updatedAt = deletedAt,
        ).toRemoteSnapshot()

        val action = reconcile(remote = listOf(valid(remote))).singleAction()

        val insert = action as SyncReconciliationAction.InsertRemoteLocally
        assertEquals(deletedAt, insert.movement.deletedAt)
        assertEquals(SyncStatus.SYNCED, insert.movement.syncStatus)
        assertEquals(syncTime, insert.movement.lastSyncedAt)
    }

    @Test
    fun `local newer than remote uploads local`() {
        val local = movement(updatedAt = baseTime.plusHours(2))
        val remote = movement(
            id = 0,
            updatedAt = baseTime.plusHours(1),
            detail = "Remote older",
        ).toRemoteSnapshot()

        val action = reconcile(
            local = listOf(local),
            remote = listOf(valid(remote)),
        ).singleAction()

        val upload = action as SyncReconciliationAction.UploadLocalMovement
        assertEquals(local.detail, upload.payload.detail)
    }

    @Test
    fun `remote newer than local updates local and preserves Room id`() {
        val local = movement(id = 77, updatedAt = baseTime.plusHours(1))
        val remote = movement(
            id = 0,
            updatedAt = baseTime.plusHours(2),
            detail = "Remote newer",
        ).toRemoteSnapshot().copy(createdAt = baseTime.minusDays(3))

        val action = reconcile(
            local = listOf(local),
            remote = listOf(valid(remote)),
        ).singleAction()

        val update = action as SyncReconciliationAction.UpdateLocalFromRemote
        assertEquals(77L, update.movement.id)
        assertEquals("Remote newer", update.movement.detail)
        assertEquals(local.createdAt, update.movement.createdAt)
        assertEquals(SyncStatus.SYNCED, update.movement.syncStatus)
        assertEquals(syncTime, update.movement.lastSyncedAt)
    }

    @Test
    fun `remote tombstone newer or equal updates local tombstone without physical delete`() {
        listOf(baseTime.plusHours(2), baseTime.plusHours(1)).forEach { remoteUpdatedAt ->
            val localUpdatedAt = baseTime.plusHours(1)
            val local = movement(id = 42, updatedAt = localUpdatedAt)
            val remote = movement(
                id = 0,
                updatedAt = remoteUpdatedAt,
                deletedAt = remoteUpdatedAt,
            ).toRemoteSnapshot()

            val action = reconcile(
                local = listOf(local),
                remote = listOf(valid(remote)),
            ).singleAction()

            val update = action as SyncReconciliationAction.UpdateLocalFromRemote
            assertEquals(42L, update.movement.id)
            assertEquals(remoteUpdatedAt, update.movement.deletedAt)
            assertEquals(SyncStatus.SYNCED, update.movement.syncStatus)
        }
    }

    @Test
    fun `local tombstone newer or equal uploads tombstone`() {
        listOf(baseTime.plusHours(2), baseTime.plusHours(1)).forEach { localUpdatedAt ->
            val remoteUpdatedAt = baseTime.plusHours(1)
            val local = movement(
                updatedAt = localUpdatedAt,
                deletedAt = localUpdatedAt,
                syncStatus = SyncStatus.PENDING_DELETE,
            )
            val remote = movement(
                id = 0,
                updatedAt = remoteUpdatedAt,
            ).toRemoteSnapshot()

            val action = reconcile(
                local = listOf(local),
                remote = listOf(valid(remote)),
            ).singleAction()

            assertTrue(action is SyncReconciliationAction.UploadLocalTombstone)
        }
    }

    @Test
    fun `equal timestamp and equivalent data marks local synced`() {
        val local = movement(
            id = 9,
            syncStatus = SyncStatus.PENDING_UPLOAD,
            lastSyncedAt = baseTime.minusDays(1),
        )
        val remote = local.toRemoteSnapshot()

        val action = reconcile(
            local = listOf(local),
            remote = listOf(valid(remote)),
        ).singleAction()

        assertEquals(
            SyncReconciliationAction.MarkLocalSynced(
                localId = 9,
                uuid = testUuid,
                lastSyncedAt = syncTime,
            ),
            action,
        )
    }

    @Test
    fun `equal mutable data with different createdAt marks synced`() {
        val local = movement(
            id = 10,
            syncStatus = SyncStatus.SYNCED,
        )
        val remote = local.toRemoteSnapshot().copy(
            createdAt = local.createdAt.minusDays(3),
        )

        val action = reconcile(
            local = listOf(local),
            remote = listOf(valid(remote)),
        ).singleAction()

        assertEquals(
            SyncReconciliationAction.MarkLocalSynced(
                localId = 10,
                uuid = testUuid,
                lastSyncedAt = syncTime,
            ),
            action,
        )
    }

    @Test
    fun `equal timestamp but different pending data deterministically uploads local`() {
        val local = movement(
            detail = "Local value",
            syncStatus = SyncStatus.PENDING_UPLOAD,
        )
        val remote = movement(
            id = 0,
            detail = "Remote value",
        ).toRemoteSnapshot()

        val action = reconcile(
            local = listOf(local),
            remote = listOf(valid(remote)),
        ).singleAction()

        val upload = action as SyncReconciliationAction.UploadLocalMovement
        assertEquals("Local value", upload.payload.detail)
    }

    @Test
    fun `equal timestamp but different synced data deterministically accepts remote`() {
        val local = movement(
            id = 17,
            detail = "Local value",
            syncStatus = SyncStatus.SYNCED,
        )
        val remote = movement(
            id = 0,
            detail = "Remote value",
        ).toRemoteSnapshot()

        val action = reconcile(
            local = listOf(local),
            remote = listOf(valid(remote)),
        ).singleAction()

        val update = action as SyncReconciliationAction.UpdateLocalFromRemote
        assertEquals(17L, update.movement.id)
        assertEquals("Remote value", update.movement.detail)
    }

    @Test
    fun `pending delete without deletedAt returns item error without crashing plan`() {
        val invalidLocal = movement(
            uuid = testUuid,
            syncStatus = SyncStatus.PENDING_DELETE,
            deletedAt = null,
        )
        val validLocal = movement(
            uuid = secondUuid,
            syncStatus = SyncStatus.PENDING_UPLOAD,
        )

        val actions = reconcile(local = listOf(invalidLocal, validLocal)).actions

        assertTrue(
            actions.contains(
                SyncReconciliationAction.MarkLocalSyncError(
                    localId = invalidLocal.id,
                    uuid = testUuid,
                    reason = LocalSyncErrorReason.PENDING_DELETE_WITHOUT_TOMBSTONE,
                ),
            ),
        )
        assertTrue(actions.any { it is SyncReconciliationAction.UploadLocalMovement })
    }

    @Test
    fun `duplicate local UUID marks every row as error and blocks uploads`() {
        val first = movement(
            id = 41,
            uuid = testUuid,
            syncStatus = SyncStatus.PENDING_UPLOAD,
        )
        val second = movement(
            id = 42,
            uuid = testUuid,
            syncStatus = SyncStatus.PENDING_DELETE,
            deletedAt = baseTime.plusHours(2),
        )

        val actions = reconcile(local = listOf(second, first)).actions

        assertEquals(
            listOf(
                SyncReconciliationAction.MarkLocalSyncError(
                    localId = 41,
                    uuid = testUuid,
                    reason = LocalSyncErrorReason.DUPLICATE_LOCAL_UUID,
                ),
                SyncReconciliationAction.MarkLocalSyncError(
                    localId = 42,
                    uuid = testUuid,
                    reason = LocalSyncErrorReason.DUPLICATE_LOCAL_UUID,
                ),
            ),
            actions,
        )
        assertFalse(
            actions.any {
                it is SyncReconciliationAction.UploadLocalMovement ||
                    it is SyncReconciliationAction.UploadLocalTombstone
            },
        )
    }

    @Test
    fun `invalid remote item is skipped and does not crash or overwrite local`() {
        val local = movement(uuid = testUuid)
        val invalidRemote = RemoteMovementInput.Invalid(
            documentId = testUuid,
            reason = InvalidRemoteItemReason.INVALID_DOCUMENT,
        )

        val actions = reconcile(
            local = listOf(local),
            remote = listOf(invalidRemote),
        ).actions

        assertEquals(
            listOf(
                SyncReconciliationAction.SkipInvalidRemote(
                    documentId = testUuid,
                    reason = InvalidRemoteItemReason.INVALID_DOCUMENT,
                ),
            ),
            actions,
        )
    }

    @Test
    fun `upload actions and payload contain no Room id or local sync metadata`() {
        val actionFieldNames = listOf(
            SyncReconciliationAction.UploadLocalMovement::class.java,
            SyncReconciliationAction.UploadLocalTombstone::class.java,
        ).flatMap { type ->
            type.declaredFields.map { it.name }.filterNot { it.startsWith("$") }
        }.toSet()
        val payloadFieldNames = MovementRemoteSnapshot::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()

        assertEquals(setOf("payload"), actionFieldNames)
        assertFalse("id" in payloadFieldNames)
        assertFalse("syncStatus" in payloadFieldNames)
        assertFalse("lastSyncedAt" in payloadFieldNames)
        assertEquals(RemotePayloadFieldNames, payloadFieldNames)
    }

    private fun reconcile(
        local: List<Movement> = emptyList(),
        remote: List<RemoteMovementInput> = emptyList(),
    ): SyncReconciliationPlan = MovementSyncReconciler.reconcile(
        localMovements = local,
        remoteInputs = remote,
        syncTime = syncTime,
    )

    private fun SyncReconciliationPlan.singleAction(): SyncReconciliationAction =
        actions.single()

    private fun valid(snapshot: MovementRemoteSnapshot): RemoteMovementInput =
        RemoteMovementInput.Valid(snapshot)

    private fun movement(
        id: Long = 1,
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
        const val secondUuid = "bfa01442-30ed-4d90-83ab-cee48d00dfe3"

        val baseTime: LocalDateTime = LocalDateTime.of(2026, 6, 11, 8, 0)
        val syncTime: LocalDateTime = LocalDateTime.of(2026, 6, 11, 12, 0)

        val RemotePayloadFieldNames = setOf(
            "uuid",
            "type",
            "amountMinor",
            "currency",
            "date",
            "category",
            "subcategory",
            "detail",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "schemaVersion",
        )
    }
}
