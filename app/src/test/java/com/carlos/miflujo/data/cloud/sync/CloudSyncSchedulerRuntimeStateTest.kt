package com.carlos.miflujo.data.cloud.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncSchedulerRuntimeStateTest {
    @Test
    fun `runtime state maps every field to decision input`() {
        val runtimeState = CloudSyncSchedulerRuntimeState(
            cloudSyncEnabled = false,
            cloudSyncActivated = true,
            networkAvailable = false,
            accountAuthorized = true,
            alreadyRunning = true,
            accountOperationRunning = false,
            hasPendingLocalChanges = true,
        )

        val input = runtimeState.toDecisionInput(
            reason = CloudSyncTriggerReason.FOREGROUND_PENDING_TIMER,
        )

        assertEquals(false, input.cloudSyncEnabled)
        assertEquals(true, input.cloudSyncActivated)
        assertEquals(false, input.networkAvailable)
        assertEquals(true, input.accountAuthorized)
        assertEquals(true, input.alreadyRunning)
        assertEquals(false, input.accountOperationRunning)
        assertEquals(true, input.hasPendingLocalChanges)
    }

    @Test
    fun `runtime state preserves trigger reason`() {
        val input = runtimeState().toDecisionInput(
            reason = CloudSyncTriggerReason.CONNECTIVITY_RECOVERED,
        )

        assertEquals(CloudSyncTriggerReason.CONNECTIVITY_RECOVERED, input.reason)
    }

    private fun runtimeState(): CloudSyncSchedulerRuntimeState =
        CloudSyncSchedulerRuntimeState(
            cloudSyncEnabled = true,
            cloudSyncActivated = true,
            networkAvailable = true,
            accountAuthorized = true,
            alreadyRunning = false,
            accountOperationRunning = false,
            hasPendingLocalChanges = false,
        )
}
