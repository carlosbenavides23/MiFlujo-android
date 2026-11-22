package com.carlos.miflujo.cataloglab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.MovementType
import kotlinx.coroutines.launch

internal fun CatalogMovementSelection?.withCategory(uuid: String?): CatalogMovementSelection =
    this?.takeIf { it.categoryUuid == uuid } ?: CatalogMovementSelection(uuid)

internal fun BackupCategory.visibleName(): String = if (archived) "$name (archivada)" else name

@Composable
internal fun CatalogCategoryControl(
    category: BackupCategory?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val label = category?.visibleName() ?: "Elegir categoría"
    val background = when {
        pressed || focused -> colors.secondaryContainer
        category != null -> colors.surfaceContainerHighest
        else -> colors.surface
    }
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = "Categoría: $label"
        },
        shape = CircleShape,
        color = background,
        contentColor = if (pressed || focused) colors.onSecondaryContainer else colors.onSurface,
        border = if (focused) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outline),
        interactionSource = interaction,
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CatalogIcon(category?.iconKey, Modifier.size(20.dp), color = category?.iconColor, background = background)
            Text(
                label, Modifier.weight(1f).clearAndSetSemantics {},
                style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ExpandMore, contentDescription = null, Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogCategoryPicker(
    categories: List<BackupCategory>,
    type: MovementType,
    selectedUuid: String?,
    allowUncategorized: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var closing by remember { mutableStateOf(false) }
    val search = query.trim()
    val matching = remember(categories, type, search) {
        categories.filter { !it.archived && it.scope.supports(type) && it.name.contains(search, ignoreCase = true) }
    }
    val showUncategorized = allowUncategorized && "Sin categoría".contains(search, ignoreCase = true)
    val background = MaterialTheme.colorScheme.surfaceContainerLow

    fun close(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            sheet.hide()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = background) {
        val keyboard = LocalSoftwareKeyboardController.current
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Elegir categoría", Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = { close(onDismiss) }, enabled = !closing) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar selector de categorías")
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).testTag("category-picker-list"),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                FilledTonalButton(
                    onClick = { keyboard?.hide(); close(onCreate) },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).heightIn(min = 48.dp),
                    enabled = !closing,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Crear nueva categoría")
                }
            }
            item {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    label = { Text("Buscar categorías") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                )
                HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
            if (matching.isEmpty() && !showUncategorized) {
                item {
                    Text(
                        if (search.isBlank()) "No hay categorías disponibles." else "No se encontraron categorías.",
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (showUncategorized) {
                item(key = "uncategorized") {
                    CategoryPickerRow(null, selectedUuid == null, !closing, background) {
                        keyboard?.hide()
                        close { onSelect(null) }
                    }
                }
            }
            items(matching, key = { it.uuid }) { category ->
                CategoryPickerRow(category, selectedUuid == category.uuid, !closing, background) {
                    keyboard?.hide()
                    close { onSelect(category.uuid) }
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerRow(
    category: BackupCategory?,
    selected: Boolean,
    enabled: Boolean,
    background: Color,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.secondaryContainer else background
    val content = if (selected) colors.onSecondaryContainer else colors.onSurface
    ListItem(
        headlineContent = { Text(category?.name ?: "Sin categoría", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        leadingContent = { CatalogIcon(category?.iconKey, color = category?.iconColor, background = container) },
        trailingContent = if (selected) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
        colors = ListItemDefaults.colors(containerColor = container, headlineColor = content, leadingIconColor = content, trailingIconColor = content),
    )
}
