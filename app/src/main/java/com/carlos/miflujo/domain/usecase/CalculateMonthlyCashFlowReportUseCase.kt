package com.carlos.miflujo.domain.usecase

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.CurrencySummary
import com.carlos.miflujo.domain.model.ExpenseBreakdown
import com.carlos.miflujo.domain.model.MonthlyCashFlowReport
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.YearMonth

class CalculateMonthlyCashFlowReportUseCase {

    operator fun invoke(
        movements: List<Movement>,
        month: YearMonth
    ): MonthlyCashFlowReport {
        val monthlyMovements = movements.filter { YearMonth.from(it.date) == month }

        return MonthlyCashFlowReport(
            month = month,
            cordoba = monthlyMovements.summaryFor(Currency.CORDOBA),
            dollar = monthlyMovements.summaryFor(Currency.DOLLAR)
        )
    }

    private fun List<Movement>.summaryFor(currency: Currency): CurrencySummary {
        val currencyMovements = filter { it.currency == currency }
        val incomeMinor = currencyMovements
            .filter { it.type == MovementType.INCOME }
            .sumOf { it.amountMinor }
        val expenseMovements = currencyMovements.filter { it.type == MovementType.EXPENSE }
        val expenseMinor = expenseMovements.sumOf { it.amountMinor }

        return CurrencySummary(
            currency = currency,
            totalIncomeMinor = incomeMinor,
            totalExpenseMinor = expenseMinor,
            expenseBreakdown = expenseMovements.toExpenseBreakdown()
        )
    }

    private fun List<Movement>.toExpenseBreakdown(): ExpenseBreakdown {
        var fixedCostMinor = 0L
        var maintenanceMinor = 0L
        var otherMinor = 0L
        var waterMinor = 0L
        var electricityMinor = 0L
        var internetMinor = 0L

        for (movement in this) {
            when (movement.category) {
                MovementCategory.FIXED_COST -> {
                    fixedCostMinor += movement.amountMinor

                    when (movement.subcategory) {
                        MovementSubcategory.WATER -> waterMinor += movement.amountMinor
                        MovementSubcategory.ELECTRICITY -> electricityMinor += movement.amountMinor
                        MovementSubcategory.INTERNET -> internetMinor += movement.amountMinor
                        null -> Unit
                    }
                }
                MovementCategory.MAINTENANCE -> maintenanceMinor += movement.amountMinor
                MovementCategory.OTHER -> otherMinor += movement.amountMinor
                MovementCategory.GENERAL_INCOME -> Unit
            }
        }

        return ExpenseBreakdown(
            fixedCostMinor = fixedCostMinor,
            maintenanceMinor = maintenanceMinor,
            otherMinor = otherMinor,
            waterMinor = waterMinor,
            electricityMinor = electricityMinor,
            internetMinor = internetMinor
        )
    }
}
