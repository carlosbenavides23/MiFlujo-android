package com.carlos.miflujo.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carlos.miflujo.data.cloud.auth.CloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import com.carlos.miflujo.data.cloud.auth.LegacyGoogleSignInResult
import com.carlos.miflujo.data.cloud.auth.MiFlujoAuthLogTag
import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunCoordinator
import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerCoordinator
import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerRuntimeState
import com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason
import com.carlos.miflujo.data.cloud.sync.logMiFlujoSyncDebug
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.ui.backup.BackupDocument
import com.carlos.miflujo.ui.backup.BackupExporter
import com.carlos.miflujo.ui.backup.BackupImporter
import com.carlos.miflujo.ui.backup.InvalidBackupException
import com.carlos.miflujo.domain.model.Movement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isExportingBackup: Boolean = false,
    val isRestoringBackup: Boolean = false,
    val pendingRestoreMovementCount: Int? = null,
    val cloudAccountStatus: CloudAccountStatus = CloudAccountStatus.Loading,
    val isCloudAccountOperationInProgress: Boolean = false,
) {
    val isBackupOperationInProgress: Boolean
        get() = isExportingBackup || isRestoringBackup || pendingRestoreMovementCount != null
}

class SettingsViewModel(
    private val movementRepository: MovementRepository,
    private val cloudAccountRepository: CloudAccountRepository,
    cloudSyncRunCoordinator: CloudSyncRunCoordinator,
    cloudSyncEnabledStore: CloudSyncEnabledStore,
) : ViewModel() {
    private val manualCloudSyncStateHolder = ManualCloudSyncStateHolder(
        cloudSyncRunCoordinator = cloudSyncRunCoordinator,
        cloudSyncEnabledStore = cloudSyncEnabledStore,
    )
    private val cloudSyncSchedulerCoordinator = CloudSyncSchedulerCoordinator(
        executor = ManualCloudSyncScheduledRunExecutor(manualCloudSyncStateHolder),
    )
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    private val exportFeedback = MutableSharedFlow<BackupExportFeedback>(extraBufferCapacity = 1)
    private val createDocumentRequests = MutableSharedFlow<CreateBackupDocumentRequest>(
        extraBufferCapacity = 1,
    )
    private val restoreFeedback = MutableSharedFlow<BackupRestoreFeedback>(extraBufferCapacity = 1)
    private val openBackupDocumentRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val legacyGoogleSignInRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val legacyGoogleSignOutRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val cloudAccountFeedback = MutableSharedFlow<CloudAccountFeedback>(
        extraBufferCapacity = 1,
    )
    private var exportJob: Job? = null
    private var restoreJob: Job? = null
    private var cloudAccountJob: Job? = null
    private var manualCloudSyncJob: Job? = null
    private var isLegacyGoogleSignInPending = false
    private var isLegacyGoogleSignOutPending = false
    private var pendingBackupDocument: BackupDocument? = null
    private var pendingRestoreMovements: List<Movement>? = null

    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    val exportFeedbackEvents: SharedFlow<BackupExportFeedback> = exportFeedback.asSharedFlow()
    val createDocumentRequestEvents: SharedFlow<CreateBackupDocumentRequest> =
        createDocumentRequests.asSharedFlow()
    val restoreFeedbackEvents: SharedFlow<BackupRestoreFeedback> = restoreFeedback.asSharedFlow()
    val openBackupDocumentRequestEvents: SharedFlow<Unit> = openBackupDocumentRequests.asSharedFlow()
    val legacyGoogleSignInRequestEvents: SharedFlow<Unit> =
        legacyGoogleSignInRequests.asSharedFlow()
    val legacyGoogleSignOutRequestEvents: SharedFlow<Unit> =
        legacyGoogleSignOutRequests.asSharedFlow()
    val cloudAccountFeedbackEvents: SharedFlow<CloudAccountFeedback> =
        cloudAccountFeedback.asSharedFlow()
    val manualCloudSyncState: StateFlow<ManualCloudSyncUiState> =
        manualCloudSyncStateHolder.state
    val cloudSyncActivated: StateFlow<Boolean> =
        manualCloudSyncStateHolder.cloudSyncActivated
    val cloudSyncEnabled: StateFlow<Boolean> =
        manualCloudSyncStateHolder.cloudSyncEnabled
    val lastSyncTimestamp: StateFlow<Long?> =
        manualCloudSyncStateHolder.lastSyncTimestamp
    val isCloudSyncRunning: Boolean
        get() = manualCloudSyncStateHolder.isSyncRunning ||
            manualCloudSyncJob?.isActive == true
    val isCloudAccountOperationRunning: Boolean
        get() = mutableUiState.value.isCloudAccountOperationInProgress ||
            cloudAccountJob?.isActive == true

    init {
        refreshCloudAccountStatus()
    }

    fun signInWithGoogle() {
        Log.d(MiFlujoAuthLogTag, "Settings sign-in button action starts.")
        if (
            !canStartCloudAccountAction(
                isCloudAccountOperationInProgress =
                    mutableUiState.value.isCloudAccountOperationInProgress,
                isCloudAccountJobActive = cloudAccountJob?.isActive == true,
                isManualCloudSyncJobActive = manualCloudSyncJob?.isActive == true,
                manualCloudSyncState = manualCloudSyncState.value,
            )
        ) {
            Log.d(MiFlujoAuthLogTag, "Settings sign-in ignored: account operation already running.")
            return
        }

        mutableUiState.value = mutableUiState.value.copy(isCloudAccountOperationInProgress = true)
        Log.d(MiFlujoAuthLogTag, "SettingsViewModel state updated: sign-in running.")
        if (!requestLegacyGoogleSignIn()) {
            finishCloudAccountOperation()
        }
    }

    fun completeLegacyGoogleSignIn(result: LegacyGoogleSignInResult) {
        if (!isLegacyGoogleSignInPending) {
            Log.d(MiFlujoAuthLogTag, "Ignoring legacy fallback result without a pending request.")
            return
        }
        isLegacyGoogleSignInPending = false

        if (result !is LegacyGoogleSignInResult.Success) {
            cloudAccountFeedback.tryEmit(fallbackFeedbackForResult(result))
            finishCloudAccountOperation()
            return
        }

        cloudAccountJob = viewModelScope.launch {
            try {
                val signInStatus = cloudAccountRepository.signInWithGoogleIdToken(result.idToken)
                completeSuccessfulSignIn(signInStatus)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(
                    MiFlujoAuthLogTag,
                    "GoogleSignInClient fallback FirebaseAuth flow failed: " +
                        "class=${exception.javaClass.name}, message=Sign-in could not be completed.",
                )
                cloudAccountFeedback.tryEmit(exception.toCloudAccountFeedback())
            } finally {
                finishCloudAccountOperation()
                cloudAccountJob = null
            }
        }
    }

    fun handleLegacyGoogleSignInLaunchFailure() {
        if (!isLegacyGoogleSignInPending) return
        isLegacyGoogleSignInPending = false
        Log.e(MiFlujoAuthLogTag, "GoogleSignInClient fallback could not be launched.")
        cloudAccountFeedback.tryEmit(CloudAccountFeedback.SignInIncomplete)
        finishCloudAccountOperation()
    }

    fun signOut() {
        if (
            !canStartCloudAccountAction(
                isCloudAccountOperationInProgress =
                    mutableUiState.value.isCloudAccountOperationInProgress,
                isCloudAccountJobActive = cloudAccountJob?.isActive == true,
                isManualCloudSyncJobActive = manualCloudSyncJob?.isActive == true,
                manualCloudSyncState = manualCloudSyncState.value,
            )
        ) {
            Log.d(
                MiFlujoAuthLogTag,
                "Cloud account sign-out ignored: account operation or manual sync is running.",
            )
            return
        }

        mutableUiState.value = mutableUiState.value.copy(isCloudAccountOperationInProgress = true)
        isLegacyGoogleSignOutPending = true
        if (!legacyGoogleSignOutRequests.tryEmit(Unit)) {
            isLegacyGoogleSignOutPending = false
            Log.e(MiFlujoAuthLogTag, "Legacy GoogleSignInClient sign-out request was not delivered.")
            cloudAccountFeedback.tryEmit(CloudAccountFeedback.SignOutFailed)
            finishCloudAccountOperation()
        }
    }

    fun completeLegacyGoogleSignOut(context: Context) {
        if (!isLegacyGoogleSignOutPending) {
            Log.d(MiFlujoAuthLogTag, "Ignoring legacy sign-out completion without a pending request.")
            return
        }
        isLegacyGoogleSignOutPending = false

        cloudAccountJob = viewModelScope.launch {
            try {
                cloudAccountRepository.signOut(context)
                mutableUiState.value = mutableUiState.value.copy(
                    cloudAccountStatus = CloudAccountStatus.SignedOut,
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(MiFlujoAuthLogTag, "Cloud account sign-out failed.")
                cloudAccountFeedback.tryEmit(CloudAccountFeedback.SignOutFailed)
            } finally {
                finishCloudAccountOperation()
                cloudAccountJob = null
            }
        }
    }

    fun prepareBackupForSave() {
        if (!canStartBackupOperation()) return

        mutableUiState.value = mutableUiState.value.copy(isExportingBackup = true)
        exportJob = viewModelScope.launch {
            try {
                val backupDocument = BackupExporter.createBackupDocument(
                    movements = movementRepository.getAllMovements(),
                )
                pendingBackupDocument = backupDocument
                exportJob = null
                createDocumentRequests.emit(
                    CreateBackupDocumentRequest(fileName = backupDocument.fileName),
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(BackupExportLogTag, "JSON backup export failed", exception)
                exportFeedback.tryEmit(BackupExportFeedback.ExportFailed)
                finishExport()
            }
        }
    }

    fun savePreparedBackup(
        context: Context,
        destinationUri: Uri,
    ) {
        val backupDocument = pendingBackupDocument ?: return

        exportJob = viewModelScope.launch {
            try {
                BackupExporter.saveBackup(
                    context = context,
                    destinationUri = destinationUri,
                    backupDocument = backupDocument,
                )
                exportFeedback.emit(BackupExportFeedback.ExportSaved)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(BackupExportLogTag, "JSON backup save failed", exception)
                exportFeedback.tryEmit(BackupExportFeedback.ExportFailed)
            } finally {
                finishExport()
            }
        }
    }

    fun cancelPreparedBackup() {
        finishExport()
    }

    fun handleDocumentCreatorFailure(exception: Exception) {
        Log.e(BackupExportLogTag, "Document creator launch failed", exception)
        exportFeedback.tryEmit(BackupExportFeedback.ExportFailed)
        finishExport()
    }

    fun shareBackup(context: Context) {
        if (!canStartBackupOperation()) return

        mutableUiState.value = mutableUiState.value.copy(isExportingBackup = true)
        exportJob = viewModelScope.launch {
            try {
                val backupDocument = BackupExporter.createBackupDocument(
                    movements = movementRepository.getAllMovements(),
                )
                BackupExporter.shareBackup(
                    context = context,
                    backupDocument = backupDocument,
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(BackupExportLogTag, "JSON backup share failed", exception)
                exportFeedback.tryEmit(BackupExportFeedback.ExportFailed)
            } finally {
                finishExport()
            }
        }
    }

    fun requestBackupRestore() {
        if (!canStartRestoreOperation()) return

        mutableUiState.value = mutableUiState.value.copy(isRestoringBackup = true)
        if (!openBackupDocumentRequests.tryEmit(Unit)) {
            restoreFeedback.tryEmit(BackupRestoreFeedback.RestoreFailed)
            finishRestore()
        }
    }

    fun readSelectedBackup(
        context: Context,
        sourceUri: Uri,
    ) {
        if (
            cloudSyncActivated.value ||
            !mutableUiState.value.isRestoringBackup ||
            restoreJob?.isActive == true
        ) {
            if (cloudSyncActivated.value) finishRestore()
            return
        }

        restoreJob = viewModelScope.launch {
            try {
                val backup = BackupImporter.readBackup(
                    contentResolver = context.contentResolver,
                    sourceUri = sourceUri,
                )
                pendingRestoreMovements = backup.movements
                mutableUiState.value = mutableUiState.value.copy(
                    isRestoringBackup = false,
                    pendingRestoreMovementCount = backup.movements.size,
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(BackupRestoreLogTag, "JSON backup read failed", exception)
                restoreFeedback.tryEmit(
                    if (exception is InvalidBackupException) {
                        BackupRestoreFeedback.InvalidBackup
                    } else {
                        BackupRestoreFeedback.RestoreFailed
                    },
                )
                finishRestore()
            } finally {
                restoreJob = null
            }
        }
    }

    fun cancelBackupSelection() {
        finishRestore()
    }

    fun handleDocumentPickerFailure(exception: Exception) {
        Log.e(BackupRestoreLogTag, "Document picker launch failed", exception)
        restoreFeedback.tryEmit(BackupRestoreFeedback.RestoreFailed)
        finishRestore()
    }

    fun cancelPendingRestore() {
        finishRestore()
    }

    fun confirmPendingRestore() {
        val movements = pendingRestoreMovements ?: return
        if (
            cloudSyncActivated.value ||
            restoreJob?.isActive == true ||
            mutableUiState.value.isRestoringBackup
        ) {
            if (cloudSyncActivated.value) finishRestore()
            return
        }

        mutableUiState.value = mutableUiState.value.copy(
            isRestoringBackup = true,
            pendingRestoreMovementCount = null,
        )
        restoreJob = viewModelScope.launch {
            try {
                movementRepository.replaceAllMovements(movements)
                restoreFeedback.emit(BackupRestoreFeedback.RestoreSucceeded)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(BackupRestoreLogTag, "JSON backup restore failed", exception)
                restoreFeedback.tryEmit(BackupRestoreFeedback.RestoreFailed)
            } finally {
                finishRestore()
            }
        }
    }

    private fun canStartBackupOperation(): Boolean =
        exportJob?.isActive != true &&
            restoreJob?.isActive != true &&
            !mutableUiState.value.isBackupOperationInProgress

    private fun canStartRestoreOperation(): Boolean =
        !cloudSyncActivated.value && canStartBackupOperation()

    fun refreshCloudAccountStatus() {
        if (
            !canStartCloudAccountAction(
                isCloudAccountOperationInProgress =
                    mutableUiState.value.isCloudAccountOperationInProgress,
                isCloudAccountJobActive = cloudAccountJob?.isActive == true,
                isManualCloudSyncJobActive = manualCloudSyncJob?.isActive == true,
                manualCloudSyncState = manualCloudSyncState.value,
            )
        ) {
            Log.d(MiFlujoAuthLogTag, "Cloud account refresh skipped: operation already running.")
            return
        }

        mutableUiState.value = mutableUiState.value.copy(isCloudAccountOperationInProgress = true)
        Log.d(MiFlujoAuthLogTag, "Before refreshing cloud account status.")
        cloudAccountJob = viewModelScope.launch {
            try {
                val status = cloudAccountRepository.getCurrentStatus()
                Log.d(MiFlujoAuthLogTag, status.toSafeLogMessage("Status refresh returned"))
                updateCloudAccountStatus(status)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(
                    MiFlujoAuthLogTag,
                    "Authorization check result: Failure. class=${exception.javaClass.name}, " +
                        "message=Cloud account status refresh failed.",
                )
                mutableUiState.value = mutableUiState.value.copy(
                    cloudAccountStatus = CloudAccountStatus.SignedOut,
                )
                Log.d(
                    MiFlujoAuthLogTag,
                    "SettingsViewModel state updated: cloudAccountStatus=SignedOut after failure.",
                )
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    isCloudAccountOperationInProgress = false,
                )
                cloudAccountJob = null
            }
        }
    }

    private fun updateCloudAccountStatus(status: CloudAccountStatus) {
        mutableUiState.value = mutableUiState.value.copy(cloudAccountStatus = status)
        Log.d(
            MiFlujoAuthLogTag,
            status.toSafeLogMessage("SettingsViewModel state updated"),
        )
    }

    private suspend fun completeSuccessfulSignIn(signInStatus: CloudAccountStatus) {
        Log.d(MiFlujoAuthLogTag, signInStatus.toSafeLogMessage("Sign-in returned"))
        updateCloudAccountStatus(signInStatus)

        Log.d(MiFlujoAuthLogTag, "Refreshing cloud account status after sign-in.")
        val refreshedStatus = cloudAccountRepository.getCurrentStatus()
        Log.d(
            MiFlujoAuthLogTag,
            refreshedStatus.toSafeLogMessage("Post-sign-in refresh returned"),
        )
        updateCloudAccountStatus(refreshedStatus)
    }

    private fun requestLegacyGoogleSignIn(): Boolean {
        Log.d(
            MiFlujoAuthLogTag,
            "Explicit sign-in path selected: legacy/canary-compatible.",
        )
        isLegacyGoogleSignInPending = true
        if (legacyGoogleSignInRequests.tryEmit(Unit)) return true

        isLegacyGoogleSignInPending = false
        Log.e(MiFlujoAuthLogTag, "GoogleSignInClient fallback request could not be delivered.")
        cloudAccountFeedback.tryEmit(CloudAccountFeedback.SignInIncomplete)
        return false
    }

    private fun finishCloudAccountOperation() {
        mutableUiState.value = mutableUiState.value.copy(
            isCloudAccountOperationInProgress = false,
        )
        Log.d(MiFlujoAuthLogTag, "SettingsViewModel state updated: account operation finished.")
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        manualCloudSyncStateHolder.setCloudSyncEnabled(enabled)
    }

    fun requestAppForegroundSync(runtimeState: CloudSyncSchedulerRuntimeState) {
        viewModelScope.launch {
            cloudSyncSchedulerCoordinator.requestSync(
                runtimeState.toDecisionInput(CloudSyncTriggerReason.APP_FOREGROUND),
            )
        }
    }

    fun requestConnectivityRecoveredSync(runtimeState: CloudSyncSchedulerRuntimeState) {
        viewModelScope.launch {
            cloudSyncSchedulerCoordinator.requestSync(
                runtimeState.toDecisionInput(CloudSyncTriggerReason.CONNECTIVITY_RECOVERED),
            )
        }
    }

    fun syncNow() {
        val requestId = com.carlos.miflujo.data.cloud.sync.generateCloudSyncRequestId()
        val reason = com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS
        com.carlos.miflujo.data.cloud.sync.logCloudSyncRequest(requestId, reason)

        if (
            !cloudSyncEnabled.value ||
            mutableUiState.value.isCloudAccountOperationInProgress ||
            cloudAccountJob?.isActive == true ||
            manualCloudSyncJob?.isActive == true
        ) {
            val action = if (!cloudSyncEnabled.value) {
                com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerAction.SKIP_DISABLED
            } else if (mutableUiState.value.isCloudAccountOperationInProgress || cloudAccountJob?.isActive == true) {
                com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerAction.SKIP_ACCOUNT_OPERATION_RUNNING
            } else {
                com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerAction.SKIP_ALREADY_RUNNING
            }
            com.carlos.miflujo.data.cloud.sync.logCloudSyncDecision(
                id = requestId,
                reason = reason,
                action = action,
                cloudSyncEnabled = cloudSyncEnabled.value,
                alreadyRunning = manualCloudSyncJob?.isActive == true,
            )
            return
        }

        com.carlos.miflujo.data.cloud.sync.logCloudSyncDecision(
            id = requestId,
            reason = reason,
            action = com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerAction.RUN,
            cloudSyncEnabled = cloudSyncEnabled.value,
            alreadyRunning = false,
        )

        manualCloudSyncJob = viewModelScope.launch {
            try {
                manualCloudSyncStateHolder.syncNow(requestId, reason)
                if (cloudSyncActivated.value) {
                    finishRestore()
                }
            } finally {
                manualCloudSyncJob = null
            }
        }
    }

    private fun finishExport() {
        pendingBackupDocument = null
        mutableUiState.value = mutableUiState.value.copy(isExportingBackup = false)
        exportJob = null
    }

    private fun finishRestore() {
        pendingRestoreMovements = null
        mutableUiState.value = mutableUiState.value.copy(
            isRestoringBackup = false,
            pendingRestoreMovementCount = null,
        )
        restoreJob = null
    }
}

private const val BackupExportLogTag = "MiFlujoBackupExport"
private const val BackupRestoreLogTag = "MiFlujoBackupRestore"
private fun CloudAccountStatus.toSafeLogMessage(prefix: String): String = when (this) {
    is CloudAccountStatus.Authorized ->
        "$prefix: Authorized, uidLength=${account.uid.length}."
    is CloudAccountStatus.Unauthorized ->
        "$prefix: Unauthorized, uidLength=${account.uid.length}."
    CloudAccountStatus.SignedOut -> "$prefix: SignedOut."
    CloudAccountStatus.Loading -> "$prefix: Loading."
}

data class CreateBackupDocumentRequest(
    val fileName: String,
)

sealed class BackupExportFeedback(
    val message: String,
) {
    data object ExportSaved : BackupExportFeedback("Respaldo guardado correctamente.")
    data object ExportFailed : BackupExportFeedback("No se pudo crear el respaldo.")
}

sealed class BackupRestoreFeedback(
    val message: String,
) {
    data object RestoreSucceeded : BackupRestoreFeedback("Respaldo restaurado correctamente.")
    data object InvalidBackup : BackupRestoreFeedback("El archivo seleccionado no es un respaldo válido.")
    data object RestoreFailed : BackupRestoreFeedback("No se pudo restaurar el respaldo.")
}

sealed class CloudAccountFeedback(
    val message: String,
) {
    data object SignInIncomplete : CloudAccountFeedback(
        "No se completó el inicio de sesión. Intenta nuevamente.",
    )
    data object NoGoogleCredentialAvailable : CloudAccountFeedback(
        "No se encontró una cuenta Google disponible o la configuración de inicio de sesión no está completa. MiFlujo continúa en modo local.",
    )
    data object UnsupportedCredential : CloudAccountFeedback(
        "La cuenta seleccionada no pudo procesarse. Intenta nuevamente. MiFlujo continúa en modo local.",
    )
    data object GoogleSignInConfigurationError : CloudAccountFeedback(
        "No se pudo iniciar sesión porque la configuración de Google no está completa. MiFlujo continúa en modo local.",
    )
    data object SignInFailed : CloudAccountFeedback(
        "No se pudo iniciar sesión con Google. Intenta nuevamente.",
    )
    data object SignOutFailed : CloudAccountFeedback(
        "No se pudo cerrar la sesión. Intenta nuevamente.",
    )
}

internal fun Exception.toCloudAccountFeedback(): CloudAccountFeedback =
    CloudAccountFeedback.SignInFailed

internal fun fallbackFeedbackForResult(
    result: LegacyGoogleSignInResult,
): CloudAccountFeedback = when (result) {
    LegacyGoogleSignInResult.Canceled -> CloudAccountFeedback.SignInIncomplete
    LegacyGoogleSignInResult.ConfigurationError ->
        CloudAccountFeedback.GoogleSignInConfigurationError
    LegacyGoogleSignInResult.MissingIdToken,
    LegacyGoogleSignInResult.Failure,
    -> CloudAccountFeedback.SignInFailed
    is LegacyGoogleSignInResult.Success -> error("A successful fallback result has no feedback.")
}

internal fun canStartCloudAccountAction(
    isCloudAccountOperationInProgress: Boolean,
    isCloudAccountJobActive: Boolean,
    isManualCloudSyncJobActive: Boolean,
    manualCloudSyncState: ManualCloudSyncUiState,
): Boolean =
    !isCloudAccountOperationInProgress &&
        !isCloudAccountJobActive &&
        !isManualCloudSyncJobActive &&
        manualCloudSyncState !is ManualCloudSyncUiState.Running

class SettingsViewModelFactory(
    private val movementRepository: MovementRepository,
    private val cloudAccountRepository: CloudAccountRepository,
    private val cloudSyncRunCoordinator: CloudSyncRunCoordinator,
    private val cloudSyncEnabledStore: CloudSyncEnabledStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                movementRepository = movementRepository,
                cloudAccountRepository = cloudAccountRepository,
                cloudSyncRunCoordinator = cloudSyncRunCoordinator,
                cloudSyncEnabledStore = cloudSyncEnabledStore,
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
