package com.carlos.miflujo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerRuntimeState

internal class ConnectivityRecoveredSyncRequestGate {
    private var previousNetworkAvailable: Boolean? = null
    private var recoveryPending = false

    fun shouldRequest(
        networkAvailable: Boolean,
        isForeground: Boolean,
        stateReady: Boolean,
    ): Boolean {
        val previousNetwork = previousNetworkAvailable
        previousNetworkAvailable = networkAvailable

        if (!isForeground) {
            recoveryPending = false
            return false
        }
        if (!networkAvailable) {
            recoveryPending = false
            return false
        }
        if (previousNetwork == false) {
            recoveryPending = true
        }
        if (!recoveryPending || !stateReady) return false

        recoveryPending = false
        return true
    }
}

@Composable
internal fun CloudSyncConnectivityRecoveredTrigger(
    lifecycle: Lifecycle,
    runtimeState: CloudSyncSchedulerRuntimeState,
    stateReady: Boolean,
    onRequestSync: (CloudSyncSchedulerRuntimeState) -> Unit,
) {
    var isForeground by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val requestGate = remember(lifecycle) { ConnectivityRecoveredSyncRequestGate() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isForeground = true
                Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(
        runtimeState.networkAvailable,
        isForeground,
        stateReady,
        runtimeState,
    ) {
        if (
            requestGate.shouldRequest(
                networkAvailable = runtimeState.networkAvailable,
                isForeground = isForeground,
                stateReady = stateReady,
            )
        ) {
            onRequestSync(runtimeState)
        }
    }
}
