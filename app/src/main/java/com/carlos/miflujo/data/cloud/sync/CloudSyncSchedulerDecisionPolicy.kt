package com.carlos.miflujo.data.cloud.sync

data class CloudSyncSchedulerDecisionInput(
    val reason: CloudSyncTriggerReason,
    val cloudSyncEnabled: Boolean,
    val cloudSyncActivated: Boolean,
    val networkAvailable: Boolean,
    val accountAuthorized: Boolean,
    val alreadyRunning: Boolean,
    val accountOperationRunning: Boolean,
    val hasPendingLocalChanges: Boolean,
)

fun decideCloudSyncSchedulerAction(
    input: CloudSyncSchedulerDecisionInput,
): CloudSyncSchedulerAction {
    if (!input.cloudSyncEnabled) return CloudSyncSchedulerAction.SKIP_DISABLED
    if (input.accountOperationRunning) {
        return CloudSyncSchedulerAction.SKIP_ACCOUNT_OPERATION_RUNNING
    }
    if (input.alreadyRunning) return CloudSyncSchedulerAction.SKIP_ALREADY_RUNNING
    if (!input.networkAvailable) return CloudSyncSchedulerAction.SKIP_OFFLINE
    if (!input.accountAuthorized) {
        return CloudSyncSchedulerAction.SKIP_ACCOUNT_NOT_AUTHORIZED
    }
    if (input.reason.requiresActivation() && !input.cloudSyncActivated) {
        return CloudSyncSchedulerAction.SKIP_NOT_ACTIVATED
    }
    if (input.reason.requiresPendingLocalChanges() && !input.hasPendingLocalChanges) {
        return CloudSyncSchedulerAction.SKIP_NO_PENDING_CHANGES
    }
    return CloudSyncSchedulerAction.RUN
}

private fun CloudSyncTriggerReason.requiresActivation(): Boolean =
    this != CloudSyncTriggerReason.MANUAL_SETTINGS

private fun CloudSyncTriggerReason.requiresPendingLocalChanges(): Boolean =
    this == CloudSyncTriggerReason.FOREGROUND_PENDING_TIMER ||
        this == CloudSyncTriggerReason.WORK_MANAGER_BACKUP
