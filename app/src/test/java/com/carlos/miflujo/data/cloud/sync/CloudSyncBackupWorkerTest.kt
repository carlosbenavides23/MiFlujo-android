package com.carlos.miflujo.data.cloud.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncBackupWorkerTest {
    @Test
    fun `runtime state without pending changes skips backup`() = runBlocking {
        val runtimeState = runtimeStateProvider(hasPendingChanges = false).getRuntimeState()

        assertEquals(
            CloudSyncSchedulerAction.SKIP_NO_PENDING_CHANGES,
            decideCloudSyncSchedulerAction(
                runtimeState.toDecisionInput(CloudSyncTriggerReason.WORK_MANAGER_BACKUP),
            ),
        )
    }

    @Test
    fun `runtime state with pending changes runs eligible backup`() = runBlocking {
        val runtimeState = runtimeStateProvider(hasPendingChanges = true).getRuntimeState()

        assertEquals(
            CloudSyncSchedulerAction.RUN,
            decideCloudSyncSchedulerAction(
                runtimeState.toDecisionInput(CloudSyncTriggerReason.WORK_MANAGER_BACKUP),
            ),
        )
    }

    @Test
    fun `no pending changes maps to success`() {
        assertEquals(
            CloudSyncBackupWorkResult.SUCCESS,
            requestResult(CloudSyncSchedulerAction.SKIP_NO_PENDING_CHANGES)
                .toBackupWorkResult(),
        )
    }

    @Test
    fun `offline maps to retry`() {
        assertEquals(
            CloudSyncBackupWorkResult.RETRY,
            requestResult(CloudSyncSchedulerAction.SKIP_OFFLINE).toBackupWorkResult(),
        )
    }

    @Test
    fun `already running maps to retry`() {
        assertEquals(
            CloudSyncBackupWorkResult.RETRY,
            requestResult(CloudSyncSchedulerAction.SKIP_ALREADY_RUNNING)
                .toBackupWorkResult(),
        )
    }

    @Test
    fun `successful and partial runs map to success`() {
        listOf(CloudSyncStatus.SUCCESS, CloudSyncStatus.PARTIAL).forEach { status ->
            assertEquals(
                CloudSyncBackupWorkResult.SUCCESS,
                requestResult(
                    action = CloudSyncSchedulerAction.RUN,
                    outcome = CloudSyncRunOutcome.Completed(CloudSyncResult(status)),
                ).toBackupWorkResult(),
            )
        }
    }

    @Test
    fun `failed run maps to retry`() {
        assertEquals(
            CloudSyncBackupWorkResult.RETRY,
            requestResult(
                action = CloudSyncSchedulerAction.RUN,
                outcome = CloudSyncRunOutcome.Completed(
                    CloudSyncResult(CloudSyncStatus.FAILURE),
                ),
            ).toBackupWorkResult(),
        )
    }

    private fun runtimeStateProvider(
        hasPendingChanges: Boolean,
    ): CloudSyncBackupRuntimeStateProvider = CloudSyncBackupRuntimeStateProvider(
        cloudSyncEnabled = { true },
        cloudSyncActivated = { true },
        accountAuthorized = { true },
        syncRunning = { false },
        hasPendingLocalChanges = { hasPendingChanges },
    )

    private fun requestResult(
        action: CloudSyncSchedulerAction,
        outcome: CloudSyncRunOutcome? = null,
    ): CloudSyncSchedulerRequestResult = CloudSyncSchedulerRequestResult(
        requestId = "test-request",
        action = action,
        runOutcome = outcome,
    )
}
