package com.carlos.miflujo.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.CurrencySummary
import com.carlos.miflujo.domain.model.ExpenseBreakdown
import com.carlos.miflujo.domain.model.MonthlyCashFlowReport
import java.time.YearMonth

@Composable
fun ReportScreen(modifier: Modifier = Modifier) {
    var selectedMonth by remember { mutableStateOf(YearMonth.of(2026, 5)) }
    val report = localSampleReports[selectedMonth] ?: selectedMonth.emptySampleReport()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Reporte",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MonthSelector(
            selectedMonth = selectedMonth,
            onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
            onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
        )
        CurrencyReportCard(
            title = "Resumen en C$",
            currencySymbol = "C$",
            summary = report.cordoba,
        )
        CurrencyReportCard(
            title = "Resumen en US$",
            currencySymbol = "US$",
            summary = report.dollar,
        )
    }
}

@Composable
private fun MonthSelector(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onPreviousMonth) {
                Text(text = "Anterior")
            }
            Column {
                Text(
                    text = "Mes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selectedMonth.toSpanishMonthLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            TextButton(onClick = onNextMonth) {
                Text(text = "Siguiente")
            }
        }
    }
}

@Composable
private fun CurrencyReportCard(
    title: String,
    currencySymbol: String,
    summary: CurrencySummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            SummaryLine(
                label = "Total ingresos",
                value = "+ $currencySymbol ${summary.totalIncomeMinor.formatMinorAmount()}",
                valueColor = MaterialTheme.colorScheme.primary,
            )
            SummaryLine(
                label = "Total egresos",
                value = "- $currencySymbol ${summary.totalExpenseMinor.formatMinorAmount()}",
                valueColor = MaterialTheme.colorScheme.error,
            )
            SummaryLine(
                label = "Flujo neto del mes",
                value = summary.netCashFlowMinor.formatSignedAmount(currencySymbol),
                valueColor = if (summary.netCashFlowMinor < 0L) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            ExpenseBreakdownSection(
                currencySymbol = currencySymbol,
                breakdown = summary.expenseBreakdown,
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun ExpenseBreakdownSection(
    currencySymbol: String,
    breakdown: ExpenseBreakdown,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Desglose de egresos",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        BreakdownLine(
            label = "Costos fijos",
            value = "$currencySymbol ${breakdown.fixedCostMinor.formatMinorAmount()}",
        )
        BreakdownLine(
            label = "Mantenimiento",
            value = "$currencySymbol ${breakdown.maintenanceMinor.formatMinorAmount()}",
        )
        BreakdownLine(
            label = "Otros",
            value = "$currencySymbol ${breakdown.otherMinor.formatMinorAmount()}",
        )
        Text(
            text = "Costos fijos",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BreakdownLine(
            label = "Agua",
            value = "$currencySymbol ${breakdown.waterMinor.formatMinorAmount()}",
        )
        BreakdownLine(
            label = "Luz",
            value = "$currencySymbol ${breakdown.electricityMinor.formatMinorAmount()}",
        )
        BreakdownLine(
            label = "Internet",
            value = "$currencySymbol ${breakdown.internetMinor.formatMinorAmount()}",
        )
    }
}

@Composable
private fun BreakdownLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun Long.formatMinorAmount(): String {
    val absoluteAmount = kotlin.math.abs(this)
    val whole = absoluteAmount / 100L
    val cents = absoluteAmount % 100L
    return "$whole.${cents.toString().padStart(2, '0')}"
}

private fun Long.formatSignedAmount(currencySymbol: String): String {
    val sign = if (this < 0L) "-" else "+"
    return "$sign $currencySymbol ${formatMinorAmount()}"
}

private fun YearMonth.toSpanishMonthLabel(): String {
    val month = when (monthValue) {
        1 -> "Enero"
        2 -> "Febrero"
        3 -> "Marzo"
        4 -> "Abril"
        5 -> "Mayo"
        6 -> "Junio"
        7 -> "Julio"
        8 -> "Agosto"
        9 -> "Septiembre"
        10 -> "Octubre"
        11 -> "Noviembre"
        else -> "Diciembre"
    }
    return "$month $year"
}

private fun YearMonth.emptySampleReport(): MonthlyCashFlowReport {
    return MonthlyCashFlowReport(
        month = this,
        cordoba = CurrencySummary(
            currency = Currency.CORDOBA,
            totalIncomeMinor = 0L,
            totalExpenseMinor = 0L,
            expenseBreakdown = ExpenseBreakdown(),
        ),
        dollar = CurrencySummary(
            currency = Currency.DOLLAR,
            totalIncomeMinor = 0L,
            totalExpenseMinor = 0L,
            expenseBreakdown = ExpenseBreakdown(),
        ),
    )
}

private val localSampleReports = mapOf(
    YearMonth.of(2026, 5) to MonthlyCashFlowReport(
        month = YearMonth.of(2026, 5),
        cordoba = CurrencySummary(
            currency = Currency.CORDOBA,
            totalIncomeMinor = 3_500_000L,
            totalExpenseMinor = 2_250_000L,
            expenseBreakdown = ExpenseBreakdown(
                fixedCostMinor = 650_000L,
                maintenanceMinor = 1_100_000L,
                otherMinor = 500_000L,
                waterMinor = 120_000L,
                electricityMinor = 180_000L,
                internetMinor = 350_000L,
            ),
        ),
        dollar = CurrencySummary(
            currency = Currency.DOLLAR,
            totalIncomeMinor = 50_000L,
            totalExpenseMinor = 12_000L,
            expenseBreakdown = ExpenseBreakdown(
                fixedCostMinor = 0L,
                maintenanceMinor = 10_000L,
                otherMinor = 2_000L,
                waterMinor = 0L,
                electricityMinor = 0L,
                internetMinor = 0L,
            ),
        ),
    ),
    YearMonth.of(2026, 4) to MonthlyCashFlowReport(
        month = YearMonth.of(2026, 4),
        cordoba = CurrencySummary(
            currency = Currency.CORDOBA,
            totalIncomeMinor = 1_200_000L,
            totalExpenseMinor = 1_430_000L,
            expenseBreakdown = ExpenseBreakdown(
                fixedCostMinor = 600_000L,
                maintenanceMinor = 400_000L,
                otherMinor = 430_000L,
                waterMinor = 100_000L,
                electricityMinor = 170_000L,
                internetMinor = 330_000L,
            ),
        ),
        dollar = CurrencySummary(
            currency = Currency.DOLLAR,
            totalIncomeMinor = 0L,
            totalExpenseMinor = 8_500L,
            expenseBreakdown = ExpenseBreakdown(
                fixedCostMinor = 0L,
                maintenanceMinor = 8_500L,
                otherMinor = 0L,
                waterMinor = 0L,
                electricityMinor = 0L,
                internetMinor = 0L,
            ),
        ),
    ),
)
