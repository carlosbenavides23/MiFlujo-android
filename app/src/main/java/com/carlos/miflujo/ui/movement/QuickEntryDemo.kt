package com.carlos.miflujo.ui.movement

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.carlos.miflujo.domain.model.*
import com.carlos.miflujo.ui.formatVisibleDate
import com.carlos.miflujo.ui.theme.financePositiveColor
import com.carlos.miflujo.ui.theme.financeNegativeColor
import java.time.LocalDate

@Composable
internal fun QuickEntryDemo(onClose: () -> Unit) {
    var state by rememberSaveable { mutableStateOf(QuickEntryState()) }
    val hapticFeedback = LocalHapticFeedback.current
    val haptics = remember(hapticFeedback) { QuickEntryHaptics(hapticFeedback) }
    val active = state.active
    val pending = state.pending
    val editing = state.editingId
    val categoryMenu = remember { SelectorMenuState() }
    val subcategoryMenu = remember { SelectorMenuState() }
    var transfer by remember { mutableStateOf<PendingTransfer?>(null) }
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }
    var targetBounds by remember { mutableStateOf<Rect?>(null) }
    var hostBounds by remember { mutableStateOf<Rect?>(null) }
    var selectorHostBounds by remember { mutableStateOf<Rect?>(null) }
    var transferReady by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val progress = remember { Animatable(0f) }
    val sourceHeight = with(LocalDensity.current) { 56.dp.toPx() }
    val transferId = transfer?.entry?.id
    val targetExists = pending.any { it.id == transferId }
    LaunchedEffect(transferId, targetExists, editing, state.exitRequested, state.deleteRequestedId, state.confirmedCount) {
        val flight = transfer ?: return@LaunchedEffect
        try {
            transferReady = false
            if (!targetExists || editing != null || state.exitRequested || state.deleteRequestedId != null || state.confirmedCount != null) return@LaunchedEffect
            val motionDisabled = coroutineContext[MotionDurationScale]?.scaleFactor == 0f
            progress.snapTo(0f)
            // Reveal an appended destination without changing the data or snapping the scroll.
            val index = state.pending.indexOfFirst { it.id == flight.entry.id }
            if (index < 0) return@LaunchedEffect
            val laidOut = withTimeoutOrNull(700) {
                snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > index }
            }
            if (laidOut == null) return@LaunchedEffect
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.find { it.key == flight.entry.id }
            if (item == null || item.offset < layout.viewportStartOffset || item.offset + item.size > layout.viewportEndOffset) {
                listState.animateScrollToItem(index)
            }
            if (motionDisabled) return@LaunchedEffect
            val measured = withTimeoutOrNull(700) {
                snapshotFlow { targetBounds }.filterNotNull().first()
            }
            if (measured != null) {
                transferReady = true
                progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
            }
        } finally {
            // Repeated adds, removal, screen disposal and cancelled scroll never affect the queue.
            if (transfer?.entry?.id == flight.entry.id) {
                transfer = null
                transferReady = false
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    // Keep a neutral surface suited to the active color scheme.
    val activeSurface = if (colors.background.luminance() < 0.5f) Color(0xFF1B1E23) else colors.surfaceContainerHigh
    fun close() { if (state.hasWork) state = state.requestExit() else onClose() }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { selectorHostBounds = it.boundsInWindow() }
                .pointerInput(categoryMenu, subcategoryMenu) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                            val origin = selectorHostBounds?.topLeft
                            if (down != null && origin != null) {
                                val position = down.position + origin
                                categoryMenu.dismissOutside(position)
                                subcategoryMenu.dismissOutside(position)
                            }
                        }
                    }
                }
                .padding(12.dp).imePadding(), shape = RoundedCornerShape(28.dp)) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { hostBounds = it.boundsInWindow() }) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Registro con cápsulas", style = MaterialTheme.typography.titleLarge)
                Text("Demostración: nada se guarda. Use datos ficticios.", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pending.isEmpty()) item { Text("Los movimientos pendientes aparecerán aquí.") }
                    items(pending, key = { it.id }) { entry ->
                        val draft = entry.draft
                        val isEditing = editing == entry.id
                        val canEdit = editing == null && !state.dirty
                        val editDescription = when {
                            isEditing -> "Movimiento en edición"
                            editing != null -> "Edición no disponible: guarde o cancele la edición actual"
                            state.dirty -> "Edición no disponible: termine la captura actual"
                            else -> "Editar movimiento"
                        }
                        val borderColor by animateColorAsState(
                            if (isEditing) colors.primary else colors.outlineVariant,
                            tween(180), label = "pendingEditing",
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh,
                            contentColor = colors.onSurface,
                            border = BorderStroke(1.dp, borderColor),
                            modifier = Modifier.fillMaxWidth()
                                .animateItem(fadeInSpec = null, placementSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(150))
                                .onGloballyPositioned { if (transferId == entry.id) targetBounds = it.boundsInWindow() }
                                .graphicsLayer { alpha = if (transferId == entry.id) 0f else 1f },
                        ) {
                            PendingSummary(draft, isEditing = isEditing) {
                                IconButton(onClick = {
                                    val previousId = state.editingId
                                    state = state.edit(entry.id)
                                    if (previousId == null && state.editingId == entry.id) haptics.perform(QuickEntryHaptics.Action.EditStarted)
                                }, enabled = canEdit, modifier = Modifier.size(PendingActionSize)) {
                                    Icon(Icons.Default.Edit, contentDescription = editDescription)
                                }
                                PendingMenu(onDelete = { state = state.requestDelete(entry.id) })
                            }
                        }
                    }
                    if (pending.isNotEmpty() && (state.dirty || editing != null)) {
                        item(key = "edit-unavailable") {
                            Text(
                                if (editing != null) "Guarde o cancele la edición para editar otro movimiento."
                                else "Termine la captura para editar un movimiento.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(24.dp), color = activeSurface, contentColor = colors.onSurface, modifier = Modifier.fillMaxWidth().onGloballyPositioned { sourceBounds = it.boundsInWindow() }) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (editing != null) Text("Editando movimiento", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactEntryField(active.amount, { state = state.update(active.copy(amount = it)) }, "Monto", Modifier.weight(1f), KeyboardType.Decimal)
                            CapsuleToggle(
                                value = active.currency, label = if (active.currency == Currency.CORDOBA) "Córdobas, cambiar a dólares" else "Dólares, cambiar a córdobas",
                                accent = if (active.currency == Currency.CORDOBA) CapsuleToggleColors.Cordoba else CapsuleToggleColors.Dollar,
                                foreground = if (active.currency == Currency.CORDOBA) CapsuleToggleColors.CordobaText else Color.White,
                                onClick = {
                                    val previous = state.active.currency
                                    state = state.update(state.active.copy(currency = if (previous == Currency.CORDOBA) Currency.DOLLAR else Currency.CORDOBA))
                                    if (state.active.currency != previous) haptics.perform(QuickEntryHaptics.Action.ToggleCurrency)
                                },
                            ) { currency -> CurrencyMorph(currency) }
                            CapsuleToggle(
                                value = active.type, label = if (active.type == MovementType.INCOME) "Ingreso, cambiar a egreso" else "Egreso, cambiar a ingreso",
                                accent = if (active.type == MovementType.INCOME) CapsuleToggleColors.Income else CapsuleToggleColors.Expense,
                                onClick = {
                                    state = state.update(state.active.copy(type = if (state.active.type == MovementType.INCOME) MovementType.EXPENSE else MovementType.INCOME, category = null, subcategory = null))
                                    haptics.perform(QuickEntryHaptics.Action.ToggleType)
                                },
                            ) { type -> MovementSymbol(type) }
                        }
                        Column {
                            CompactEntryField(active.detail, { state = state.update(active.copy(detail = it)) }, "Detalle (opcional)", Modifier.fillMaxWidth(), secondary = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                val context = LocalContext.current
                                CapsuleMenuControl(icon = Icons.Default.CalendarToday, label = "Fecha", text = if (active.date == LocalDate.now()) "Hoy" else active.date.formatVisibleDate(), modifier = Modifier.weight(1f), onClick = { DatePickerDialog(context, { _, y, m, d -> state = state.update(active.copy(date = LocalDate.of(y, m + 1, d))) }, active.date.year, active.date.monthValue - 1, active.date.dayOfMonth).show() })
                                if (active.type == MovementType.EXPENSE) CategoryMenu(active.category, categoryMenu, Modifier.weight(1f)) { state = state.update(active.copy(category = it, subcategory = null)) }
                                else Text("Ingreso general", modifier = Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (active.type == MovementType.EXPENSE && active.category == MovementCategory.FIXED_COST) SubcategoryMenu(active.subcategory, subcategoryMenu) { state = state.update(active.copy(subcategory = it)) }
                    }
                }
                state.message?.let {
                    Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { close() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Salir") }
                    if (editing == null) {
                        OutlinedButton(onClick = {
                            val before = state.pending.size
                            val previousMessage = state.message
                            state = state.add()
                            if (state.pending.size > before) {
                                haptics.perform(QuickEntryHaptics.Action.Added)
                                targetBounds = null
                                transferReady = false
                                transfer = sourceBounds?.let { bounds ->
                                    PendingTransfer(state.pending.last(), Rect(bounds.left, bounds.top, bounds.right, bounds.top + minOf(bounds.height, sourceHeight)))
                                }
                            } else if (state.message != null && state.message != previousMessage) {
                                haptics.perform(QuickEntryHaptics.Action.ValidationRejected)
                            }
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Agregar otro") }
                        Button(onClick = {
                            val previousMessage = state.message
                            state = state.confirm()
                            if (state.message != null && state.message != previousMessage) haptics.perform(QuickEntryHaptics.Action.ValidationRejected)
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Confirmar") }
                    } else {
                        OutlinedButton(onClick = { state = state.cancelEdit() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") }
                        Button(onClick = {
                            val previousMessage = state.message
                            state = state.saveEdit()
                            if (state.message != null && state.message != previousMessage) haptics.perform(QuickEntryHaptics.Action.ValidationRejected)
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Guardar cambios") }
                    }
                }
            }
            val flight = transfer
            val host = hostBounds
            if (flight != null && host != null && transferReady) {
                TransferOverlay(flight, targetBounds ?: flight.source, host, progress, activeSurface)
            }
            }
        }
    }
    if (state.exitRequested) AlertDialog(
        onDismissRequest = { state = state.copy(exitRequested = false) },
        title = { Text("Salir de la demostración") }, text = { Text("Se descartarán los movimientos provisionales y los cambios de la cápsula activa.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Descartar y salir") } },
        dismissButton = { TextButton(onClick = { state = state.copy(exitRequested = false) }) { Text("Continuar") } },
    )
    if (state.deleteRequestedId != null) AlertDialog(
        onDismissRequest = { state = state.copy(deleteRequestedId = null) },
        title = { Text("Eliminar movimiento provisional") }, text = { Text("Se quitará únicamente este movimiento de la cola. Si está en edición, también se descartarán sus cambios.") },
        confirmButton = { TextButton(onClick = {
            val before = state.pending.size
            state = state.deleteRequested()
            if (state.pending.size < before) haptics.perform(QuickEntryHaptics.Action.Deleted)
        }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { state = state.copy(deleteRequestedId = null) }) { Text("Cancelar") } },
    )
    if (state.confirmedCount != null) AlertDialog(
        onDismissRequest = { state = state.copy(confirmedCount = null) },
        title = { Text("Confirmación de demostración") }, text = { Text("Hay ${state.confirmedCount} movimiento(s) provisionales. No se guardó ningún movimiento. Al salir se descartarán.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Descartar y salir") } },
        dismissButton = { TextButton(onClick = { state = state.copy(confirmedCount = null) }) { Text("Seguir revisando") } },
    )
}

// Visual snapshot only: never participates in validation or queue transitions.
private data class PendingTransfer(val entry: PendingEntry, val source: Rect)
private val PendingActionSize = 48.dp

@Composable
private fun TransferOverlay(flight: PendingTransfer, target: Rect, host: Rect, progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, sourceColor: androidx.compose.ui.graphics.Color) {
    val density = LocalDensity.current
    val draft = flight.entry.draft
    Surface(
        modifier = Modifier
            .offset { IntOffset((target.left - host.left).roundToInt(), (target.top - host.top).roundToInt()) }
            .size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
            .graphicsLayer {
                val t = progress.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                translationX = (flight.source.left - target.left) * (1f - t)
                translationY = (flight.source.top - target.top) * (1f - t)
                scaleX = (flight.source.width + (target.width - flight.source.width) * t) / target.width.coerceAtLeast(1f)
                scaleY = (flight.source.height + (target.height - flight.source.height) * t) / target.height.coerceAtLeast(1f)
            }
            .clearAndSetSemantics { },
        shape = RoundedCornerShape(20.dp),
        color = lerp(sourceColor, MaterialTheme.colorScheme.surfaceContainerHigh, progress.value.coerceIn(0f, 1f)),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        PendingSummary(draft) { Spacer(Modifier.size(width = PendingActionSize * 2, height = PendingActionSize)) }
    }
}

@Composable
private fun PendingSummary(draft: QuickEntryDraft, isEditing: Boolean = false, actions: @Composable RowScope.() -> Unit) {
    val amountColor = if (draft.type == MovementType.INCOME) financePositiveColor() else financeNegativeColor()
    val category = listOf(categoryLabel(draft.category), subcategoryLabel(draft.subcategory))
        .filter { it.isNotBlank() }.joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${if (draft.type == MovementType.INCOME) "+" else "−"} ${if (draft.currency == Currency.CORDOBA) "C$" else "US$"} ${draft.amount}",
                style = MaterialTheme.typography.titleMedium, color = amountColor,
            )
            if (isEditing) Text("En edición", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (draft.detail.isNotBlank()) Text(
                draft.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (category.isNotBlank()) Text(
                    category, modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(draft.date.formatVisibleDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

private object CapsuleToggleColors {
    val Income = Color(0xFF218B53)
    val Expense = Color(0xFFC34850)
    val Cordoba = Color(0xFF8995A5)
    val CordobaText = Color(0xFF20242B)
    val Dollar = Color(0xFF5B7C64)
}

@Composable
private fun <T> CapsuleToggle(value: T, label: String, accent: androidx.compose.ui.graphics.Color, foreground: Color = Color.White, onClick: () -> Unit, content: @Composable (T) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium), label = "togglePress",
    )
    val backgroundColor by animateColorAsState(accent, tween(160), label = "toggleBackground")
    val symbolColor by animateColorAsState(foreground, tween(160), label = "toggleForeground")
    Button(
        onClick = onClick, interactionSource = interaction,
        modifier = Modifier.width(56.dp).height(48.dp).semantics { contentDescription = label }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = symbolColor),
    ) {
        content(value)
    }
}

@Composable
private fun MovementSymbol(type: MovementType) {
    val vertical by animateFloatAsState(
        targetValue = if (type == MovementType.INCOME) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "movementSymbol",
    )
    val symbolColor = LocalContentColor.current
    Canvas(Modifier.size(20.dp).clearAndSetSemantics { }) {
        val half = size.minDimension * 0.32f
        val thickness = 2.dp.toPx()
        drawLine(symbolColor, Offset(center.x - half, center.y), Offset(center.x + half, center.y), thickness, cap = StrokeCap.Round)
        val length = half * vertical.coerceIn(0f, 1f)
        if (length > 0f) {
            drawLine(symbolColor, Offset(center.x, center.y - length), Offset(center.x, center.y + length), thickness, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun PendingMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(PendingActionSize)) { Icon(Icons.Default.MoreVert, contentDescription = "Opciones del movimiento") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { expanded = false; onDelete() })
        }
    }
}

private fun categoryLabel(value: MovementCategory?) = when (value) { MovementCategory.FIXED_COST -> "Costos fijos"; MovementCategory.MAINTENANCE -> "Mantenimiento"; MovementCategory.OTHER -> "Otros"; else -> "" }
private fun subcategoryLabel(value: MovementSubcategory?) = when (value) { MovementSubcategory.WATER -> "Agua"; MovementSubcategory.ELECTRICITY -> "Luz"; MovementSubcategory.INTERNET -> "Internet"; null -> "" }

@Composable
private fun CapsuleMenuControl(icon: ImageVector, label: String, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "$label: $text" }.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(4.dp))
        Text(text, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

// Outside taps are handled in the dialog, excluding the anchor. A non-focusable
// popup otherwise dismisses on DOWN before the anchor receives its own click.
private class SelectorMenuState {
    var expanded by mutableStateOf(false)
    var anchor: Rect? = null

    fun dismissOutside(position: Offset) {
        if (expanded && anchor?.contains(position) != true) expanded = false
    }
}

@Composable
private fun CategoryMenu(value: MovementCategory?, menu: SelectorMenuState, modifier: Modifier = Modifier, onSelect: (MovementCategory) -> Unit) {
    DisposableEffect(menu) { onDispose { menu.expanded = false; menu.anchor = null } }
    BackHandler(enabled = menu.expanded) { menu.expanded = false }
    Box(modifier.onGloballyPositioned { menu.anchor = it.boundsInWindow() }) {
        CapsuleMenuControl(icon = Icons.Default.Category, label = "Categoría", text = categoryLabel(value).ifBlank { "Categoría" }, modifier = Modifier.fillMaxWidth(), onClick = { menu.expanded = !menu.expanded })
        DropdownMenu(expanded = menu.expanded, onDismissRequest = { menu.expanded = false }, properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
            listOf(MovementCategory.FIXED_COST, MovementCategory.MAINTENANCE, MovementCategory.OTHER).forEach { item -> DropdownMenuItem(text = { Text(categoryLabel(item)) }, onClick = { onSelect(item); menu.expanded = false }) }
        }
    }
}
@Composable
private fun SubcategoryMenu(value: MovementSubcategory?, menu: SelectorMenuState, onSelect: (MovementSubcategory) -> Unit) {
    DisposableEffect(menu) { onDispose { menu.expanded = false; menu.anchor = null } }
    BackHandler(enabled = menu.expanded) { menu.expanded = false }
    Box(Modifier.onGloballyPositioned { menu.anchor = it.boundsInWindow() }) { OutlinedButton(onClick = { menu.expanded = !menu.expanded }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text(subcategoryLabel(value).ifBlank { "Subcategoría" }) }
        DropdownMenu(expanded = menu.expanded, onDismissRequest = { menu.expanded = false }, properties = PopupProperties(focusable = false, dismissOnClickOutside = false)) {
            MovementSubcategory.entries.forEach { item -> DropdownMenuItem(text = { Text(subcategoryLabel(item)) }, onClick = { onSelect(item); menu.expanded = false }) }
        }
    }
}

@Composable
private fun CompactEntryField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text, secondary: Boolean = false) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = (if (secondary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge).copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.heightIn(min = 48.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(start = 8.dp, end = if (value.isEmpty()) 8.dp else 0.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inner()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar $label", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}
