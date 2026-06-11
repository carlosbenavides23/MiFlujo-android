package com.carlos.miflujo.data.cloud.sync

import com.carlos.miflujo.data.cloud.auth.CloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import com.carlos.miflujo.data.cloud.firestore.CloudMovementRemoteDataSource
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.sync.MovementSyncReconciler
import com.carlos.miflujo.domain.sync.SyncReconciliationAction
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CloudSyncStatus {
    SUCCESS,
    PARTIAL,
    SIGNED_OUT,
    UNAUTHORIZED,
    FAILURE,
}

data class CloudSyncResult(
    val status: CloudSyncStatus,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val updatedLocal: Int = 0,
    val markedSynced: Int = 0,
    val skippedRemote: Int = 0,
    val localErrors: Int = 0,
    val remoteErrors: Int = 0,
)

class CloudSyncEngine(
    private val cloudAccountRepository: CloudAccountRepository,
    private val localDataSource: CloudSyncLocalDataSource,
    private val remoteDataSource: CloudMovementRemoteDataSource,
    private val syncTimeProvider: () -> LocalDateTime = LocalDateTime::now,
) {
    private val syncMutex = Mutex()

    suspend fun syncNow(): CloudSyncResult = syncMutex.withLock {
        when (val accountStatus = currentAccountStatus()) {
            CloudAccountStatus.SignedOut -> CloudSyncResult(CloudSyncStatus.SIGNED_OUT)
            is CloudAccountStatus.Unauthorized -> CloudSyncResult(CloudSyncStatus.UNAUTHORIZED)
            CloudAccountStatus.Loading -> CloudSyncResult(CloudSyncStatus.FAILURE)
            is CloudAccountStatus.Authorized -> syncAuthorized(accountStatus.account.uid)
            null -> CloudSyncResult(CloudSyncStatus.FAILURE)
        }
    }

    private suspend fun currentAccountStatus(): CloudAccountStatus? = try {
        cloudAccountRepository.getCurrentStatus()
    } catch (exception: Exception) {
        if (exception is CancellationException) throw exception
        null
    }

    private suspend fun syncAuthorized(uid: String): CloudSyncResult {
        val syncTime = syncTimeProvider()
        val localMovements = try {
            localDataSource.fetchAllIncludingTombstones()
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            return CloudSyncResult(
                status = CloudSyncStatus.FAILURE,
                localErrors = 1,
            )
        }
        val remoteInputs = try {
            remoteDataSource.fetchAll(uid)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            return CloudSyncResult(
                status = CloudSyncStatus.FAILURE,
                remoteErrors = 1,
            )
        }
        val plan = try {
            MovementSyncReconciler.reconcile(
                localMovements = localMovements,
                remoteInputs = remoteInputs,
                syncTime = syncTime,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            return CloudSyncResult(
                status = CloudSyncStatus.FAILURE,
                localErrors = 1,
            )
        }

        val counts = MutableCloudSyncCounts()
        val localByUuid = localMovements.associateBy(Movement::uuid)
        plan.actions.forEach { action ->
            applyAction(
                uid = uid,
                action = action,
                localByUuid = localByUuid,
                syncTime = syncTime,
                counts = counts,
            )
        }
        return counts.toResult()
    }

    private suspend fun applyAction(
        uid: String,
        action: SyncReconciliationAction,
        localByUuid: Map<String, Movement>,
        syncTime: LocalDateTime,
        counts: MutableCloudSyncCounts,
    ) {
        when (action) {
            is SyncReconciliationAction.UploadLocalMovement -> upload(
                uid = uid,
                local = localByUuid[action.payload.uuid],
                syncTime = syncTime,
                counts = counts,
            ) {
                remoteDataSource.upsertVisible(uid, action.payload)
            }

            is SyncReconciliationAction.UploadLocalTombstone -> upload(
                uid = uid,
                local = localByUuid[action.payload.uuid],
                syncTime = syncTime,
                counts = counts,
            ) {
                remoteDataSource.upsertTombstone(uid, action.payload)
            }

            is SyncReconciliationAction.InsertRemoteLocally -> {
                try {
                    localDataSource.insertRemoteMovement(action.movement)
                    counts.downloaded += 1
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    counts.localErrors += 1
                }
            }

            is SyncReconciliationAction.UpdateLocalFromRemote -> {
                try {
                    localDataSource.updateRemoteMovement(action.movement)
                    counts.updatedLocal += 1
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    counts.localErrors += 1
                    markSyncErrorBestEffort(
                        localId = action.movement.id,
                        uuid = action.movement.uuid,
                    )
                }
            }

            is SyncReconciliationAction.MarkLocalSynced -> {
                try {
                    localDataSource.markSynced(
                        localId = action.localId,
                        uuid = action.uuid,
                        lastSyncedAt = action.lastSyncedAt,
                    )
                    counts.markedSynced += 1
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    counts.localErrors += 1
                    markSyncErrorBestEffort(action.localId, action.uuid)
                }
            }

            is SyncReconciliationAction.MarkLocalSyncError -> {
                counts.localErrors += 1
                markSyncErrorBestEffort(action.localId, action.uuid)
            }

            is SyncReconciliationAction.SkipInvalidRemote -> {
                counts.skippedRemote += 1
                counts.remoteErrors += 1
            }
        }
    }

    private suspend fun upload(
        uid: String,
        local: Movement?,
        syncTime: LocalDateTime,
        counts: MutableCloudSyncCounts,
        remoteWrite: suspend () -> Unit,
    ) {
        if (local == null) {
            counts.localErrors += 1
            return
        }

        try {
            remoteWrite()
            counts.uploaded += 1
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            counts.remoteErrors += 1
            counts.localErrors += 1
            markSyncErrorBestEffort(local.id, local.uuid)
            return
        }

        try {
            localDataSource.markSynced(
                localId = local.id,
                uuid = local.uuid,
                lastSyncedAt = syncTime,
            )
            counts.markedSynced += 1
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            counts.localErrors += 1
            markSyncErrorBestEffort(local.id, local.uuid)
        }
    }

    private suspend fun markSyncErrorBestEffort(
        localId: Long,
        uuid: String,
    ) {
        try {
            localDataSource.markSyncError(localId, uuid)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
        }
    }
}

private data class MutableCloudSyncCounts(
    var uploaded: Int = 0,
    var downloaded: Int = 0,
    var updatedLocal: Int = 0,
    var markedSynced: Int = 0,
    var skippedRemote: Int = 0,
    var localErrors: Int = 0,
    var remoteErrors: Int = 0,
) {
    fun toResult(): CloudSyncResult = CloudSyncResult(
        status = if (localErrors == 0 && remoteErrors == 0) {
            CloudSyncStatus.SUCCESS
        } else {
            CloudSyncStatus.PARTIAL
        },
        uploaded = uploaded,
        downloaded = downloaded,
        updatedLocal = updatedLocal,
        markedSynced = markedSynced,
        skippedRemote = skippedRemote,
        localErrors = localErrors,
        remoteErrors = remoteErrors,
    )
}
