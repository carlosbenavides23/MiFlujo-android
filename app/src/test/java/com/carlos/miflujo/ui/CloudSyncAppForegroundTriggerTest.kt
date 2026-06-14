package com.carlos.miflujo.ui

import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerAction
import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerRuntimeState
import com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason
import com.carlos.miflujo.data.cloud.sync.decideCloudSyncSchedulerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncAppForegroundTriggerTest {
    @Test
    fun `eligible app foreground state runs without pending changes`() {
        assertDecision(CloudSyncSchedulerAction.RUN)
    }

    @Test
    fun `app foreground skips when not activated`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_NOT_ACTIVATED,
            runtimeState = runtimeState().copy(cloudSyncActivated = false),
        )
    }

    @Test
    fun `app foreground skips when disabled`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_DISABLED,
            runtimeState = runtimeState().copy(cloudSyncEnabled = false),
        )
    }

    @Test
    fun `app foreground skips when offline`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_OFFLINE,
            runtimeState = runtimeState().copy(networkAvailable = false),
        )
    }

    @Test
    fun `app foreground skips when account is not authorized`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ACCOUNT_NOT_AUTHORIZED,
            runtimeState = runtimeState().copy(accountAuthorized = false),
        )
    }

    @Test
    fun `app foreground skips when sync is already running`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ALREADY_RUNNING,
            runtimeState = runtimeState().copy(alreadyRunning = true),
        )
    }

    @Test
    fun `app foreground skips while account operation is running`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ACCOUNT_OPERATION_RUNNING,
            runtimeState = runtimeState().copy(accountOperationRunning = true),
        )
    }

    @Test
    fun `foreground gate emits once per entry and waits for ready state`() {
        val gate = AppForegroundSyncRequestGate()

        assertFalse(gate.shouldRequest(1L, isForeground = true, stateReady = false))
        assertTrue(gate.shouldRequest(1L, isForeground = true, stateReady = true))
        assertFalse(gate.shouldRequest(1L, isForeground = true, stateReady = true))
        assertFalse(gate.shouldRequest(2L, isForeground = false, stateReady = true))
        assertTrue(gate.shouldRequest(2L, isForeground = true, stateReady = true))
    }

    private fun assertDecision(
        expected: CloudSyncSchedulerAction,
        runtimeState: CloudSyncSchedulerRuntimeState = runtimeState(),
    ) {
        assertEquals(
            expected,
            decideCloudSyncSchedulerAction(
                runtimeState.toDecisionInput(CloudSyncTriggerReason.APP_FOREGROUND),
            ),
        )
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
