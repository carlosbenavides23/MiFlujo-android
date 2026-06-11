package com.carlos.miflujo.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carlos.miflujo.data.cloud.auth.CloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import com.carlos.miflujo.data.cloud.auth.CloudSignInCanceledException
import com.carlos.miflujo.data.cloud.auth.CloudSignInTimedOutException
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
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    private val exportFeedback = MutableSharedFlow<BackupExportFeedback>(extraBufferCapacity = 1)
    private val createDocumentRequests = MutableSharedFlow<CreateBackupDocumentRequest>(
        extraBufferCapacity = 1,
    )
    private val restoreFeedback = MutableSharedFlow<BackupRestoreFeedback>(extraBufferCapacity = 1)
    private val openBackupDocumentRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val cloudAccountFeedback = MutableSharedFlow<CloudAccountFeedback>(
        extraBufferCapacity = 1,
    )
    private var exportJob: Job? = null
    private var restoreJob: Job? = null
    private var cloudAccountJob: Job? = null
    private var pendingBackupDocument: BackupDocument? = null
    private var pendingRestoreMovements: List<Movement>? = null

    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    val exportFeedbackEvents: SharedFlow<BackupExportFeedback> = exportFeedback.asSharedFlow()
    val createDocumentRequestEvents: SharedFlow<CreateBackupDocumentRequest> =
        createDocumentRequests.asSharedFlow()
    val restoreFeedbackEvents: SharedFlow<BackupRestoreFeedback> = restoreFeedback.asSharedFlow()
    val openBackupDocumentRequestEvents: SharedFlow<Unit> = openBackupDocumentRequests.asSharedFlow()
    val cloudAccountFeedbackEvents: SharedFlow<CloudAccountFeedback> =
        cloudAccountFeedback.asSharedFlow()

    init {
        refreshCloudAccountStatus()
    }

    fun signInWithGoogle(context: Context) {
        if (cloudAccountJob?.isActive == true) return

        mutableUiState.value = mutableUiState.value.copy(isCloudAccountOperationInProgress = true)
        cloudAccountJob = viewModelScope.launch {
            try {
                val status = cloudAccountRepository.signInWithGoogle(context)
                Log.d(CloudAccountLogTag, status.toSignInLogMessage())
                mutableUiState.value = mutableUiState.value.copy(cloudAccountStatus = status)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                when (exception) {
                    is CloudSignInCanceledException -> Unit
                    is CloudSignInTimedOutException -> {
                        Log.w(CloudAccountLogTag, "Google sign-in timed out.", exception)
                        cloudAccountFeedback.tryEmit(CloudAccountFeedback.SignInTimedOut)
                    }
                    is NoCredentialException -> {
                        Log.w(
                            CloudAccountLogTag,
                            "No Google credential available for explicit sign-in.",
                            exception,
                        )
                        cloudAccountFeedback.tryEmit(
                            CloudAccountFeedback.NoGoogleCredentialAvailable,
                        )
                    }
                    else -> {
                        Log.e(CloudAccountLogTag, "Google sign-in failed.", exception)
                        cloudAccountFeedback.tryEmit(CloudAccountFeedback.SignInFailed)
                    }
                }
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    isCloudAccountOperationInProgress = false,
                )
                cloudAccountJob = null
            }
        }
    }

    fun signOut(context: Context) {
        if (cloudAccountJob?.isActive == true) return

        mutableUiState.value = mutableUiState.value.copy(isCloudAccountOperationInProgress = true)
        cloudAccountJob = viewModelScope.launch {
            try {
                cloudAccountRepository.signOut(context)
                mutableUiState.value = mutableUiState.value.copy(
                    cloudAccountStatus = CloudAccountStatus.SignedOut,
                )
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(CloudAccountLogTag, "Cloud account sign-out failed.", exception)
                cloudAccountFeedback.tryEmit(CloudAccountFeedback.SignOutFailed)
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    isCloudAccountOperationInProgress = false,
                )
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
        if (!canStartBackupOperation()) return

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
        if (!mutableUiState.value.isRestoringBackup || restoreJob?.isActive == true) return

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
        if (restoreJob?.isActive == true || mutableUiState.value.isRestoringBackup) return

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

    fun refreshCloudAccountStatus() {
        if (cloudAccountJob?.isActive == true) return

        mutableUiState.value = mutableUiState.value.copy(isCloudAccountOperationInProgress = true)
        cloudAccountJob = viewModelScope.launch {
            try {
                val status = cloudAccountRepository.getCurrentStatus()
                mutableUiState.value = mutableUiState.value.copy(cloudAccountStatus = status)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                Log.e(CloudAccountLogTag, "Cloud account status refresh failed.", exception)
                mutableUiState.value = mutableUiState.value.copy(
                    cloudAccountStatus = CloudAccountStatus.SignedOut,
                )
            } finally {
                mutableUiState.value = mutableUiState.value.copy(
                    isCloudAccountOperationInProgress = false,
                )
                cloudAccountJob = null
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
private const val CloudAccountLogTag = "MiFlujoCloudAccount"

private fun CloudAccountStatus.toSignInLogMessage(): String = when (this) {
    is CloudAccountStatus.Authorized ->
        "Google sign-in returned Authorized for UID ${account.uid.toShortLogUid()}."
    is CloudAccountStatus.Unauthorized ->
        "Google sign-in returned Unauthorized for UID ${account.uid.toShortLogUid()}."
    CloudAccountStatus.SignedOut -> "Google sign-in returned SignedOut."
    CloudAccountStatus.Loading -> "Google sign-in returned Loading."
}

private fun String.toShortLogUid(): String = take(6) + "..."

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
    data object SignInTimedOut : CloudAccountFeedback(
        "El inicio de sesión tardó demasiado. Intenta nuevamente. MiFlujo continúa en modo local.",
    )
    data object NoGoogleCredentialAvailable : CloudAccountFeedback(
        "No se encontró una cuenta Google disponible o la configuración de inicio de sesión no está completa. MiFlujo continúa en modo local.",
    )
    data object SignInFailed : CloudAccountFeedback(
        "No se pudo iniciar sesión con Google. MiFlujo continúa en modo local.",
    )
    data object SignOutFailed : CloudAccountFeedback(
        "No se pudo cerrar la sesión. Intenta nuevamente.",
    )
}

class SettingsViewModelFactory(
    private val movementRepository: MovementRepository,
    private val cloudAccountRepository: CloudAccountRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                movementRepository = movementRepository,
                cloudAccountRepository = cloudAccountRepository,
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
