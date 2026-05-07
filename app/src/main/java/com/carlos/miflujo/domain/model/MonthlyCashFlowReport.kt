package com.carlos.miflujo.domain.model

import java.time.YearMonth

data class MonthlyCashFlowReport(
    val month: YearMonth,
    val cordoba: CurrencySummary,
    val dollar: CurrencySummary
)
