package com.carlos.miflujo.data.repository

import com.carlos.miflujo.data.cloud.sync.CloudSyncActivationStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncBackupWorkScheduler
import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncPendingChangesProvider
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncSchedulingMovementRepositoryTest {
    @Test
    fun `active cloud sync marks insert pending and enqueues backup`() = runBlocking {
        val delegate = RecordingMovementRepository()
        val scheduler = RecordingBackupScheduler()
        val repository = repository(
            delegate = delegate,
            scheduler = scheduler,
            enabled = true,
            activated = true,
            hasPendingChanges = true,
        )

        repository.insertMovement(movement())

        assertEquals(SyncStatus.PENDING_UPLOAD, delegate.inserted.single().syncStatus)
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test
    fun `active cloud sync marks update pending and enqueues backup`() = runBlocking {
        val delegate = RecordingMovementRepository()
        val scheduler = RecordingBackupScheduler()
        val repository = repository(
            delegate = delegate,
            scheduler = scheduler,
            enabled = true,
            activated = true,
            hasPendingChanges = true,
        )

        repository.updateMovement(movement(syncStatus = SyncStatus.SYNCED))

        assertEquals(SyncStatus.PENDING_UPLOAD, delegate.updated.single().syncStatus)
        assertEquals(1, scheduler.enqueueCount)
    }

    @Test
    fun `first activation remains manual and does not enqueue backup`() = runBlocking {
        val delegate = RecordingMovementRepository()
        val scheduler = RecordingBackupScheduler()
        val repository = repository(
            delegate = delegate,
            scheduler = scheduler,
            enabled = true,
            activated = false,
            hasPendingChanges = true,
        )

        repository.insertMovement(movement())

        assertEquals(SyncStatus.LOCAL_ONLY, delegate.inserted.single().syncStatus)
        assertEquals(0, scheduler.enqueueCount)
    }

    @Test
    fun `disabled cloud sync keeps local only and does not enqueue backup`() = runBlocking {
        val delegate = RecordingMovementRepository()
        val scheduler = RecordingBackupScheduler()
        val repository = repository(
            delegate = delegate,
            scheduler = scheduler,
            enabled = false,
            activated = true,
            hasPendingChanges = true,
        )

        repository.updateMovement(movement(syncStatus = SyncStatus.SYNCED))

        assertEquals(SyncStatus.LOCAL_ONLY, delegate.updated.single().syncStatus)
        assertEquals(0, scheduler.enqueueCount)
    }

    @Test
    fun `delete enqueues only when Room reports pending work`() = runBlocking {
        val delegate = RecordingMovementRepository()
        val scheduler = RecordingBackupScheduler()
        val repository = repository(
            delegate = delegate,
            scheduler = scheduler,
            enabled = true,
            activated = true,
            hasPendingChanges = false,
        )

        repository.deleteMovement(movement(syncStatus = SyncStatus.SYNCED))

        assertEquals(1, delegate.deleted.size)
        assertEquals(0, scheduler.enqueueCount)
    }

    private fun repository(
        delegate: RecordingMovementRepository,
        scheduler: RecordingBackupScheduler,
        enabled: Boolean,
        activated: Boolean,
        hasPendingChanges: Boolean,
    ): CloudSyncSchedulingMovementRepository = CloudSyncSchedulingMovementRepository(
        delegate = delegate,
        cloudSyncEnabledStore = FixedEnabledStore(enabled),
        cloudSyncActivationStore = FixedActivationStore(activated),
        pendingChangesProvider = CloudSyncPendingChangesProvider { hasPendingChanges },
        backupWorkScheduler = scheduler,
    )

    private fun movement(
        syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    ): Movement = Movement(
        id = 1L,
        uuid = "11111111-1111-4111-8111-111111111111",
        type = MovementType.INCOME,
        amountMinor = 100L,
        currency = Currency.CORDOBA,
        date = LocalDate.of(2026, 6, 13),
        category = MovementCategory.GENERAL_INCOME,
        createdAt = LocalDateTime.of(2026, 6, 13, 10, 0),
        updatedAt = LocalDateTime.of(2026, 6, 13, 10, 0),
        syncStatus = syncStatus,
    )

    private class FixedEnabledStore(
        private val enabled: Boolean,
    ) : CloudSyncEnabledStore {
        override fun isEnabled(): Boolean = enabled

        override fun setEnabled(enabled: Boolean): Boolean = false
    }

    private class FixedActivationStore(
        private val activated: Boolean,
    ) : CloudSyncActivationStore {
        override fun isActivated(): Boolean = activated

        override fun markActivated() = Unit
    }

    private class RecordingBackupScheduler : CloudSyncBackupWorkScheduler {
        var enqueueCount = 0

        override fun enqueueBackup() {
            enqueueCount += 1
        }
    }

    private class RecordingMovementRepository : MovementRepository {
        val inserted = mutableListOf<Movement>()
        val updated = mutableListOf<Movement>()
        val deleted = mutableListOf<Movement>()

        override suspend fun insertMovement(movement: Movement): Long {
            inserted += movement
            return 1L
        }

        override suspend fun updateMovement(movement: Movement) {
            updated += movement
        }

        override suspend fun deleteMovement(movement: Movement) {
            deleted += movement
        }

        override suspend fun getAllMovements(): List<Movement> = emptyList()

        override suspend fun replaceAllMovements(movements: List<Movement>) = Unit

        override fun getMovementsByDateRange(
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<Movement>> = flowOf(emptyList())

        override fun getRecentMovements(limit: Int): Flow<List<Movement>> =
            flowOf(emptyList())
    }
}
