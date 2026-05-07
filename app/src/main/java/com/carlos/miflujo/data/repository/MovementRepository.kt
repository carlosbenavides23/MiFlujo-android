package com.carlos.miflujo.data.repository

import com.carlos.miflujo.domain.model.Movement
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface MovementRepository {
    suspend fun insertMovement(movement: Movement): Long

    suspend fun updateMovement(movement: Movement)

    suspend fun deleteMovement(movement: Movement)

    fun getMovementsByDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Movement>>

    fun getRecentMovements(limit: Int): Flow<List<Movement>>
}
