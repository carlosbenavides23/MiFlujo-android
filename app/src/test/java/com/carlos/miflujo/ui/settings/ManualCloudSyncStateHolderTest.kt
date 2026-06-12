package com.carlos.miflujo.ui.settings

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
        val holder = holder(CloudSyncResult(status = CloudSyncStatus.SIGNED_OUT))

        holder.syncNow()

        assertEquals(ManualCloudSyncUiState.SignedOut, holder.state.value)
    }

    @Test
    fun `unauthorized result is rendered safely`() = runBlocking {
        val holder = holder(CloudSyncResult(status = CloudSyncStatus.UNAUTHORIZED))

        holder.syncNow()

        assertEquals(ManualCloudSyncUiState.Unauthorized, holder.state.value)
    }

    @Test
    fun `successful sync shows success state and counts`() = runBlocking {
        val result = syncResult(status = CloudSyncStatus.SUCCESS)
        val holder = holder(result)

        holder.syncNow()

        assertEquals(
            ManualCloudSyncUiState.Success(result.toExpectedCounts()),
            holder.state.value,
        )
    }

    @Test
    fun `partial sync shows partial state and relevant counts`() = runBlocking {
        val result = syncResult(status = CloudSyncStatus.PARTIAL)
        val holder = holder(result)

        holder.syncNow()

        assertEquals(
            ManualCloudSyncUiState.Partial(result.toExpectedCounts()),
            holder.state.value,
        )
    }

    @Test
    fun `sync failure shows failure state`() = runBlocking {
        val holder = holder(CloudSyncResult(status = CloudSyncStatus.FAILURE))

        holder.syncNow()

        assertEquals(ManualCloudSyncUiState.Failure, holder.state.value)
    }

    @Test
    fun `repeated taps while running launch only one sync`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var callCount = 0
        val holder = ManualCloudSyncStateHolder(
            CloudSyncRunner {
                callCount += 1
                started.complete(Unit)
                release.await()
                CloudSyncResult(status = CloudSyncStatus.SUCCESS)
            },
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

    private fun holder(result: CloudSyncResult): ManualCloudSyncStateHolder =
        ManualCloudSyncStateHolder(CloudSyncRunner { result })

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
