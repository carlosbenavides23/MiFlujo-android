package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.sync.CloudSyncResult
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunner
import com.carlos.miflujo.data.cloud.sync.CloudSyncStatus
import com.carlos.miflujo.data.cloud.sync.logMiFlujoSyncDebug
import com.carlos.miflujo.data.cloud.sync.logMiFlujoSyncError
import com.carlos.miflujo.data.cloud.sync.toSafeSyncLogMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ManualCloudSyncUiState {
    data object Idle : ManualCloudSyncUiState
    data object Running : ManualCloudSyncUiState

    data class Success(
        val counts: ManualCloudSyncCounts,
    ) : ManualCloudSyncUiState

    data class Partial(
        val counts: ManualCloudSyncCounts,
    ) : ManualCloudSyncUiState

    data object SignedOut : ManualCloudSyncUiState
    data object Unauthorized : ManualCloudSyncUiState
    data object Failure : ManualCloudSyncUiState
}

data class ManualCloudSyncCounts(
    val uploaded: Int,
    val downloaded: Int,
    val updatedLocal: Int,
    val markedSynced: Int,
    val skippedRemote: Int,
    val localErrors: Int,
    val remoteErrors: Int,
)

class ManualCloudSyncStateHolder(
    private val cloudSyncRunner: CloudSyncRunner,
) {
    private val running = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<ManualCloudSyncUiState>(
        ManualCloudSyncUiState.Idle,
    )

    val state: StateFlow<ManualCloudSyncUiState> = mutableState.asStateFlow()

    suspend fun syncNow() {
        logMiFlujoSyncDebug("Manual Cloud Sync requested.")
        if (!running.compareAndSet(false, true)) {
            logMiFlujoSyncDebug("Manual Cloud Sync ignored: already running.")
            return
        }

        mutableState.value = ManualCloudSyncUiState.Running
        logMiFlujoSyncDebug("Manual Cloud Sync state updated: Running.")
        try {
            val result = cloudSyncRunner.syncNow()
            logMiFlujoSyncDebug(
                result.toSafeSyncLogMessage("Manual Cloud Sync runner returned"),
            )
            mutableState.value = result.toUiState()
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            logMiFlujoSyncError(
                "Manual Cloud Sync failed before result: class=${exception.javaClass.name}, " +
                    "message=Manual sync failed.",
            )
            mutableState.value = ManualCloudSyncUiState.Failure
        } finally {
            running.set(false)
        }
    }
}

private fun CloudSyncResult.toUiState(): ManualCloudSyncUiState {
    val counts = ManualCloudSyncCounts(
        uploaded = uploaded,
        downloaded = downloaded,
        updatedLocal = updatedLocal,
        markedSynced = markedSynced,
        skippedRemote = skippedRemote,
        localErrors = localErrors,
        remoteErrors = remoteErrors,
    )
    return when (status) {
        CloudSyncStatus.SUCCESS -> ManualCloudSyncUiState.Success(counts)
        CloudSyncStatus.PARTIAL -> ManualCloudSyncUiState.Partial(counts)
        CloudSyncStatus.SIGNED_OUT -> ManualCloudSyncUiState.SignedOut
        CloudSyncStatus.UNAUTHORIZED -> ManualCloudSyncUiState.Unauthorized
        CloudSyncStatus.FAILURE -> ManualCloudSyncUiState.Failure
    }
}
