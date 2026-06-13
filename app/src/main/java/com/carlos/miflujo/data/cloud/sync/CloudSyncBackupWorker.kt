package com.carlos.miflujo.data.cloud.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.carlos.miflujo.MiFlujoAppProvider
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import kotlinx.coroutines.CancellationException

internal class CloudSyncBackupRuntimeStateProvider(
    private val cloudSyncEnabled: () -> Boolean,
    private val cloudSyncActivated: () -> Boolean,
    private val accountAuthorized: suspend () -> Boolean,
    private val syncRunning: () -> Boolean,
    private val hasPendingLocalChanges: suspend () -> Boolean,
) {
    suspend fun getRuntimeState(): CloudSyncSchedulerRuntimeState =
        CloudSyncSchedulerRuntimeState(
            cloudSyncEnabled = cloudSyncEnabled(),
            cloudSyncActivated = cloudSyncActivated(),
            // CONNECTED is enforced by this worker's WorkManager constraint.
            networkAvailable = true,
            accountAuthorized = accountAuthorized(),
            alreadyRunning = syncRunning(),
            // Account operations are UI-owned; there is no background account operation runner.
            accountOperationRunning = false,
            hasPendingLocalChanges = hasPendingLocalChanges(),
        )
}

internal enum class CloudSyncBackupWorkResult {
    SUCCESS,
    RETRY,
}

internal fun CloudSyncSchedulerRequestResult.toBackupWorkResult(): CloudSyncBackupWorkResult =
    when (action) {
        CloudSyncSchedulerAction.SKIP_DISABLED,
        CloudSyncSchedulerAction.SKIP_NOT_ACTIVATED,
        CloudSyncSchedulerAction.SKIP_NO_PENDING_CHANGES,
        CloudSyncSchedulerAction.SKIP_ACCOUNT_NOT_AUTHORIZED,
        -> CloudSyncBackupWorkResult.SUCCESS

        CloudSyncSchedulerAction.SKIP_OFFLINE,
        CloudSyncSchedulerAction.SKIP_ALREADY_RUNNING,
        CloudSyncSchedulerAction.SKIP_ACCOUNT_OPERATION_RUNNING,
        -> CloudSyncBackupWorkResult.RETRY

        CloudSyncSchedulerAction.RUN -> when (val outcome = runOutcome) {
            is CloudSyncRunOutcome.Completed -> when (outcome.result.status) {
                CloudSyncStatus.SUCCESS,
                CloudSyncStatus.PARTIAL,
                CloudSyncStatus.SIGNED_OUT,
                CloudSyncStatus.UNAUTHORIZED,
                -> CloudSyncBackupWorkResult.SUCCESS

                CloudSyncStatus.FAILURE -> CloudSyncBackupWorkResult.RETRY
            }

            CloudSyncRunOutcome.SkippedDisabled -> CloudSyncBackupWorkResult.SUCCESS
            CloudSyncRunOutcome.SkippedAlreadyRunning,
            CloudSyncRunOutcome.Failure,
            null,
            -> CloudSyncBackupWorkResult.RETRY
        }
    }

class CloudSyncBackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return try {
            val container = MiFlujoAppProvider.container(applicationContext)
            val runtimeState = CloudSyncBackupRuntimeStateProvider(
                cloudSyncEnabled = container.cloudSyncEnabledStore::isEnabled,
                cloudSyncActivated = container.cloudSyncActivationStore::isActivated,
                accountAuthorized = {
                    container.cloudAccountRepository.getCurrentStatus() is
                        CloudAccountStatus.Authorized
                },
                syncRunning = { container.cloudSyncRunCoordinator.isRunning },
                hasPendingLocalChanges = {
                    container.cloudSyncPendingChangesProvider.hasPendingLocalChanges()
                },
            ).getRuntimeState()
            val requestResult = CloudSyncSchedulerCoordinator(
                executor = container.cloudSyncRunCoordinator,
            ).requestSync(
                runtimeState.toDecisionInput(CloudSyncTriggerReason.WORK_MANAGER_BACKUP),
            )

            when (requestResult.toBackupWorkResult()) {
                CloudSyncBackupWorkResult.SUCCESS -> Result.success()
                CloudSyncBackupWorkResult.RETRY -> Result.retry()
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            logMiFlujoSyncError("Cloud Sync backup worker could not evaluate or run.")
            Result.retry()
        }
    }
}
