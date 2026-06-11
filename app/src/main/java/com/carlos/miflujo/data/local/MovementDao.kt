package com.carlos.miflujo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.carlos.miflujo.data.model.MovementEntity
import com.carlos.miflujo.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: MovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovements(movements: List<MovementEntity>)

    @Update
    suspend fun updateMovement(movement: MovementEntity)

    @Delete
    suspend fun deleteMovement(movement: MovementEntity)

    @Query(
        """
        SELECT * FROM movements
        WHERE id = :localId AND uuid = :uuid
        LIMIT 1
        """,
    )
    suspend fun getMovementByIdAndUuid(
        localId: Long,
        uuid: String,
    ): MovementEntity?

    @Query(
        """
        SELECT * FROM movements
        WHERE deleted_at_epoch_millis IS NULL
        ORDER BY date_epoch_day DESC, created_at_epoch_millis DESC, id DESC
        """,
    )
    suspend fun getAllMovements(): List<MovementEntity>

    @Query(
        """
        SELECT * FROM movements
        ORDER BY date_epoch_day DESC, created_at_epoch_millis DESC, id DESC
        """,
    )
    suspend fun getAllMovementsIncludingDeleted(): List<MovementEntity>

    @Update
    suspend fun updateMovementFromSync(movement: MovementEntity): Int

    @Transaction
    suspend fun deleteMovementPreservingSyncState(
        localId: Long,
        uuid: String,
        deletionEpochMillis: Long,
    ): Boolean {
        val current = getMovementByIdAndUuid(localId, uuid) ?: return false
        if (
            current.syncStatus == SyncStatus.LOCAL_ONLY &&
            current.lastSyncedAt == null
        ) {
            deleteMovement(current)
            return true
        }
        if (current.deletedAt != null) {
            return true
        }

        val tombstoneTime = maxOf(deletionEpochMillis, current.updatedAtEpochMillis)
        return updateMovementFromSync(
            current.copy(
                updatedAtEpochMillis = tombstoneTime,
                syncStatus = SyncStatus.PENDING_DELETE,
                deletedAt = tombstoneTime,
            ),
        ) == 1
    }

    @Transaction
    suspend fun updateMovementFromSyncIfUnchanged(
        expected: MovementEntity,
        replacement: MovementEntity,
    ): Boolean {
        require(expected.id == replacement.id && expected.uuid == replacement.uuid)
        val current = getMovementByIdAndUuid(expected.id, expected.uuid) ?: return false
        if (!current.matchesSyncVersion(expected)) {
            return false
        }
        return updateMovementFromSync(replacement) == 1
    }

    @Transaction
    suspend fun prepareMovementForUploadIfUnchanged(
        expected: MovementEntity,
        pendingStatus: SyncStatus,
    ): MovementEntity? {
        require(
            pendingStatus == SyncStatus.PENDING_UPLOAD ||
                pendingStatus == SyncStatus.PENDING_DELETE,
        )
        val current = getMovementByIdAndUuid(expected.id, expected.uuid) ?: return null
        if (!current.matchesSyncVersion(expected)) {
            return null
        }
        val prepared = current.copy(syncStatus = pendingStatus)
        return if (prepared == current || updateMovementFromSync(prepared) == 1) {
            prepared
        } else {
            null
        }
    }

    @Transaction
    suspend fun markSyncedIfUnchanged(
        expected: MovementEntity,
        lastSyncedAtEpochMillis: Long,
    ): Boolean {
        val current = getMovementByIdAndUuid(expected.id, expected.uuid) ?: return false
        if (!current.matchesSyncVersion(expected)) {
            return false
        }
        return updateMovementFromSync(
            current.copy(
                syncStatus = SyncStatus.SYNCED,
                lastSyncedAt = lastSyncedAtEpochMillis,
            ),
        ) == 1
    }

    @Transaction
    suspend fun markSyncErrorIfUnchanged(expected: MovementEntity): Boolean {
        val current = getMovementByIdAndUuid(expected.id, expected.uuid) ?: return false
        if (!current.matchesSyncVersion(expected)) {
            return false
        }
        return updateMovementFromSync(
            current.copy(syncStatus = SyncStatus.SYNC_ERROR),
        ) == 1
    }

    @Query("DELETE FROM movements")
    suspend fun deleteAllMovements()

    @Transaction
    suspend fun replaceAllMovements(movements: List<MovementEntity>) {
        deleteAllMovements()
        if (movements.isNotEmpty()) {
            insertMovements(movements)
        }
    }

    @Query(
        """
        SELECT * FROM movements
        WHERE deleted_at_epoch_millis IS NULL
            AND date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
        ORDER BY date_epoch_day DESC, created_at_epoch_millis DESC
        """,
    )
    fun getMovementsByDateRange(
        startEpochDay: Long,
        endEpochDay: Long,
    ): Flow<List<MovementEntity>>

    @Query(
        """
        SELECT * FROM movements
        WHERE deleted_at_epoch_millis IS NULL
        ORDER BY date_epoch_day DESC, created_at_epoch_millis DESC
        LIMIT :limit
        """,
    )
    fun getRecentMovements(limit: Int): Flow<List<MovementEntity>>
}

private fun MovementEntity.matchesSyncVersion(expected: MovementEntity): Boolean =
    id == expected.id &&
        uuid == expected.uuid &&
        updatedAtEpochMillis == expected.updatedAtEpochMillis &&
        deletedAt == expected.deletedAt &&
        syncStatus == expected.syncStatus
