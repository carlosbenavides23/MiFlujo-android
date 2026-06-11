package com.carlos.miflujo.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.data.model.MovementEntity
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementDaoTest {
    private lateinit var database: MiFlujoDatabase
    private lateinit var movementDao: MovementDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MiFlujoDatabase::class.java,
        ).build()
        movementDao = database.movementDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun visibleQueriesExcludeDeletedMovements() = runBlocking {
        val visibleMovement = movementEntity(
            id = 1L,
            uuid = "3f83ad74-77f1-4625-a525-66d860a86e76",
            deletedAt = null,
        )
        val deletedMovement = movementEntity(
            id = 2L,
            uuid = "bfa01442-30ed-4d90-83ab-cee48d00dfe3",
            deletedAt = 1_780_734_600_000L,
        )
        movementDao.insertMovements(listOf(visibleMovement, deletedMovement))

        assertEquals(listOf(visibleMovement), movementDao.getAllMovements())
        assertEquals(
            listOf(visibleMovement),
            movementDao.getMovementsByDateRange(
                startEpochDay = LocalDate.of(2026, 6, 1).toEpochDay(),
                endEpochDay = LocalDate.of(2026, 6, 30).toEpochDay(),
            ).first(),
        )
        assertEquals(
            listOf(visibleMovement),
            movementDao.getRecentMovements(limit = 10).first(),
        )
    }

    @Test
    fun syncReadIncludesTombstonesAndMetadataUpdatesDoNotChangeMovementData() = runBlocking {
        val visibleMovement = movementEntity(
            id = 1L,
            uuid = "3f83ad74-77f1-4625-a525-66d860a86e76",
            deletedAt = null,
        )
        val tombstone = movementEntity(
            id = 2L,
            uuid = "bfa01442-30ed-4d90-83ab-cee48d00dfe3",
            deletedAt = 1_780_734_600_000L,
        )
        movementDao.insertMovements(listOf(visibleMovement, tombstone))

        assertEquals(
            listOf(tombstone, visibleMovement),
            movementDao.getAllMovementsIncludingDeleted(),
        )

        val syncTime = 1_781_000_000_000L
        assertEquals(
            1,
            movementDao.updateSyncMetadata(
                localId = visibleMovement.id,
                uuid = visibleMovement.uuid,
                syncStatus = SyncStatus.SYNCED,
                lastSyncedAtEpochMillis = syncTime,
            ),
        )
        val synced = movementDao.getAllMovementsIncludingDeleted()
            .single { it.id == visibleMovement.id }
        assertEquals(
            visibleMovement.copy(
                syncStatus = SyncStatus.SYNCED,
                lastSyncedAt = syncTime,
            ),
            synced,
        )

        assertEquals(
            1,
            movementDao.updateSyncStatus(
                localId = visibleMovement.id,
                uuid = visibleMovement.uuid,
                syncStatus = SyncStatus.SYNC_ERROR,
            ),
        )
        val syncError = movementDao.getAllMovementsIncludingDeleted()
            .single { it.id == visibleMovement.id }
        assertEquals(
            synced.copy(syncStatus = SyncStatus.SYNC_ERROR),
            syncError,
        )
    }

    @Test
    fun syncUpdatePreservesProvidedRoomIdAndCreatedAt() = runBlocking {
        val original = movementEntity(
            id = 7L,
            uuid = "3f83ad74-77f1-4625-a525-66d860a86e76",
            deletedAt = null,
        )
        movementDao.insertMovement(original)
        val remoteUpdate = original.copy(
            amountMinor = 250_00L,
            detail = "Remote update",
            updatedAtEpochMillis = original.updatedAtEpochMillis + 10_000L,
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = original.updatedAtEpochMillis + 20_000L,
        )

        assertEquals(1, movementDao.updateMovementFromSync(remoteUpdate))

        val updated = movementDao.getAllMovementsIncludingDeleted().single()
        assertEquals(original.id, updated.id)
        assertEquals(original.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertEquals(remoteUpdate, updated)
    }

    private fun movementEntity(
        id: Long,
        uuid: String,
        deletedAt: Long?,
    ): MovementEntity = MovementEntity(
        id = id,
        uuid = uuid,
        type = "INCOME",
        amountMinor = 100_00L,
        currency = "CORDOBA",
        dateEpochDay = LocalDate.of(2026, 6, 10).toEpochDay(),
        category = "GENERAL_INCOME",
        createdAtEpochMillis = 1_780_734_600_000L + id,
        updatedAtEpochMillis = 1_780_734_600_000L + id,
        deletedAt = deletedAt,
    )
}
