package com.carlos.miflujo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.data.cloud.auth.CloudAccount
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    isExportingBackup: Boolean,
    isRestoringBackup: Boolean,
    pendingRestoreMovementCount: Int?,
    cloudAccountStatus: CloudAccountStatus,
    isCloudAccountOperationInProgress: Boolean,
    isCloudSyncRunning: Boolean,
    manualCloudSyncState: ManualCloudSyncUiState,
    isOffline: Boolean,
    cloudSyncActivated: Boolean,
    cloudSyncEnabled: Boolean,
    lastSyncTimestamp: Long?,
    onSaveBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onCancelRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onRefreshCloudAuthorization: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleCloudSyncEnabled: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onCopyUid: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBackupActions by rememberSaveable { mutableStateOf(false) }
    val isBackupOperationInProgress =
        isExportingBackup || isRestoringBackup || pendingRestoreMovementCount != null
    val restoreAvailability = mapRestoreBackupAvailability(
        cloudSyncEnabled = cloudSyncEnabled,
        cloudSyncActivated = cloudSyncActivated,
        cloudAccountStatus = cloudAccountStatus,
        isCloudSyncRunning = isCloudSyncRunning,
        isCloudAccountOperationRunning = isCloudAccountOperationInProgress,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        CloudSyncSection(
            status = cloudAccountStatus,
            isOperationInProgress = isCloudAccountOperationInProgress,
            manualSyncState = manualCloudSyncState,
            isOffline = isOffline,
            cloudSyncActivated = cloudSyncActivated,
            cloudSyncEnabled = cloudSyncEnabled,
            lastSyncTimestamp = lastSyncTimestamp,
            onSignInWithGoogle = onSignInWithGoogle,
            onRefreshCloudAuthorization = onRefreshCloudAuthorization,
            onSyncNow = onSyncNow,
            onToggleCloudSyncEnabled = onToggleCloudSyncEnabled,
            onSignOut = onSignOut,
            onCopyUid = onCopyUid,
        )
        SettingsSection(
            title = "Datos",
            options = listOf(
                SettingsOption(
                    title = "Crear respaldo local",
                    description = "Exporta tus movimientos a un archivo para guardarlo fuera de la app.",
                    enabled = !isBackupOperationInProgress,
                    status = if (isExportingBackup) "Preparando respaldo..." else "Crear",
                    onClick = {
                        showBackupActions = true
                    },
                ),
                SettingsOption(
                    title = "Restaurar respaldo",
                    description = when {
                        isBackupOperationInProgress ||
                            restoreAvailability ==
                            RestoreBackupAvailability.BLOCKED_OPERATION_RUNNING ->
                            "Espera a que termine la operación actual."

                        restoreAvailability ==
                            RestoreBackupAvailability.BLOCKED_CLOUD_SYNC_ACTIVE ->
                            "Desactiva Cloud Sync o cierra sesión para restaurar un respaldo local."

                        else -> "Recupera movimientos desde un archivo de respaldo anterior."
                    },
                    enabled = !isBackupOperationInProgress &&
                        restoreAvailability == RestoreBackupAvailability.AVAILABLE,
                    status = when {
                        isRestoringBackup -> "Restaurando respaldo..."
                        isBackupOperationInProgress ||
                            restoreAvailability ==
                            RestoreBackupAvailability.BLOCKED_OPERATION_RUNNING ->
                            "Espera"

                        restoreAvailability ==
                            RestoreBackupAvailability.BLOCKED_CLOUD_SYNC_ACTIVE ->
                            "No disponible"

                        else -> "Restaurar"
                    },
                    onClick = onRestoreBackup,
                ),
            ),
        )
        SettingsSection(
            title = "Información",
            options = listOf(
                SettingsOption(
                    title = "Acerca de MiFlujo",
                    description = "Próximamente",
                ),
                SettingsOption(
                    title = "Changelog",
                    description = "Próximamente",
                ),
            ),
        )
    }

    if (showBackupActions) {
        CreateBackupDialog(
            onDismissRequest = {
                showBackupActions = false
            },
            onSaveBackup = {
                showBackupActions = false
                onSaveBackup()
            },
            onShareBackup = {
                showBackupActions = false
                onShareBackup()
            },
        )
    }

    pendingRestoreMovementCount?.let { movementCount ->
        RestoreBackupDialog(
            movementCount = movementCount,
            onDismissRequest = onCancelRestore,
            onConfirmRestore = onConfirmRestore,
        )
    }
}

@Composable
private fun CloudSyncSection(
    status: CloudAccountStatus,
    isOperationInProgress: Boolean,
    manualSyncState: ManualCloudSyncUiState,
    isOffline: Boolean,
    cloudSyncActivated: Boolean,
    cloudSyncEnabled: Boolean,
    lastSyncTimestamp: Long?,
    onSignInWithGoogle: () -> Unit,
    onRefreshCloudAuthorization: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleCloudSyncEnabled: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onCopyUid: (String) -> Unit,
) {
    val presentation = mapToCloudSyncSettingsPresentation(
        cloudSyncActivated = cloudSyncActivated,
        cloudSyncEnabled = cloudSyncEnabled,
        lastSyncTimestamp = lastSyncTimestamp,
        isOffline = isOffline,
        cloudAccountStatus = status,
        manualCloudSyncState = manualSyncState,
        isAccountOperationInProgress = isOperationInProgress,
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Cloud Sync",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (presentation.status == CloudSyncSettingsStatus.LOADING && isOperationInProgress) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text(text = "Comprobando estado de la cuenta...")
                    }
                } else {
                    CloudStatusText(
                        title = presentation.title,
                        description = presentation.description,
                    )
                }

                if (presentation.showCloudAccountIdentity && presentation.account != null) {
                    CloudAccountIdentity(account = presentation.account)
                }

                if (presentation.showCloudSyncEnabledToggle) {
                    CloudSyncEnabledToggle(
                        enabled = cloudSyncEnabled,
                        onToggle = onToggleCloudSyncEnabled,
                        isOperationInProgress = isOperationInProgress ||
                            manualSyncState is ManualCloudSyncUiState.Running,
                    )
                }

                if (presentation.showLastSyncTimestamp) {
                    LastSyncTimestampText(lastSyncTimestamp = lastSyncTimestamp)
                }

                if (presentation.showSignInButton) {
                    Button(
                        onClick = onSignInWithGoogle,
                        enabled = presentation.isAccountActionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isOperationInProgress) {
                                "Iniciando sesión..."
                            } else {
                                "Iniciar sesión con Google"
                            },
                        )
                    }
                }

                if (presentation.showUnauthorizedActions) {
                    Text(
                        text = "UID\n${presentation.account?.uid}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onRefreshCloudAuthorization,
                        enabled = presentation.isAccountActionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isOperationInProgress) {
                                "Verificando..."
                            } else {
                                "Verificar autorización"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            presentation.account?.uid?.let(onCopyUid)
                        },
                        enabled = !isOperationInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Copiar UID")
                    }
                }

                if (presentation.showSyncNowButton || presentation.showSignOutButton) {
                    CloudSyncActionGroup(
                        showSyncNowButton = presentation.showSyncNowButton,
                        showSignOutButton = presentation.showSignOutButton,
                        isManualSyncEnabled = presentation.isManualSyncEnabled,
                        isAccountActionsEnabled = presentation.isAccountActionsEnabled,
                        manualSyncState = manualSyncState,
                        onSyncNow = onSyncNow,
                        onSignOut = onSignOut,
                    )
                }

                ManualCloudSyncResult(
                    state = manualSyncState,
                )
            }
        }
    }
}

@Composable
private fun CloudSyncActionGroup(
    showSyncNowButton: Boolean,
    showSignOutButton: Boolean,
    isManualSyncEnabled: Boolean,
    isAccountActionsEnabled: Boolean,
    manualSyncState: ManualCloudSyncUiState,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showSyncNowButton) {
            Button(
                onClick = onSyncNow,
                enabled = isManualSyncEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (manualSyncState is ManualCloudSyncUiState.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Sincronizando...",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(text = "Sincronizar ahora")
                }
            }
        }

        if (showSignOutButton) {
            OutlinedButton(
                onClick = onSignOut,
                enabled = isAccountActionsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Cerrar sesión")
            }
        }
    }
}

@Composable
private fun CloudSyncEnabledToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    isOperationInProgress: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (enabled) "Cloud Sync activado" else "Cloud Sync desactivado",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = !isOperationInProgress,
        )
    }
}

@Composable
private fun LastSyncTimestampText(lastSyncTimestamp: Long?) {
    val text = if (lastSyncTimestamp == null) {
        "Aún no se ha sincronizado este dispositivo."
    } else {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")
        val formatted = Instant.ofEpochMilli(lastSyncTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
        "Última sincronización: $formatted"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal data class ManualCloudSyncResultPresentation(
    val title: String,
    val detail: String,
)

internal fun mapManualCloudSyncResultPresentation(
    state: ManualCloudSyncUiState,
): ManualCloudSyncResultPresentation? {
    return when (state) {
        ManualCloudSyncUiState.Idle,
        ManualCloudSyncUiState.Running,
        -> null
        is ManualCloudSyncUiState.Success -> ManualCloudSyncResultPresentation(
            title = "Sincronización completada.",
            detail = state.counts.successDetail(),
        )

        is ManualCloudSyncUiState.Partial -> ManualCloudSyncResultPresentation(
            title = "Sincronización parcial.",
            detail = state.counts.partialDetail(),
        )

        else -> null
    }
}

@Composable
private fun ManualCloudSyncResult(
    state: ManualCloudSyncUiState,
) {
    val presentation = mapManualCloudSyncResultPresentation(
        state = state,
    ) ?: return

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = presentation.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = presentation.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ManualCloudSyncCounts.successDetail(): String {
    val hasDataActivity = uploaded > 0 || downloaded > 0 || updatedLocal > 0
    if (
        !hasDataActivity &&
        skippedRemote == 0 &&
        localErrors == 0 &&
        remoteErrors == 0
    ) {
        return "Todo está al día."
    }
    return meaningfulCounts().ifEmpty {
        listOf("Todo está al día.")
    }.joinToString(separator = " · ")
}

private fun ManualCloudSyncCounts.partialDetail(): String =
    meaningfulCounts(errorsFirst = true).ifEmpty {
        listOf("Algunos elementos se reintentarán después.")
    }.joinToString(separator = " · ")

private fun ManualCloudSyncCounts.meaningfulCounts(
    errorsFirst: Boolean = false,
): List<String> {
    val activity = buildList {
        if (uploaded > 0) add("Subidos: $uploaded")
        if (downloaded > 0) add("Descargados: $downloaded")
        if (updatedLocal > 0) add("Actualizados: $updatedLocal")
        if (markedSynced > 0) add("Confirmados: $markedSynced")
        if (skippedRemote > 0) add("Omitidos: $skippedRemote")
    }
    val errors = buildList {
        if (localErrors > 0) add("Errores locales: $localErrors")
        if (remoteErrors > 0) add("Errores remotos: $remoteErrors")
    }
    return if (errorsFirst) errors + activity else activity + errors
}

@Composable
private fun CloudStatusText(
    title: String,
    description: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CloudAccountIdentity(account: CloudAccount) {
    val identityLines = listOfNotNull(
        account.displayName?.takeIf { it.isNotBlank() },
        account.email?.takeIf { it.isNotBlank() },
    )
    if (identityLines.isEmpty()) return

    Text(
        text = identityLines.joinToString(separator = "\n"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CreateBackupDialog(
    onDismissRequest: () -> Unit,
    onSaveBackup: () -> Unit,
    onShareBackup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Crear respaldo local")
        },
        text = {
            Text(text = "Este archivo contiene tus movimientos. Guárdalo en un lugar seguro.")
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                TextButton(onClick = onSaveBackup) {
                    Text(text = "Guardar en Archivos")
                }
                TextButton(onClick = onShareBackup) {
                    Text(text = "Compartir con otra app")
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = "Cancelar")
                }
            }
        },
    )
}

@Composable
private fun RestoreBackupDialog(
    movementCount: Int,
    onDismissRequest: () -> Unit,
    onConfirmRestore: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Restaurar respaldo")
        },
        text = {
            Text(
                text = "Esta acción eliminará todos los movimientos actuales y los reemplazará " +
                    "por los $movementCount movimientos del respaldo. No se puede deshacer.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmRestore) {
                Text(
                    text = "Restaurar",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancelar")
            }
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    options: List<SettingsOption>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                options.forEachIndexed { index, option ->
                    SettingsOptionRow(option = option)
                    if (index < options.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionRow(option: SettingsOption) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = option.enabled,
                onClick = option.onClick,
            )
            .alpha(if (option.enabled) 1f else 0.68f)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = option.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        option.status?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

private data class SettingsOption(
    val title: String,
    val description: String,
    val enabled: Boolean = false,
    val status: String? = "Próximamente",
    val onClick: () -> Unit = {},
)
