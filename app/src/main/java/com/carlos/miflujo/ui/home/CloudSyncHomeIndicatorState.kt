package com.carlos.miflujo.ui.home

import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import com.carlos.miflujo.ui.settings.ManualCloudSyncUiState

enum class CloudSyncHomeIndicatorStatus {
    DISABLED,
    ACTIVE,
    SYNCING,
    NO_INTERNET,
    SYNC_WARNING,
    ACCOUNT_ISSUE,
}

sealed interface CloudSyncHomeIndicatorState {
    data object Hidden : CloudSyncHomeIndicatorState
    data class Visible(val status: CloudSyncHomeIndicatorStatus) : CloudSyncHomeIndicatorState
}

fun mapToCloudSyncHomeIndicatorState(
    cloudSyncActivated: Boolean,
    cloudSyncEnabled: Boolean,
    cloudAccountStatus: CloudAccountStatus,
    manualCloudSyncState: ManualCloudSyncUiState,
    isOffline: Boolean,
): CloudSyncHomeIndicatorState {
    if (!cloudSyncActivated) {
        return CloudSyncHomeIndicatorState.Hidden
    }

    if (!cloudSyncEnabled) {
        return CloudSyncHomeIndicatorState.Visible(CloudSyncHomeIndicatorStatus.DISABLED)
    }

    if (isOffline) {
        return CloudSyncHomeIndicatorState.Visible(CloudSyncHomeIndicatorStatus.NO_INTERNET)
    }

    if (manualCloudSyncState is ManualCloudSyncUiState.Running) {
        return CloudSyncHomeIndicatorState.Visible(CloudSyncHomeIndicatorStatus.SYNCING)
    }

    if (
        manualCloudSyncState is ManualCloudSyncUiState.Unauthorized ||
        manualCloudSyncState is ManualCloudSyncUiState.SignedOut
    ) {
        return CloudSyncHomeIndicatorState.Visible(CloudSyncHomeIndicatorStatus.ACCOUNT_ISSUE)
    }

    if (manualCloudSyncState is ManualCloudSyncUiState.Failure) {
        return CloudSyncHomeIndicatorState.Visible(CloudSyncHomeIndicatorStatus.SYNC_WARNING)
    }

    if (
        cloudAccountStatus is CloudAccountStatus.Unauthorized ||
        cloudAccountStatus is CloudAccountStatus.SignedOut
    ) {
        return CloudSyncHomeIndicatorState.Visible(CloudSyncHomeIndicatorStatus.ACCOUNT_ISSUE)
    }

    return CloudSyncHomeIndicatorState.Visible(CloudSyncHomeIndicatorStatus.ACTIVE)
}
