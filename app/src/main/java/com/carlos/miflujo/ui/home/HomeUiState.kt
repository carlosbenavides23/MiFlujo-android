package com.carlos.miflujo.ui.home

import com.carlos.miflujo.domain.model.MonthlyCashFlowReport
import com.carlos.miflujo.domain.model.Movement
import java.time.YearMonth

data class HomeUiState(
    val currentMonth: YearMonth,
    val report: MonthlyCashFlowReport,
    val recentMovements: List<Movement> = emptyList(),
)
