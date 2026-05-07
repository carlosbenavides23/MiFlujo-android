package com.carlos.miflujo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.ui.home.HomeScreen
import com.carlos.miflujo.ui.movement.AddMovementDialog
import com.carlos.miflujo.ui.movement.MovementsScreen
import com.carlos.miflujo.ui.report.ReportScreen
import kotlinx.coroutines.launch

private enum class MainDestination(
    val label: String,
    val iconText: String,
) {
    Home(label = "Inicio", iconText = "I"),
    Movements(label = "Movimientos", iconText = "M"),
    Report(label = "Reporte", iconText = "R"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiFlujoApp() {
    var selectedDestination by rememberSaveable { mutableStateOf(MainDestination.Home) }
    var showAddMovementDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "MiFlujo")
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = { selectedDestination = destination },
                        icon = {
                            DestinationIcon(text = destination.iconText)
                        },
                        label = {
                            Text(text = destination.label)
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    showAddMovementDialog = true
                },
            ) {
                Text(text = "+ Agregar")
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedDestination) {
                MainDestination.Home -> HomeScreen()
                MainDestination.Movements -> MovementsScreen()
                MainDestination.Report -> ReportScreen()
            }
        }
    }

    if (showAddMovementDialog) {
        AddMovementDialog(
            onDismissRequest = {
                showAddMovementDialog = false
            },
            onValidated = {
                showAddMovementDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Movimiento listo para guardar en una siguiente etapa",
                    )
                }
            },
        )
    }
}

@Composable
private fun DestinationIcon(text: String) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
