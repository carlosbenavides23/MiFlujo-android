package com.carlos.miflujo.data.cloud.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncSchedulerDecisionPolicyTest {
    @Test
    fun `manual settings can run first sync without pending changes`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.RUN,
            reason = CloudSyncTriggerReason.MANUAL_SETTINGS,
            cloudSyncActivated = false,
            hasPendingLocalChanges = false,
        )
    }

    @Test
    fun `app foreground skips when Cloud Sync is not activated`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_NOT_ACTIVATED,
            reason = CloudSyncTriggerReason.APP_FOREGROUND,
            cloudSyncActivated = false,
        )
    }

    @Test
    fun `connectivity recovered skips when Cloud Sync is not activated`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_NOT_ACTIVATED,
            reason = CloudSyncTriggerReason.CONNECTIVITY_RECOVERED,
            cloudSyncActivated = false,
        )
    }

    @Test
    fun `foreground pending timer skips without pending changes`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_NO_PENDING_CHANGES,
            reason = CloudSyncTriggerReason.FOREGROUND_PENDING_TIMER,
            hasPendingLocalChanges = false,
        )
    }

    @Test
    fun `work manager backup skips without pending changes`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_NO_PENDING_CHANGES,
            reason = CloudSyncTriggerReason.WORK_MANAGER_BACKUP,
            hasPendingLocalChanges = false,
        )
    }

    @Test
    fun `disabled has highest skip priority`() {
        CloudSyncTriggerReason.entries.forEach { reason ->
            assertDecision(
                expected = CloudSyncSchedulerAction.SKIP_DISABLED,
                reason = reason,
                cloudSyncEnabled = false,
                accountOperationRunning = true,
                alreadyRunning = true,
                networkAvailable = false,
                accountAuthorized = false,
                cloudSyncActivated = false,
                hasPendingLocalChanges = false,
            )
        }
    }

    @Test
    fun `offline skips before account activation and pending checks`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_OFFLINE,
            networkAvailable = false,
            accountAuthorized = false,
            cloudSyncActivated = false,
            hasPendingLocalChanges = false,
        )
    }

    @Test
    fun `already running skips before connectivity and account checks`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ALREADY_RUNNING,
            alreadyRunning = true,
            networkAvailable = false,
            accountAuthorized = false,
        )
    }

    @Test
    fun `account operation running skips before already running`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ACCOUNT_OPERATION_RUNNING,
            accountOperationRunning = true,
            alreadyRunning = true,
        )
    }

    @Test
    fun `account not authorized skips before activation and pending checks`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ACCOUNT_NOT_AUTHORIZED,
            accountAuthorized = false,
            cloudSyncActivated = false,
            hasPendingLocalChanges = false,
        )
    }

    @Test
    fun `app foreground can run without pending local changes`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.RUN,
            reason = CloudSyncTriggerReason.APP_FOREGROUND,
            hasPendingLocalChanges = false,
        )
    }

    @Test
    fun `work manager backup can run when pending changes exist`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.RUN,
            reason = CloudSyncTriggerReason.WORK_MANAGER_BACKUP,
            hasPendingLocalChanges = true,
        )
    }

    private fun assertDecision(
        expected: CloudSyncSchedulerAction,
        reason: CloudSyncTriggerReason = CloudSyncTriggerReason.FOREGROUND_PENDING_TIMER,
        cloudSyncEnabled: Boolean = true,
        cloudSyncActivated: Boolean = true,
        networkAvailable: Boolean = true,
        accountAuthorized: Boolean = true,
        alreadyRunning: Boolean = false,
        accountOperationRunning: Boolean = false,
        hasPendingLocalChanges: Boolean = true,
    ) {
        assertEquals(
            expected,
            decideCloudSyncSchedulerAction(
                CloudSyncSchedulerDecisionInput(
                    reason = reason,
                    cloudSyncEnabled = cloudSyncEnabled,
                    cloudSyncActivated = cloudSyncActivated,
                    networkAvailable = networkAvailable,
                    accountAuthorized = accountAuthorized,
                    alreadyRunning = alreadyRunning,
                    accountOperationRunning = accountOperationRunning,
                    hasPendingLocalChanges = hasPendingLocalChanges,
                ),
            ),
        )
    }
}
