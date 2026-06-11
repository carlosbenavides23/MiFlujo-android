package com.carlos.miflujo.data.cloud.sync

import com.carlos.miflujo.domain.model.Movement
import java.time.LocalDateTime

interface CloudSyncLocalDataSource {
    suspend fun fetchAllIncludingTombstones(): List<Movement>

    suspend fun insertRemoteMovement(movement: Movement): Long

    suspend fun updateRemoteMovement(movement: Movement)

    suspend fun markSynced(
        localId: Long,
        uuid: String,
        lastSyncedAt: LocalDateTime,
    )

    suspend fun markSyncError(
        localId: Long,
        uuid: String,
    )
}
