package com.carlos.miflujo.ui.movement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MovementViewModel(
    private val movementRepository: MovementRepository,
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedFilter = MutableStateFlow(MovementFilter.All)
    private val monthMovements = MutableStateFlow<List<Movement>>(emptyList())
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    val feedbackMessage: StateFlow<String?> = _feedbackMessage

    val uiState: StateFlow<MovementUiState> = combine(
        selectedMonth,
        selectedFilter,
        monthMovements,
    ) { month, filter, movements ->
        MovementUiState(
            selectedMonth = month,
            selectedFilter = filter,
            movements = movements.filterBy(filter),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MovementUiState(),
    )

    init {
        viewModelScope.launch {
            selectedMonth.collectLatest { month ->
                movementRepository
                    .getMovementsByDateRange(
                        startDate = month.atDay(1),
                        endDate = month.atEndOfMonth(),
                    )
                    .collect { movements ->
                        monthMovements.value = movements
                    }
            }
        }
    }

    fun goToPreviousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun goToNextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun selectFilter(filter: MovementFilter) {
        selectedFilter.value = filter
    }

    fun addMovement(
        input: AddMovementInput,
        onInserted: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                movementRepository.insertMovement(input.toMovement())
                _feedbackMessage.value = "Movimiento guardado"
                onInserted()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _feedbackMessage.value = "No se pudo guardar el movimiento"
            }
        }
    }

    fun deleteMovement(
        movement: Movement,
        onDeleted: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                movementRepository.deleteMovement(movement)
                _feedbackMessage.value = "Movimiento eliminado"
                onDeleted()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _feedbackMessage.value = "No se pudo eliminar el movimiento"
            }
        }
    }

    fun updateMovement(
        movement: Movement,
        input: AddMovementInput,
        onUpdated: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                movementRepository.updateMovement(movement.updatedWith(input))
                _feedbackMessage.value = "Movimiento actualizado"
                onUpdated()
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _feedbackMessage.value = "No se pudo actualizar el movimiento"
            }
        }
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    private fun AddMovementInput.toMovement(): Movement {
        val now = LocalDateTime.now()

        return Movement(
            type = type,
            amountMinor = amountMinor,
            currency = currency,
            date = date,
            category = category,
            subcategory = subcategory,
            detail = detail,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun Movement.updatedWith(input: AddMovementInput): Movement {
        return copy(
            type = input.type,
            amountMinor = input.amountMinor,
            currency = input.currency,
            date = input.date,
            category = input.category,
            subcategory = input.subcategory,
            detail = input.detail,
            updatedAt = LocalDateTime.now(),
        )
    }

    private fun List<Movement>.filterBy(filter: MovementFilter): List<Movement> {
        return when (filter) {
            MovementFilter.All -> this
            MovementFilter.Income -> filter { it.type == MovementType.INCOME }
            MovementFilter.Expense -> filter { it.type == MovementType.EXPENSE }
        }
    }
}

class MovementViewModelFactory(
    private val movementRepository: MovementRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MovementViewModel::class.java)) {
            return MovementViewModel(movementRepository) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
