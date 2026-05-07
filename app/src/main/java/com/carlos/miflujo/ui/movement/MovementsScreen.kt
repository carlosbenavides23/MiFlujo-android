package com.carlos.miflujo.ui.movement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private enum class MovementFilter(val label: String) {
    All(label = "Todos"),
    Income(label = "Ingresos"),
    Expense(label = "Egresos"),
}

private data class LocalSampleMovement(
    val id: Long,
    val type: MovementType,
    val amountMinor: Long,
    val currency: Currency,
    val date: LocalDate,
    val category: MovementCategory,
    val subcategory: MovementSubcategory? = null,
    val detail: String? = null,
)

private val movementDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

@Composable
fun MovementsScreen(modifier: Modifier = Modifier) {
    var selectedMonth by remember { mutableStateOf(YearMonth.of(2026, 5)) }
    var selectedFilter by rememberSaveable { mutableStateOf(MovementFilter.All) }
    var selectedMovement by remember { mutableStateOf<LocalSampleMovement?>(null) }
    var movementPendingDelete by remember { mutableStateOf<LocalSampleMovement?>(null) }

    val visibleMovements = remember(selectedMonth, selectedFilter) {
        localSampleMovements
            .filter { YearMonth.from(it.date) == selectedMonth }
            .filter { movement ->
                when (selectedFilter) {
                    MovementFilter.All -> true
                    MovementFilter.Income -> movement.type == MovementType.INCOME
                    MovementFilter.Expense -> movement.type == MovementType.EXPENSE
                }
            }
            .sortedByDescending { it.date }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Movimientos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            MonthSelector(
                selectedMonth = selectedMonth,
                onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
            )
        }

        item {
            MovementFilters(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
            )
        }

        if (visibleMovements.isEmpty()) {
            item {
                EmptyMovementsCard()
            }
        } else {
            items(
                items = visibleMovements,
                key = { it.id },
            ) { movement ->
                MovementRow(
                    movement = movement,
                    onClick = { selectedMovement = movement },
                )
            }
        }
    }

    selectedMovement?.let { movement ->
        MovementDetailDialog(
            movement = movement,
            onDismissRequest = { selectedMovement = null },
            onEditClick = { selectedMovement = null },
            onDeleteClick = { movementPendingDelete = movement },
        )
    }

    movementPendingDelete?.let { movement ->
        DeleteMovementConfirmationDialog(
            movement = movement,
            onDismissRequest = { movementPendingDelete = null },
            onConfirmPlaceholder = {
                movementPendingDelete = null
                selectedMovement = null
            },
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
private fun MovementFilters(
    selectedFilter: MovementFilter,
    onFilterSelected: (MovementFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MovementFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(text = filter.label)
                },
            )
        }
    }
}

@Composable
private fun MovementRow(
    movement: LocalSampleMovement,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = movement.date.format(movementDateFormatter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = movement.formattedSignedAmount(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = movement.amountColor(),
                )
            }
            Text(
                text = movement.detail.orEmpty().ifBlank { "Sin detalle" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = movement.categoryLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyMovementsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sin movimientos para mostrar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Cambie el mes o el filtro para revisar otros movimientos.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MovementDetailDialog(
    movement: LocalSampleMovement,
    onDismissRequest: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Detalle del movimiento")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailLine(label = "Tipo", value = movement.typeLabel())
                DetailLine(label = "Monto", value = movement.formattedSignedAmount())
                DetailLine(label = "Fecha", value = movement.date.format(movementDateFormatter))
                DetailLine(label = "Detalle", value = movement.detail.orEmpty().ifBlank { "Sin detalle" })
                DetailLine(label = "Clasificación", value = movement.categoryLabel())
            }
        },
        confirmButton = {
            TextButton(onClick = onEditClick) {
                Text(text = "Editar")
            }
        },
        dismissButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDeleteClick) {
                    Text(
                        text = "Eliminar",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = "Cerrar")
                }
            }
        },
    )
}

@Composable
private fun DeleteMovementConfirmationDialog(
    movement: LocalSampleMovement,
    onDismissRequest: () -> Unit,
    onConfirmPlaceholder: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Confirmar eliminación")
        },
        text = {
            Text(
                text = "Eliminar ${movement.formattedSignedAmount()} del ${movement.date.format(movementDateFormatter)} se implementará cuando exista persistencia. No se borrará nada en esta etapa.",
            )
        },
        confirmButton = {
            Button(onClick = onConfirmPlaceholder) {
                Text(text = "Entendido")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancelar")
            }
        },
    )
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LocalSampleMovement.amountColor() = when (type) {
    MovementType.INCOME -> MaterialTheme.colorScheme.primary
    MovementType.EXPENSE -> MaterialTheme.colorScheme.error
}

private fun LocalSampleMovement.formattedSignedAmount(): String {
    val sign = when (type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
    }
    return "$sign ${currency.symbol()} ${amountMinor.formatMinorAmount()}"
}

private fun LocalSampleMovement.typeLabel(): String {
    return when (type) {
        MovementType.INCOME -> "Ingreso"
        MovementType.EXPENSE -> "Egreso"
    }
}

private fun LocalSampleMovement.categoryLabel(): String {
    return when (category) {
        MovementCategory.GENERAL_INCOME -> "Ingreso"
        MovementCategory.FIXED_COST -> "Costo fijo${subcategory?.let { " · ${it.label()}" }.orEmpty()}"
        MovementCategory.MAINTENANCE -> "Mantenimiento"
        MovementCategory.OTHER -> "Otros"
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

private fun Long.formatMinorAmount(): String {
    val whole = this / 100L
    val cents = this % 100L
    return "$whole.${cents.toString().padStart(2, '0')}"
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

private val localSampleMovements = listOf(
    LocalSampleMovement(
        id = 1L,
        type = MovementType.INCOME,
        amountMinor = 500000L,
        currency = Currency.CORDOBA,
        date = LocalDate.of(2026, 5, 5),
        category = MovementCategory.GENERAL_INCOME,
        detail = "Venta del día",
    ),
    LocalSampleMovement(
        id = 2L,
        type = MovementType.EXPENSE,
        amountMinor = 180000L,
        currency = Currency.CORDOBA,
        date = LocalDate.of(2026, 5, 5),
        category = MovementCategory.FIXED_COST,
        subcategory = MovementSubcategory.ELECTRICITY,
        detail = "Pago de luz",
    ),
    LocalSampleMovement(
        id = 3L,
        type = MovementType.EXPENSE,
        amountMinor = 10000L,
        currency = Currency.DOLLAR,
        date = LocalDate.of(2026, 5, 4),
        category = MovementCategory.MAINTENANCE,
        detail = "Repuesto comprado",
    ),
    LocalSampleMovement(
        id = 4L,
        type = MovementType.INCOME,
        amountMinor = 25000L,
        currency = Currency.DOLLAR,
        date = LocalDate.of(2026, 5, 2),
        category = MovementCategory.GENERAL_INCOME,
        detail = "Pago recibido",
    ),
    LocalSampleMovement(
        id = 5L,
        type = MovementType.EXPENSE,
        amountMinor = 45000L,
        currency = Currency.CORDOBA,
        date = LocalDate.of(2026, 4, 28),
        category = MovementCategory.OTHER,
        detail = null,
    ),
)
