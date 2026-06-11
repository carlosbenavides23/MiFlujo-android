package com.carlos.miflujo.data.cloud.sync

import com.carlos.miflujo.data.local.MovementDao
import com.carlos.miflujo.data.model.toDomain
import com.carlos.miflujo.data.model.toEntity
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDateTime
import java.time.ZoneOffset

class RoomCloudSyncLocalDataSource(
    private val movementDao: MovementDao,
) : CloudSyncLocalDataSource {
    override suspend fun fetchAllIncludingTombstones(): List<Movement> =
        movementDao.getAllMovementsIncludingDeleted().map { it.toDomain() }

    override suspend fun insertRemoteMovement(movement: Movement): Long {
        require(movement.id == 0L) {
            "Remote inserts must not provide a Room id."
        }
        requireSyncedRemoteMovement(movement)
        return movementDao.insertMovement(movement.toEntity())
    }

    override suspend fun prepareForUpload(
        expectedLocal: Movement,
        tombstone: Boolean,
    ): Movement? = movementDao.prepareMovementForUploadIfUnchanged(
        expected = expectedLocal.toEntity(),
        pendingStatus = if (tombstone) {
            SyncStatus.PENDING_DELETE
        } else {
            SyncStatus.PENDING_UPLOAD
        },
    )?.toDomain()

    override suspend fun updateRemoteMovement(
        expectedLocal: Movement,
        movement: Movement,
    ): Boolean {
        require(movement.id > 0L) {
            "Remote updates must preserve an existing Room id."
        }
        require(expectedLocal.id == movement.id && expectedLocal.uuid == movement.uuid) {
            "Remote updates must target the reconciled local movement."
        }
        requireSyncedRemoteMovement(movement)
        return movementDao.updateMovementFromSyncIfUnchanged(
            expected = expectedLocal.toEntity(),
            replacement = movement.toEntity(),
        )
    }

    override suspend fun markSynced(
        expectedLocal: Movement,
        lastSyncedAt: LocalDateTime,
    ): Boolean = movementDao.markSyncedIfUnchanged(
        expected = expectedLocal.toEntity(),
        lastSyncedAtEpochMillis = lastSyncedAt.toEpochMillis(),
    )

    override suspend fun markSyncError(expectedLocal: Movement): Boolean =
        movementDao.markSyncErrorIfUnchanged(expectedLocal.toEntity())

    private fun requireSyncedRemoteMovement(movement: Movement) {
        require(movement.syncStatus == SyncStatus.SYNCED) {
            "Remote local writes must already be marked SYNCED."
        }
        requireNotNull(movement.lastSyncedAt) {
            "Remote local writes must include lastSyncedAt."
        }
    }
}

private fun LocalDateTime.toEpochMillis(): Long =
    toInstant(ZoneOffset.UTC).toEpochMilli()
