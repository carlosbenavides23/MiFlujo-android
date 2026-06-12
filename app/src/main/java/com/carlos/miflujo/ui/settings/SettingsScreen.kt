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

@Composable
fun SettingsScreen(
    isExportingBackup: Boolean,
    isRestoringBackup: Boolean,
    pendingRestoreMovementCount: Int?,
    cloudAccountStatus: CloudAccountStatus,
    isCloudAccountOperationInProgress: Boolean,
    manualCloudSyncState: ManualCloudSyncUiState,
    cloudSyncActivated: Boolean,
    cloudSyncEnabled: Boolean,
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
            cloudSyncEnabled = cloudSyncEnabled,
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
                    description = if (cloudSyncActivated) {
                        "Restaurar respaldo no está disponible después de activar Cloud Sync."
                    } else {
                        "Recupera movimientos desde un archivo de respaldo anterior."
                    },
                    enabled = isRestoreBackupEnabled(
                        isBackupOperationInProgress = isBackupOperationInProgress,
                        cloudSyncActivated = cloudSyncActivated,
                    ),
                    status = when {
                        cloudSyncActivated -> "No disponible"
                        isRestoringBackup -> "Restaurando respaldo..."
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
    cloudSyncEnabled: Boolean,
    onSignInWithGoogle: () -> Unit,
    onRefreshCloudAuthorization: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleCloudSyncEnabled: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onCopyUid: (String) -> Unit,
) {
    val accountActionsEnabled = areCloudAccountActionsEnabled(
        isAccountOperationInProgress = isOperationInProgress,
        manualSyncState = manualSyncState,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (status) {
                    CloudAccountStatus.Loading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(text = "Comprobando estado de la cuenta...")
                        }
                    }

                    CloudAccountStatus.SignedOut -> {
                        CloudStatusText(
                            title = "Sin sesión",
                            description = "MiFlujo funciona en modo local. Iniciar sesión no activa " +
                                "la sincronización automáticamente.",
                        )
                        Button(
                            onClick = onSignInWithGoogle,
                            enabled = accountActionsEnabled,
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

                    is CloudAccountStatus.Authorized -> {
                        CloudStatusText(
                            title = "Cuenta autorizada",
                            description = if (cloudSyncEnabled) {
                                "Puedes sincronizar manualmente. MiFlujo no ejecutará " +
                                    "Cloud Sync automáticamente."
                            } else {
                                "Cloud Sync está desactivado. Puedes activarlo para " +
                                    "sincronizar manualmente."
                            },
                        )
                        CloudAccountIdentity(account = status.account)
                        CloudSyncEnabledToggle(
                            enabled = cloudSyncEnabled,
                            onToggle = onToggleCloudSyncEnabled,
                            isOperationInProgress = isOperationInProgress ||
                                manualSyncState is ManualCloudSyncUiState.Running,
                        )
                        Button(
                            onClick = onSyncNow,
                            enabled = isManualSyncEnabled(
                                cloudSyncEnabled = cloudSyncEnabled,
                                isAccountOperationInProgress = isOperationInProgress,
                                manualSyncState = manualSyncState,
                            ),
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
                        OutlinedButton(
                            onClick = onSignOut,
                            enabled = accountActionsEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Cerrar sesión")
                        }
                    }

                    is CloudAccountStatus.Unauthorized -> {
                        CloudStatusText(
                            title = "Cuenta no autorizada",
                            description = "Esta cuenta no tiene acceso a Cloud Sync. MiFlujo continúa " +
                                "funcionando solo con los datos locales.",
                        )
                        CloudAccountIdentity(account = status.account)
                        Text(
                            text = "UID\n${status.account.uid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onRefreshCloudAuthorization,
                            enabled = accountActionsEnabled,
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
                                onCopyUid(status.account.uid)
                            },
                            enabled = !isOperationInProgress,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Copiar UID")
                        }
                        TextButton(
                            onClick = onSignOut,
                            enabled = accountActionsEnabled,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(text = "Cerrar sesión")
                        }
                    }
                }
                ManualCloudSyncResult(state = manualSyncState)
            }
        }
    }
}

internal fun isRestoreBackupEnabled(
    isBackupOperationInProgress: Boolean,
    cloudSyncActivated: Boolean,
): Boolean = !isBackupOperationInProgress && !cloudSyncActivated

internal fun areCloudAccountActionsEnabled(
    isAccountOperationInProgress: Boolean,
    manualSyncState: ManualCloudSyncUiState,
): Boolean =
    !isAccountOperationInProgress && manualSyncState !is ManualCloudSyncUiState.Running

internal fun isManualSyncEnabled(
    cloudSyncEnabled: Boolean,
    isAccountOperationInProgress: Boolean,
    manualSyncState: ManualCloudSyncUiState,
): Boolean =
    cloudSyncEnabled &&
        !isAccountOperationInProgress &&
        manualSyncState !is ManualCloudSyncUiState.Running

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
private fun ManualCloudSyncResult(state: ManualCloudSyncUiState) {
    val message = when (state) {
        ManualCloudSyncUiState.Idle,
        ManualCloudSyncUiState.Running,
        -> return
        is ManualCloudSyncUiState.Success -> "Sincronización completada."
        is ManualCloudSyncUiState.Partial ->
            "Sincronización parcial. Algunos elementos se reintentarán después."
        ManualCloudSyncUiState.SignedOut -> "Inicia sesión para sincronizar."
        ManualCloudSyncUiState.Unauthorized ->
            "Tu cuenta no está autorizada para Cloud Sync."
        ManualCloudSyncUiState.Failure ->
            "No se pudo sincronizar. Intenta de nuevo."
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            is ManualCloudSyncUiState.Success -> SyncCountsText(state.counts)
            is ManualCloudSyncUiState.Partial -> SyncCountsText(state.counts)
            else -> Unit
        }
    }
}

@Composable
private fun SyncCountsText(counts: ManualCloudSyncCounts) {
    Text(
        text = "Subidos: ${counts.uploaded} · Descargados: ${counts.downloaded} · " +
            "Actualizados: ${counts.updatedLocal} · Confirmados: ${counts.markedSynced} · " +
            "Omitidos: ${counts.skippedRemote} · Errores locales: ${counts.localErrors} · " +
            "Errores remotos: ${counts.remoteErrors}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
