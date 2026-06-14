package com.carlos.miflujo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carlos.miflujo.data.cloud.sync.CloudSyncSchedulerRuntimeState

internal class AppForegroundSyncRequestGate {
    private var requestedEntryId: Long? = null

    fun shouldRequest(
        foregroundEntryId: Long,
        isForeground: Boolean,
        stateReady: Boolean,
    ): Boolean {
        if (!isForeground || !stateReady || foregroundEntryId <= 0L) return false
        if (requestedEntryId == foregroundEntryId) return false
        requestedEntryId = foregroundEntryId
        return true
    }
}

@Composable
internal fun CloudSyncAppForegroundTrigger(
    lifecycle: Lifecycle,
    runtimeState: CloudSyncSchedulerRuntimeState,
    stateReady: Boolean,
    onRequestSync: (CloudSyncSchedulerRuntimeState) -> Unit,
) {
    val initiallyForeground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    var isForeground by remember(lifecycle) { mutableStateOf(initiallyForeground) }
    var foregroundEntryId by remember(lifecycle) {
        mutableLongStateOf(if (initiallyForeground) 1L else 0L)
    }
    val requestGate = remember(lifecycle) { AppForegroundSyncRequestGate() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (!isForeground) {
                        isForeground = true
                        foregroundEntryId += 1L
                    }
                }

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
        foregroundEntryId,
        isForeground,
        stateReady,
        runtimeState,
    ) {
        if (
            requestGate.shouldRequest(
                foregroundEntryId = foregroundEntryId,
                isForeground = isForeground,
                stateReady = stateReady,
            )
        ) {
            onRequestSync(runtimeState)
        }
    }
}
