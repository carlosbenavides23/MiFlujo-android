package com.carlos.miflujo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.usecase.CalculateMonthlyCashFlowReportUseCase
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(
    movementRepository: MovementRepository,
    private val calculateMonthlyCashFlowReport: CalculateMonthlyCashFlowReportUseCase,
) : ViewModel() {
    private val currentMonth = YearMonth.now()

    val uiState: StateFlow<HomeUiState> = combine(
        movementRepository.getMovementsByDateRange(
            startDate = currentMonth.atDay(1),
            endDate = currentMonth.atEndOfMonth(),
        ),
        movementRepository.getRecentMovements(RECENT_MOVEMENT_LIMIT),
    ) { monthMovements, recentMovements ->
        currentMonth.toUiState(
            monthMovements = monthMovements,
            recentMovements = recentMovements,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = currentMonth.toUiState(
            monthMovements = emptyList(),
            recentMovements = emptyList(),
        ),
    )

    private fun YearMonth.toUiState(
        monthMovements: List<Movement>,
        recentMovements: List<Movement>,
    ): HomeUiState {
        return HomeUiState(
            currentMonth = this,
            report = calculateMonthlyCashFlowReport(
                movements = monthMovements,
                month = this,
            ),
            recentMovements = recentMovements,
        )
    }

    private companion object {
        const val RECENT_MOVEMENT_LIMIT = 3
    }
}

class HomeViewModelFactory(
    private val movementRepository: MovementRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                movementRepository = movementRepository,
                calculateMonthlyCashFlowReport = CalculateMonthlyCashFlowReportUseCase(),
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
