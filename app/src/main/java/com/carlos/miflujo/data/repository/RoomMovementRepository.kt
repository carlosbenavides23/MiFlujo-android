package com.carlos.miflujo.data.repository

import com.carlos.miflujo.data.local.MovementDao
import com.carlos.miflujo.data.model.toDomain
import com.carlos.miflujo.data.model.toEntity
import com.carlos.miflujo.domain.model.Movement
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMovementRepository(
    private val movementDao: MovementDao,
) : MovementRepository {
    override suspend fun insertMovement(movement: Movement): Long =
        movementDao.insertMovement(movement.toEntity())

    override suspend fun updateMovement(movement: Movement) {
        movementDao.updateMovement(movement.toEntity())
    }

    override suspend fun deleteMovement(movement: Movement) {
        movementDao.deleteMovement(movement.toEntity())
    }

    override suspend fun getAllMovements(): List<Movement> =
        movementDao.getAllMovements().map { it.toDomain() }

    override suspend fun replaceAllMovements(movements: List<Movement>) {
        movementDao.replaceAllMovements(movements.map { it.toEntity() })
    }

    override fun getMovementsByDateRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Movement>> = movementDao
        .getMovementsByDateRange(
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay(),
        )
        .map { movements -> movements.map { it.toDomain() } }

    override fun getRecentMovements(limit: Int): Flow<List<Movement>> = movementDao
        .getRecentMovements(limit)
        .map { movements -> movements.map { it.toDomain() } }
}
