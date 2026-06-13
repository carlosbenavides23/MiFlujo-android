package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.auth.CloudAccount
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCloudSafetyTest {
    private val account = CloudAccount("uid", null, null)

    @Test
    fun `restore is enabled when Cloud Sync was never activated`() {
        assertRestoreAvailability(
            expected = RestoreBackupAvailability.AVAILABLE,
            cloudSyncActivated = false,
        )
    }

    @Test
    fun `restore is enabled when activated Cloud Sync is disabled`() {
        assertRestoreAvailability(
            expected = RestoreBackupAvailability.AVAILABLE,
            cloudSyncEnabled = false,
        )
    }

    @Test
    fun `restore is disabled when Cloud Sync is active and authorized`() {
        assertRestoreAvailability(
            expected = RestoreBackupAvailability.BLOCKED_CLOUD_SYNC_ACTIVE,
        )
    }

    @Test
    fun `restore is enabled when activated account is signed out`() {
        assertRestoreAvailability(
            expected = RestoreBackupAvailability.AVAILABLE,
            cloudAccountStatus = CloudAccountStatus.SignedOut,
        )
    }

    @Test
    fun `restore is enabled when activated account is unauthorized`() {
        assertRestoreAvailability(
            expected = RestoreBackupAvailability.AVAILABLE,
            cloudAccountStatus = CloudAccountStatus.Unauthorized(account),
        )
    }

    @Test
    fun `restore is disabled while Cloud Sync is running`() {
        assertRestoreAvailability(
            expected = RestoreBackupAvailability.BLOCKED_OPERATION_RUNNING,
            isCloudSyncRunning = true,
        )
    }

    @Test
    fun `restore is disabled while account operation is running`() {
        assertRestoreAvailability(
            expected = RestoreBackupAvailability.BLOCKED_OPERATION_RUNNING,
            isCloudAccountOperationRunning = true,
        )
    }

    @Test
    fun `account actions are disabled while manual sync is running`() {
        assertFalse(
            canStartCloudAccountAction(
                isCloudAccountOperationInProgress = false,
                isCloudAccountJobActive = false,
                isManualCloudSyncJobActive = false,
                manualCloudSyncState = ManualCloudSyncUiState.Running,
            ),
        )
        assertFalse(
            canStartCloudAccountAction(
                isCloudAccountOperationInProgress = false,
                isCloudAccountJobActive = false,
                isManualCloudSyncJobActive = true,
                manualCloudSyncState = ManualCloudSyncUiState.Idle,
            ),
        )
    }

    @Test
    fun `account actions remain available when no operation is running`() {
        assertTrue(
            canStartCloudAccountAction(
                isCloudAccountOperationInProgress = false,
                isCloudAccountJobActive = false,
                isManualCloudSyncJobActive = false,
                manualCloudSyncState = ManualCloudSyncUiState.Idle,
            ),
        )
    }

    @Test
    fun `manual sync is disabled when cloudSyncEnabled is false`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = false,
            lastSyncTimestamp = null,
            isOffline = false,
            cloudAccountStatus = com.carlos.miflujo.data.cloud.auth.CloudAccountStatus.Loading, // Doesn't matter
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )
        assertFalse(presentation.isManualSyncEnabled)
    }

    @Test
    fun `manual sync is disabled when offline`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = null,
            isOffline = true,
            cloudAccountStatus = com.carlos.miflujo.data.cloud.auth.CloudAccountStatus.Loading,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )
        assertFalse(presentation.isManualSyncEnabled)
    }

    @Test
    fun `manual sync and account actions are disabled when manual sync is running`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = null,
            isOffline = false,
            cloudAccountStatus = com.carlos.miflujo.data.cloud.auth.CloudAccountStatus.Loading,
            manualCloudSyncState = ManualCloudSyncUiState.Running,
            isAccountOperationInProgress = false,
        )
        assertFalse(presentation.isManualSyncEnabled)
        assertFalse(presentation.isAccountActionsEnabled)
    }

    @Test
    fun `manual sync and account actions are disabled when account operation is running`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = null,
            isOffline = false,
            cloudAccountStatus = com.carlos.miflujo.data.cloud.auth.CloudAccountStatus.Loading,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = true,
        )
        assertFalse(presentation.isManualSyncEnabled)
        assertFalse(presentation.isAccountActionsEnabled)
    }

    private fun assertRestoreAvailability(
        expected: RestoreBackupAvailability,
        cloudSyncEnabled: Boolean = true,
        cloudSyncActivated: Boolean = true,
        cloudAccountStatus: CloudAccountStatus = CloudAccountStatus.Authorized(account),
        isCloudSyncRunning: Boolean = false,
        isCloudAccountOperationRunning: Boolean = false,
    ) {
        assertEquals(
            expected,
            mapRestoreBackupAvailability(
                cloudSyncEnabled = cloudSyncEnabled,
                cloudSyncActivated = cloudSyncActivated,
                cloudAccountStatus = cloudAccountStatus,
                isCloudSyncRunning = isCloudSyncRunning,
                isCloudAccountOperationRunning = isCloudAccountOperationRunning,
            ),
        )
    }
}
