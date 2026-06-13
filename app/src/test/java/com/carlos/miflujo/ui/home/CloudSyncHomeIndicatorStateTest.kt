package com.carlos.miflujo.ui.home

import com.carlos.miflujo.data.cloud.auth.CloudAccount
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import com.carlos.miflujo.ui.settings.ManualCloudSyncUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncHomeIndicatorStateTest {
    private val authorizedStatus = CloudAccountStatus.Authorized(
        CloudAccount("uid", null, null),
    )

    @Test
    fun `not activated is hidden`() {
        assertIndicatorState(
            cloudSyncActivated = false,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            expected = CloudSyncHomeIndicatorState.Hidden,
        )
    }

    @Test
    fun `activated and disabled is disabled`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = false,
            isOffline = true,
            manualCloudSyncState = ManualCloudSyncUiState.Unauthorized,
            expected = visible(CloudSyncHomeIndicatorStatus.DISABLED),
        )
    }

    @Test
    fun `activated enabled and offline is no internet`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = true,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            expected = visible(CloudSyncHomeIndicatorStatus.NO_INTERNET),
        )
    }

    @Test
    fun `offline manual unauthorized and account authorized is no internet`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = true,
            manualCloudSyncState = ManualCloudSyncUiState.Unauthorized,
            expected = visible(CloudSyncHomeIndicatorStatus.NO_INTERNET),
        )
    }

    @Test
    fun `online manual unauthorized is account issue`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.Unauthorized,
            expected = visible(CloudSyncHomeIndicatorStatus.ACCOUNT_ISSUE),
        )
    }

    @Test
    fun `offline manual signed out is no internet`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = true,
            manualCloudSyncState = ManualCloudSyncUiState.SignedOut,
            expected = visible(CloudSyncHomeIndicatorStatus.NO_INTERNET),
        )
    }

    @Test
    fun `online manual signed out is account issue`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.SignedOut,
            expected = visible(CloudSyncHomeIndicatorStatus.ACCOUNT_ISSUE),
        )
    }

    @Test
    fun `offline manual failure is no internet`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = true,
            manualCloudSyncState = ManualCloudSyncUiState.Failure,
            expected = visible(CloudSyncHomeIndicatorStatus.NO_INTERNET),
        )
    }

    @Test
    fun `online manual failure is sync warning`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.Failure,
            expected = visible(CloudSyncHomeIndicatorStatus.SYNC_WARNING),
        )
    }

    @Test
    fun `online running is syncing`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.Running,
            expected = visible(CloudSyncHomeIndicatorStatus.SYNCING),
        )
    }

    @Test
    fun `online normal activated enabled and authorized is active`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            expected = visible(CloudSyncHomeIndicatorStatus.ACTIVE),
        )
    }

    @Test
    fun `online unauthorized account is account issue`() {
        val unauthorizedStatus = CloudAccountStatus.Unauthorized(
            CloudAccount("uid", null, null),
        )

        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            cloudAccountStatus = unauthorizedStatus,
            expected = visible(CloudSyncHomeIndicatorStatus.ACCOUNT_ISSUE),
        )
    }

    @Test
    fun `online signed out account is account issue`() {
        assertIndicatorState(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            isOffline = false,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            cloudAccountStatus = CloudAccountStatus.SignedOut,
            expected = visible(CloudSyncHomeIndicatorStatus.ACCOUNT_ISSUE),
        )
    }

    private fun assertIndicatorState(
        cloudSyncActivated: Boolean,
        cloudSyncEnabled: Boolean,
        isOffline: Boolean,
        manualCloudSyncState: ManualCloudSyncUiState,
        expected: CloudSyncHomeIndicatorState,
        cloudAccountStatus: CloudAccountStatus = authorizedStatus,
    ) {
        assertEquals(
            expected,
            mapToCloudSyncHomeIndicatorState(
                cloudSyncActivated = cloudSyncActivated,
                cloudSyncEnabled = cloudSyncEnabled,
                cloudAccountStatus = cloudAccountStatus,
                manualCloudSyncState = manualCloudSyncState,
                isOffline = isOffline,
            ),
        )
    }

    private fun visible(
        status: CloudSyncHomeIndicatorStatus,
    ): CloudSyncHomeIndicatorState = CloudSyncHomeIndicatorState.Visible(status)
}
