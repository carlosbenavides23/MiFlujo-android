package com.carlos.miflujo.domain.usecase

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateMonthlyCashFlowReportUseCaseTest {

    private val useCase = CalculateMonthlyCashFlowReportUseCase()
    private val selectedMonth = YearMonth.of(2026, 5)

    @Test
    fun `calculates income expense and net cash flow per currency for selected month`() {
        val movements = listOf(
            income(amountMinor = 500_000L, currency = Currency.CORDOBA),
            expense(amountMinor = 180_000L, currency = Currency.CORDOBA),
            income(amountMinor = 10_000L, currency = Currency.DOLLAR),
            expense(amountMinor = 12_000L, currency = Currency.DOLLAR),
            income(
                amountMinor = 999_999L,
                currency = Currency.CORDOBA,
                date = LocalDate.of(2026, 4, 30),
            ),
            expense(
                amountMinor = 999_999L,
                currency = Currency.DOLLAR,
                date = LocalDate.of(2026, 6, 1),
            ),
        )

        val report = useCase(movements, selectedMonth)

        assertEquals(500_000L, report.cordoba.totalIncomeMinor)
        assertEquals(180_000L, report.cordoba.totalExpenseMinor)
        assertEquals(320_000L, report.cordoba.netCashFlowMinor)
        assertEquals(10_000L, report.dollar.totalIncomeMinor)
        assertEquals(12_000L, report.dollar.totalExpenseMinor)
        assertEquals(-2_000L, report.dollar.netCashFlowMinor)
    }

    @Test
    fun `keeps cordoba and dollar totals separated and never combined`() {
        val movements = listOf(
            income(amountMinor = 100_000L, currency = Currency.CORDOBA),
            income(amountMinor = 20_000L, currency = Currency.DOLLAR),
            expense(amountMinor = 30_000L, currency = Currency.CORDOBA),
            expense(amountMinor = 4_000L, currency = Currency.DOLLAR),
        )

        val report = useCase(movements, selectedMonth)

        assertEquals(Currency.CORDOBA, report.cordoba.currency)
        assertEquals(100_000L, report.cordoba.totalIncomeMinor)
        assertEquals(30_000L, report.cordoba.totalExpenseMinor)
        assertEquals(70_000L, report.cordoba.netCashFlowMinor)
        assertEquals(Currency.DOLLAR, report.dollar.currency)
        assertEquals(20_000L, report.dollar.totalIncomeMinor)
        assertEquals(4_000L, report.dollar.totalExpenseMinor)
        assertEquals(16_000L, report.dollar.netCashFlowMinor)
    }

    @Test
    fun `breaks fixed costs down by water electricity and internet per currency`() {
        val movements = listOf(
            expense(
                amountMinor = 120_000L,
                currency = Currency.CORDOBA,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.WATER,
            ),
            expense(
                amountMinor = 180_000L,
                currency = Currency.CORDOBA,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.ELECTRICITY,
            ),
            expense(
                amountMinor = 350_000L,
                currency = Currency.CORDOBA,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.INTERNET,
            ),
            expense(
                amountMinor = 2_500L,
                currency = Currency.DOLLAR,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.WATER,
            ),
        )

        val report = useCase(movements, selectedMonth)

        assertEquals(650_000L, report.cordoba.expenseBreakdown.fixedCostMinor)
        assertEquals(120_000L, report.cordoba.expenseBreakdown.waterMinor)
        assertEquals(180_000L, report.cordoba.expenseBreakdown.electricityMinor)
        assertEquals(350_000L, report.cordoba.expenseBreakdown.internetMinor)
        assertEquals(2_500L, report.dollar.expenseBreakdown.fixedCostMinor)
        assertEquals(2_500L, report.dollar.expenseBreakdown.waterMinor)
        assertEquals(0L, report.dollar.expenseBreakdown.electricityMinor)
        assertEquals(0L, report.dollar.expenseBreakdown.internetMinor)
    }

    @Test
    fun `breaks expenses down by category and ignores income in expense breakdown`() {
        val movements = listOf(
            income(amountMinor = 900_000L, currency = Currency.CORDOBA),
            expense(
                amountMinor = 100_000L,
                currency = Currency.CORDOBA,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.INTERNET,
            ),
            expense(
                amountMinor = 200_000L,
                currency = Currency.CORDOBA,
                category = MovementCategory.MAINTENANCE,
            ),
            expense(
                amountMinor = 300_000L,
                currency = Currency.CORDOBA,
                category = MovementCategory.OTHER,
            ),
            expense(
                amountMinor = 4_000L,
                currency = Currency.DOLLAR,
                category = MovementCategory.MAINTENANCE,
            ),
        )

        val report = useCase(movements, selectedMonth)

        assertEquals(600_000L, report.cordoba.totalExpenseMinor)
        assertEquals(100_000L, report.cordoba.expenseBreakdown.fixedCostMinor)
        assertEquals(200_000L, report.cordoba.expenseBreakdown.maintenanceMinor)
        assertEquals(300_000L, report.cordoba.expenseBreakdown.otherMinor)
        assertEquals(4_000L, report.dollar.totalExpenseMinor)
        assertEquals(0L, report.dollar.expenseBreakdown.fixedCostMinor)
        assertEquals(4_000L, report.dollar.expenseBreakdown.maintenanceMinor)
        assertEquals(0L, report.dollar.expenseBreakdown.otherMinor)
    }

    @Test
    fun `reports negative net cash flow when expenses exceed income`() {
        val movements = listOf(
            income(amountMinor = 50_000L, currency = Currency.CORDOBA),
            expense(amountMinor = 275_000L, currency = Currency.CORDOBA),
        )

        val report = useCase(movements, selectedMonth)

        assertEquals(-225_000L, report.cordoba.netCashFlowMinor)
    }

    private fun income(
        amountMinor: Long,
        currency: Currency,
        date: LocalDate = LocalDate.of(2026, 5, 5),
    ): Movement {
        return movement(
            type = MovementType.INCOME,
            amountMinor = amountMinor,
            currency = currency,
            date = date,
            category = MovementCategory.GENERAL_INCOME,
        )
    }

    private fun expense(
        amountMinor: Long,
        currency: Currency,
        date: LocalDate = LocalDate.of(2026, 5, 5),
        category: MovementCategory = MovementCategory.OTHER,
        subcategory: MovementSubcategory? = null,
    ): Movement {
        return movement(
            type = MovementType.EXPENSE,
            amountMinor = amountMinor,
            currency = currency,
            date = date,
            category = category,
            subcategory = subcategory,
        )
    }

    private fun movement(
        type: MovementType,
        amountMinor: Long,
        currency: Currency,
        date: LocalDate,
        category: MovementCategory,
        subcategory: MovementSubcategory? = null,
    ): Movement {
        val timestamp = LocalDateTime.of(2026, 5, 5, 9, 0)

        return Movement(
            type = type,
            amountMinor = amountMinor,
            currency = currency,
            date = date,
            category = category,
            subcategory = subcategory,
            detail = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }
}
