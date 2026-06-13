package com.carlos.miflujo.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCloudSafetyTest {
    @Test
    fun `restore is disabled after Cloud Sync activation`() {
        assertFalse(
            isRestoreBackupEnabled(
                isBackupOperationInProgress = false,
                cloudSyncActivated = true,
            ),
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
}
