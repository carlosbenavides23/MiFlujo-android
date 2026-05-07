package com.carlos.miflujo.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.carlos.miflujo.MiFlujoAppProvider
import com.carlos.miflujo.ui.home.HomeScreen
import com.carlos.miflujo.ui.home.HomeViewModel
import com.carlos.miflujo.ui.home.HomeViewModelFactory
import com.carlos.miflujo.ui.movement.AddMovementDialog
import com.carlos.miflujo.ui.movement.MovementViewModel
import com.carlos.miflujo.ui.movement.MovementViewModelFactory
import com.carlos.miflujo.ui.movement.MovementsScreen
import com.carlos.miflujo.ui.report.ReportScreen
import com.carlos.miflujo.ui.report.ReportViewModel
import com.carlos.miflujo.ui.report.ReportViewModelFactory

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
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    val movementRepository = remember(context) {
        MiFlujoAppProvider.movementRepository(context)
    }
    val homeViewModel = remember(activity, movementRepository) {
        ViewModelProvider(
            activity,
            HomeViewModelFactory(movementRepository),
        )[HomeViewModel::class.java]
    }
    val movementViewModel = remember(activity, movementRepository) {
        ViewModelProvider(
            activity,
            MovementViewModelFactory(movementRepository),
        )[MovementViewModel::class.java]
    }
    val reportViewModel = remember(activity, movementRepository) {
        ViewModelProvider(
            activity,
            ReportViewModelFactory(movementRepository),
        )[ReportViewModel::class.java]
    }
    val homeUiState by homeViewModel.uiState.collectAsState()
    val movementUiState by movementViewModel.uiState.collectAsState()
    val reportUiState by reportViewModel.uiState.collectAsState()
    val feedbackMessage by movementViewModel.feedbackMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        val message = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        movementViewModel.clearFeedbackMessage()
    }

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
                MainDestination.Home -> HomeScreen(uiState = homeUiState)
                MainDestination.Movements -> MovementsScreen(
                    uiState = movementUiState,
                    onPreviousMonth = movementViewModel::goToPreviousMonth,
                    onNextMonth = movementViewModel::goToNextMonth,
                    onFilterSelected = movementViewModel::selectFilter,
                    onDeleteMovement = movementViewModel::deleteMovement,
                )
                MainDestination.Report -> ReportScreen(
                    uiState = reportUiState,
                    onPreviousMonth = reportViewModel::goToPreviousMonth,
                    onNextMonth = reportViewModel::goToNextMonth,
                )
            }
        }
    }

    if (showAddMovementDialog) {
        AddMovementDialog(
            onDismissRequest = {
                showAddMovementDialog = false
            },
            onSubmit = { input ->
                movementViewModel.addMovement(
                    input = input,
                    onInserted = {
                        showAddMovementDialog = false
                    },
                )
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

private tailrec fun Context.findComponentActivity(): ComponentActivity {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> error("MiFlujoApp must run inside a ComponentActivity.")
    }
}
