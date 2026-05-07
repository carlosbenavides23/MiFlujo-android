package com.carlos.miflujo.ui.report

import com.carlos.miflujo.domain.model.MonthlyCashFlowReport
import java.time.YearMonth

data class ReportUiState(
    val selectedMonth: YearMonth,
    val report: MonthlyCashFlowReport,
)
