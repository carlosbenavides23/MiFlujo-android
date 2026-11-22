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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.category.CategoryRepository
import java.io.Serializable
import com.carlos.miflujo.domain.model.*
import com.carlos.miflujo.ui.movement.CapsuleMenuControl
import com.carlos.miflujo.ui.movement.SelectorMenuState
import com.carlos.miflujo.ui.movement.CapsuleToggle
import com.carlos.miflujo.ui.movement.CapsuleToggleColors
import com.carlos.miflujo.ui.movement.CompactEntryField
import com.carlos.miflujo.ui.movement.CurrencyMorph
import com.carlos.miflujo.ui.movement.MovementSymbol
import com.carlos.miflujo.ui.movement.formatAmountInput
import com.carlos.miflujo.ui.movement.parseAmountMinorOrNull
import com.carlos.miflujo.ui.movement.rememberCategoryPickerMotion
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
    val menu = remember { SelectorMenuState() }
    var dialogCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val categoriesByUsage = remember(catalog.categories, catalog.movements) { catalog.categoriesByRecentUsage() }
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    val (type, currency, amount, date, detail, categoryUuid) = entry
    val category = catalog.categories.firstOrNull { it.uuid == categoryUuid }

    Dialog(onDismissRequest = {
        if (menu.expanded) menu.expanded = false else if (!saving) onDismiss()
    }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier
                    .onGloballyPositioned { dialogCoordinates = it }
                    .pointerInput(menu) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                                val coordinates = dialogCoordinates
                                if (down != null && coordinates?.isAttached == true) {
                                    menu.dismissOutside(coordinates.localToWindow(down.position))
                                }
                            }
                        }
                    }
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                Box(Modifier.fillMaxWidth().onGloballyPositioned { menu.anchor = it.boundsInWindow() }) {
                    val motion = rememberCategoryPickerMotion(menu.expanded)
                    CapsuleMenuControl(
                        label = "Categoría",
                        text = category?.visibleName() ?: "Categoría",
                        modifier = Modifier.fillMaxWidth().then(motion.buttonReaction()),
                        onClick = { menu.expanded = !menu.expanded },
                        announceLabelOnly = true,
                    ) {
                        CatalogIcon(category?.iconKey, it, color = category?.iconColor)
                    }
                    CatalogCategoryPicker(
                        categoriesByUsage, type, categoryUuid, allowUncategorized = true,
                        expanded = menu.expanded, motion = motion,
                        onDismiss = { menu.expanded = false },
                        onSelect = {
                            entry = entry.selectCategory(it)
                            menu.expanded = false
                        },
                        onCreate = {
                            menu.expanded = false
                            creatingCategory = true
                        },
                    )
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
                            onSave(CatalogMovementDraft(type, minor, currency, day, entry.selectionFor(previous?.selection), savedDetail))
                        }
                    }) { Text("Guardar") }
                }
            }
        }
    }
    if (creatingCategory) CatalogItemEditor(
        title = "Nueva categoría",
        saveLabel = "Guardar categoría",
        initial = CatalogItemInput(),
        onDismiss = { creatingCategory = false },
        save = { input, uuid -> repository.createFromEditor(input, uuid) },
        onSaved = { uuid ->
            entry = entry.selectCategory(uuid)
            creatingCategory = false
            onCatalogChanged()
        },
    )
}


internal data class CatalogMovementEntry(
    val type: MovementType,
    val currency: Currency,
    val amount: String,
    val date: String,
    val detail: String,
    val categoryUuid: String?,
) : Serializable {
    fun selectCategory(uuid: String?): CatalogMovementEntry =
        copy(categoryUuid = uuid)

    fun selectionFor(previous: CatalogMovementSelection?): CatalogMovementSelection =
        previous.withCategory(categoryUuid)

    companion object {
        fun from(draft: CatalogMovementDraft?) = CatalogMovementEntry(
            type = draft?.type ?: MovementType.EXPENSE,
            currency = draft?.currency ?: Currency.CORDOBA,
            amount = draft?.amountMinor?.formatAmountInput().orEmpty(),
            date = (draft?.date ?: LocalDate.now()).toString(),
            detail = draft?.detail.orEmpty(),
            categoryUuid = draft?.selection?.categoryUuid,
        )
    }
}
