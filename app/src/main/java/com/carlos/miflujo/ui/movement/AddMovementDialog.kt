package com.carlos.miflujo.ui.movement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType

@Composable
fun AddMovementDialog(
    onDismissRequest: () -> Unit,
    onValidated: () -> Unit,
) {
    var movementType by rememberSaveable { mutableStateOf<MovementType?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf<Currency?>(null) }
    var date by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<MovementCategory?>(null) }
    var subcategory by rememberSaveable { mutableStateOf<MovementSubcategory?>(null) }
    var detail by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf(AddMovementFormErrors()) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Agregar movimiento")
        },
        text = {
            AddMovementFormContent(
                movementType = movementType,
                onMovementTypeChange = { selectedType ->
                    movementType = selectedType
                    if (selectedType == MovementType.INCOME) {
                        category = null
                        subcategory = null
                    }
                    errors = errors.copy(movementType = null)
                },
                amount = amount,
                onAmountChange = {
                    amount = it
                    errors = errors.copy(amount = null)
                },
                currency = currency,
                onCurrencyChange = {
                    currency = it
                    errors = errors.copy(currency = null)
                },
                date = date,
                onDateChange = {
                    date = it
                    errors = errors.copy(date = null)
                },
                category = category,
                onCategoryChange = { selectedCategory ->
                    category = selectedCategory
                    if (selectedCategory != MovementCategory.FIXED_COST) {
                        subcategory = null
                    }
                    errors = errors.copy(category = null, subcategory = null)
                },
                subcategory = subcategory,
                onSubcategoryChange = {
                    subcategory = it
                    errors = errors.copy(subcategory = null)
                },
                detail = detail,
                onDetailChange = {
                    detail = it
                },
                errors = errors,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val validationErrors = validateAddMovementForm(
                        movementType = movementType,
                        amount = amount,
                        currency = currency,
                        date = date,
                        category = category,
                        subcategory = subcategory,
                    )
                    errors = validationErrors
                    if (!validationErrors.hasErrors) {
                        onValidated()
                    }
                },
            ) {
                Text(text = "Continuar")
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
private fun AddMovementFormContent(
    movementType: MovementType?,
    onMovementTypeChange: (MovementType) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    currency: Currency?,
    onCurrencyChange: (Currency) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
    category: MovementCategory?,
    onCategoryChange: (MovementCategory) -> Unit,
    subcategory: MovementSubcategory?,
    onSubcategoryChange: (MovementSubcategory) -> Unit,
    detail: String,
    onDetailChange: (String) -> Unit,
    errors: AddMovementFormErrors,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ChipGroup(
            title = "¿Qué desea registrar?",
            error = errors.movementType,
        ) {
            ChoiceChip(
                text = "Ingreso",
                selected = movementType == MovementType.INCOME,
                onClick = { onMovementTypeChange(MovementType.INCOME) },
            )
            ChoiceChip(
                text = "Egreso",
                selected = movementType == MovementType.EXPENSE,
                onClick = { onMovementTypeChange(MovementType.EXPENSE) },
            )
        }

        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = "Monto")
            },
            placeholder = {
                Text(text = "Ej. 1800.50")
            },
            prefix = {
                Text(text = movementType.signLabel())
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = errors.amount != null,
            supportingText = {
                FieldSupportingText(
                    error = errors.amount,
                    helper = "Use punto o coma para centavos.",
                )
            },
        )

        ChipGroup(
            title = "Moneda",
            error = errors.currency,
        ) {
            ChoiceChip(
                text = "C$",
                selected = currency == Currency.CORDOBA,
                onClick = { onCurrencyChange(Currency.CORDOBA) },
            )
            ChoiceChip(
                text = "US$",
                selected = currency == Currency.DOLLAR,
                onClick = { onCurrencyChange(Currency.DOLLAR) },
            )
        }

        OutlinedTextField(
            value = date,
            onValueChange = onDateChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = "Fecha")
            },
            placeholder = {
                Text(text = "dd/MM/yy")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            isError = errors.date != null,
            supportingText = {
                FieldSupportingText(
                    error = errors.date,
                    helper = "Formato visible esperado: dd/MM/yy.",
                )
            },
        )

        if (movementType == MovementType.EXPENSE) {
            ChipGroup(
                title = "Categoría del egreso",
                error = errors.category,
            ) {
                ExpenseCategoryChip(
                    text = "Costos fijos",
                    category = MovementCategory.FIXED_COST,
                    selectedCategory = category,
                    onCategoryChange = onCategoryChange,
                )
                ExpenseCategoryChip(
                    text = "Mantenimiento",
                    category = MovementCategory.MAINTENANCE,
                    selectedCategory = category,
                    onCategoryChange = onCategoryChange,
                )
                ExpenseCategoryChip(
                    text = "Otros",
                    category = MovementCategory.OTHER,
                    selectedCategory = category,
                    onCategoryChange = onCategoryChange,
                )
            }
        }

        if (movementType == MovementType.EXPENSE && category == MovementCategory.FIXED_COST) {
            ChipGroup(
                title = "Subcategoría",
                error = errors.subcategory,
            ) {
                FixedCostSubcategoryChip(
                    text = "Agua",
                    subcategory = MovementSubcategory.WATER,
                    selectedSubcategory = subcategory,
                    onSubcategoryChange = onSubcategoryChange,
                )
                FixedCostSubcategoryChip(
                    text = "Luz",
                    subcategory = MovementSubcategory.ELECTRICITY,
                    selectedSubcategory = subcategory,
                    onSubcategoryChange = onSubcategoryChange,
                )
                FixedCostSubcategoryChip(
                    text = "Internet",
                    subcategory = MovementSubcategory.INTERNET,
                    selectedSubcategory = subcategory,
                    onSubcategoryChange = onSubcategoryChange,
                )
            }
        }

        OutlinedTextField(
            value = detail,
            onValueChange = onDetailChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(text = "Detalle")
            },
            placeholder = {
                Text(text = "Opcional, recomendado")
            },
            minLines = 2,
            supportingText = {
                Text(text = "Puede dejarlo vacío si no aplica.")
            },
        )
    }
}

@Composable
private fun ChipGroup(
    title: String,
    error: String?,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(text = text)
        },
    )
}

@Composable
private fun ExpenseCategoryChip(
    text: String,
    category: MovementCategory,
    selectedCategory: MovementCategory?,
    onCategoryChange: (MovementCategory) -> Unit,
) {
    ChoiceChip(
        text = text,
        selected = selectedCategory == category,
        onClick = { onCategoryChange(category) },
    )
}

@Composable
private fun FixedCostSubcategoryChip(
    text: String,
    subcategory: MovementSubcategory,
    selectedSubcategory: MovementSubcategory?,
    onSubcategoryChange: (MovementSubcategory) -> Unit,
) {
    ChoiceChip(
        text = text,
        selected = selectedSubcategory == subcategory,
        onClick = { onSubcategoryChange(subcategory) },
    )
}

@Composable
private fun FieldSupportingText(
    error: String?,
    helper: String,
) {
    Text(
        text = error ?: helper,
        color = if (error != null) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

private data class AddMovementFormErrors(
    val movementType: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val date: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(movementType, amount, currency, date, category, subcategory).any { it != null }
}

private fun validateAddMovementForm(
    movementType: MovementType?,
    amount: String,
    currency: Currency?,
    date: String,
    category: MovementCategory?,
    subcategory: MovementSubcategory?,
): AddMovementFormErrors {
    val parsedAmountMinor = parseAmountMinorOrNull(amount)

    return AddMovementFormErrors(
        movementType = if (movementType == null) "Seleccione ingreso o egreso." else null,
        amount = when {
            amount.isBlank() -> "Ingrese un monto."
            parsedAmountMinor == null -> "Ingrese un monto válido."
            parsedAmountMinor <= 0L -> "El monto debe ser mayor que 0."
            else -> null
        },
        currency = if (currency == null) "Seleccione C$ o US$." else null,
        date = if (date.isBlank()) "Ingrese la fecha del movimiento." else null,
        category = if (movementType == MovementType.EXPENSE && category == null) {
            "Seleccione una categoría."
        } else {
            null
        },
        subcategory = if (
            movementType == MovementType.EXPENSE &&
            category == MovementCategory.FIXED_COST &&
            subcategory == null
        ) {
            "Seleccione agua, luz o internet."
        } else {
            null
        },
    )
}

private fun parseAmountMinorOrNull(rawAmount: String): Long? {
    val normalizedAmount = rawAmount.trim().replace(',', '.')
    if (normalizedAmount.isBlank()) return null

    val parts = normalizedAmount.split('.')
    if (parts.size > 2) return null

    val wholePart = parts[0]
    val centsPart = parts.getOrNull(1).orEmpty()

    if (wholePart.isBlank() || !wholePart.all { it.isDigit() }) return null
    if (centsPart.length > 2 || !centsPart.all { it.isDigit() }) return null

    return try {
        val wholeMinor = Math.multiplyExact(wholePart.toLong(), 100L)
        val centsMinor = centsPart.padEnd(2, '0').ifBlank { "0" }.toLong()
        Math.addExact(wholeMinor, centsMinor)
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

private fun MovementType?.signLabel(): String {
    return when (this) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
        null -> ""
    }
}
