package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.auth.CloudAccount
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualCloudSyncResultMessageTest {
    private val account = CloudAccount("uid", null, null)
    private val authorizedStatus = CloudAccountStatus.Authorized(account)
    private val unauthorizedStatus = CloudAccountStatus.Unauthorized(account)

    @Test
    fun `offline account and sync failures use calm local mode message`() {
        val expected = "Sin internet. MiFlujo sigue funcionando localmente."

        listOf(
            ManualCloudSyncUiState.Unauthorized,
            ManualCloudSyncUiState.SignedOut,
            ManualCloudSyncUiState.Failure,
        ).forEach { state ->
            assertEquals(
                expected,
                manualCloudSyncResultMessage(
                    state = state,
                    cloudAccountStatus = authorizedStatus,
                    isOffline = true,
                ),
            )
        }
    }

    @Test
    fun `online unauthorized result keeps account message when account is unauthorized`() {
        assertEquals(
            "Tu cuenta no está autorizada para Cloud Sync.",
            manualCloudSyncResultMessage(
                state = ManualCloudSyncUiState.Unauthorized,
                cloudAccountStatus = unauthorizedStatus,
                isOffline = false,
            ),
        )
    }

    @Test
    fun `online unauthorized result uses generic failure when account is authorized`() {
        assertEquals(
            "No se pudo sincronizar. Intenta de nuevo.",
            manualCloudSyncResultMessage(
                state = ManualCloudSyncUiState.Unauthorized,
                cloudAccountStatus = authorizedStatus,
                isOffline = false,
            ),
        )
    }

    @Test
    fun `online signed out result keeps sign in message`() {
        assertEquals(
            "Inicia sesión para sincronizar.",
            manualCloudSyncResultMessage(
                state = ManualCloudSyncUiState.SignedOut,
                cloudAccountStatus = CloudAccountStatus.SignedOut,
                isOffline = false,
            ),
        )
    }

    @Test
    fun `online failure result keeps generic sync failure message`() {
        assertEquals(
            "No se pudo sincronizar. Intenta de nuevo.",
            manualCloudSyncResultMessage(
                state = ManualCloudSyncUiState.Failure,
                cloudAccountStatus = authorizedStatus,
                isOffline = false,
            ),
        )
    }
}
