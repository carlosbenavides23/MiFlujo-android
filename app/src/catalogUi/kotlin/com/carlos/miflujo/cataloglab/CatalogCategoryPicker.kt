package com.carlos.miflujo.cataloglab

import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
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
import com.carlos.miflujo.ui.movement.CategoryPickerMotion
import com.carlos.miflujo.ui.movement.rememberCategoryPickerMotion

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
    motion: CategoryPickerMotion = rememberCategoryPickerMotion(expanded),
) {
    if (!motion.isVisible) return

    // Keep the order from opening, but use live metadata and eligibility.
    val openingOrder = remember(type) { categories.map { it.uuid } }
    val options = remember(categories, type, openingOrder) {
        val available = categories.filter { !it.archived && it.scope.supports(type) }.associateBy { it.uuid }
        val original = openingOrder.mapNotNull(available::get)
        val originalIds = openingOrder.toHashSet()
        original + available.values.filterNot { it.uuid in originalIds }
    }
    // Keep the outgoing rows in place if the chosen label wraps when its check appears.
    var previousSelection by remember { mutableStateOf(selectedUuid) }
    SideEffect { if (expanded) previousSelection = selectedUuid }
    val visibleSelection = if (expanded) selectedUuid else previousSelection

    val density = LocalDensity.current
    val gap = with(density) { 4.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val maxHeight = with(density) { 260.dp.roundToPx() }
    var availableHeight by remember { mutableIntStateOf(maxHeight) }
    var origin by remember { mutableStateOf(CategoryPickerOrigin()) }
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
                // The popup includes the gap so the reveal can reach the actual control edge.
                val wantedY = if (openAbove) anchorBounds.top - popupContentSize.height else anchorBounds.bottom
                val y = wantedY.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
                val anchorCenterX = anchorBounds.left + anchorBounds.width / 2f
                origin = CategoryPickerOrigin(
                    above = openAbove,
                    x = anchorCenterX - x,
                    y = (if (openAbove) anchorBounds.top else anchorBounds.bottom).toFloat() - y,
                )
                return IntOffset(x, y)
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
        val spacing = with(density) { gap.toDp() }
        Surface(
            Modifier
                .semantics { if (!expanded) hideFromAccessibility() }
                .categoryPickerReveal(motion, spacing) { origin }
                .padding(
                    top = if (origin.above) 0.dp else spacing,
                    bottom = if (origin.above) spacing else 0.dp,
                )
                .width(236.dp)
                .heightIn(max = with(density) { availableHeight.toDp() }),
            shape = MaterialTheme.shapes.medium,
            color = background,
            shadowElevation = 4.dp,
        ) {
            Column {
                if (options.isNotEmpty() || allowUncategorized) {
                    LazyColumn(Modifier.weight(1f, fill = false).testTag("category-picker-list")) {
                        items(options, key = { it.uuid }) { category ->
                            CategoryPickerRow(category, visibleSelection == category.uuid, background) {
                                if (expanded) onSelect(category.uuid)
                            }
                        }
                        if (allowUncategorized) {
                            item(key = "uncategorized") {
                                CategoryPickerRow(null, visibleSelection == null, background) {
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

private data class CategoryPickerOrigin(
    val above: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
)

private val revealEasing = CubicBezierEasing(0.23f, 0.05f, 0.28f, 1f)

private fun Modifier.categoryPickerReveal(
    motion: CategoryPickerMotion,
    gap: Dp,
    origin: () -> CategoryPickerOrigin,
): Modifier = graphicsLayer {
    alpha = (2.2f * revealEasing.transform(motion.progress)).coerceAtMost(1f)
}.drawWithCache {
    val outline = Path()
    val travel = gap.toPx()
    val maxRadius = 12.dp.toPx()

    onDrawWithContent {
        val progress = motion.progress
        if (progress >= 1f) {
            drawContent()
        } else if (progress > 0f) {
            // Expand the visible area from the measured anchor without scaling the rows.
            val anchor = origin()
            val x = anchor.x.coerceIn(0f, size.width)
            val y = anchor.y.coerceIn(0f, size.height)
            val rise = revealEasing.transform(progress)
            val spread = LinearOutSlowInEasing.transform(rise)
            val left = x * (1f - spread)
            val right = x + (size.width - x) * spread
            val top = y * (1f - rise)
            val bottom = y + (size.height - y) * rise
            val radius = minOf(maxRadius, (right - left) / 2f, (bottom - top) / 2f)
            outline.reset()
            outline.addRoundRect(RoundRect(left, top, right, bottom, radius, radius))
            val lift = travel * (1f - rise) * if (anchor.above) 1f else -1f
            clipPath(outline) { translate(top = lift) { this@onDrawWithContent.drawContent() } }
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
