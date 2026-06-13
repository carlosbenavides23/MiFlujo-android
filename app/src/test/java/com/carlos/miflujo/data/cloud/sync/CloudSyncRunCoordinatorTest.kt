package com.carlos.miflujo.data.cloud.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncRunCoordinatorTest {
    @Test
    fun `shared guard prevents overlapping sync execution`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var callCount = 0
        val coordinator = coordinator(
            runner = CloudSyncRunner {
                callCount += 1
                started.complete(Unit)
                release.await()
                CloudSyncResult(CloudSyncStatus.SUCCESS)
            },
        )

        val firstRun = async {
            coordinator.runCloudSync("first", CloudSyncTriggerReason.MANUAL_SETTINGS)
        }
        started.await()

        val secondOutcome = coordinator.runCloudSync(
            "second",
            CloudSyncTriggerReason.WORK_MANAGER_BACKUP,
        )

        assertEquals(CloudSyncRunOutcome.SkippedAlreadyRunning, secondOutcome)
        assertEquals(1, callCount)

        release.complete(Unit)
        assertEquals(
            CloudSyncRunOutcome.Completed(CloudSyncResult(CloudSyncStatus.SUCCESS)),
            firstRun.await(),
        )
        assertEquals(false, coordinator.isRunning)
    }

    @Test
    fun `successful run updates shared activation and timestamp state`() = runBlocking {
        val activationStore = TestActivationStore()
        val metadataStore = TestMetadataStore()
        val coordinator = coordinator(
            runner = CloudSyncRunner {
                CloudSyncResult(CloudSyncStatus.PARTIAL)
            },
            activationStore = activationStore,
            metadataStore = metadataStore,
            currentTimeMillis = { 1234L },
        )

        coordinator.runCloudSync("request", CloudSyncTriggerReason.MANUAL_SETTINGS)

        assertEquals(true, activationStore.activated)
        assertEquals(true, coordinator.cloudSyncActivated.value)
        assertEquals(1234L, metadataStore.timestamp)
        assertEquals(1234L, coordinator.lastSyncTimestamp.value)
    }

    private fun coordinator(
        runner: CloudSyncRunner,
        activationStore: TestActivationStore = TestActivationStore(),
        metadataStore: TestMetadataStore = TestMetadataStore(),
        currentTimeMillis: () -> Long = { 1L },
    ): CloudSyncRunCoordinator = CloudSyncRunCoordinator(
        cloudSyncRunner = runner,
        cloudSyncActivationStore = activationStore,
        cloudSyncEnabledStore = TestEnabledStore(),
        cloudSyncMetadataStore = metadataStore,
        currentTimeMillis = currentTimeMillis,
    )

    private class TestActivationStore : CloudSyncActivationStore {
        var activated = false

        override fun isActivated(): Boolean = activated

        override fun markActivated() {
            activated = true
        }
    }

    private class TestEnabledStore : CloudSyncEnabledStore {
        override fun isEnabled(): Boolean = true

        override fun setEnabled(enabled: Boolean): Boolean = true
    }

    private class TestMetadataStore : CloudSyncMetadataStore {
        var timestamp: Long? = null

        override fun getLastSyncTimestamp(): Long? = timestamp

        override fun updateLastSyncTimestamp(timestamp: Long) {
            this.timestamp = timestamp
        }
    }
}
