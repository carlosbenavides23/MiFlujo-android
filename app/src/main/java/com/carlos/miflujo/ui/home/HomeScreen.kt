package com.carlos.miflujo.ui.home

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.CurrencySummary
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Flujo de efectivo mensual",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Inicio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        CurrentMonthCard(
            currentMonth = uiState.currentMonth,
            cordoba = uiState.report.cordoba,
            dollar = uiState.report.dollar,
        )
        RecentMovementsCard(movements = uiState.recentMovements)
    }
}

@Composable
private fun CurrentMonthCard(
    currentMonth: YearMonth,
    cordoba: CurrencySummary,
    dollar: CurrencySummary,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Mes actual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = currentMonth.toSpanishMonthLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CurrencySummarySection(
                title = "Córdobas",
                currencySymbol = "C$",
                summary = cordoba,
            )
            CurrencySummarySection(
                title = "Dólares",
                currencySymbol = "US$",
                summary = dollar,
            )
        }
    }
}

@Composable
private fun CurrencySummarySection(
    title: String,
    currencySymbol: String,
    summary: CurrencySummary,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        SummaryLine(
            label = "Ingresos",
            value = "+ $currencySymbol ${summary.totalIncomeMinor.formatMinorAmount()}",
            valueColor = MaterialTheme.colorScheme.primary,
        )
        SummaryLine(
            label = "Egresos",
            value = "- $currencySymbol ${summary.totalExpenseMinor.formatMinorAmount()}",
            valueColor = MaterialTheme.colorScheme.error,
        )
        SummaryLine(
            label = "Flujo neto",
            value = summary.netCashFlowMinor.formatSignedAmount(currencySymbol),
            valueColor = if (summary.netCashFlowMinor < 0L) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
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
                movements.forEach { movement ->
                    RecentMovementRow(movement = movement)
                }
            }
        }
    }
}

@Composable
private fun RecentMovementRow(movement: Movement) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = movement.detail.orEmpty().ifBlank { "Sin detalle" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${movement.date.format(visibleDateFormatter)} · ${movement.categoryLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            modifier = Modifier.padding(start = 12.dp),
            text = movement.formattedSignedAmount(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = movement.amountColor(),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Movement.amountColor() = when (type) {
    MovementType.INCOME -> MaterialTheme.colorScheme.primary
    MovementType.EXPENSE -> MaterialTheme.colorScheme.error
}

private fun Movement.formattedSignedAmount(): String {
    val sign = when (type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
    }
    return "$sign ${currency.symbol()} ${amountMinor.formatMinorAmount()}"
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

private val visibleDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
