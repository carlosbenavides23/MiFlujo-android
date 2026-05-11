package com.carlos.miflujo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
fun HomeScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = HomeHorizontalPadding,
                top = 20.dp,
                end = HomeHorizontalPadding,
                bottom = HomeBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        HomeHeader(currentMonth = uiState.currentMonth)
        CurrentMonthDashboardCard(
            cordoba = uiState.report.cordoba,
            dollar = uiState.report.dollar,
        )
        ExpenseSummaryCard(
            cordoba = uiState.report.cordoba,
            dollar = uiState.report.dollar,
        )
        RecentMovementsCard(movements = uiState.recentMovements)
    }
}

@Composable
private fun HomeHeader(currentMonth: YearMonth) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Flujo de efectivo mensual",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = currentMonth.toSpanishMonthLabel(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CurrentMonthDashboardCard(
    cordoba: CurrencySummary,
    dollar: CurrencySummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Flujo neto del mes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            DashboardCurrencySection(
                title = "Córdobas (C$)",
                currencySymbol = "C$",
                summary = cordoba,
            )
            HorizontalDivider()
            DashboardCurrencySection(
                title = "Dólares (US$)",
                currencySymbol = "US$",
                summary = dollar,
            )
        }
    }
}

@Composable
private fun DashboardCurrencySection(
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
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(min = NetAmountMinWidth),
                text = summary.netCashFlowMinor.formatSignedVisibleMoney(currencySymbol),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = summary.netFlowColor(),
                textAlign = TextAlign.End,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InlineMetric(
                modifier = Modifier.weight(1f),
                label = "Ingresos",
                value = "+ ${summary.totalIncomeMinor.formatVisibleMoney(currencySymbol)}",
                valueColor = financePositiveColor(),
            )
            InlineMetric(
                modifier = Modifier.weight(1f),
                label = "Egresos",
                value = "- ${summary.totalExpenseMinor.formatVisibleMoney(currencySymbol)}",
                valueColor = financeNegativeColor(),
                horizontalAlignment = Alignment.End,
            )
        }
    }
}

@Composable
private fun InlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = if (horizontalAlignment == Alignment.End) {
                TextAlign.End
            } else {
                TextAlign.Start
            },
        )
    }
}

@Composable
private fun ExpenseSummaryCard(
    cordoba: CurrencySummary,
    dollar: CurrencySummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "Resumen de egresos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ExpenseBreakdownSection(
                title = "Córdobas (C$)",
                currencySymbol = "C$",
                summary = cordoba,
            )
            HorizontalDivider()
            ExpenseBreakdownSection(
                title = "Dólares (US$)",
                currencySymbol = "US$",
                summary = dollar,
            )
        }
    }
}

@Composable
private fun ExpenseBreakdownSection(
    title: String,
    currencySymbol: String,
    summary: CurrencySummary,
) {
    val breakdown = summary.expenseBreakdown

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Total egresos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(min = SummaryAmountMinWidth),
                text = summary.totalExpenseMinor.formatVisibleMoney(currencySymbol),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryLine(
                label = "Costos fijos",
                value = breakdown.fixedCostMinor.formatVisibleMoney(currencySymbol),
            )
            SummaryLine(
                label = "Mantenimiento",
                value = breakdown.maintenanceMinor.formatVisibleMoney(currencySymbol),
            )
            SummaryLine(
                label = "Otros",
                value = breakdown.otherMinor.formatVisibleMoney(currencySymbol),
            )
        }
        if (breakdown.hasFixedCostDetails()) {
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = "Detalle de costos fijos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FixedCostDetailLine(
                    label = "Agua",
                    value = breakdown.waterMinor,
                    currencySymbol = currencySymbol,
                )
                FixedCostDetailLine(
                    label = "Luz",
                    value = breakdown.electricityMinor,
                    currencySymbol = currencySymbol,
                )
                FixedCostDetailLine(
                    label = "Internet",
                    value = breakdown.internetMinor,
                    currencySymbol = currencySymbol,
                )
            }
        }
    }
}

@Composable
private fun FixedCostDetailLine(
    label: String,
    value: Long,
    currencySymbol: String,
) {
    if (value <= 0L) return

    SummaryLine(
        label = label,
        value = value.formatVisibleMoney(currencySymbol),
        compact = true,
        indented = true,
    )
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    compact: Boolean = false,
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
            style = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier
                .padding(start = 16.dp)
                .widthIn(min = SummaryAmountMinWidth),
            text = value,
            style = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun RecentMovementsCard(movements: List<Movement>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Últimos movimientos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            if (movements.isEmpty()) {
                Text(
                    text = "Aún no hay movimientos registrados.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                movements.forEachIndexed { index, movement ->
                    RecentMovementRow(movement = movement)
                    if (index < movements.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentMovementRow(movement: Movement) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MovementSignBadge(movement = movement)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = movement.detail.orEmpty().ifBlank { "Sin detalle" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${movement.date.formatVisibleDate()} · ${movement.categoryLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            modifier = Modifier.width(116.dp),
            text = movement.formattedSignedAmount(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = movement.amountColor(),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MovementSignBadge(movement: Movement) {
    Surface(
        modifier = Modifier.size(34.dp),
        color = movement.amountColor().copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when (movement.type) {
                    MovementType.INCOME -> "+"
                    MovementType.EXPENSE -> "-"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = movement.amountColor(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Movement.amountColor() = when (type) {
    MovementType.INCOME -> financePositiveColor()
    MovementType.EXPENSE -> financeNegativeColor()
}

@Composable
private fun CurrencySummary.netFlowColor() = if (netCashFlowMinor < 0L) {
    financeNegativeColor()
} else {
    financePositiveColor()
}

private fun Movement.formattedSignedAmount(): String {
    val sign = when (type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
    }
    return "$sign ${amountMinor.formatVisibleMoney(currency.symbol())}"
}

private fun Currency.symbol(): String {
    return when (this) {
        Currency.CORDOBA -> "C$"
        Currency.DOLLAR -> "US$"
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

private fun MovementSubcategory.label(): String {
    return when (this) {
        MovementSubcategory.WATER -> "Agua"
        MovementSubcategory.ELECTRICITY -> "Luz"
        MovementSubcategory.INTERNET -> "Internet"
    }
}

private fun ExpenseBreakdown.hasFixedCostDetails(): Boolean {
    return waterMinor > 0L || electricityMinor > 0L || internetMinor > 0L
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

private val HomeHorizontalPadding = 20.dp
private val HomeBottomPadding = 128.dp
private val NetAmountMinWidth = 128.dp
private val SummaryAmountMinWidth = 112.dp
