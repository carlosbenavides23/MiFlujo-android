package com.carlos.miflujo.ui.movement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.ui.formatVisibleDate
import com.carlos.miflujo.ui.formatVisibleMoney
import com.carlos.miflujo.ui.theme.financeNegativeColor
import com.carlos.miflujo.ui.theme.financePositiveColor
import java.time.YearMonth

@Composable
fun MovementsScreen(
    uiState: MovementUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onFilterSelected: (MovementFilter) -> Unit,
    onEditMovement: (Movement, AddMovementInput, onUpdated: () -> Unit) -> Unit,
    onDeleteMovement: (Movement, onDeleted: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedMovement by remember { mutableStateOf<Movement?>(null) }
    var movementPendingEdit by remember { mutableStateOf<Movement?>(null) }
    var movementPendingDelete by remember { mutableStateOf<Movement?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 144.dp,
        ),
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
                selectedMonth = uiState.selectedMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
        }

        item {
            MovementFilters(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = onFilterSelected,
            )
        }

        if (uiState.movements.isEmpty()) {
            item {
                EmptyMovementsCard()
            }
        } else {
            items(
                items = uiState.movements,
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
            onEditClick = {
                movementPendingEdit = movement
                selectedMovement = null
            },
            onDeleteClick = { movementPendingDelete = movement },
        )
    }

    movementPendingEdit?.let { movement ->
        AddMovementDialog(
            initialMovement = movement,
            onDismissRequest = { movementPendingEdit = null },
            onSubmit = { input ->
                onEditMovement(movement, input) {
                    movementPendingEdit = null
                    selectedMovement = null
                }
            },
        )
    }

    movementPendingDelete?.let { movement ->
        DeleteMovementConfirmationDialog(
            movement = movement,
            onDismissRequest = { movementPendingDelete = null },
            onConfirmDelete = {
                onDeleteMovement(movement) {
                    movementPendingDelete = null
                    selectedMovement = null
                }
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
    movement: Movement,
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
                    text = movement.date.formatVisibleDate(),
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
    movement: Movement,
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
                DetailLine(label = "Fecha", value = movement.date.formatVisibleDate())
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
    movement: Movement,
    onDismissRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Eliminar movimiento")
        },
        text = {
            Text(
                text = "Esta acción eliminará ${movement.formattedSignedAmount()} del ${movement.date.formatVisibleDate()}.",
            )
        },
        confirmButton = {
            Button(onClick = onConfirmDelete) {
                Text(text = "Eliminar")
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
private fun Movement.amountColor() = when (type) {
    MovementType.INCOME -> financePositiveColor()
    MovementType.EXPENSE -> financeNegativeColor()
}

private fun Movement.formattedSignedAmount(): String {
    val sign = when (type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
    }
    return "$sign ${amountMinor.formatVisibleMoney(currency.symbol())}"
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
