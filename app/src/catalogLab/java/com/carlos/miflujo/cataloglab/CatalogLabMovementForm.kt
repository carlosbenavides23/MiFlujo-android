package com.carlos.miflujo.cataloglab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.model.*
import com.carlos.miflujo.ui.movement.CapsuleToggle
import com.carlos.miflujo.ui.movement.CapsuleToggleColors
import com.carlos.miflujo.ui.movement.CompactEntryField
import com.carlos.miflujo.ui.movement.CurrencyMorph
import com.carlos.miflujo.ui.movement.MovementSymbol
import com.carlos.miflujo.ui.movement.formatAmountInput
import com.carlos.miflujo.ui.movement.parseAmountMinorOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Composable
internal fun CatalogLabMovementForm(
    catalog: CatalogBackup,
    existing: CatalogMovement?,
    saving: Boolean,
    onDismiss: () -> Unit,
    saveError: String?,
    onSave: (CatalogMovementDraft) -> Unit,
) {
    val previous = existing?.draft
    var type by remember { mutableStateOf(previous?.type ?: MovementType.EXPENSE) }
    var currency by remember { mutableStateOf(previous?.currency ?: Currency.CORDOBA) }
    var amount by remember { mutableStateOf(previous?.amountMinor?.formatAmountInput().orEmpty()) }
    var date by remember { mutableStateOf((previous?.date ?: LocalDate.now()).toString()) }
    var detail by remember { mutableStateOf(previous?.detail.orEmpty()) }
    var selection by remember { mutableStateOf(previous?.selection ?: CatalogMovementSelection()) }
    var error by remember { mutableStateOf<String?>(null) }
    val parent = catalog.categories.firstOrNull { it.uuid == selection.categoryUuid }
    val child = catalog.subcategories.firstOrNull { it.uuid == selection.subcategoryUuid }
    val parents = catalog.categories.filter { !it.archived && it.scope.supports(type) }
    val children = catalog.subcategories.filter { it.categoryUuid == parent?.uuid && !it.archived }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (existing == null) "Nuevo movimiento" else "Editar movimiento", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactEntryField(amount, { amount = it }, "Monto", Modifier.weight(1f), KeyboardType.Decimal)
                    val isCordoba = currency == Currency.CORDOBA
                    CapsuleToggle(currency, if (isCordoba) "Córdobas, cambiar a dólares" else "Dólares, cambiar a córdobas",
                        if (isCordoba) CapsuleToggleColors.Cordoba else CapsuleToggleColors.Dollar,
                        if (isCordoba) CapsuleToggleColors.CordobaText else Color.White,
                        onClick = { currency = if (isCordoba) Currency.DOLLAR else Currency.CORDOBA },
                    ) { CurrencyMorph(it) }
                    val isIncome = type == MovementType.INCOME
                    CapsuleToggle(type, if (isIncome) "Ingreso, cambiar a egreso" else "Egreso, cambiar a ingreso",
                        if (isIncome) CapsuleToggleColors.Income else CapsuleToggleColors.Expense,
                        onClick = {
                            type = if (isIncome) MovementType.EXPENSE else MovementType.INCOME
                            selection = CatalogMovementSelection()
                        },
                    ) { MovementSymbol(it) }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
                CatalogMenu(
                    label = parent?.let { if (it.archived) "${it.name} (archivada)" else it.name } ?: "Sin categoría",
                    icon = parent?.iconKey, emptyLabel = "Sin categoría",
                    options = parents.map { CatalogMenuItem(it.uuid, it.name, it.iconKey) },
                    select = { if (it != selection.categoryUuid) selection = CatalogMovementSelection(it) },
                )
                if (parent != null && !parent.archived) {
                    CatalogMenu(
                        child?.let { if (it.archived) "${it.name} (archivada)" else it.name } ?: "Sin subcategoría",
                        child?.iconKey, "Sin subcategoría", children.map { CatalogMenuItem(it.uuid, it.name, it.iconKey) },
                        { selection = CatalogMovementSelection(parent.uuid, it) },
                    )
                } else if (child != null) {
                    Text(child.name)
                }
                OutlinedTextField(detail, { detail = it }, label = { Text("Detalle") })
                (error ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss, enabled = !saving) { Text("Cancelar") }
                    Button(enabled = !saving, onClick = {
                        val minor = parseAmountMinorOrNull(amount)?.takeIf { it > 0 }
                        val day = try { LocalDate.parse(date) } catch (_: DateTimeParseException) { null }
                        error = when {
                            minor == null -> "Ingresá un monto positivo válido."
                            day == null -> "Ingresá una fecha válida."
                            else -> null
                        }
                        if (minor != null && day != null) {
                            val savedDetail = if (previous != null && detail == previous.detail.orEmpty()) previous.detail
                            else detail.takeIf { it.isNotBlank() }
                            onSave(CatalogMovementDraft(type, minor, currency, day, selection, savedDetail))
                        }
                    }) { Text("Guardar") }
                }
            }
        }
    }
}

private data class CatalogMenuItem(val uuid: String, val name: String, val icon: String)

@Composable
private fun CatalogMenu(
    label: String,
    icon: String?,
    emptyLabel: String,
    options: List<CatalogMenuItem>,
    select: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            CatalogIcon(icon)
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(emptyLabel) }, onClick = { expanded = false; select(null) })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, leadingIcon = { CatalogIcon(option.icon) },
                    onClick = { expanded = false; select(option.uuid) })
            }
        }
    }
}

@Composable
private fun CatalogIcon(icon: String?) {
    Icon(when (icon) {
        "home" -> Icons.Default.Home
        "water", "electricity", "internet" -> Icons.Default.Info
        "income", "maintenance" -> Icons.Default.Star
        else -> Icons.Default.Category
    }, contentDescription = null)
}
