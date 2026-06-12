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
            areCloudAccountActionsEnabled(
                isAccountOperationInProgress = false,
                manualSyncState = ManualCloudSyncUiState.Running,
            ),
        )
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
            areCloudAccountActionsEnabled(
                isAccountOperationInProgress = false,
                manualSyncState = ManualCloudSyncUiState.Idle,
            ),
        )
        assertTrue(
            canStartCloudAccountAction(
                isCloudAccountOperationInProgress = false,
                isCloudAccountJobActive = false,
                isManualCloudSyncJobActive = false,
                manualCloudSyncState = ManualCloudSyncUiState.Idle,
            ),
        )
    }
}
