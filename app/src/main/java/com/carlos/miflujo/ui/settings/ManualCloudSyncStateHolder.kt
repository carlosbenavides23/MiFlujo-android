package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncResult
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunCoordinator
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunOutcome
import com.carlos.miflujo.data.cloud.sync.CloudSyncStatus
import com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason
import com.carlos.miflujo.data.cloud.sync.logMiFlujoSyncDebug
import com.carlos.miflujo.data.cloud.sync.logMiFlujoSyncError
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
    private val cloudSyncRunCoordinator: CloudSyncRunCoordinator,
    private val cloudSyncEnabledStore: CloudSyncEnabledStore,
) {
    private val mutableState = MutableStateFlow<ManualCloudSyncUiState>(
        ManualCloudSyncUiState.Idle,
    )
    private val mutableCloudSyncEnabled = MutableStateFlow(
        cloudSyncEnabledStore.isEnabled(),
    )

    val state: StateFlow<ManualCloudSyncUiState> = mutableState.asStateFlow()
    val cloudSyncActivated: StateFlow<Boolean> = cloudSyncRunCoordinator.cloudSyncActivated
    val cloudSyncEnabled: StateFlow<Boolean> = mutableCloudSyncEnabled.asStateFlow()
    val lastSyncTimestamp: StateFlow<Long?> = cloudSyncRunCoordinator.lastSyncTimestamp
    val isSyncRunning: Boolean
        get() = cloudSyncRunCoordinator.isRunning

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

    suspend fun syncNow(
        requestId: String,
        reason: CloudSyncTriggerReason,
    ): CloudSyncRunOutcome {
        if (!mutableCloudSyncEnabled.value) {
            logMiFlujoSyncDebug("Manual Cloud Sync ignored: disabled.")
            return CloudSyncRunOutcome.SkippedDisabled
        }

        val outcome = cloudSyncRunCoordinator.runCloudSync(
            requestId = requestId,
            reason = reason,
            onStarted = {
                mutableState.value = ManualCloudSyncUiState.Running
            },
        )
        when (outcome) {
            is CloudSyncRunOutcome.Completed -> {
                mutableState.value = outcome.result.toUiState()
            }

            CloudSyncRunOutcome.Failure -> {
                mutableState.value = ManualCloudSyncUiState.Failure
            }

            CloudSyncRunOutcome.SkippedAlreadyRunning -> {
                logMiFlujoSyncDebug("Manual Cloud Sync ignored: already running.")
            }

            CloudSyncRunOutcome.SkippedDisabled -> {
                logMiFlujoSyncDebug("Manual Cloud Sync ignored: disabled.")
            }
        }
        return outcome
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
