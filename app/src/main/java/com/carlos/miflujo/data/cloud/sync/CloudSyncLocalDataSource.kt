package com.carlos.miflujo.data.cloud.sync

import com.carlos.miflujo.domain.model.Movement
import java.time.LocalDateTime

interface CloudSyncLocalDataSource {
    suspend fun fetchAllIncludingTombstones(): List<Movement>

    suspend fun insertRemoteMovement(movement: Movement): Long

    suspend fun prepareForUpload(
        expectedLocal: Movement,
        tombstone: Boolean,
    ): Movement?

    suspend fun updateRemoteMovement(
        expectedLocal: Movement,
        movement: Movement,
    ): Boolean

    suspend fun markSynced(
        expectedLocal: Movement,
        lastSyncedAt: LocalDateTime,
    ): Boolean

    suspend fun markSyncError(expectedLocal: Movement): Boolean
}
