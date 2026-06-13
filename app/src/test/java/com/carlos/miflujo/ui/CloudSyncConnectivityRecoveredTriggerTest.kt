package com.carlos.miflujo.ui

import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerAction
import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerRuntimeState
import com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason
import com.carlos.miflujo.data.cloud.sync.decideCloudSyncSchedulerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncConnectivityRecoveredTriggerTest {
    @Test
    fun `does not emit when app starts already online`() {
        val gate = ConnectivityRecoveredSyncRequestGate()

        assertFalse(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = true,
                stateReady = true,
            ),
        )
    }

    @Test
    fun `emits once when network recovers in foreground and state is ready`() {
        val gate = offlineForegroundGate()

        assertTrue(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = true,
                stateReady = true,
            ),
        )
    }

    @Test
    fun `does not emit repeatedly for repeated online emissions`() {
        val gate = offlineForegroundGate()

        assertTrue(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = true,
                stateReady = true,
            ),
        )
        assertFalse(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = true,
                stateReady = true,
            ),
        )
    }

    @Test
    fun `does not emit while app is background`() {
        val gate = ConnectivityRecoveredSyncRequestGate()
        gate.shouldRequest(networkAvailable = false, isForeground = false, stateReady = true)

        assertFalse(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = false,
                stateReady = true,
            ),
        )
    }

    @Test
    fun `defers recovered event while state is not ready`() {
        val gate = offlineForegroundGate()

        assertFalse(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = true,
                stateReady = false,
            ),
        )
    }

    @Test
    fun `emits once when state becomes ready after recovery`() {
        val gate = offlineForegroundGate()
        gate.shouldRequest(networkAvailable = true, isForeground = true, stateReady = false)

        assertTrue(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = true,
                stateReady = true,
            ),
        )
        assertFalse(
            gate.shouldRequest(
                networkAvailable = true,
                isForeground = true,
                stateReady = true,
            ),
        )
    }

    @Test
    fun `does not emit for online to offline transition`() {
        val gate = ConnectivityRecoveredSyncRequestGate()
        gate.shouldRequest(networkAvailable = true, isForeground = true, stateReady = true)

        assertFalse(
            gate.shouldRequest(
                networkAvailable = false,
                isForeground = true,
                stateReady = true,
            ),
        )
    }

    @Test
    fun `does not emit for repeated offline emissions`() {
        val gate = ConnectivityRecoveredSyncRequestGate()

        assertFalse(
            gate.shouldRequest(
                networkAvailable = false,
                isForeground = true,
                stateReady = true,
            ),
        )
        assertFalse(
            gate.shouldRequest(
                networkAvailable = false,
                isForeground = true,
                stateReady = true,
            ),
        )
    }

    @Test
    fun `eligible recovered connectivity runs without pending changes`() {
        assertDecision(CloudSyncSchedulerAction.RUN)
    }

    @Test
    fun `recovered connectivity skips when not activated`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_NOT_ACTIVATED,
            runtimeState = runtimeState().copy(cloudSyncActivated = false),
        )
    }

    @Test
    fun `recovered connectivity skips when disabled`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_DISABLED,
            runtimeState = runtimeState().copy(cloudSyncEnabled = false),
        )
    }

    @Test
    fun `recovered connectivity skips when account is not authorized`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ACCOUNT_NOT_AUTHORIZED,
            runtimeState = runtimeState().copy(accountAuthorized = false),
        )
    }

    @Test
    fun `recovered connectivity skips when sync is already running`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ALREADY_RUNNING,
            runtimeState = runtimeState().copy(alreadyRunning = true),
        )
    }

    @Test
    fun `recovered connectivity skips while account operation is running`() {
        assertDecision(
            expected = CloudSyncSchedulerAction.SKIP_ACCOUNT_OPERATION_RUNNING,
            runtimeState = runtimeState().copy(accountOperationRunning = true),
        )
    }

    private fun offlineForegroundGate(): ConnectivityRecoveredSyncRequestGate =
        ConnectivityRecoveredSyncRequestGate().also { gate ->
            gate.shouldRequest(
                networkAvailable = false,
                isForeground = true,
                stateReady = true,
            )
        }

    private fun assertDecision(
        expected: CloudSyncSchedulerAction,
        runtimeState: CloudSyncSchedulerRuntimeState = runtimeState(),
    ) {
        assertEquals(
            expected,
            decideCloudSyncSchedulerAction(
                runtimeState.toDecisionInput(CloudSyncTriggerReason.CONNECTIVITY_RECOVERED),
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
