package com.carlos.miflujo.cataloglab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.category.CategoryRepository
import com.carlos.miflujo.domain.category.Subcategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class EditKind { CREATE_PARENT, EDIT_PARENT, CREATE_CHILD, EDIT_CHILD }

@Composable
internal fun CatalogManagement(
    snapshot: CatalogBackup?,
    repository: CategoryRepository,
    modifier: Modifier,
    onBack: () -> Unit,
    refresh: suspend () -> Unit,
    loadError: String?,
) {
    val scope = rememberCoroutineScope()
    var archived by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf<EditKind?>(null) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runChange(block: suspend () -> Unit = {}) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                block()
                refresh()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                error = "No se pudo completar el cambio. Actualizá la lista e intentá de nuevo."
            } finally {
                busy = false
            }
        }
    }

    fun edit(kind: EditKind, uuid: String? = null) {
        editorId = uuid
        editor = kind
    }

    val childrenByParent = snapshot?.subcategories.orEmpty().groupBy { it.categoryUuid }
    val parents = snapshot?.categories.orEmpty().filter { parent ->
        if (archived) parent.archived || childrenByParent[parent.uuid].orEmpty().any { it.archived }
        else !parent.archived
    }

    BackHandler { if (!busy) onBack() }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onBack, enabled = !busy) { Text("Volver a Configuración") }
            Text("Categorías y subcategorías", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!archived, { archived = false }, label = { Text("Activas") }, enabled = !busy)
                FilterChip(archived, { archived = true }, label = { Text("Archivadas") }, enabled = !busy)
            }
            if (!archived && snapshot != null) Button(onClick = { edit(EditKind.CREATE_PARENT) }, enabled = !busy) {
                Text("Nueva categoría")
            }
            if ((snapshot == null && loadError == null) || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: loadError.takeIf { snapshot == null })?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { runChange() }, enabled = !busy) { Text("Actualizar") }
            }
            if (snapshot != null && parents.isEmpty()) Text(
                if (archived) "No hay elementos archivados." else "Todavía no hay categorías activas.",
            )
        }
        items(parents, key = { it.uuid }) { parent ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    TextButton(onClick = { expanded = if (expanded == parent.uuid) null else parent.uuid }) {
                        CatalogIcon(parent.iconKey, color = parent.iconColor, background = MaterialTheme.colorScheme.surfaceContainerLow)
                        Spacer(Modifier.width(8.dp))
                        Text(if (parent.archived) "${parent.name} (archivada)" else parent.name)
                    }
                    if (expanded == parent.uuid) {
                        ItemActions(
                            parent.archived, busy, onEdit = { edit(EditKind.EDIT_PARENT, parent.uuid) },
                            onArchive = { archiveId = parent.uuid },
                        )
                        val children = childrenByParent[parent.uuid].orEmpty().filter {
                            if (archived) parent.archived || it.archived else !it.archived
                        }
                        if (children.isEmpty()) Text("Sin subcategorías en esta vista.")
                        children.forEach { child ->
                            HorizontalDivider()
                            ListItem(
                                headlineContent = { Text(child.name) },
                                supportingContent = if (child.archived) ({ Text("Archivada") }) else null,
                                leadingContent = { CatalogIcon(child.iconKey, color = child.iconColor) },
                            )
                            ItemActions(
                                child.archived, busy, onEdit = { edit(EditKind.EDIT_CHILD, child.uuid) },
                                onArchive = { archiveId = child.uuid },
                                allowRestore = !parent.archived,
                            )
                        }
                        if (!archived && !parent.archived) TextButton(
                            enabled = !busy, onClick = { edit(EditKind.CREATE_CHILD, parent.uuid) },
                        ) { Text("Nueva subcategoría") }
                    }
                }
            }
        }
    }

    val kind = editor
    if (kind != null && snapshot != null) {
        val parent = snapshot.categories.firstOrNull { it.uuid == editorId }
        val child = snapshot.subcategories.firstOrNull { it.uuid == editorId }
        val initial = when (kind) {
            EditKind.EDIT_PARENT -> parent?.let { CatalogItemInput(it.name, it.iconKey, it.scope, it.reportGroup, it.iconColor) }
            EditKind.EDIT_CHILD -> child?.let { CatalogItemInput(it.name, it.iconKey, iconColor = it.iconColor) }
            EditKind.CREATE_PARENT -> CatalogItemInput()
            EditKind.CREATE_CHILD -> CatalogItemInput().takeIf { parent != null && !parent.archived }
        }
        if (initial != null) CatalogItemEditor(
            title = when (kind) {
                EditKind.CREATE_PARENT -> "Nueva categoría"
                EditKind.EDIT_PARENT -> "Editar categoría"
                EditKind.CREATE_CHILD -> "Nueva subcategoría"
                EditKind.EDIT_CHILD -> "Editar subcategoría"
            },
            initial = initial,
            saveLabel = if (kind == EditKind.CREATE_CHILD || kind == EditKind.EDIT_CHILD) "Guardar subcategoría" else "Guardar categoría",
            allowScope = kind == EditKind.CREATE_PARENT,
            onDismiss = { editor = null },
            save = { input, uuid ->
                when (kind) {
                    EditKind.CREATE_PARENT -> repository.createFromEditor(input, uuid)
                    EditKind.EDIT_PARENT -> {
                        val target = checkNotNull(parent)
                        val row = checkNotNull(repository.category(target.uuid))
                        repository.updateCategory(row.value.edited(name = input.name, iconKey = input.iconKey, iconColor = input.iconColor), target.revision).value.uuid
                    }
                    EditKind.EDIT_CHILD -> {
                        val target = checkNotNull(child)
                        val row = checkNotNull(repository.subcategory(target.uuid))
                        repository.updateSubcategory(row.value.edited(name = input.name, iconKey = input.iconKey, iconColor = input.iconColor), target.revision).value.uuid
                    }
                    EditKind.CREATE_CHILD -> {
                        val parentRow = checkNotNull(repository.category(checkNotNull(parent).uuid)).value
                        val value = Subcategory(uuid = uuid, categoryId = parentRow.id, name = input.name, iconKey = input.iconKey, iconColor = input.iconColor)
                        val previous = repository.subcategory(uuid)
                        if (previous == null) repository.createSubcategory(value).value.uuid else {
                            check(previous.value.copy(id = 0) == value)
                            uuid
                        }
                    }
                }
            },
            onSaved = { editor = null; runChange() },
        ) else LaunchedEffect(kind, editorId) {
            editor = null
            error = "El elemento ya no está disponible."
        }
    }

    val archiveParent = snapshot?.categories?.firstOrNull { it.uuid == archiveId }
    val archiveChild = snapshot?.subcategories?.firstOrNull { it.uuid == archiveId }
    val targetName = archiveParent?.name ?: archiveChild?.name
    if (targetName != null) {
        val restore = archiveParent?.archived ?: checkNotNull(archiveChild).archived
        AlertDialog(
            onDismissRequest = { archiveId = null },
            title = { Text(if (restore) "¿Restaurar elemento?" else "¿Archivar elemento?") },
            text = { Text(if (restore) "«$targetName» volverá a estar disponible." else
                "«$targetName» dejará de aparecer al registrar movimientos nuevos. Los movimientos existentes conservarán su clasificación.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    archiveId = null
                    runChange {
                        if (archiveParent != null) {
                            val row = checkNotNull(repository.category(archiveParent.uuid))
                            repository.updateCategory(row.value.edited(archived = !restore), archiveParent.revision)
                        } else {
                            val child = checkNotNull(archiveChild)
                            val row = checkNotNull(repository.subcategory(child.uuid))
                            if (restore) check(!checkNotNull(repository.category(child.categoryUuid)).value.archived)
                            repository.updateSubcategory(row.value.edited(archived = !restore), child.revision)
                        }
                    }
                }) { Text(if (restore) "Restaurar" else "Archivar") }
            },
            dismissButton = { TextButton(onClick = { archiveId = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ItemActions(
    archived: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    allowRestore: Boolean = true,
) {
    Row {
        TextButton(onEdit, enabled = !busy) { Text("Editar") }
        if (!archived || allowRestore) TextButton(onArchive, enabled = !busy) {
            Text(if (archived) "Restaurar" else "Archivar")
        }
    }
    if (archived && !allowRestore) Text("Restaurá primero la categoría.")
}
