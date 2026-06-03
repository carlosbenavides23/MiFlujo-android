package com.carlos.miflujo.ui.report

import com.carlos.miflujo.domain.model.MonthlyCashFlowReport
import com.carlos.miflujo.domain.model.Movement
import java.time.YearMonth

data class ReportUiState(
    val selectedMonth: YearMonth,
    val report: MonthlyCashFlowReport,
    val movements: List<Movement>,
    val isExportingReport: Boolean = false,
)
