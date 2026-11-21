package com.carlos.miflujo.cataloglab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.category.CatalogIconColor
import com.carlos.miflujo.domain.category.CategoryRepository
import com.carlos.miflujo.domain.category.CategoryScope
import java.io.Serializable
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
    repository: CategoryRepository,
    onCatalogChanged: () -> Unit,
    onSave: (CatalogMovementDraft) -> Unit,
) {
    val previous = existing?.draft
    var entry by rememberSaveable { mutableStateOf(CatalogMovementEntry.from(previous)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    val (type, currency, amount, date, detail, categoryUuid, subcategoryUuid) = entry
    val selection = CatalogMovementSelection(categoryUuid, subcategoryUuid)
    val parent = catalog.categories.firstOrNull { it.uuid == selection.categoryUuid }
    val child = catalog.subcategories.firstOrNull { it.uuid == selection.subcategoryUuid }
    val parents = catalog.categories.filter { !it.archived && it.scope.supports(type) }
    val children = catalog.subcategories.filter { it.categoryUuid == parent?.uuid && !it.archived }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (existing == null) "Nuevo movimiento" else "Editar movimiento", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompactEntryField(amount, { entry = entry.copy(amount = it) }, "Monto", Modifier.weight(1f), KeyboardType.Decimal)
                    val isCordoba = currency == Currency.CORDOBA
                    CapsuleToggle(currency, if (isCordoba) "Córdobas, cambiar a dólares" else "Dólares, cambiar a córdobas",
                        if (isCordoba) CapsuleToggleColors.Cordoba else CapsuleToggleColors.Dollar,
                        if (isCordoba) CapsuleToggleColors.CordobaText else Color.White,
                        onClick = { entry = entry.copy(currency = if (isCordoba) Currency.DOLLAR else Currency.CORDOBA) },
                    ) { CurrencyMorph(it) }
                    val isIncome = type == MovementType.INCOME
                    CapsuleToggle(type, if (isIncome) "Ingreso, cambiar a egreso" else "Egreso, cambiar a ingreso",
                        if (isIncome) CapsuleToggleColors.Income else CapsuleToggleColors.Expense,
                        onClick = {
                            entry = entry.copy(type = if (isIncome) MovementType.EXPENSE else MovementType.INCOME)
                                .selectCategory(null)
                        },
                    ) { MovementSymbol(it) }
                }
                OutlinedTextField(date, { entry = entry.copy(date = it) }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
                CatalogMenu(
                    label = parent?.let { if (it.archived) "${it.name} (archivada)" else it.name } ?: "Sin categoría",
                    icon = parent?.iconKey, emptyLabel = "Sin categoría", iconColor = parent?.iconColor,
                    options = parents.map { CatalogMenuItem(it.uuid, it.name, it.iconKey, it.iconColor) },
                    select = { entry = entry.selectCategory(it) },
                    onCreate = { creatingCategory = true },
                )
                if (parent != null && !parent.archived) {
                    CatalogMenu(
                        child?.let { if (it.archived) "${it.name} (archivada)" else it.name } ?: "Sin subcategoría",
                        child?.iconKey, "Sin subcategoría", children.map { CatalogMenuItem(it.uuid, it.name, it.iconKey, it.iconColor) },
                        { entry = entry.copy(subcategoryUuid = it) },
                        iconColor = child?.iconColor,
                    )
                } else if (child != null) {
                    Row {
                        CatalogIcon(child.iconKey, color = child.iconColor)
                        Spacer(Modifier.width(8.dp))
                        Text(child.name)
                    }
                }
                OutlinedTextField(detail, { entry = entry.copy(detail = it) }, label = { Text("Detalle") })
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
    if (creatingCategory) CatalogItemEditor(
        title = "Nueva categoría",
        saveLabel = "Guardar categoría",
        initial = CatalogItemInput(scope = if (type == MovementType.INCOME) CategoryScope.INCOME else CategoryScope.EXPENSE),
        allowGroup = true,
        onDismiss = { creatingCategory = false },
        save = { input, uuid -> repository.createFromEditor(input, uuid) },
        onSaved = { uuid ->
            entry = entry.selectCategory(uuid)
            creatingCategory = false
            onCatalogChanged()
        },
    )
}

private data class CatalogMenuItem(val uuid: String, val name: String, val icon: String, val color: CatalogIconColor?)

@Composable
private fun CatalogMenu(
    label: String,
    icon: String?,
    emptyLabel: String,
    options: List<CatalogMenuItem>,
    select: (String?) -> Unit,
    onCreate: (() -> Unit)? = null,
    iconColor: CatalogIconColor? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            CatalogIcon(icon, color = iconColor)
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(emptyLabel) }, onClick = { expanded = false; select(null) })
            if (onCreate != null) DropdownMenuItem(
                text = { Text("Crear nueva categoría") }, onClick = { expanded = false; onCreate() },
            )
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.name) }, leadingIcon = { CatalogIcon(option.icon, color = option.color, background = MaterialTheme.colorScheme.surfaceContainer) },
                    onClick = { expanded = false; select(option.uuid) })
            }
        }
    }
}


internal data class CatalogMovementEntry(
    val type: MovementType,
    val currency: Currency,
    val amount: String,
    val date: String,
    val detail: String,
    val categoryUuid: String?,
    val subcategoryUuid: String?,
) : Serializable {
    fun selectCategory(uuid: String?): CatalogMovementEntry =
        if (uuid == categoryUuid) this else copy(categoryUuid = uuid, subcategoryUuid = null)

    companion object {
        fun from(draft: CatalogMovementDraft?) = CatalogMovementEntry(
            type = draft?.type ?: MovementType.EXPENSE,
            currency = draft?.currency ?: Currency.CORDOBA,
            amount = draft?.amountMinor?.formatAmountInput().orEmpty(),
            date = (draft?.date ?: LocalDate.now()).toString(),
            detail = draft?.detail.orEmpty(),
            categoryUuid = draft?.selection?.categoryUuid,
            subcategoryUuid = draft?.selection?.subcategoryUuid,
        )
    }
}
