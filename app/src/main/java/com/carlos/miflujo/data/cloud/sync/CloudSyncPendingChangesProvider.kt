package com.carlos.miflujo.data.cloud.sync

import com.carlos.miflujo.data.local.MovementDao
import com.carlos.miflujo.domain.model.SyncStatus

fun interface CloudSyncPendingChangesProvider {
    suspend fun hasPendingLocalChanges(): Boolean
}

class RoomCloudSyncPendingChangesProvider internal constructor(
    private val query: suspend (SyncStatus, SyncStatus, SyncStatus) -> Boolean,
) : CloudSyncPendingChangesProvider {
    constructor(
        movementDao: MovementDao,
    ) : this(movementDao::hasPendingCloudSyncChanges)

    override suspend fun hasPendingLocalChanges(): Boolean = query(
        SyncStatus.PENDING_UPLOAD,
        SyncStatus.PENDING_DELETE,
        SyncStatus.SYNC_ERROR,
    )
}
