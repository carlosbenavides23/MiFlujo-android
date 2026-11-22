package com.carlos.miflujo.cataloglab

import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
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
    expanded: Boolean = true,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    val transition = rememberTransition(visibility, label = "CategoryPicker")
    val progress by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(230, easing = LinearOutSlowInEasing)
            else tween(170, easing = FastOutLinearInEasing)
        },
        label = "Unfold",
    ) { visible -> if (visible) 1f else 0f }

    if (visibility.isIdle && !visibility.currentState) return

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
    var opensAbove by remember { mutableStateOf(false) }
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
                opensAbove = openAbove
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
    BackHandler(enabled = expanded, onBack = onDismiss)
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        // The host handles outside DOWN without consuming it, preserving keyboard and anchor toggling.
        properties = if (expanded) {
            PopupProperties(focusable = false, dismissOnClickOutside = false)
        } else {
            // Keep drawing the exit without intercepting taps on the capsule.
            PopupProperties(
                flags = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE,
                dismissOnClickOutside = false,
                excludeFromSystemGesture = false,
            )
        },
    ) {
        val background = MaterialTheme.colorScheme.surfaceContainer
        Surface(
            Modifier
                .width(236.dp)
                .heightIn(max = with(density) { availableHeight.toDp() })
                .semantics { if (!expanded) hideFromAccessibility() }
                .graphicsLayer {
                    val fraction = progress
                    val above = opensAbove
                    transformOrigin = TransformOrigin(0.5f, if (above) 1f else 0f)
                    scaleY = 0.88f + 0.12f * fraction
                    translationY = (if (above) -gap else gap) * (1f - fraction)
                    alpha = fraction
                },
            shape = MaterialTheme.shapes.medium,
            color = background,
            shadowElevation = 4.dp,
        ) {
            Column {
                if (options.isNotEmpty() || allowUncategorized) {
                    LazyColumn(Modifier.weight(1f, fill = false).testTag("category-picker-list")) {
                        items(options, key = { it.uuid }) { category ->
                            CategoryPickerRow(category, selectedUuid == category.uuid, background) {
                                if (expanded) onSelect(category.uuid)
                            }
                        }
                        if (allowUncategorized) {
                            item(key = "uncategorized") {
                                CategoryPickerRow(null, selectedUuid == null, background) {
                                    if (expanded) onSelect(null)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                DropdownMenuItem(
                    text = { Text("Crear nueva categoría", style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    onClick = { if (expanded) onCreate() },
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
        contentPadding = PaddingValues(horizontal = 10.dp),
        onClick = onClick,
    )
}
