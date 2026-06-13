package com.carlos.miflujo.data.cloud.sync

fun interface CloudSyncScheduledRunExecutor {
    suspend fun runCloudSync(
        requestId: String,
        reason: CloudSyncTriggerReason,
    ): CloudSyncRunOutcome
}

data class CloudSyncSchedulerRequestResult(
    val requestId: String,
    val action: CloudSyncSchedulerAction,
    val runOutcome: CloudSyncRunOutcome? = null,
)

class CloudSyncSchedulerCoordinator(
    private val executor: CloudSyncScheduledRunExecutor,
    private val requestIdProvider: () -> String = ::generateCloudSyncRequestId,
) {
    suspend fun requestSync(
        input: CloudSyncSchedulerDecisionInput,
    ): CloudSyncSchedulerRequestResult {
        val requestId = requestIdProvider()
        logCloudSyncRequest(requestId, input.reason)

        val action = decideCloudSyncSchedulerAction(input)
        logCloudSyncDecision(
            id = requestId,
            reason = input.reason,
            action = action,
            cloudSyncEnabled = input.cloudSyncEnabled,
            cloudSyncActivated = input.cloudSyncActivated,
            networkAvailable = input.networkAvailable,
            accountAuthorized = input.accountAuthorized,
            alreadyRunning = input.alreadyRunning,
            accountOperationRunning = input.accountOperationRunning,
            hasPendingLocalChanges = input.hasPendingLocalChanges,
        )

        val runOutcome = if (action == CloudSyncSchedulerAction.RUN) {
            executor.runCloudSync(requestId, input.reason)
        } else {
            null
        }
        return CloudSyncSchedulerRequestResult(
            requestId = requestId,
            action = action,
            runOutcome = runOutcome,
        )
    }
}
