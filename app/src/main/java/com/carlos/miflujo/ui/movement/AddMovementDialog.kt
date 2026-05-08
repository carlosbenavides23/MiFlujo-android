package com.carlos.miflujo.ui.movement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AddMovementDialog(
    onDismissRequest: () -> Unit,
    onSubmit: (AddMovementInput) -> Unit,
) {
    var movementType by rememberSaveable { mutableStateOf<MovementType?>(null) }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf<Currency?>(null) }
    val initialDate = LocalDate.now().format(formDateFormatter)
    var date by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initialDate,
                selection = TextRange(initialDate.length),
            ),
        )
    }
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
                onDateChange = { updatedDate ->
                    date = updatedDate.formatVisibleDateInput(previousFormattedText = date.text)
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
                    val validationResult = validateAddMovementForm(
                        movementType = movementType,
                        amount = amount,
                        currency = currency,
                        date = date.text,
                        category = category,
                        subcategory = subcategory,
                    )
                    errors = validationResult.errors
                    if (!validationResult.errors.hasErrors) {
                        onSubmit(
                            AddMovementInput(
                                type = movementType ?: return@Button,
                                amountMinor = validationResult.amountMinor ?: return@Button,
                                currency = currency ?: return@Button,
                                date = validationResult.date ?: return@Button,
                                category = movementType.categoryForSubmit(category),
                                subcategory = movementType.subcategoryForSubmit(
                                    category = category,
                                    subcategory = subcategory,
                                ),
                                detail = detail.trim().ifBlank { null },
                            ),
                        )
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
    date: TextFieldValue,
    onDateChange: (TextFieldValue) -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormSection(
            title = "Tipo de movimiento",
            error = errors.movementType,
        ) {
            SegmentedChoiceGroup {
                SegmentedChoice(
                    text = "+ Ingreso",
                    selected = movementType == MovementType.INCOME,
                    onClick = { onMovementTypeChange(MovementType.INCOME) },
                )
                SegmentedChoice(
                    text = "- Egreso",
                    selected = movementType == MovementType.EXPENSE,
                    onClick = { onMovementTypeChange(MovementType.EXPENSE) },
                )
            }
        }

        FormSection(
            title = "Moneda",
            error = errors.currency,
        ) {
            SegmentedChoiceGroup {
                SegmentedChoice(
                    text = "C$",
                    selected = currency == Currency.CORDOBA,
                    onClick = { onCurrencyChange(Currency.CORDOBA) },
                )
                SegmentedChoice(
                    text = "US$",
                    selected = currency == Currency.DOLLAR,
                    onClick = { onCurrencyChange(Currency.DOLLAR) },
                )
            }
        }

        FormSection(title = "Monto") {
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = "Monto")
                },
                placeholder = {
                    Text(text = "0.00")
                },
                prefix = {
                    currency?.let {
                        Text(text = it.amountPrefix())
                    }
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
        }

        FormSection(title = "Fecha") {
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
        }

        if (movementType == MovementType.EXPENSE) {
            FormSection(
                title = "Categoría",
                error = errors.category,
            ) {
                SegmentedChoiceGroup {
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
        }

        if (movementType == MovementType.EXPENSE && category == MovementCategory.FIXED_COST) {
            FormSection(
                title = "Subcategoría",
                error = errors.subcategory,
            ) {
                SegmentedChoiceGroup {
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
        }

        FormSection(title = "Detalle") {
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
}

@Composable
private fun FormSection(
    title: String,
    error: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
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
private fun SegmentedChoiceGroup(
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun SegmentedChoice(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExpenseCategoryChip(
    text: String,
    category: MovementCategory,
    selectedCategory: MovementCategory?,
    onCategoryChange: (MovementCategory) -> Unit,
) {
    SegmentedChoice(
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
    SegmentedChoice(
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

private data class AddMovementValidationResult(
    val errors: AddMovementFormErrors,
    val amountMinor: Long?,
    val date: LocalDate?,
)

private fun validateAddMovementForm(
    movementType: MovementType?,
    amount: String,
    currency: Currency?,
    date: String,
    category: MovementCategory?,
    subcategory: MovementSubcategory?,
): AddMovementValidationResult {
    val parsedAmountMinor = parseAmountMinorOrNull(amount)
    val parsedDate = parseVisibleDateOrNull(date)

    return AddMovementValidationResult(
        errors = AddMovementFormErrors(
            movementType = if (movementType == null) "Seleccione ingreso o egreso." else null,
            amount = when {
                amount.isBlank() -> "Ingrese un monto."
                parsedAmountMinor == null -> "Ingrese un monto válido."
                parsedAmountMinor <= 0L -> "El monto debe ser mayor que 0."
                else -> null
            },
            currency = if (currency == null) "Seleccione C$ o US$." else null,
            date = when {
                date.isBlank() -> "Ingrese la fecha del movimiento."
                parsedDate == null -> "Ingrese una fecha válida en formato dd/MM/yy."
                else -> null
            },
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
        ),
        amountMinor = parsedAmountMinor,
        date = parsedDate,
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

private fun TextFieldValue.formatVisibleDateInput(previousFormattedText: String): TextFieldValue {
    val previousDigits = previousFormattedText.dateDigits()
    val rawDigits = text.dateDigits()
    val isDeletionFromEnd = text.length < previousFormattedText.length &&
        previousFormattedText.startsWith(text)
    val formattedText = when {
        text.isEmpty() -> ""
        isDeletionFromEnd -> previousDigits.dropLast(1).formatVisibleDateInput()
        else -> rawDigits.formatVisibleDateInput()
    }

    return TextFieldValue(
        text = formattedText,
        selection = TextRange(formattedText.length),
    )
}

private fun String.formatVisibleDateInput(): String {
    val digits = take(MAX_DATE_DIGITS)
    return buildString {
        digits.forEachIndexed { index, digit ->
            append(digit)
            if ((index == 1 || index == 3) && index != MAX_DATE_DIGITS - 1) {
                append('/')
            }
        }
    }
}

private fun String.dateDigits(): String {
    return filter { it.isDigit() }.take(MAX_DATE_DIGITS)
}

private const val MAX_DATE_DIGITS = 6

private fun parseVisibleDateOrNull(rawDate: String): LocalDate? {
    val parts = rawDate.trim().split('/')
    if (parts.size != 3) return null
    if (parts[0].length != 2 || parts[1].length != 2 || parts[2].length != 2) return null

    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val twoDigitYear = parts[2].toIntOrNull() ?: return null

    return try {
        LocalDate.of(2000 + twoDigitYear, month, day)
    } catch (_: DateTimeException) {
        null
    }
}

private fun MovementType?.categoryForSubmit(category: MovementCategory?): MovementCategory {
    return when (this) {
        MovementType.INCOME -> MovementCategory.GENERAL_INCOME
        MovementType.EXPENSE -> category ?: MovementCategory.OTHER
        null -> MovementCategory.OTHER
    }
}

private fun MovementType?.subcategoryForSubmit(
    category: MovementCategory?,
    subcategory: MovementSubcategory?,
): MovementSubcategory? {
    return if (this == MovementType.EXPENSE && category == MovementCategory.FIXED_COST) {
        subcategory
    } else {
        null
    }
}

private fun Currency.amountPrefix(): String {
    return when (this) {
        Currency.CORDOBA -> "C$"
        Currency.DOLLAR -> "US$"
    }
}

private val formDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")
