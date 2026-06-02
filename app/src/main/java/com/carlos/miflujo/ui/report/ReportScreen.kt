package com.carlos.miflujo.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.CurrencySummary
import com.carlos.miflujo.domain.model.ExpenseBreakdown
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.ui.formatVisibleDate
import com.carlos.miflujo.ui.formatSignedVisibleMoney
import com.carlos.miflujo.ui.formatVisibleMoney
import com.carlos.miflujo.ui.theme.financeNegativeColor
import com.carlos.miflujo.ui.theme.financePositiveColor
import java.time.YearMonth

@Composable
fun ReportScreen(
    uiState: ReportUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 144.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Reporte",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MonthSelector(
            selectedMonth = uiState.selectedMonth,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )
        MonthlyFlowStatementCard(
            cordoba = uiState.report.cordoba,
            dollar = uiState.report.dollar,
        )
        MonthlyActivityCard(movements = uiState.movements)
        ExpenseDetailCard(
            cordoba = uiState.report.cordoba,
            dollar = uiState.report.dollar,
        )
        MonthlyMovementsCard(movements = uiState.movements)
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
private fun MonthlyFlowStatementCard(
    cordoba: CurrencySummary,
    dollar: CurrencySummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Estado del flujo mensual",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (cordoba.isZero() && dollar.isZero()) {
                    Text(
                        text = "Sin movimientos registrados para este mes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CurrencyStatementSection(
                title = "Córdobas",
                currencySymbol = "C$",
                summary = cordoba,
            )
            HorizontalDivider()
            CurrencyStatementSection(
                title = "Dólares",
                currencySymbol = "US$",
                summary = dollar,
            )
        }
    }
}

@Composable
private fun CurrencyStatementSection(
    title: String,
    currencySymbol: String,
    summary: CurrencySummary,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary.statusLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(min = ReportNetAmountMinWidth),
                text = summary.netCashFlowMinor.formatSignedVisibleMoney(currencySymbol),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = summary.netFlowColor(),
                textAlign = TextAlign.End,
            )
        }
        StatementLine(
            label = "Ingresos",
            value = "+ ${summary.totalIncomeMinor.formatVisibleMoney(currencySymbol)}",
            valueColor = financePositiveColor(),
        )
        StatementLine(
            label = "Egresos",
            value = "- ${summary.totalExpenseMinor.formatVisibleMoney(currencySymbol)}",
            valueColor = financeNegativeColor(),
        )
    }
}

@Composable
private fun ExpenseDetailCard(
    cordoba: CurrencySummary,
    dollar: CurrencySummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Detalle de egresos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            ExpenseDetailCurrencySection(
                title = "Córdobas",
                currencySymbol = "C$",
                summary = cordoba,
            )
            HorizontalDivider()
            ExpenseDetailCurrencySection(
                title = "Dólares",
                currencySymbol = "US$",
                summary = dollar,
            )
        }
    }
}

@Composable
private fun ExpenseDetailCurrencySection(
    title: String,
    currencySymbol: String,
    summary: CurrencySummary,
) {
    val breakdown = summary.expenseBreakdown

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(min = ReportAmountMinWidth),
                text = summary.totalExpenseMinor.formatVisibleMoney(currencySymbol),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = financeNegativeColor(),
                textAlign = TextAlign.End,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BreakdownLine(
                label = "Costos fijos",
                value = breakdown.fixedCostMinor.formatVisibleMoney(currencySymbol),
            )
            BreakdownLine(
                label = "Mantenimiento",
                value = breakdown.maintenanceMinor.formatVisibleMoney(currencySymbol),
            )
            BreakdownLine(
                label = "Otros",
                value = breakdown.otherMinor.formatVisibleMoney(currencySymbol),
            )
        }
        HorizontalDivider()
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Costos fijos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BreakdownLine(
                label = "Agua",
                value = breakdown.waterMinor.formatVisibleMoney(currencySymbol),
                indented = true,
            )
            BreakdownLine(
                label = "Luz",
                value = breakdown.electricityMinor.formatVisibleMoney(currencySymbol),
                indented = true,
            )
            BreakdownLine(
                label = "Internet",
                value = breakdown.internetMinor.formatVisibleMoney(currencySymbol),
                indented = true,
            )
        }
    }
}

@Composable
private fun MonthlyActivityCard(
    movements: List<Movement>,
) {
    val totalMovements = movements.size
    val incomeMovements = movements.count { it.type == MovementType.INCOME }
    val expenseMovements = movements.count { it.type == MovementType.EXPENSE }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Actividad del mes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = totalMovements.movementCountLabel(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${incomeMovements.incomeCountLabel()} · ${expenseMovements.expenseCountLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthlyMovementsCard(
    movements: List<Movement>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Movimientos del mes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (movements.isEmpty()) {
                Text(
                    text = "Sin movimientos registrados para este mes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    movements.forEachIndexed { index, movement ->
                        if (index > 0) {
                            HorizontalDivider()
                        }
                        MonthlyMovementRow(movement = movement)
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyMovementRow(
    movement: Movement,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = movement.date.formatVisibleDate(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = movement.classificationLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(min = ReportAmountMinWidth),
                text = movement.formattedSignedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = movement.amountColor(),
                textAlign = TextAlign.End,
            )
        }
        Text(
            text = movement.detail.orEmpty().ifBlank { "Sin detalle" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatementLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier
                .padding(start = 16.dp)
                .widthIn(min = ReportAmountMinWidth),
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun BreakdownLine(
    label: String,
    value: String,
    indented: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (indented) 12.dp else 0.dp),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier
                .padding(start = 16.dp)
                .widthIn(min = ReportAmountMinWidth),
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun CurrencySummary.netFlowColor() = if (netCashFlowMinor < 0L) {
    financeNegativeColor()
} else {
    financePositiveColor()
}

private fun CurrencySummary.statusLabel(): String {
    return when {
        netCashFlowMinor > 0L -> "Flujo positivo"
        netCashFlowMinor < 0L -> "Flujo negativo"
        else -> "Sin movimiento neto"
    }
}

private fun CurrencySummary.isZero(): Boolean {
    return totalIncomeMinor == 0L &&
        totalExpenseMinor == 0L &&
        expenseBreakdown.isZero()
}

private fun ExpenseBreakdown.isZero(): Boolean {
    return fixedCostMinor == 0L &&
        maintenanceMinor == 0L &&
        otherMinor == 0L &&
        waterMinor == 0L &&
        electricityMinor == 0L &&
        internetMinor == 0L
}

private fun Int.movementCountLabel(): String {
    return "$this ${if (this == 1) "movimiento" else "movimientos"}"
}

private fun Int.incomeCountLabel(): String {
    return "$this ${if (this == 1) "ingreso" else "ingresos"}"
}

private fun Int.expenseCountLabel(): String {
    return "$this ${if (this == 1) "egreso" else "egresos"}"
}

@Composable
private fun Movement.amountColor() = when (type) {
    MovementType.INCOME -> financePositiveColor()
    MovementType.EXPENSE -> financeNegativeColor()
}

private fun Movement.formattedSignedAmount(): String {
    return signedAmountMinor().formatSignedVisibleMoney(currency.symbol())
}

private fun Movement.signedAmountMinor(): Long {
    return when (type) {
        MovementType.INCOME -> amountMinor
        MovementType.EXPENSE -> -amountMinor
    }
}

private fun Movement.typeLabel(): String {
    return when (type) {
        MovementType.INCOME -> "Ingreso"
        MovementType.EXPENSE -> "Egreso"
    }
}

private fun Movement.categoryLabel(): String {
    return when (category) {
        MovementCategory.GENERAL_INCOME -> "Ingreso"
        MovementCategory.FIXED_COST -> "Costo fijo${subcategory?.let { " · ${it.label()}" }.orEmpty()}"
        MovementCategory.MAINTENANCE -> "Mantenimiento"
        MovementCategory.OTHER -> "Otros"
    }
}

private fun Movement.classificationLabel(): String {
    return when (type) {
        MovementType.INCOME -> typeLabel()
        MovementType.EXPENSE -> "${typeLabel()} · ${categoryLabel()}"
    }
}

private fun Currency.symbol(): String {
    return when (this) {
        Currency.CORDOBA -> "C$"
        Currency.DOLLAR -> "US$"
    }
}

private fun MovementSubcategory.label(): String {
    return when (this) {
        MovementSubcategory.WATER -> "Agua"
        MovementSubcategory.ELECTRICITY -> "Luz"
        MovementSubcategory.INTERNET -> "Internet"
    }
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

private val ReportAmountMinWidth = 112.dp
private val ReportNetAmountMinWidth = 128.dp
