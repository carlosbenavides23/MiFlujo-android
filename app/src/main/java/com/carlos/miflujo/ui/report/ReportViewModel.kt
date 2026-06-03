package com.carlos.miflujo.ui.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.usecase.CalculateMonthlyCashFlowReportUseCase
import com.carlos.miflujo.ui.report.export.ReportPdfExporter
import java.time.YearMonth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val isExportingReport = MutableStateFlow(false)
    private val exportFeedback = MutableSharedFlow<ReportExportFeedback>(extraBufferCapacity = 1)
    private var exportJob: Job? = null

    val uiState: StateFlow<ReportUiState> = combine(
        selectedMonth,
        monthMovements,
        isExportingReport,
    ) { month, movements, isExporting ->
        month.toUiState(
            movements = movements,
            isExportingReport = isExporting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = selectedMonth.value.toUiState(
            movements = emptyList(),
            isExportingReport = false,
        ),
    )
    val exportFeedbackEvents: SharedFlow<ReportExportFeedback> = exportFeedback.asSharedFlow()

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

    fun shareReport(
        context: Context,
        uiState: ReportUiState,
    ) {
        if (exportJob?.isActive == true || isExportingReport.value) return

        val reportSnapshot = uiState.copy(isExportingReport = false)
        isExportingReport.value = true
        exportJob = viewModelScope.launch {
            try {
                ReportPdfExporter.shareMonthlyReport(
                    context = context,
                    uiState = reportSnapshot,
                )
            } catch (exception: Exception) {
                exportFeedback.tryEmit(ReportExportFeedback.ExportFailed)
            } finally {
                isExportingReport.value = false
            }
        }
    }

    private fun YearMonth.toUiState(
        movements: List<Movement>,
        isExportingReport: Boolean,
    ): ReportUiState {
        return ReportUiState(
            selectedMonth = this,
            report = calculateMonthlyCashFlowReport(
                movements = movements,
                month = this,
            ),
            movements = movements,
            isExportingReport = isExportingReport,
        )
    }
}

sealed class ReportExportFeedback(
    val message: String,
) {
    data object ExportFailed : ReportExportFeedback("No se pudo generar el reporte.")
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
