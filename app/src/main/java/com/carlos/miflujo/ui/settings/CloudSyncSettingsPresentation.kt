package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.auth.CloudAccount
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus

enum class CloudSyncSettingsStatus {
    NOT_ACTIVATED,
    DISABLED,
    NO_INTERNET,
    LOADING,
    SIGNED_OUT,
    UNAUTHORIZED,
    SYNC_WARNING,
    ACTIVE
}

data class CloudSyncSettingsPresentation(
    val status: CloudSyncSettingsStatus,
    val title: String,
    val description: String,
    val isManualSyncEnabled: Boolean,
    val isAccountActionsEnabled: Boolean,
    val showCloudAccountIdentity: Boolean,
    val showSignInButton: Boolean,
    val showCloudSyncEnabledToggle: Boolean,
    val showLastSyncTimestamp: Boolean,
    val showSyncNowButton: Boolean,
    val showSignOutButton: Boolean,
    val showUnauthorizedActions: Boolean,
    val account: CloudAccount?,
)

fun mapToCloudSyncSettingsPresentation(
    cloudSyncActivated: Boolean,
    cloudSyncEnabled: Boolean,
    lastSyncTimestamp: Long?,
    isOffline: Boolean,
    cloudAccountStatus: CloudAccountStatus,
    manualCloudSyncState: ManualCloudSyncUiState,
    isAccountOperationInProgress: Boolean,
): CloudSyncSettingsPresentation {
    val isAccountRunning = isAccountOperationInProgress
    val isSyncRunning = manualCloudSyncState is ManualCloudSyncUiState.Running
    val accountActionsEnabled = !isAccountRunning && !isSyncRunning

    val account = when (cloudAccountStatus) {
        is CloudAccountStatus.Authorized -> cloudAccountStatus.account
        is CloudAccountStatus.Unauthorized -> cloudAccountStatus.account
        else -> null
    }
    val isUnauthorized = cloudAccountStatus is CloudAccountStatus.Unauthorized
    val isSignedOut = cloudAccountStatus is CloudAccountStatus.SignedOut

    // 1. Loading
    if (isAccountRunning || isSyncRunning) {
        return CloudSyncSettingsPresentation(
            status = CloudSyncSettingsStatus.LOADING,
            title = if (isAccountRunning) "Comprobando cuenta..." else "Sincronizando...",
            description = "Espera un momento.",
            isManualSyncEnabled = false,
            isAccountActionsEnabled = false,
            showCloudAccountIdentity = account != null,
            showSignInButton = false,
            showCloudSyncEnabledToggle = account != null && !isUnauthorized,
            showLastSyncTimestamp = account != null && !isUnauthorized && cloudSyncActivated,
            showSyncNowButton = account != null && !isUnauthorized,
            showSignOutButton = account != null,
            showUnauthorizedActions = isUnauthorized,
            account = account,
        )
    }

    // 2. Disabled
    if (!cloudSyncEnabled && account != null && !isUnauthorized) {
        return CloudSyncSettingsPresentation(
            status = CloudSyncSettingsStatus.DISABLED,
            title = "Desactivado",
            description = "Cloud Sync está desactivado. MiFlujo funciona en modo local.",
            isManualSyncEnabled = false,
            isAccountActionsEnabled = accountActionsEnabled,
            showCloudAccountIdentity = true,
            showSignInButton = false,
            showCloudSyncEnabledToggle = true,
            showLastSyncTimestamp = cloudSyncActivated,
            showSyncNowButton = true,
            showSignOutButton = true,
            showUnauthorizedActions = false,
            account = account,
        )
    }

    // 3. Offline
    if (isOffline) {
        return CloudSyncSettingsPresentation(
            status = CloudSyncSettingsStatus.NO_INTERNET,
            title = "Sin internet",
            description = "MiFlujo sigue funcionando localmente. Podrás sincronizar cuando vuelva la conexión.",
            isManualSyncEnabled = false,
            isAccountActionsEnabled = accountActionsEnabled,
            showCloudAccountIdentity = account != null,
            showSignInButton = false,
            showCloudSyncEnabledToggle = account != null && !isUnauthorized,
            showLastSyncTimestamp = account != null && !isUnauthorized && cloudSyncActivated,
            showSyncNowButton = account != null && !isUnauthorized,
            showSignOutButton = account != null,
            showUnauthorizedActions = false,
            account = account,
        )
    }

    // 4. Signed Out
    if (isSignedOut || account == null) {
        return CloudSyncSettingsPresentation(
            status = CloudSyncSettingsStatus.SIGNED_OUT,
            title = "Revisar cuenta",
            description = "Inicia sesión para sincronizar.",
            isManualSyncEnabled = false,
            isAccountActionsEnabled = accountActionsEnabled,
            showCloudAccountIdentity = false,
            showSignInButton = true,
            showCloudSyncEnabledToggle = false,
            showLastSyncTimestamp = false,
            showSyncNowButton = false,
            showSignOutButton = false,
            showUnauthorizedActions = false,
            account = null,
        )
    }

    // 5. Unauthorized
    if (isUnauthorized) {
        return CloudSyncSettingsPresentation(
            status = CloudSyncSettingsStatus.UNAUTHORIZED,
            title = "Revisar cuenta",
            description = "Tu cuenta no está autorizada para Cloud Sync. MiFlujo continúa funcionando solo con los datos locales.",
            isManualSyncEnabled = false,
            isAccountActionsEnabled = accountActionsEnabled,
            showCloudAccountIdentity = true,
            showSignInButton = false,
            showCloudSyncEnabledToggle = false,
            showLastSyncTimestamp = false,
            showSyncNowButton = false,
            showSignOutButton = true,
            showUnauthorizedActions = true,
            account = account,
        )
    }

    // 6. Sync Warning (Stale manual failure while online and authorized)
    if (
        manualCloudSyncState is ManualCloudSyncUiState.Failure ||
        manualCloudSyncState is ManualCloudSyncUiState.Unauthorized ||
        manualCloudSyncState is ManualCloudSyncUiState.SignedOut
    ) {
        return CloudSyncSettingsPresentation(
            status = CloudSyncSettingsStatus.SYNC_WARNING,
            title = "Revisar sync",
            description = "No se pudo sincronizar. Intenta de nuevo.",
            isManualSyncEnabled = true,
            isAccountActionsEnabled = accountActionsEnabled,
            showCloudAccountIdentity = true,
            showSignInButton = false,
            showCloudSyncEnabledToggle = true,
            showLastSyncTimestamp = cloudSyncActivated,
            showSyncNowButton = true,
            showSignOutButton = true,
            showUnauthorizedActions = false,
            account = account,
        )
    }

    // 7. Not Activated (Authorized, Online, Enabled, but never synced successfully)
    if (!cloudSyncActivated) {
        return CloudSyncSettingsPresentation(
            status = CloudSyncSettingsStatus.NOT_ACTIVATED,
            title = "Aún no se ha sincronizado este dispositivo",
            description = "Puedes sincronizar manualmente cuando estés listo.",
            isManualSyncEnabled = true,
            isAccountActionsEnabled = accountActionsEnabled,
            showCloudAccountIdentity = true,
            showSignInButton = false,
            showCloudSyncEnabledToggle = true,
            showLastSyncTimestamp = false,
            showSyncNowButton = true,
            showSignOutButton = true,
            showUnauthorizedActions = false,
            account = account,
        )
    }

    return CloudSyncSettingsPresentation(
        status = CloudSyncSettingsStatus.ACTIVE,
        title = "Activo",
        description = "MiFlujo mantiene tus datos sincronizados cuando hay conexión. También puedes sincronizar manualmente.",
        isManualSyncEnabled = true,
        isAccountActionsEnabled = accountActionsEnabled,
        showCloudAccountIdentity = true,
        showSignInButton = false,
        showCloudSyncEnabledToggle = true,
        showLastSyncTimestamp = true,
        showSyncNowButton = true,
        showSignOutButton = true,
        showUnauthorizedActions = false,
        account = account,
    )
}
