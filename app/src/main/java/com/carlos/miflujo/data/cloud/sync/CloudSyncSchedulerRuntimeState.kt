package com.carlos.miflujo.data.cloud.sync

data class CloudSyncSchedulerRuntimeState(
    val cloudSyncEnabled: Boolean,
    val cloudSyncActivated: Boolean,
    val networkAvailable: Boolean,
    val accountAuthorized: Boolean,
    val alreadyRunning: Boolean,
    val accountOperationRunning: Boolean,
    val hasPendingLocalChanges: Boolean,
) {
    fun toDecisionInput(
        reason: CloudSyncTriggerReason,
    ): CloudSyncSchedulerDecisionInput = CloudSyncSchedulerDecisionInput(
        reason = reason,
        cloudSyncEnabled = cloudSyncEnabled,
        cloudSyncActivated = cloudSyncActivated,
        networkAvailable = networkAvailable,
        accountAuthorized = accountAuthorized,
        alreadyRunning = alreadyRunning,
        accountOperationRunning = accountOperationRunning,
        hasPendingLocalChanges = hasPendingLocalChanges,
    )
}
