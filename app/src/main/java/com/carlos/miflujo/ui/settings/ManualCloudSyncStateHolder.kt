package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.sync.CloudSyncActivationStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncMetadataStore
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
    private val cloudSyncActivationStore: CloudSyncActivationStore,
    private val cloudSyncEnabledStore: CloudSyncEnabledStore,
    private val cloudSyncMetadataStore: CloudSyncMetadataStore,
) {
    private val running = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<ManualCloudSyncUiState>(
        ManualCloudSyncUiState.Idle,
    )
    private val mutableCloudSyncActivated = MutableStateFlow(
        cloudSyncActivationStore.isActivated(),
    )
    private val mutableCloudSyncEnabled = MutableStateFlow(
        cloudSyncEnabledStore.isEnabled(),
    )
    private val mutableLastSyncTimestamp = MutableStateFlow(
        cloudSyncMetadataStore.getLastSyncTimestamp(),
    )

    val state: StateFlow<ManualCloudSyncUiState> = mutableState.asStateFlow()
    val cloudSyncActivated: StateFlow<Boolean> = mutableCloudSyncActivated.asStateFlow()
    val cloudSyncEnabled: StateFlow<Boolean> = mutableCloudSyncEnabled.asStateFlow()
    val lastSyncTimestamp: StateFlow<Long?> = mutableLastSyncTimestamp.asStateFlow()

    fun setCloudSyncEnabled(enabled: Boolean) {
        val persisted = runCatching { cloudSyncEnabledStore.setEnabled(enabled) }
            .onFailure {
                logMiFlujoSyncError("Cloud Sync enabled preference could not be toggled.")
            }
            .getOrDefault(false)

        if (persisted) {
            mutableCloudSyncEnabled.value = enabled
        } else {
            logMiFlujoSyncError("Cloud Sync enabled preference persistence failed.")
        }
    }

    suspend fun syncNow() {
        logMiFlujoSyncDebug("Manual Cloud Sync requested.")
        if (!mutableCloudSyncEnabled.value) {
            logMiFlujoSyncDebug("Manual Cloud Sync ignored: disabled.")
            return
        }
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
            if (result.status.activatesCloudSync()) {
                markCloudSyncActivated()
            }
            if (result.status.updatesLastSyncTimestamp()) {
                val timestamp = System.currentTimeMillis()
                cloudSyncMetadataStore.updateLastSyncTimestamp(timestamp)
                mutableLastSyncTimestamp.value = timestamp
            }
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

    private fun markCloudSyncActivated() {
        if (mutableCloudSyncActivated.value) return

        runCatching { cloudSyncActivationStore.markActivated() }
            .onFailure {
                logMiFlujoSyncError("Cloud Sync activation flag could not be persisted.")
            }
        mutableCloudSyncActivated.value = true
    }
}

private fun CloudSyncStatus.activatesCloudSync(): Boolean =
    this == CloudSyncStatus.SUCCESS || this == CloudSyncStatus.PARTIAL

private fun CloudSyncStatus.updatesLastSyncTimestamp(): Boolean =
    this == CloudSyncStatus.SUCCESS || this == CloudSyncStatus.PARTIAL

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
