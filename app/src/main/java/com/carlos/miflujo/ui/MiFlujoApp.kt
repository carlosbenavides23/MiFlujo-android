package com.carlos.miflujo.ui

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.carlos.miflujo.MiFlujoAppProvider
import com.carlos.miflujo.R
import com.carlos.miflujo.data.cloud.auth.LegacyGoogleSignInFallback
import com.carlos.miflujo.data.cloud.auth.MiFlujoAuthLogTag
import com.carlos.miflujo.data.cloud.auth.CloudAccountStatus
import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerRuntimeState
import com.carlos.miflujo.ui.home.HomeScreen
import com.carlos.miflujo.ui.home.HomeViewModel
import com.carlos.miflujo.ui.home.HomeViewModelFactory
import com.carlos.miflujo.ui.home.mapToCloudSyncHomeIndicatorState
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
    val isNetworkAvailable by rememberNetworkAvailableState(context)
    val activity = remember(context) { context.findComponentActivity() }
    val movementRepository = remember(context) {
        MiFlujoAppProvider.movementRepository(context)
    }
    val cloudAccountRepository = remember(context) {
        MiFlujoAppProvider.cloudAccountRepository(context)
    }
    val cloudSyncEngine = remember(context) {
        MiFlujoAppProvider.cloudSyncEngine(context)
    }
    val cloudSyncActivationStore = remember(context) {
        MiFlujoAppProvider.cloudSyncActivationStore(context)
    }
    val cloudSyncEnabledStore = remember(context) {
        MiFlujoAppProvider.cloudSyncEnabledStore(context)
    }
    val cloudSyncMetadataStore = remember(context) {
        MiFlujoAppProvider.cloudSyncMetadataStore(context)
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
    val settingsViewModel = remember(
        activity,
        movementRepository,
        cloudAccountRepository,
        cloudSyncEngine,
        cloudSyncActivationStore,
        cloudSyncEnabledStore,
        cloudSyncMetadataStore,
    ) {
        ViewModelProvider(
            activity,
            SettingsViewModelFactory(
                movementRepository = movementRepository,
                cloudAccountRepository = cloudAccountRepository,
                cloudSyncRunner = cloudSyncEngine,
                cloudSyncActivationStore = cloudSyncActivationStore,
                cloudSyncEnabledStore = cloudSyncEnabledStore,
                cloudSyncMetadataStore = cloudSyncMetadataStore,
            ),
        )[SettingsViewModel::class.java]
    }
    val homeUiState by homeViewModel.uiState.collectAsState()
    val movementUiState by movementViewModel.uiState.collectAsState()
    val reportUiState by reportViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val manualCloudSyncState by settingsViewModel.manualCloudSyncState.collectAsState()
    val cloudSyncActivated by settingsViewModel.cloudSyncActivated.collectAsState()
    val cloudSyncEnabled by settingsViewModel.cloudSyncEnabled.collectAsState()
    val feedback by movementViewModel.feedback.collectAsState()

    val appForegroundSyncRuntimeState = CloudSyncSchedulerRuntimeState(
        cloudSyncEnabled = cloudSyncEnabled,
        cloudSyncActivated = cloudSyncActivated,
        networkAvailable = isNetworkAvailable,
        accountAuthorized = settingsUiState.cloudAccountStatus is CloudAccountStatus.Authorized,
        alreadyRunning = settingsViewModel.isCloudSyncRunning,
        accountOperationRunning = settingsViewModel.isCloudAccountOperationRunning,
        hasPendingLocalChanges = false,
    )
    CloudSyncAppForegroundTrigger(
        lifecycle = activity.lifecycle,
        runtimeState = appForegroundSyncRuntimeState,
        stateReady = settingsUiState.cloudAccountStatus !is CloudAccountStatus.Loading &&
            !settingsViewModel.isCloudAccountOperationRunning,
        onRequestSync = settingsViewModel::requestAppForegroundSync,
    )

    val cloudSyncHomeIndicatorState = mapToCloudSyncHomeIndicatorState(
        cloudSyncActivated = cloudSyncActivated,
        cloudSyncEnabled = cloudSyncEnabled,
        cloudAccountStatus = settingsUiState.cloudAccountStatus,
        manualCloudSyncState = manualCloudSyncState,
        isOffline = !isNetworkAvailable,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val legacyGoogleSignInFallback = remember(activity) {
        LegacyGoogleSignInFallback(
            activity = activity,
            googleWebClientId = activity.getString(R.string.default_web_client_id),
        )
    }
    val legacyGoogleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        Log.d(
            MiFlujoAuthLogTag,
            "GoogleSignInClient fallback result received: resultCode=${result.resultCode}.",
        )
        settingsViewModel.completeLegacyGoogleSignIn(
            legacyGoogleSignInFallback.parseResult(result.data),
        )
    }
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
    val openBackupDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { sourceUri ->
        if (sourceUri == null) {
            settingsViewModel.cancelBackupSelection()
        } else {
            settingsViewModel.readSelectedBackup(
                context = context,
                sourceUri = sourceUri,
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
        settingsViewModel.restoreFeedbackEvents.collect { feedbackEvent ->
            Toast.makeText(
                context,
                feedbackEvent.message,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.cloudAccountFeedbackEvents.collect { feedbackEvent ->
            Toast.makeText(
                context,
                feedbackEvent.message,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.legacyGoogleSignInRequestEvents.collect {
            try {
                legacyGoogleSignInLauncher.launch(legacyGoogleSignInFallback.signInIntent())
                Log.d(MiFlujoAuthLogTag, "GoogleSignInClient fallback intent launched.")
            } catch (exception: Exception) {
                Log.e(
                    MiFlujoAuthLogTag,
                    "GoogleSignInClient fallback launch failed: " +
                        "class=${exception.javaClass.name}.",
                )
                settingsViewModel.handleLegacyGoogleSignInLaunchFailure()
            }
        }
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.legacyGoogleSignOutRequestEvents.collect {
            legacyGoogleSignInFallback.signOut {
                settingsViewModel.completeLegacyGoogleSignOut(context.applicationContext)
            }
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

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.openBackupDocumentRequestEvents.collect {
            try {
                openBackupDocumentLauncher.launch(arrayOf(BackupJsonMimeType))
            } catch (exception: Exception) {
                settingsViewModel.handleDocumentPickerFailure(exception)
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
                    MainDestination.Home -> HomeScreen(
                        uiState = homeUiState,
                        indicatorState = cloudSyncHomeIndicatorState,
                    )
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
                    isRestoringBackup = settingsUiState.isRestoringBackup,
                    pendingRestoreMovementCount = settingsUiState.pendingRestoreMovementCount,
                    cloudAccountStatus = settingsUiState.cloudAccountStatus,
                    isCloudAccountOperationInProgress =
                        settingsUiState.isCloudAccountOperationInProgress,
                    manualCloudSyncState = manualCloudSyncState,
                    isOffline = !isNetworkAvailable,
                    cloudSyncActivated = cloudSyncActivated,
                    cloudSyncEnabled = cloudSyncEnabled,
                    lastSyncTimestamp = settingsViewModel.lastSyncTimestamp.collectAsState().value,
                    onSaveBackup = settingsViewModel::prepareBackupForSave,
                    onShareBackup = {
                        settingsViewModel.shareBackup(context)
                    },
                    onRestoreBackup = settingsViewModel::requestBackupRestore,
                    onCancelRestore = settingsViewModel::cancelPendingRestore,
                    onConfirmRestore = settingsViewModel::confirmPendingRestore,
                    onSignInWithGoogle = {
                        settingsViewModel.signInWithGoogle()
                    },
                    onRefreshCloudAuthorization =
                        settingsViewModel::refreshCloudAccountStatus,
                    onSyncNow = settingsViewModel::syncNow,
                    onToggleCloudSyncEnabled = settingsViewModel::setCloudSyncEnabled,
                    onSignOut = settingsViewModel::signOut,
                    onCopyUid = { uid ->
                        context.copyCloudUid(uid)
                        Toast.makeText(context, "UID copiado.", Toast.LENGTH_SHORT).show()
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

private fun Context.copyCloudUid(uid: String) {
    val clipboardManager = getSystemService(ClipboardManager::class.java)
    clipboardManager.setPrimaryClip(ClipData.newPlainText("MiFlujo UID", uid))
}

private fun Context.isAirplaneModeEnabled(): Boolean {
    return Settings.Global.getInt(
        contentResolver,
        Settings.Global.AIRPLANE_MODE_ON,
        0,
    ) == 1
}

private data class ConnectivityUiState(
    val networkAvailable: Boolean,
    val airplaneMode: Boolean,
    val activeNetworkPresent: Boolean,
    val validatedAcceptedNetworkCount: Int,
    val hasAnyWifi: Boolean,
    val hasAnyCellular: Boolean,
    val hasAnyEthernet: Boolean,
    val hasAnyVpn: Boolean,
    val hasAnyBluetooth: Boolean,
)

private fun Context.readConnectivityUiState(): ConnectivityUiState {
    val airplaneMode = isAirplaneModeEnabled()
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
        ?: return ConnectivityUiState(
            networkAvailable = false,
            airplaneMode = airplaneMode,
            activeNetworkPresent = false,
            validatedAcceptedNetworkCount = 0,
            hasAnyWifi = false,
            hasAnyCellular = false,
            hasAnyEthernet = false,
            hasAnyVpn = false,
            hasAnyBluetooth = false,
        )

    var validatedAcceptedNetworkCount = 0
    var hasAnyWifi = false
    var hasAnyCellular = false
    var hasAnyEthernet = false
    var hasAnyVpn = false
    var hasAnyBluetooth = false

    connectivityManager.allNetworks.forEach { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@forEach
        val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val hasBluetooth = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)

        hasAnyWifi = hasAnyWifi || hasWifi
        hasAnyCellular = hasAnyCellular || hasCellular
        hasAnyEthernet = hasAnyEthernet || hasEthernet
        hasAnyVpn = hasAnyVpn || hasVpn
        hasAnyBluetooth = hasAnyBluetooth || hasBluetooth

        if (
            isUsableCloudSyncNetwork(
                hasInternet = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                ),
                isValidated = capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED,
                ),
                hasWifi = hasWifi,
                hasCellular = hasCellular,
                hasEthernet = hasEthernet,
                hasVpn = hasVpn,
                hasBluetooth = hasBluetooth,
            )
        ) {
            validatedAcceptedNetworkCount += 1
        }
    }

    return ConnectivityUiState(
        networkAvailable = !airplaneMode && validatedAcceptedNetworkCount > 0,
        airplaneMode = airplaneMode,
        activeNetworkPresent = connectivityManager.activeNetwork != null,
        validatedAcceptedNetworkCount = validatedAcceptedNetworkCount,
        hasAnyWifi = hasAnyWifi,
        hasAnyCellular = hasAnyCellular,
        hasAnyEthernet = hasAnyEthernet,
        hasAnyVpn = hasAnyVpn,
        hasAnyBluetooth = hasAnyBluetooth,
    )
}

private tailrec fun Context.findComponentActivity(): ComponentActivity {
    return when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> error("MiFlujoApp must run inside a ComponentActivity.")
    }
}

@Composable
private fun rememberNetworkAvailableState(context: Context): State<Boolean> {
    val connectivityManager = remember(context) {
        context.getSystemService(ConnectivityManager::class.java)
    }
    val lifecycle = remember(context) { context.findComponentActivity().lifecycle }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val isNetworkAvailable = remember(context) {
        mutableStateOf(context.readConnectivityUiState().networkAvailable)
    }

    DisposableEffect(connectivityManager, context, lifecycle, mainHandler) {
        if (connectivityManager == null) return@DisposableEffect onDispose {}

        val refreshNetworkState = {
            val connectivityState = context.readConnectivityUiState()
            Log.d(
                "MiFlujoSync",
                "Connectivity UI state refreshed: " +
                    "networkAvailable=${connectivityState.networkAvailable}, " +
                    "airplaneMode=${connectivityState.airplaneMode}, " +
                    "activeNetworkPresent=${connectivityState.activeNetworkPresent}, " +
                    "validatedAcceptedNetworkCount=" +
                    "${connectivityState.validatedAcceptedNetworkCount}, " +
                    "hasAnyWifi=${connectivityState.hasAnyWifi}, " +
                    "hasAnyCellular=${connectivityState.hasAnyCellular}, " +
                    "hasAnyEthernet=${connectivityState.hasAnyEthernet}, " +
                    "hasAnyVpn=${connectivityState.hasAnyVpn}, " +
                    "hasAnyBluetooth=${connectivityState.hasAnyBluetooth}.",
            )
            isNetworkAvailable.value = connectivityState.networkAvailable
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshNetworkState()
            }

            override fun onLost(network: Network) {
                refreshNetworkState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                refreshNetworkState()
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNetworkState()
            }
        }
        val airplaneModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(broadcastContext: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                    refreshNetworkState()
                }
            }
        }

        refreshNetworkState()
        connectivityManager.registerDefaultNetworkCallback(callback, mainHandler)
        context.registerReceiver(
            airplaneModeReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
        )
        lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            context.unregisterReceiver(airplaneModeReceiver)
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    return isNetworkAvailable
}
