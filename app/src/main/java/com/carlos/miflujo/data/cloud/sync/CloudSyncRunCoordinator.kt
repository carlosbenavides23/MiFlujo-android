package com.carlos.miflujo.data.cloud.sync

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CloudSyncRunOutcome {
    data class Completed(
        val result: CloudSyncResult,
    ) : CloudSyncRunOutcome

    data object SkippedDisabled : CloudSyncRunOutcome
    data object SkippedAlreadyRunning : CloudSyncRunOutcome
    data object Failure : CloudSyncRunOutcome
}

class CloudSyncRunCoordinator(
    private val cloudSyncRunner: CloudSyncRunner,
    private val cloudSyncActivationStore: CloudSyncActivationStore,
    private val cloudSyncEnabledStore: CloudSyncEnabledStore,
    private val cloudSyncMetadataStore: CloudSyncMetadataStore,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : CloudSyncScheduledRunExecutor {
    private val running = AtomicBoolean(false)
    private val mutableCloudSyncActivated = MutableStateFlow(
        cloudSyncActivationStore.isActivated(),
    )
    private val mutableLastSyncTimestamp = MutableStateFlow(
        cloudSyncMetadataStore.getLastSyncTimestamp(),
    )

    val cloudSyncActivated: StateFlow<Boolean> = mutableCloudSyncActivated.asStateFlow()
    val lastSyncTimestamp: StateFlow<Long?> = mutableLastSyncTimestamp.asStateFlow()
    val isRunning: Boolean
        get() = running.get()

    override suspend fun runCloudSync(
        requestId: String,
        reason: CloudSyncTriggerReason,
    ): CloudSyncRunOutcome = runCloudSync(
        requestId = requestId,
        reason = reason,
        onStarted = {},
    )

    suspend fun runCloudSync(
        requestId: String,
        reason: CloudSyncTriggerReason,
        onStarted: () -> Unit,
    ): CloudSyncRunOutcome {
        if (!cloudSyncEnabledStore.isEnabled()) {
            return CloudSyncRunOutcome.SkippedDisabled
        }
        if (!running.compareAndSet(false, true)) {
            return CloudSyncRunOutcome.SkippedAlreadyRunning
        }

        logCloudSyncRunStarted(requestId, reason)
        return try {
            onStarted()
            val result = cloudSyncRunner.syncNow()
            logCloudSyncRunFinished(requestId, reason, result)
            updateMetadata(requestId, result.status)
            CloudSyncRunOutcome.Completed(result)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            logMiFlujoSyncError(
                "Cloud Sync failed before result: class=${exception.javaClass.name}, " +
                    "message=Sync run failed.",
            )
            CloudSyncRunOutcome.Failure
        } finally {
            running.set(false)
        }
    }

    private fun updateMetadata(
        requestId: String,
        status: CloudSyncStatus,
    ) {
        var activatedUpdated = false
        var lastSyncUpdated = false

        if (status.updatesSuccessfulSyncMetadata()) {
            if (!mutableCloudSyncActivated.value) {
                runCatching { cloudSyncActivationStore.markActivated() }
                    .onFailure {
                        logMiFlujoSyncError("Cloud Sync activation flag could not be persisted.")
                    }
                mutableCloudSyncActivated.value = true
                activatedUpdated = true
            }

            val timestamp = currentTimeMillis()
            cloudSyncMetadataStore.updateLastSyncTimestamp(timestamp)
            mutableLastSyncTimestamp.value = timestamp
            lastSyncUpdated = true
        }

        if (activatedUpdated || lastSyncUpdated) {
            logCloudSyncMetadataUpdate(
                id = requestId,
                activatedUpdated = activatedUpdated,
                lastSyncUpdated = lastSyncUpdated,
            )
        }
    }
}

private fun CloudSyncStatus.updatesSuccessfulSyncMetadata(): Boolean =
    this == CloudSyncStatus.SUCCESS || this == CloudSyncStatus.PARTIAL
