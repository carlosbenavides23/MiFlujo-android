package com.carlos.miflujo.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.usecase.CalculateMonthlyCashFlowReportUseCase
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportViewModel(
    private val movementRepository: MovementRepository,
    private val calculateMonthlyCashFlowReport: CalculateMonthlyCashFlowReportUseCase,
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val monthMovements = MutableStateFlow<List<Movement>>(emptyList())

    val uiState: StateFlow<ReportUiState> = combine(
        selectedMonth,
        monthMovements,
    ) { month, movements ->
        month.toUiState(movements)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = selectedMonth.value.toUiState(emptyList()),
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

    private fun YearMonth.toUiState(movements: List<Movement>): ReportUiState {
        return ReportUiState(
            selectedMonth = this,
            report = calculateMonthlyCashFlowReport(
                movements = movements,
                month = this,
            ),
            movements = movements,
        )
    }
}

class ReportViewModelFactory(
    private val movementRepository: MovementRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            return ReportViewModel(
                movementRepository = movementRepository,
                calculateMonthlyCashFlowReport = CalculateMonthlyCashFlowReportUseCase(),
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
