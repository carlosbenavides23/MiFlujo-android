package com.carlos.miflujo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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

@Composable
fun SettingsScreen(
    isExportingBackup: Boolean,
    isRestoringBackup: Boolean,
    pendingRestoreMovementCount: Int?,
    onSaveBackup: () -> Unit,
    onShareBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onCancelRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
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
                    description = "Recupera movimientos desde un archivo de respaldo anterior.",
                    enabled = !isBackupOperationInProgress,
                    status = if (isRestoringBackup) "Restaurando respaldo..." else "Restaurar",
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
