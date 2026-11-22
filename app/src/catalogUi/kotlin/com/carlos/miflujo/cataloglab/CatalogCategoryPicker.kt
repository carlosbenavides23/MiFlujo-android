package com.carlos.miflujo.cataloglab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.MovementType

internal fun CatalogMovementSelection?.withCategory(uuid: String?): CatalogMovementSelection =
    this?.takeIf { it.categoryUuid == uuid } ?: CatalogMovementSelection(uuid)

internal fun BackupCategory.visibleName(): String = if (archived) "$name (archivada)" else name

/** Compose this as a child of the Box that contains the category control. */
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
    // Keep the order from opening, but use live metadata and eligibility.
    val openingOrder = remember(type) { categories.map { it.uuid } }
    val options = remember(categories, type, openingOrder) {
        val available = categories.filter { !it.archived && it.scope.supports(type) }.associateBy { it.uuid }
        val original = openingOrder.mapNotNull(available::get)
        val originalIds = openingOrder.toHashSet()
        original + available.values.filterNot { it.uuid in originalIds }
    }
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val maxHeight = with(density) { 260.dp.roundToPx() }
    var availableHeight by remember { mutableIntStateOf(maxHeight) }
    val provider = remember(gap, margin, maxHeight) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val above = (anchorBounds.top - gap - margin).coerceAtLeast(0)
                val below = (windowSize.height - anchorBounds.bottom - gap - margin).coerceAtLeast(0)
                val openAbove = above >= minOf(maxHeight, below)
                availableHeight = minOf(maxHeight, if (openAbove) above else below)
                val wantedX = if (layoutDirection == LayoutDirection.Ltr) {
                    anchorBounds.right - popupContentSize.width
                } else anchorBounds.left
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val x = wantedX.coerceIn(minOf(margin, maxX), (maxX - margin).coerceAtLeast(minOf(margin, maxX)))
                val wantedY = if (openAbove) anchorBounds.top - gap - popupContentSize.height else anchorBounds.bottom + gap
                return IntOffset(x, wantedY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
            }
        }
    }
    BackHandler(onBack = onDismiss)
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // The host handles outside DOWN without consuming it, preserving keyboard and anchor toggling.
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
    ) {
        val background = MaterialTheme.colorScheme.surfaceContainer
        Surface(
            Modifier.width(256.dp).heightIn(max = with(density) { availableHeight.toDp() }),
            shape = MaterialTheme.shapes.medium,
            color = background,
            shadowElevation = 4.dp,
        ) {
            Column {
                if (options.isNotEmpty() || allowUncategorized) {
                    LazyColumn(Modifier.weight(1f, fill = false).testTag("category-picker-list")) {
                        items(options, key = { it.uuid }) { category ->
                            CategoryPickerRow(category, selectedUuid == category.uuid, background) { onSelect(category.uuid) }
                        }
                        if (allowUncategorized) {
                            item(key = "uncategorized") {
                                CategoryPickerRow(null, selectedUuid == null, background) { onSelect(null) }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                DropdownMenuItem(
                    text = { Text("Crear nueva categoría", style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onCreate,
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.primary,
                        leadingIconColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun CategoryPickerRow(
    category: BackupCategory?,
    isSelected: Boolean,
    background: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(category?.name ?: "Sin categoría", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.semantics { selected = isSelected },
        leadingIcon = { CatalogIcon(category?.iconKey, color = category?.iconColor, background = background) },
        trailingIcon = if (isSelected) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
        onClick = onClick,
    )
}
