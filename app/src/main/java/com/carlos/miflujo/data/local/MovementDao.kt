package com.carlos.miflujo.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.carlos.miflujo.data.model.MovementEntity
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
        ORDER BY date_epoch_day DESC, created_at_epoch_millis DESC, id DESC
        """,
    )
    suspend fun getAllMovements(): List<MovementEntity>

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
        WHERE date_epoch_day BETWEEN :startEpochDay AND :endEpochDay
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
        ORDER BY date_epoch_day DESC, created_at_epoch_millis DESC
        LIMIT :limit
        """,
    )
    fun getRecentMovements(limit: Int): Flow<List<MovementEntity>>
}
