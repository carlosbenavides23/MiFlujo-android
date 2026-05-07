package com.carlos.miflujo.domain.model

data class CurrencySummary(
    val currency: Currency,
    val totalIncomeMinor: Long,
    val totalExpenseMinor: Long,
    val expenseBreakdown: ExpenseBreakdown
) {
    val netCashFlowMinor: Long
        get() = totalIncomeMinor - totalExpenseMinor
}
