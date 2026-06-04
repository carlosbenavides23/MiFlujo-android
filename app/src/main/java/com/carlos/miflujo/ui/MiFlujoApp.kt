package com.carlos.miflujo.ui

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.carlos.miflujo.MiFlujoAppProvider
import com.carlos.miflujo.ui.home.HomeScreen
import com.carlos.miflujo.ui.home.HomeViewModel
import com.carlos.miflujo.ui.home.HomeViewModelFactory
import com.carlos.miflujo.ui.backup.BackupJsonMimeType
import com.carlos.miflujo.ui.movement.AddMovementDialog
import com.carlos.miflujo.ui.movement.MovementFeedbackType
import com.carlos.miflujo.ui.movement.MovementViewModel
import com.carlos.miflujo.ui.movement.MovementViewModelFactory
import com.carlos.miflujo.ui.movement.MovementsScreen
import com.carlos.miflujo.ui.report.ReportScreen
import com.carlos.miflujo.ui.report.ReportViewModel
import com.carlos.miflujo.ui.report.ReportViewModelFactory
import com.carlos.miflujo.ui.settings.SettingsScreen
import com.carlos.miflujo.ui.settings.SettingsViewModel
import com.carlos.miflujo.ui.settings.SettingsViewModelFactory

private enum class MainDestination(
    val label: String,
    val icon: ImageVector,
) {
    Home(label = "Inicio", icon = Icons.Filled.Home),
    Movements(label = "Movimientos", icon = Icons.AutoMirrored.Filled.ReceiptLong),
    Report(label = "Reporte", icon = Icons.Filled.Assessment),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiFlujoApp() {
    var selectedDestination by rememberSaveable { mutableStateOf(MainDestination.Home) }
    var showAddMovementDialog by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
    val settingsViewModel = remember(activity, movementRepository) {
        ViewModelProvider(
            activity,
            SettingsViewModelFactory(movementRepository),
        )[SettingsViewModel::class.java]
    }
    val homeUiState by homeViewModel.uiState.collectAsState()
    val movementUiState by movementViewModel.uiState.collectAsState()
    val reportUiState by reportViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val feedback by movementViewModel.feedback.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val createBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupJsonMimeType),
    ) { destinationUri ->
        if (destinationUri == null) {
            settingsViewModel.cancelPreparedBackup()
        } else {
            settingsViewModel.savePreparedBackup(
                context = context,
                destinationUri = destinationUri,
            )
        }
    }

    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    LaunchedEffect(feedback) {
        val currentFeedback = feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = currentFeedback.message,
            duration = when (currentFeedback.type) {
                MovementFeedbackType.SUCCESS -> SnackbarDuration.Short
                MovementFeedbackType.ERROR -> SnackbarDuration.Long
            },
        )
        movementViewModel.clearFeedback()
    }

    LaunchedEffect(reportViewModel) {
        reportViewModel.exportFeedbackEvents.collect { feedbackEvent ->
            Toast.makeText(
                context,
                feedbackEvent.message,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.exportFeedbackEvents.collect { feedbackEvent ->
            Toast.makeText(
                context,
                feedbackEvent.message,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.createDocumentRequestEvents.collect { request ->
            try {
                createBackupDocumentLauncher.launch(request.fileName)
            } catch (exception: Exception) {
                settingsViewModel.handleDocumentCreatorFailure(exception)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = "MiFlujo")
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Ajustes",
                            )
                        }
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
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
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
                SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                    val feedbackType = feedback?.type ?: MovementFeedbackType.SUCCESS
                    Snackbar(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .widthIn(max = 360.dp),
                        snackbarData = snackbarData,
                        containerColor = when (feedbackType) {
                            MovementFeedbackType.SUCCESS -> MaterialTheme.colorScheme.surfaceVariant
                            MovementFeedbackType.ERROR -> MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = when (feedbackType) {
                            MovementFeedbackType.SUCCESS -> MaterialTheme.colorScheme.onSurfaceVariant
                            MovementFeedbackType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
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
                        onEditMovement = movementViewModel::updateMovement,
                        onDeleteMovement = movementViewModel::deleteMovement,
                    )
                    MainDestination.Report -> ReportScreen(
                        uiState = reportUiState,
                        onPreviousMonth = reportViewModel::goToPreviousMonth,
                        onNextMonth = reportViewModel::goToNextMonth,
                        onShareReport = {
                            reportViewModel.shareReport(
                                context = context,
                                uiState = reportUiState,
                            )
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                },
            enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 200),
                    initialOffsetX = { width -> width / 12 },
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                slideOutHorizontally(
                    animationSpec = tween(durationMillis = 180),
                    targetOffsetX = { width -> width / 12 },
                ),
            label = "Settings transition",
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver",
                                )
                            }
                        },
                        title = {
                            Text(text = "MiFlujo")
                        },
                    )
                },
            ) { innerPadding ->
                SettingsScreen(
                    isExportingBackup = settingsUiState.isExportingBackup,
                    onSaveBackup = settingsViewModel::prepareBackupForSave,
                    onShareBackup = {
                        settingsViewModel.shareBackup(context)
                    },
                    modifier = Modifier.padding(innerPadding),
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

private tailrec fun Context.findComponentActivity(): ComponentActivity {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> error("MiFlujoApp must run inside a ComponentActivity.")
    }
}
