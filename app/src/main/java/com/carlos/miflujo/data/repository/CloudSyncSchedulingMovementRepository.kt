package com.carlos.miflujo.data.repository

import com.carlos.miflujo.data.cloud.sync.CloudSyncActivationStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncBackupWorkScheduler
import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncPendingChangesProvider
import com.carlos.miflujo.data.cloud.sync.logMiFlujoSyncError
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class CloudSyncSchedulingMovementRepository(
    private val delegate: MovementRepository,
    private val cloudSyncEnabledStore: CloudSyncEnabledStore,
    private val cloudSyncActivationStore: CloudSyncActivationStore,
    private val pendingChangesProvider: CloudSyncPendingChangesProvider,
    private val backupWorkScheduler: CloudSyncBackupWorkScheduler,
) : MovementRepository {
    override suspend fun insertMovement(movement: Movement): Long {
        val localWrite = movement.withLocalWriteSyncStatus()
        val localId = delegate.insertMovement(localWrite)
        enqueueBackupIfPending()
        return localId
    }

    override suspend fun updateMovement(movement: Movement) {
        delegate.updateMovement(movement.withLocalWriteSyncStatus())
        enqueueBackupIfPending()
    }

    override suspend fun deleteMovement(movement: Movement) {
        delegate.deleteMovement(movement)
        enqueueBackupIfPending()
    }

    override suspend fun getAllMovements(): List<Movement> =
        delegate.getAllMovements()

    override suspend fun replaceAllMovements(movements: List<Movement>) {
        delegate.replaceAllMovements(movements)
    }

    override fun getMovementsByDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Movement>> = delegate.getMovementsByDateRange(startDate, endDate)

    override fun getRecentMovements(limit: Int): Flow<List<Movement>> =
        delegate.getRecentMovements(limit)

    private fun Movement.withLocalWriteSyncStatus(): Movement = copy(
        syncStatus = if (isCloudSyncReadyForAutomaticBackup()) {
            SyncStatus.PENDING_UPLOAD
        } else {
            SyncStatus.LOCAL_ONLY
        },
    )

    private suspend fun enqueueBackupIfPending() {
        if (!isCloudSyncReadyForAutomaticBackup()) return

        try {
            if (pendingChangesProvider.hasPendingLocalChanges()) {
                backupWorkScheduler.enqueueBackup()
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            logMiFlujoSyncError("Cloud Sync backup work could not be enqueued after local write.")
        }
    }

    private fun isCloudSyncReadyForAutomaticBackup(): Boolean =
        cloudSyncEnabledStore.isEnabled() && cloudSyncActivationStore.isActivated()
}
