package com.carlos.miflujo.ui.movement

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import com.carlos.miflujo.domain.model.*
import com.carlos.miflujo.ui.formatVisibleDate
import com.carlos.miflujo.ui.theme.financePositiveColor
import com.carlos.miflujo.ui.theme.financeNegativeColor
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal class QuickEntryCatalog(
    val initialDraft: QuickEntryDraft?,
    val validate: (QuickEntryDraft) -> String?,
    val changeType: (QuickEntryDraft, MovementType, QuickEntryDraft?) -> QuickEntryDraft,
    val selectCategory: (QuickEntryDraft, String?, QuickEntryDraft?) -> QuickEntryDraft,
    val categoryLabel: (String?) -> String,
    val categoryIcon: @Composable (String?, Modifier, Color) -> Unit,
    val categoryPicker: @Composable (
        expanded: Boolean, motion: CategoryPickerMotion, type: MovementType, selectedUuid: String?,
        onDismiss: () -> Unit, select: (String?) -> Unit, create: () -> Unit,
    ) -> Unit,
    val categoryEditor: @Composable (onCreated: (String) -> Unit, onDismiss: () -> Unit) -> Unit,
    val startSave: (String, List<PendingEntry>) -> Boolean,
    val awaitSave: suspend (String, List<PendingEntry>) -> Boolean,
)

@Composable
internal fun QuickEntryDialog(onClose: () -> Unit, catalog: QuickEntryCatalog? = null) {
    var state by rememberSaveable {
        mutableStateOf(QuickEntryState(creationDraft = catalog?.initialDraft ?: QuickEntryDraft()))
    }
    val focus = LocalFocusManager.current
    var activeId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var draftRestorations by remember { mutableIntStateOf(0) }
    var submissionId by rememberSaveable { mutableStateOf<String?>(null) }
    val saving = submissionId != null
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    val validate = catalog?.validate ?: QuickEntryDraft::validationMessage
    val editingStored = catalog?.initialDraft != null
    fun selectCategory(uuid: String?, createdInEditor: Boolean = false) {
        if (catalog == null) return
        val selected = catalog.selectCategory(state.active, uuid, state.baseline.takeIf { editingStored })
        state = state.update(selected.copy(catalogCategoryCreatedInEditor = createdInEditor))
    }
    fun ifEditable(action: () -> Unit) {
        if (submissionId == null) action()
    }
    fun editableAction(action: () -> Unit): () -> Unit = { ifEditable(action) }

    fun submissionEntries(): List<PendingEntry> =
        if (editingStored || state.includesCreationDraft) {
            state.pending + PendingEntry(activeId, state.active)
        } else state.pending

    fun save() {
        if (submissionId != null || catalog == null || choosingDate || creatingCategory ||
            state.exitRequested || state.deleteRequestedId != null || state.editingId != null
        ) return
        val id = UUID.randomUUID().toString()
        submissionId = id
        focus.clearFocus()
        if (!catalog.startSave(id, submissionEntries())) submissionId = null
    }
    val closeAfterSave by rememberUpdatedState(onClose)
    LaunchedEffect(submissionId) {
        val id = submissionId ?: return@LaunchedEffect
        val entries = submissionEntries()
        val saveError = "No se pudo guardar. Revisá la categoría e intentá de nuevo."
        val pendingSave = checkNotNull(catalog)
        var confirmed: Boolean
        while (true) {
            try {
                confirmed = pendingSave.awaitSave(id, entries)
                break
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // A failed reconciliation read does not tell us whether the batch was saved.
                state = state.copy(message = saveError)
                delay(1_000)
            }
        }
        if (confirmed) {
            closeAfterSave()
        } else {
            state = state.copy(confirmedCount = null, message = saveError)
            submissionId = null
        }
    }
    var showMorphInspector by remember { mutableStateOf(false) }
    val debuggable = LocalContext.current.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
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
    var transferHost by remember { mutableStateOf<LayoutCoordinates?>(null) }
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
            if (layout.viewportEndOffset <= layout.viewportStartOffset) return@LaunchedEffect
            val item = layout.visibleItemsInfo.find { it.key == flight.entry.id }
            if (item == null || item.offset < layout.viewportStartOffset || item.offset + item.size > layout.viewportEndOffset) {
                listState.animateScrollToItem(index)
            }
            if (motionDisabled) return@LaunchedEffect
            val measured = withTimeoutOrNull(700) {
                snapshotFlow { targetBounds }.filterNotNull().first()
            }
            if (measured != null) {
                // The dialog can grow and recenter before the transfer starts.
                sourceBounds?.let { transfer = flight.copy(source = it) }
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
    fun close() = ifEditable {
        if (state.hasWork) state = state.requestExit() else onClose()
    }
    QuickEntryWindow(
        onDismissRequest = editableAction {
            if (catalog != null && categoryMenu.expanded) categoryMenu.expanded = false else close()
        },
    ) {
        Surface(modifier = Modifier.fillMaxWidth()
                .onGloballyPositioned { selectorHostBounds = it.boundsInWindow() }
                .pointerInput(categoryMenu, subcategoryMenu, saving) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (saving) {
                                event.changes.forEach { it.consume() }
                                continue
                            }
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
                .padding(12.dp), shape = RoundedCornerShape(28.dp)) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { transferHost = it }) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (editingStored) "Editar movimiento" else "Registro con cápsulas", style = MaterialTheme.typography.titleLarge)
                if (catalog == null) Text("Demostración: nada se guarda. Use datos ficticios.", style = MaterialTheme.typography.bodySmall)
                if (debuggable && showMorphInspector) CurrencyMorphInspector(onClose = { showMorphInspector = false })
                if (pending.isNotEmpty()) LazyColumn(
                    // Measure the fixed controls first. A short queue can return any unused height.
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                ) {
                    items(pending, key = { it.id }) { entry ->
                        val draft = entry.draft
                        val isEditing = editing == entry.id
                        val editDescription = when {
                            isEditing -> "Movimiento en edición"
                            editing != null -> "Edición no disponible: guarde o cancele la edición actual"
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
                                .onGloballyPositioned {
                                    if (transferId == entry.id) {
                                        targetBounds = transferHost?.localBoundingBoxOf(it)
                                            ?.takeUnless { bounds -> bounds.isEmpty }
                                    }
                                }
                                .graphicsLayer { alpha = if (transferId == entry.id) 0f else 1f },
                        ) {
                            PendingSummary(draft, catalogLabel = catalog?.categoryLabel?.invoke(draft.catalogCategoryUuid)) {
                                IconButton(onClick = editableAction {
                                    val previousId = state.editingId
                                    state = state.edit(entry.id)
                                    if (previousId == null && state.editingId == entry.id) haptics.perform(QuickEntryHaptics.Action.EditStarted)
                                }, enabled = editing == null && !saving, modifier = Modifier.size(PendingActionSize)) {
                                    Icon(Icons.Default.Edit, contentDescription = editDescription)
                                }
                                PendingMenu(enabled = !saving, onDelete = { ifEditable { state = state.requestDelete(entry.id) } })
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = activeSurface,
                    contentColor = colors.onSurface,
                    modifier = Modifier.fillMaxWidth().onGloballyPositioned {
                        sourceBounds = transferHost?.localBoundingBoxOf(it)?.let { bounds ->
                            Rect(bounds.left, bounds.top, bounds.right, bounds.top + minOf(bounds.height, sourceHeight))
                        }
                    },
                ) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (editing != null) Text(
                            "Editando movimiento",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CompactEntryField(
                                active.amount, { amount -> ifEditable { state = state.update(state.active.copy(amount = amount)) } },
                                "Monto", Modifier.weight(1f), KeyboardType.Decimal,
                                selectionReset = draftRestorations,
                                readOnly = saving,
                            )
                            CapsuleToggle(
                                value = active.currency, label = if (active.currency == Currency.CORDOBA) "Córdobas, cambiar a dólares" else "Dólares, cambiar a córdobas",
                                accent = if (active.currency == Currency.CORDOBA) CapsuleToggleColors.Cordoba else CapsuleToggleColors.Dollar,
                                foreground = if (active.currency == Currency.CORDOBA) CapsuleToggleColors.CordobaText else Color.White,
                                onLongClick = if (debuggable) ({ ifEditable { showMorphInspector = true } }) else null,
                                onClick = editableAction {
                                    val previous = state.active.currency
                                    state = state.update(state.active.copy(currency = if (previous == Currency.CORDOBA) Currency.DOLLAR else Currency.CORDOBA))
                                    if (state.active.currency != previous) haptics.perform(QuickEntryHaptics.Action.ToggleCurrency)
                                },
                            ) { currency -> CurrencyMorph(currency) }
                            CapsuleToggle(
                                value = active.type, label = if (active.type == MovementType.INCOME) "Ingreso, cambiar a egreso" else "Egreso, cambiar a ingreso",
                                accent = if (active.type == MovementType.INCOME) CapsuleToggleColors.Income else CapsuleToggleColors.Expense,
                                onClick = editableAction {
                                    val draft = state.active
                                    val type = if (draft.type == MovementType.INCOME) MovementType.EXPENSE else MovementType.INCOME
                                    val updated = catalog?.changeType?.invoke(draft, type, state.baseline.takeIf { editingStored })
                                        ?: draft.copy(type = type, category = null, subcategory = null, catalogCategoryUuid = null)
                                    state = state.update(updated)
                                    haptics.perform(QuickEntryHaptics.Action.ToggleType)
                                },
                            ) { type -> MovementSymbol(type) }
                        }
                        Column {
                            CompactEntryField(
                                active.detail, { detail -> ifEditable { state = state.update(state.active.copy(detail = detail)) } },
                                "Detalle (opcional)", Modifier.fillMaxWidth(), secondary = true,
                                selectionReset = draftRestorations,
                                readOnly = saving,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                CapsuleMenuControl(
                                    icon = Icons.Default.CalendarToday,
                                    label = "Fecha",
                                    text = if (active.date == LocalDate.now()) "Hoy" else active.date.formatVisibleDate(),
                                    modifier = Modifier.weight(1f),
                                    onClick = editableAction { choosingDate = true },
                                )
                                if (catalog != null) {
                                    Box(Modifier.weight(1f).onGloballyPositioned { categoryMenu.anchor = it.boundsInWindow() }) {
                                        val motion = rememberCategoryPickerMotion(categoryMenu.expanded)
                                        CapsuleMenuControl(
                                            label = "Categoría",
                                            text = active.catalogCategoryUuid?.let(catalog.categoryLabel) ?: "Categoría",
                                            modifier = Modifier.fillMaxWidth(),
                                            onClick = editableAction { categoryMenu.expanded = !categoryMenu.expanded },
                                            announceLabelOnly = true,
                                            pressScale = true,
                                        ) { iconModifier ->
                                            catalog.categoryIcon(active.catalogCategoryUuid, iconModifier, activeSurface)
                                        }
                                        catalog.categoryPicker(
                                            categoryMenu.expanded, motion, active.type, active.catalogCategoryUuid,
                                            editableAction { categoryMenu.expanded = false },
                                            { uuid -> ifEditable {
                                                selectCategory(uuid)
                                                categoryMenu.expanded = false
                                            } },
                                            editableAction {
                                                categoryMenu.expanded = false
                                                creatingCategory = true
                                            },
                                        )
                                    }
                                } else if (active.type == MovementType.EXPENSE) {
                                    CategoryMenu(active.category, categoryMenu, Modifier.weight(1f)) {
                                        state = state.update(active.copy(category = it, subcategory = null))
                                    }
                                } else {
                                    Text("Ingreso general", modifier = Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (catalog == null && active.type == MovementType.EXPENSE && active.category == MovementCategory.FIXED_COST) SubcategoryMenu(active.subcategory, subcategoryMenu) { state = state.update(active.copy(subcategory = it)) }
                    }
                }
                state.message?.let {
                    Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { close() }, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Salir") }
                    if (editingStored) {
                        Button(enabled = !saving, onClick = editableAction {
                            val error = validate(state.active)
                            if (error == null) save()
                            else {
                                state = state.copy(message = error)
                                haptics.perform(QuickEntryHaptics.Action.ValidationRejected)
                            }
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (saving) "Guardando…" else "Guardar cambios") }
                    } else if (editing == null) {
                        OutlinedButton(enabled = !saving, onClick = editableAction {
                            val before = state.pending.size
                            val previousMessage = state.message
                            state = state.add(validate, activeId)
                            if (state.pending.size > before) {
                                activeId = UUID.randomUUID().toString()
                                haptics.perform(QuickEntryHaptics.Action.Added)
                                targetBounds = null
                                transferReady = false
                                transfer = sourceBounds?.let { PendingTransfer(state.pending.last(), it) }
                            } else if (state.message != null && state.message != previousMessage) {
                                haptics.perform(QuickEntryHaptics.Action.ValidationRejected)
                            }
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Agregar otro") }
                        Button(enabled = !saving, onClick = editableAction {
                            val previousMessage = state.message
                            val confirmed = state.confirm(validate)
                            if (catalog != null && confirmed.confirmedCount != null) {
                                save()
                            } else state = confirmed
                            if (state.message != null && state.message != previousMessage) haptics.perform(QuickEntryHaptics.Action.ValidationRejected)
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (saving) "Guardando…" else "Confirmar") }
                    } else {
                        val editActionPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        OutlinedButton(
                            onClick = editableAction {
                                if (state.editingId != null) {
                                    state = state.cancelEdit()
                                    draftRestorations++
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = editActionPadding,
                        ) { Text("Cancelar") }
                        Button(
                            onClick = editableAction {
                                val previousMessage = state.message
                                val wasEditing = state.editingId != null
                                state = state.saveEdit(validate)
                                if (wasEditing && state.editingId == null) draftRestorations++
                                if (state.message != null && state.message != previousMessage) {
                                    haptics.perform(QuickEntryHaptics.Action.ValidationRejected)
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = editActionPadding,
                        ) { Text("Guardar") }
                    }
                }
            }
            val flight = transfer
            val target = targetBounds
            if (flight != null && target != null && transferReady) {
                Box(Modifier.matchParentSize()) {
                    TransferOverlay(flight, target, progress, activeSurface, catalog?.categoryLabel?.invoke(flight.entry.draft.catalogCategoryUuid))
                }
            }
            }
        }
    }
    if (choosingDate) {
        QuickEntryDateDialog(
            initialDate = active.date,
            onConfirm = { date -> ifEditable {
                state = state.update(state.active.copy(date = date))
                choosingDate = false
            } },
            onDismiss = editableAction { choosingDate = false },
        )
    }
    if (catalog != null && creatingCategory) {
        catalog.categoryEditor({ uuid -> ifEditable {
            selectCategory(uuid, createdInEditor = true)
            creatingCategory = false
        } }, editableAction { creatingCategory = false })
    }
    if (state.exitRequested) AlertDialog(
        onDismissRequest = editableAction { state = state.copy(exitRequested = false) },
        title = { Text(if (catalog == null) "Salir de la demostración" else "¿Descartar cambios?") },
        text = {
            Text(
                if (editing != null && state.dirty) {
                    "Se descartarán los movimientos provisionales, los cambios de la edición y el borrador del movimiento nuevo."
                } else {
                    "Se descartarán los movimientos provisionales y los cambios de la cápsula activa."
                }
            )
        },
        confirmButton = { TextButton(onClick = editableAction(onClose)) { Text("Descartar y salir") } },
        dismissButton = { TextButton(onClick = editableAction { state = state.copy(exitRequested = false) }) { Text("Continuar") } },
    )
    if (state.deleteRequestedId != null) AlertDialog(
        onDismissRequest = editableAction { state = state.copy(deleteRequestedId = null) },
        title = { Text("Eliminar movimiento provisional") }, text = { Text("Se quitará únicamente este movimiento de la cola. Si está en edición, también se descartarán sus cambios.") },
        confirmButton = { TextButton(onClick = editableAction {
            val before = state.pending.size
            state = state.deleteRequested()
            if (state.pending.size < before) haptics.perform(QuickEntryHaptics.Action.Deleted)
        }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = editableAction { state = state.copy(deleteRequestedId = null) }) { Text("Cancelar") } },
    )
    if (state.confirmedCount != null) AlertDialog(
        onDismissRequest = { state = state.copy(confirmedCount = null) },
        title = { Text("Confirmación de demostración") }, text = { Text("Hay ${state.confirmedCount} movimiento(s) provisionales. No se guardó ningún movimiento. Al salir se descartarán.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Descartar y salir") } },
        dismissButton = { TextButton(onClick = { state = state.copy(confirmedCount = null) }) { Text("Seguir revisando") } },
    )
}

@Composable
private fun QuickEntryWindow(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val dismissOnBackdrop by rememberUpdatedState(onDismissRequest)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val density = LocalDensity.current
        val keyboardInset = WindowInsets.ime.exclude(WindowInsets.systemBars.union(WindowInsets.displayCutout))
        val panelAlignment = Alignment { size, space, direction ->
            // Preserve the resting center until the keyboard makes that position unavailable.
            val restingSpace = IntSize(space.width, space.height + keyboardInset.getBottom(density))
            val centered = Alignment.Center.align(size, restingSpace, direction)
            IntOffset(centered.x, minOf(centered.y, space.height - size.height))
        }
        // Keep the window bounds stable while the compact panel uses the inset-adjusted space.
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = panelAlignment) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures { dismissOnBackdrop() }
            })
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntryDateDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDate = initialDate)
    val selectedDate = pickerState.getSelectedDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { selectedDate?.let(onConfirm) }, enabled = selectedDate != null) {
                Text("Aceptar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = pickerState, showModeToggle = false)
    }
}

// Visual snapshot only: never participates in validation or queue transitions.
private data class PendingTransfer(val entry: PendingEntry, val source: Rect)
private val PendingActionSize = 48.dp

@Composable
private fun TransferOverlay(flight: PendingTransfer, target: Rect, progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, sourceColor: androidx.compose.ui.graphics.Color, catalogLabel: String?) {
    val density = LocalDensity.current
    val draft = flight.entry.draft
    Surface(
        modifier = Modifier
            .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
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
        PendingSummary(draft, catalogLabel = catalogLabel) { Spacer(Modifier.size(width = PendingActionSize * 2, height = PendingActionSize)) }
    }
}

@Composable
private fun PendingSummary(draft: QuickEntryDraft, catalogLabel: String? = null, actions: @Composable RowScope.() -> Unit) {
    val amountColor = if (draft.type == MovementType.INCOME) financePositiveColor() else financeNegativeColor()
    val category = catalogLabel ?: listOf(categoryLabel(draft.category), subcategoryLabel(draft.subcategory))
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

internal object CapsuleToggleColors {
    val Income = Color(0xFF218B53)
    val Expense = Color(0xFFC34850)
    val Cordoba = Color(0xFF8995A5)
    val CordobaText = Color(0xFF20242B)
    val Dollar = Color(0xFF5B7C64)
}

@Composable
private fun CurrencyMorphInspector(onClose: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val foreground = lerp(CapsuleToggleColors.CordobaText, Color.White, progress)
    val background = lerp(CapsuleToggleColors.Cordoba, CapsuleToggleColors.Dollar, progress)
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(28.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Inspector de moneda", style = MaterialTheme.typography.titleLarge)
                // Measure at the real button size; scale only its final layer for inspection.
                Box(Modifier.size(width = 168.dp, height = 144.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(width = 56.dp, height = 48.dp)
                            .graphicsLayer { scaleX = 3f; scaleY = 3f },
                        shape = RoundedCornerShape(16.dp), color = background, contentColor = foreground,
                    ) {
                        CurrencyMorph(Currency.CORDOBA, manualProgress = progress)
                    }
                }
                Text("${(progress * 100).roundToInt()} %")
                Slider(
                    value = progress, onValueChange = { progress = it }, valueRange = 0f..1f,
                    modifier = Modifier.semantics { contentDescription = "Progreso de transformación de moneda" },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(0f, 0.5f, 1f).forEach { value ->
                        TextButton(onClick = { progress = value }) { Text("${(value * 100).roundToInt()} %") }
                    }
                }
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
        }
    }
}

@Composable
internal fun <T> CapsuleToggle(value: T, label: String, accent: androidx.compose.ui.graphics.Color, foreground: Color = Color.White, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable (T) -> Unit) {
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
        modifier = Modifier.width(56.dp).height(48.dp)
            .then(if (onLongClick != null) Modifier.pointerInput(onLongClick) {
                detectTapGestures(onLongPress = { onLongClick() })
            } else Modifier)
            .semantics { contentDescription = label }
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = symbolColor),
    ) {
        content(value)
    }
}

@Composable
internal fun MovementSymbol(type: MovementType) {
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
private fun PendingMenu(enabled: Boolean, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(PendingActionSize)) { Icon(Icons.Default.MoreVert, contentDescription = "Opciones del movimiento") }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { expanded = false; onDelete() })
        }
    }
}

private fun categoryLabel(value: MovementCategory?) = when (value) { MovementCategory.FIXED_COST -> "Costos fijos"; MovementCategory.MAINTENANCE -> "Mantenimiento"; MovementCategory.OTHER -> "Otros"; else -> "" }
private fun subcategoryLabel(value: MovementSubcategory?) = when (value) { MovementSubcategory.WATER -> "Agua"; MovementSubcategory.ELECTRICITY -> "Luz"; MovementSubcategory.INTERNET -> "Internet"; null -> "" }

@Composable
private fun CapsuleMenuControl(icon: ImageVector, label: String, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CapsuleMenuControl(label, text, modifier, onClick) {
        Icon(icon, contentDescription = null, modifier = it, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun CapsuleMenuControl(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    announceLabelOnly: Boolean = false,
    pressScale: Boolean = false,
    icon: @Composable (Modifier) -> Unit,
) {
    val interaction = if (pressScale) remember { MutableInteractionSource() } else null
    val contentMotion = if (
        interaction != null && rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor != 0f
    ) {
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.965f else 1f,
            animationSpec = if (pressed) {
                tween(45, easing = LinearOutSlowInEasing)
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                    visibilityThreshold = 0.001f,
                )
            },
            label = "categoryPress",
        )
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    } else Modifier

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, interactionSource = interaction, onClick = onClick)
            .semantics { contentDescription = "$label: $text" }
            .padding(horizontal = 4.dp)
            .then(contentMotion),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            modifier = Modifier.weight(1f, fill = false).then(
                if (announceLabelOnly) Modifier.clearAndSetSemantics {} else Modifier,
            ),
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

// Outside taps are handled in the dialog, excluding the anchor. A non-focusable
// popup otherwise dismisses on DOWN before the anchor receives its own click.
internal class SelectorMenuState {
    var expanded by mutableStateOf(false)
    var anchor: Rect? = null

    fun dismissOutside(position: Offset) {
        if (expanded && anchor?.contains(position) != true) expanded = false
    }
}

@Composable
private fun CategoryMenu(value: MovementCategory?, menu: SelectorMenuState, modifier: Modifier = Modifier, onSelect: (MovementCategory) -> Unit) {
    CategoryPopup(menu, modifier, anchor = { toggle ->
        CapsuleMenuControl(Icons.Default.Category, "Categoría", categoryLabel(value).ifBlank { "Categoría" }, Modifier.fillMaxWidth(), toggle)
    }) {
        listOf(MovementCategory.FIXED_COST, MovementCategory.MAINTENANCE, MovementCategory.OTHER).forEach { item ->
            DropdownMenuItem(text = { Text(categoryLabel(item)) }, onClick = { onSelect(item); menu.expanded = false })
        }
    }
}

@Composable
private fun CategoryPopup(
    menu: SelectorMenuState,
    modifier: Modifier,
    anchor: @Composable (toggle: () -> Unit) -> Unit,
    items: @Composable ColumnScope.() -> Unit,
) {
    DisposableEffect(menu) { onDispose { menu.expanded = false; menu.anchor = null } }
    BackHandler(enabled = menu.expanded) { menu.expanded = false }
    Box(modifier.onGloballyPositioned { menu.anchor = it.boundsInWindow() }) {
        anchor { menu.expanded = !menu.expanded }
        DropdownMenu(
            expanded = menu.expanded,
            onDismissRequest = { menu.expanded = false },
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
            content = items,
        )
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
internal fun CompactEntryField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    secondary: Boolean = false,
    selectionReset: Int = 0,
    readOnly: Boolean = false,
) {
    // Keep selection and IME composition local, while the form owns the text.
    var input by remember(selectionReset) {
        mutableStateOf(TextFieldValue(value, TextRange(if (selectionReset == 0) 0 else value.length)))
    }
    val fieldValue = input.copy(text = value)
    SideEffect {
        if (fieldValue.selection != input.selection || fieldValue.composition != input.composition) {
            input = fieldValue
        }
    }
    var lastText by remember(value, selectionReset) { mutableStateOf(value) }
    BasicTextField(
        value = fieldValue,
        onValueChange = { updated ->
            input = updated
            if (lastText != updated.text) {
                lastText = updated.text
                onChange(updated.text)
            }
        },
        singleLine = true,
        readOnly = readOnly,
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
                    IconButton(onClick = { onChange("") }, enabled = !readOnly, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar $label", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
    )
}
