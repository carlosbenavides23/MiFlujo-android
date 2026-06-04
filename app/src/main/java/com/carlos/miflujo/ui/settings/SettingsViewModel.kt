package com.carlos.miflujo.ui.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.ui.backup.BackupDocument
import com.carlos.miflujo.ui.backup.BackupExporter
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
)

class SettingsViewModel(
    private val movementRepository: MovementRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SettingsUiState())
    private val exportFeedback = MutableSharedFlow<BackupExportFeedback>(extraBufferCapacity = 1)
    private val createDocumentRequests = MutableSharedFlow<CreateBackupDocumentRequest>(
        extraBufferCapacity = 1,
    )
    private var exportJob: Job? = null
    private var pendingBackupDocument: BackupDocument? = null

    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    val exportFeedbackEvents: SharedFlow<BackupExportFeedback> = exportFeedback.asSharedFlow()
    val createDocumentRequestEvents: SharedFlow<CreateBackupDocumentRequest> =
        createDocumentRequests.asSharedFlow()

    fun prepareBackupForSave() {
        if (exportJob?.isActive == true || mutableUiState.value.isExportingBackup) return

        mutableUiState.value = SettingsUiState(isExportingBackup = true)
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
        if (exportJob?.isActive == true || mutableUiState.value.isExportingBackup) return

        mutableUiState.value = SettingsUiState(isExportingBackup = true)
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

    private fun finishExport() {
        pendingBackupDocument = null
        mutableUiState.value = SettingsUiState(isExportingBackup = false)
        exportJob = null
    }
}

private const val BackupExportLogTag = "MiFlujoBackupExport"

data class CreateBackupDocumentRequest(
    val fileName: String,
)

sealed class BackupExportFeedback(
    val message: String,
) {
    data object ExportSaved : BackupExportFeedback("Respaldo guardado correctamente.")
    data object ExportFailed : BackupExportFeedback("No se pudo crear el respaldo.")
}

class SettingsViewModelFactory(
    private val movementRepository: MovementRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(movementRepository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
