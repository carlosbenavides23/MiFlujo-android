package com.carlos.miflujo.cataloglab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.carlos.miflujo.domain.category.CatalogIconColor
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryRepository
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.newCatalogUuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class CatalogItemInput(
    val name: String = "",
    val iconKey: String = DEFAULT_CATALOG_ICON,
    val scope: CategoryScope = CategoryScope.EXPENSE,
    val group: ReportGroup = ReportGroup.OTHER,
    val iconColor: CatalogIconColor? = null,
)

internal fun catalogNameError(name: String): String? = when {
    name.isBlank() -> "Ingresá un nombre."
    name.trim().toByteArray(Charsets.UTF_8).size > 480 -> "El nombre es demasiado largo para sincronizarlo."
    else -> null
}

internal suspend fun CategoryRepository.createFromEditor(input: CatalogItemInput, uuid: String): String {
    val value = Category(uuid = uuid, name = input.name, scope = input.scope, reportGroup = input.group, iconKey = input.iconKey, iconColor = input.iconColor)
    val previous = category(uuid)
    if (previous == null) return createCategory(value).value.uuid
    check(previous.value.copy(id = 0) == value)
    return uuid
}

@Composable
internal fun CatalogItemEditor(
    title: String,
    initial: CatalogItemInput,
    saveLabel: String,
    allowScope: Boolean = false,
    allowGroup: Boolean = allowScope,
    onDismiss: () -> Unit,
    save: suspend (CatalogItemInput, String) -> String,
    onSaved: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var icon by rememberSaveable { mutableStateOf(initial.iconKey) }
    var iconColor by rememberSaveable { mutableStateOf(initial.iconColor) }
    var scope by rememberSaveable { mutableStateOf(initial.scope) }
    var group by rememberSaveable { mutableStateOf(initial.group.takeUnless { it == ReportGroup.INCOME } ?: ReportGroup.OTHER) }
    var showIcons by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val newUuid = rememberSaveable { newCatalogUuid() }
    var busy by remember { mutableStateOf(false) }
    val coroutine = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 440.dp).padding(horizontal = 12.dp), shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    name, { name = it; error = null }, Modifier.fillMaxWidth(), enabled = !busy,
                    label = { Text("Nombre") }, singleLine = true,
                )
                OutlinedButton(enabled = !busy, onClick = { showIcons = true }) {
                    CatalogIcon(icon, color = iconColor)
                    Spacer(Modifier.width(8.dp))
                    Text("Elegir icono")
                }
                CatalogIconColorPicker(iconColor, enabled = !busy) { iconColor = it }
                if (allowScope) {
                    EditorChoice("Tipo de movimiento", scope, CategoryScope.entries, { it.editorLabel() }, !busy) { scope = it }
                }
                if (allowGroup && scope != CategoryScope.INCOME) {
                    EditorChoice("Grupo del reporte", group, expenseGroups, { it.editorLabel() }, !busy) { group = it }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss, enabled = !busy) { Text("Cancelar") }
                    Button(enabled = !busy, onClick = {
                        val unchangedName = name == initial.name && name.isNotBlank()
                        error = if (unchangedName) null else catalogNameError(name)
                        if (error == null) {
                            busy = true
                            coroutine.launch {
                                val saved = try {
                                    save(CatalogItemInput(if (unchangedName) name else name.trim(), icon, scope,
                                        if (scope == CategoryScope.INCOME) ReportGroup.INCOME else group, iconColor), newUuid)
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    error = "No se pudo guardar. Verificá los datos e intentá de nuevo."
                                    null
                                } finally {
                                    busy = false
                                }
                                if (saved != null) onSaved(saved)
                            }
                        }
                    }) { Text(saveLabel) }
                }
            }
        }
    }
    if (showIcons) CatalogIconPicker(icon, { showIcons = false }) {
        icon = it
        showIcons = false
    }
}

private val expenseGroups = listOf(ReportGroup.FIXED_COST, ReportGroup.MAINTENANCE, ReportGroup.OTHER)

private fun CategoryScope.editorLabel() = when (this) {
    CategoryScope.INCOME -> "Ingresos"
    CategoryScope.EXPENSE -> "Egresos"
    CategoryScope.BOTH -> "Ingresos y egresos"
}

private fun ReportGroup.editorLabel() = when (this) {
    ReportGroup.INCOME -> "Ingresos"
    ReportGroup.FIXED_COST -> "Costos fijos"
    ReportGroup.MAINTENANCE -> "Mantenimiento"
    ReportGroup.OTHER -> "Otros"
}

@Composable
private fun <T> EditorChoice(
    label: String,
    value: T,
    choices: List<T>,
    text: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(enabled = enabled, onClick = { expanded = true }) { Text("$label · ${text(value)}") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}
