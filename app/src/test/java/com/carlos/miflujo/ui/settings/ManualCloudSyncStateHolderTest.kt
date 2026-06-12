package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.sync.CloudSyncActivationStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncResult
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunner
import com.carlos.miflujo.data.cloud.sync.CloudSyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualCloudSyncStateHolderTest {
    @Test
    fun `signed out result is rendered safely`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.SIGNED_OUT),
            activationStore = activationStore,
        )

        holder.syncNow()

        assertEquals(ManualCloudSyncUiState.SignedOut, holder.state.value)
        assertEquals(false, activationStore.activated)
    }

    @Test
    fun `unauthorized result is rendered safely`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.UNAUTHORIZED),
            activationStore = activationStore,
        )

        holder.syncNow()

        assertEquals(ManualCloudSyncUiState.Unauthorized, holder.state.value)
        assertEquals(false, activationStore.activated)
    }

    @Test
    fun `successful sync shows success state and counts`() = runBlocking {
        val result = syncResult(status = CloudSyncStatus.SUCCESS)
        val activationStore = FakeCloudSyncActivationStore()
        val holder = holder(result, activationStore)

        holder.syncNow()

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
        val holder = holder(result, activationStore)

        holder.syncNow()

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
        )

        holder.syncNow()

        assertEquals(ManualCloudSyncUiState.Failure, holder.state.value)
        assertEquals(false, activationStore.activated)
    }

    @Test
    fun `activation remains visible when state holder is recreated`() = runBlocking {
        val activationStore = FakeCloudSyncActivationStore()
        holder(
            result = CloudSyncResult(status = CloudSyncStatus.SUCCESS),
            activationStore = activationStore,
        ).syncNow()

        val recreatedHolder = holder(
            result = CloudSyncResult(status = CloudSyncStatus.FAILURE),
            activationStore = activationStore,
        )

        assertEquals(true, recreatedHolder.cloudSyncActivated.value)
    }

    @Test
    fun `repeated taps while running launch only one sync`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var callCount = 0
        val holder = ManualCloudSyncStateHolder(
            cloudSyncRunner = CloudSyncRunner {
                callCount += 1
                started.complete(Unit)
                release.await()
                CloudSyncResult(status = CloudSyncStatus.SUCCESS)
            },
            cloudSyncActivationStore = FakeCloudSyncActivationStore(),
            cloudSyncEnabledStore = FakeCloudSyncEnabledStore(),
        )

        val firstSync = async { holder.syncNow() }
        started.await()
        assertEquals(ManualCloudSyncUiState.Running, holder.state.value)

        holder.syncNow()

        assertEquals(1, callCount)
        assertEquals(ManualCloudSyncUiState.Running, holder.state.value)
        release.complete(Unit)
        firstSync.await()
        assertEquals(1, callCount)
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

    private fun holder(
        result: CloudSyncResult,
        activationStore: CloudSyncActivationStore = FakeCloudSyncActivationStore(),
        enabledStore: CloudSyncEnabledStore = FakeCloudSyncEnabledStore(),
    ): ManualCloudSyncStateHolder = ManualCloudSyncStateHolder(
        cloudSyncRunner = CloudSyncRunner { result },
        cloudSyncActivationStore = activationStore,
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

    override fun setEnabled(value: Boolean): Boolean {
        if (shouldFailPersistence) return false
        enabled = value
        return true
    }
}
