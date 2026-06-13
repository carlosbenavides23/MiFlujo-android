package com.carlos.miflujo.data.cloud.sync

import com.carlos.miflujo.domain.model.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncPendingChangesProviderTest {
    @Test
    fun `returns false when all local records are synced`() = runBlocking {
        assertFalse(providerFor(SyncStatus.SYNCED).hasPendingLocalChanges())
    }

    @Test
    fun `returns true when a local record is pending upload`() = runBlocking {
        assertTrue(
            providerFor(
                SyncStatus.SYNCED,
                SyncStatus.PENDING_UPLOAD,
            ).hasPendingLocalChanges(),
        )
    }

    @Test
    fun `returns true when a local record is pending delete`() = runBlocking {
        assertTrue(
            providerFor(
                SyncStatus.SYNCED,
                SyncStatus.PENDING_DELETE,
            ).hasPendingLocalChanges(),
        )
    }

    @Test
    fun `returns true when a local record has retryable sync error`() = runBlocking {
        assertTrue(
            providerFor(
                SyncStatus.SYNCED,
                SyncStatus.SYNC_ERROR,
            ).hasPendingLocalChanges(),
        )
    }

    @Test
    fun `returns false when there are no local records`() = runBlocking {
        assertFalse(providerFor().hasPendingLocalChanges())
    }

    @Test
    fun `local only records are not queued pending work`() = runBlocking {
        assertFalse(providerFor(SyncStatus.LOCAL_ONLY).hasPendingLocalChanges())
    }

    private fun providerFor(
        vararg storedStatuses: SyncStatus,
    ): CloudSyncPendingChangesProvider = RoomCloudSyncPendingChangesProvider {
        pendingUploadStatus,
        pendingDeleteStatus,
        syncErrorStatus,
        ->
        val pendingStatuses = setOf(
            pendingUploadStatus,
            pendingDeleteStatus,
            syncErrorStatus,
        )
        storedStatuses.any(pendingStatuses::contains)
    }
}
