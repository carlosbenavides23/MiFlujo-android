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

    override suspend fun updateRemoteMovement(movement: Movement) {
        require(movement.id > 0L) {
            "Remote updates must preserve an existing Room id."
        }
        requireSyncedRemoteMovement(movement)
        check(movementDao.updateMovementFromSync(movement.toEntity()) == 1) {
            "Remote movement update did not match one local row."
        }
    }

    override suspend fun markSynced(
        localId: Long,
        uuid: String,
        lastSyncedAt: LocalDateTime,
    ) {
        check(
            movementDao.updateSyncMetadata(
                localId = localId,
                uuid = uuid,
                syncStatus = SyncStatus.SYNCED,
                lastSyncedAtEpochMillis = lastSyncedAt.toEpochMillis(),
            ) == 1,
        ) {
            "Sync metadata update did not match one local row."
        }
    }

    override suspend fun markSyncError(
        localId: Long,
        uuid: String,
    ) {
        check(
            movementDao.updateSyncStatus(
                localId = localId,
                uuid = uuid,
                syncStatus = SyncStatus.SYNC_ERROR,
            ) == 1,
        ) {
            "Sync error update did not match one local row."
        }
    }

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
