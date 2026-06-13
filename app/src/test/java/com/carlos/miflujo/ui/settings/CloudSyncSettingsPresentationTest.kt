package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.auth.CloudAccount
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncSettingsPresentationTest {
    private val account = CloudAccount("uid", null, null)
    private val authorizedStatus = CloudAccountStatus.Authorized(account)
    private val unauthorizedStatus = CloudAccountStatus.Unauthorized(account)

    @Test
    fun `authorized online enabled before first sync keeps sync actions available`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = false,
            cloudSyncEnabled = true,
            lastSyncTimestamp = null,
            isOffline = false,
            cloudAccountStatus = authorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.NOT_ACTIVATED, presentation.status)
        assertTrue(presentation.showCloudSyncEnabledToggle)
        assertFalse(presentation.showLastSyncTimestamp)
        assertTrue(presentation.showSyncNowButton)
        assertTrue(presentation.isManualSyncEnabled)
        assertTrue(presentation.showSignOutButton)
        assertFalse(presentation.showSignInButton)
    }

    @Test
    fun `authorized online disabled before first sync keeps account controls visible`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = false,
            cloudSyncEnabled = false,
            lastSyncTimestamp = null,
            isOffline = false,
            cloudAccountStatus = authorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.DISABLED, presentation.status)
        assertTrue(presentation.showCloudSyncEnabledToggle)
        assertTrue(presentation.showSyncNowButton)
        assertFalse(presentation.isManualSyncEnabled)
        assertTrue(presentation.showSignOutButton)
        assertFalse(presentation.showSignInButton)
    }

    @Test
    fun `authorized online enabled after sync is active`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = 1_000L,
            isOffline = false,
            cloudAccountStatus = authorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.ACTIVE, presentation.status)
        assertTrue(presentation.showSyncNowButton)
        assertTrue(presentation.isManualSyncEnabled)
    }

    @Test
    fun `signed out online shows sign in without sync action`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = false,
            cloudSyncEnabled = true,
            lastSyncTimestamp = null,
            isOffline = false,
            cloudAccountStatus = CloudAccountStatus.SignedOut,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.SIGNED_OUT, presentation.status)
        assertTrue(presentation.showSignInButton)
        assertFalse(presentation.showSyncNowButton)
        assertFalse(presentation.isManualSyncEnabled)
    }

    @Test
    fun `authorized offline shows no internet without account review actions`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = false,
            cloudSyncEnabled = true,
            lastSyncTimestamp = null,
            isOffline = true,
            cloudAccountStatus = authorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.NO_INTERNET, presentation.status)
        assertTrue(presentation.showCloudSyncEnabledToggle)
        assertTrue(presentation.showSyncNowButton)
        assertFalse(presentation.isManualSyncEnabled)
        assertTrue(presentation.showSignOutButton)
        assertFalse(presentation.showSignInButton)
        assertFalse(presentation.showUnauthorizedActions)
    }

    @Test
    fun `offline status overrides account unauthorized`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = 1_000L,
            isOffline = true,
            cloudAccountStatus = unauthorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.NO_INTERNET, presentation.status)
        assertFalse(presentation.showUnauthorizedActions)
    }

    @Test
    fun `offline status overrides account signed out`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = 1_000L,
            isOffline = true,
            cloudAccountStatus = CloudAccountStatus.SignedOut,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.NO_INTERNET, presentation.status)
        assertFalse(presentation.showSignInButton)
    }

    @Test
    fun `disabled status takes priority over offline for authorized account`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = false,
            lastSyncTimestamp = 1_000L,
            isOffline = true,
            cloudAccountStatus = authorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.DISABLED, presentation.status)
        assertTrue(presentation.showCloudAccountIdentity)
        assertTrue(presentation.showCloudSyncEnabledToggle)
        assertTrue(presentation.showSyncNowButton)
        assertFalse(presentation.isManualSyncEnabled)
        assertTrue(presentation.showSignOutButton)
    }

    @Test
    fun `online unauthorized account shows account review actions`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = 1_000L,
            isOffline = false,
            cloudAccountStatus = unauthorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Idle,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.UNAUTHORIZED, presentation.status)
        assertTrue(presentation.showUnauthorizedActions)
        assertFalse(presentation.showSyncNowButton)
        assertFalse(presentation.isManualSyncEnabled)
    }

    @Test
    fun `online manual failure shows sync review`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = false,
            cloudSyncEnabled = true,
            lastSyncTimestamp = null,
            isOffline = false,
            cloudAccountStatus = authorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Failure,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.SYNC_WARNING, presentation.status)
        assertTrue(presentation.showSyncNowButton)
        assertTrue(presentation.isManualSyncEnabled)
    }

    @Test
    fun `authorized account with stale manual unauthorized shows sync warning`() {
        val presentation = mapToCloudSyncSettingsPresentation(
            cloudSyncActivated = true,
            cloudSyncEnabled = true,
            lastSyncTimestamp = 1_000L,
            isOffline = false,
            cloudAccountStatus = authorizedStatus,
            manualCloudSyncState = ManualCloudSyncUiState.Unauthorized,
            isAccountOperationInProgress = false,
        )

        assertEquals(CloudSyncSettingsStatus.SYNC_WARNING, presentation.status)
        assertTrue(presentation.isManualSyncEnabled)
        assertFalse(presentation.showUnauthorizedActions)
    }
}
