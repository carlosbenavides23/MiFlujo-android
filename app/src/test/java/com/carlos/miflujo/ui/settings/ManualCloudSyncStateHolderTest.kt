package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.sync.CloudSyncActivationStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncMetadataStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncResult
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunCoordinator
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunner
import com.carlos.miflujo.data.cloud.sync.CloudSyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualCloudSyncStateHolderTest {
    @Test
    fun `sync is not running initially`() {
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.SUCCESS),
        )

        assertEquals(false, holder.isSyncRunning)
    }

    @Test
    fun `signed out result is rendered safely`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.SIGNED_OUT),
            activationStore = activationStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(ManualCloudSyncUiState.SignedOut, holder.state.value)
        assertEquals(false, activationStore.activated)
    }

    @Test
    fun `unauthorized result is rendered safely`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.UNAUTHORIZED),
            activationStore = activationStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(ManualCloudSyncUiState.Unauthorized, holder.state.value)
        assertEquals(false, activationStore.activated)
    }

    @Test
    fun `successful sync shows success state and counts`() = runBlocking {
        val result = syncResult(status = CloudSyncStatus.SUCCESS)
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(
            result = result,
            activationStore = activationStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(
            ManualCloudSyncUiState.Success(result.toExpectedCounts()),
            holder.state.value,
        )
        assertEquals(true, activationStore.activated)
        assertEquals(true, holder.cloudSyncActivated.value)
    }

    @Test
    fun `partial sync shows partial state and relevant counts`() = runBlocking {
        val result = syncResult(status = CloudSyncStatus.PARTIAL)
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(
            result = result,
            activationStore = activationStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(
            ManualCloudSyncUiState.Partial(result.toExpectedCounts()),
            holder.state.value,
        )
        assertEquals(true, activationStore.activated)
        assertEquals(true, holder.cloudSyncActivated.value)
    }

    @Test
    fun `sync failure shows failure state`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            activationStore = activationStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(ManualCloudSyncUiState.Failure, holder.state.value)
        assertEquals(false, activationStore.activated)
    }

    @Test
    fun `activation remains visible when state holder is recreated`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        holder(
            result = CloudSyncResult(status = CloudSyncStatus.SUCCESS),
            activationStore = activationStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        ).syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        val recreatedHolder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            activationStore = activationStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        assertEquals(true, recreatedHolder.cloudSyncActivated.value)
    }

    @Test
    fun `repeated taps while running launch only one sync`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var callCount = 0
        val activationStore = FakeCloudSyncActivationStore()
        val enabledStore = FakeCloudSyncEnabledStore(initialValue = true)
        val metadataStore = FakeCloudSyncMetadataStore()
        val holder = ManualCloudSyncStateHolder(
            cloudSyncRunCoordinator = CloudSyncRunCoordinator(
                cloudSyncRunner = CloudSyncRunner {
                    callCount += 1
                    started.complete(Unit)
                    release.await()
                    CloudSyncResult(status = CloudSyncStatus.SUCCESS)
                },
                cloudSyncActivationStore = activationStore,
                cloudSyncEnabledStore = enabledStore,
                cloudSyncMetadataStore = metadataStore,
            ),
            cloudSyncEnabledStore = enabledStore,
        )

        val firstSync = async { holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS) }
        started.await()
        assertEquals(ManualCloudSyncUiState.Running, holder.state.value)
        assertEquals(true, holder.isSyncRunning)

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(1, callCount)
        assertEquals(ManualCloudSyncUiState.Running, holder.state.value)
        release.complete(Unit)
        firstSync.await()
        assertEquals(1, callCount)
        assertEquals(false, holder.isSyncRunning)
        assertEquals(
            ManualCloudSyncUiState.Success(
                ManualCloudSyncCounts(
                    uploaded = 0,
                    downloaded = 0,
                    updatedLocal = 0,
                    markedSynced = 0,
                    skippedRemote = 0,
                    localErrors = 0,
                    remoteErrors = 0,
                ),
            ),
            holder.state.value,
        )
    }

    @Test
    fun `cloudSyncEnabled defaults to false when not activated`() = runBlocking {
        val enabledStore = FakeCloudSyncEnabledStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            enabledStore = enabledStore,
        )

        assertEquals(false, holder.cloudSyncEnabled.value)
    }

    @Test
    fun `cloudSyncEnabled can be toggled on and off`() = runBlocking {
        val enabledStore = FakeCloudSyncEnabledStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            enabledStore = enabledStore,
        )

        holder.setCloudSyncEnabled(true)
        assertEquals(true, holder.cloudSyncEnabled.value)
        assertEquals(true, enabledStore.enabled)

        holder.setCloudSyncEnabled(false)
        assertEquals(false, holder.cloudSyncEnabled.value)
        assertEquals(false, enabledStore.enabled)
    }

    @Test
    fun `cloudSyncActivated remains independent after toggling enabled`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        val enabledStore = FakeCloudSyncEnabledStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            activationStore = activationStore,
            enabledStore = enabledStore,
        )

        holder.setCloudSyncEnabled(true)
        assertEquals(false, activationStore.activated)
        assertEquals(false, holder.cloudSyncActivated.value)
    }

    @Test
    fun `initial lastSyncTimestamp is read from metadata store`() = runBlocking {
        val metadataStore = FakeCloudSyncMetadataStore(timestamp = 12345L)
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            metadataStore = metadataStore,
        )

        assertEquals(12345L, holder.lastSyncTimestamp.value)
    }

    @Test
    fun `successful sync updates the metadata store and exposed lastSyncTimestamp`() = runBlocking {
        val metadataStore = FakeCloudSyncMetadataStore(timestamp = 1000L)
        val holder = holder(
            result = syncResult(status = CloudSyncStatus.SUCCESS),
            metadataStore = metadataStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        val before = System.currentTimeMillis()
        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)
        val after = System.currentTimeMillis()

        val updatedTimestamp = holder.lastSyncTimestamp.value
        org.junit.Assert.assertNotNull(updatedTimestamp)
        org.junit.Assert.assertTrue(updatedTimestamp!! in before..after)
        assertEquals(updatedTimestamp, metadataStore.getLastSyncTimestamp())
    }

    @Test
    fun `failed manual sync does not update the timestamp`() = runBlocking {
        val metadataStore = FakeCloudSyncMetadataStore(timestamp = 1000L)
        val holder = holder(
            result = syncResult(status = CloudSyncStatus.FAILURE),
            metadataStore = metadataStore,
            enabledStore = FakeCloudSyncEnabledStore(initialValue = true),
        )

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(1000L, holder.lastSyncTimestamp.value)
        assertEquals(1000L, metadataStore.getLastSyncTimestamp())
    }

    @Test
    fun `cloudSyncEnabled state does not change if persistence fails`() = runBlocking {
        val enabledStore = FakeCloudSyncEnabledStore(initialValue = false, shouldFailPersistence = true)
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            enabledStore = enabledStore,
        )

        holder.setCloudSyncEnabled(true)
        assertEquals(false, holder.cloudSyncEnabled.value)
        assertEquals(false, enabledStore.enabled)
    }

    @Test
    fun `when cloudSyncEnabled is false syncNow is blocked and timestamp is not updated`() = runBlocking {
        val metadataStore = FakeCloudSyncMetadataStore(timestamp = 1000L)
        val enabledStore = FakeCloudSyncEnabledStore(initialValue = false)
        val holder = holder(
            result = syncResult(status = CloudSyncStatus.SUCCESS),
            enabledStore = enabledStore,
            metadataStore = metadataStore,
        )

        holder.syncNow("test-id", com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason.MANUAL_SETTINGS)

        // It should be Idle since it never ran
        assertEquals(ManualCloudSyncUiState.Idle, holder.state.value)
        assertEquals(1000L, holder.lastSyncTimestamp.value)
        assertEquals(1000L, metadataStore.getLastSyncTimestamp())
    }

    private fun holder(
        result: CloudSyncResult,
        activationStore: CloudSyncActivationStore = FakeCloudSyncActivationStore(),
        enabledStore: CloudSyncEnabledStore = FakeCloudSyncEnabledStore(),
        metadataStore: CloudSyncMetadataStore = FakeCloudSyncMetadataStore(),
    ): ManualCloudSyncStateHolder = ManualCloudSyncStateHolder(
        cloudSyncRunCoordinator = CloudSyncRunCoordinator(
            cloudSyncRunner = CloudSyncRunner { result },
            cloudSyncActivationStore = activationStore,
            cloudSyncEnabledStore = enabledStore,
            cloudSyncMetadataStore = metadataStore,
        ),
        cloudSyncEnabledStore = enabledStore,
    )

    private fun syncResult(status: CloudSyncStatus): CloudSyncResult = CloudSyncResult(
        status = status,
        uploaded = 1,
        downloaded = 2,
        updatedLocal = 3,
        markedSynced = 4,
        skippedRemote = 5,
        localErrors = 6,
        remoteErrors = 7,
    )

    private fun CloudSyncResult.toExpectedCounts(): ManualCloudSyncCounts =
        ManualCloudSyncCounts(
            uploaded = uploaded,
            downloaded = downloaded,
            updatedLocal = updatedLocal,
            markedSynced = markedSynced,
            skippedRemote = skippedRemote,
            localErrors = localErrors,
            remoteErrors = remoteErrors,
        )
}

private class FakeCloudSyncActivationStore(
    initialValue: Boolean = false,
) : CloudSyncActivationStore {
    var activated = initialValue

    override fun isActivated(): Boolean = activated

    override fun markActivated() {
        activated = true
    }
}

private class FakeCloudSyncEnabledStore(
    initialValue: Boolean = false,
    val shouldFailPersistence: Boolean = false,
) : CloudSyncEnabledStore {
    var enabled = initialValue

    override fun isEnabled(): Boolean = enabled

    override fun setEnabled(enabled: Boolean): Boolean {
        if (shouldFailPersistence) return false
        this.enabled = enabled
        return true
    }
}

private class FakeCloudSyncMetadataStore(
    private var timestamp: Long? = null,
) : CloudSyncMetadataStore {
    override fun getLastSyncTimestamp(): Long? = timestamp

    override fun updateLastSyncTimestamp(timestamp: Long) {
        this.timestamp = timestamp
    }
}
