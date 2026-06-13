package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus

enum class RestoreBackupAvailability {
    AVAILABLE,
    BLOCKED_OPERATION_RUNNING,
    BLOCKED_CLOUD_SYNC_ACTIVE,
}

fun mapRestoreBackupAvailability(
    cloudSyncEnabled: Boolean,
    cloudSyncActivated: Boolean,
    cloudAccountStatus: CloudAccountStatus,
    isCloudSyncRunning: Boolean,
    isCloudAccountOperationRunning: Boolean,
): RestoreBackupAvailability {
    if (isCloudSyncRunning || isCloudAccountOperationRunning) {
        return RestoreBackupAvailability.BLOCKED_OPERATION_RUNNING
    }
    if (
        cloudSyncEnabled &&
        cloudSyncActivated &&
        cloudAccountStatus is CloudAccountStatus.Authorized
    ) {
        return RestoreBackupAvailability.BLOCKED_CLOUD_SYNC_ACTIVE
    }
    return RestoreBackupAvailability.AVAILABLE
}
