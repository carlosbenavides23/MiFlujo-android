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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    var editorOpen by rememberSaveable { mutableStateOf(false) }
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

    fun edit(uuid: String? = null) {
        editorId = uuid
        editorOpen = true
    }

    val categories = snapshot?.categories.orEmpty().filter { it.archived == archived }

    BackHandler { if (!busy) onBack() }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onBack, enabled = !busy) { Text("Volver a Configuración") }
            Text("Categorías", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!archived, { archived = false }, label = { Text("Activas") }, enabled = !busy)
                FilterChip(archived, { archived = true }, label = { Text("Archivadas") }, enabled = !busy)
            }
            if (!archived && snapshot != null) Button(onClick = { edit() }, enabled = !busy) {
                Text("Nueva categoría")
            }
            if ((snapshot == null && loadError == null) || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: loadError.takeIf { snapshot == null })?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { runChange() }, enabled = !busy) { Text("Actualizar") }
            }
            if (snapshot != null && categories.isEmpty()) Text(
                if (archived) "No hay categorías archivadas." else "Todavía no hay categorías activas.",
            )
        }
        items(categories, key = { it.uuid }) { category ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    TextButton(onClick = { expanded = if (expanded == category.uuid) null else category.uuid }) {
                        CatalogIcon(category.iconKey, color = category.iconColor, background = MaterialTheme.colorScheme.surfaceContainerLow)
                        Spacer(Modifier.width(8.dp))
                        Text(if (category.archived) "${category.name} (archivada)" else category.name)
                    }
                    if (expanded == category.uuid) Row {
                        TextButton(onClick = { edit(category.uuid) }, enabled = !busy) { Text("Editar") }
                        TextButton(onClick = { archiveId = category.uuid }, enabled = !busy) {
                            Text(if (category.archived) "Restaurar" else "Archivar")
                        }
                    }
                }
            }
        }
    }

    if (editorOpen && snapshot != null) {
        val category = snapshot.categories.firstOrNull { it.uuid == editorId }
        val creating = editorId == null
        if (creating || category != null) {
            CatalogItemEditor(
                title = if (creating) "Nueva categoría" else "Editar categoría",
                initial = category?.let { CatalogItemInput(it.name, it.iconKey, it.scope, it.iconColor) }
                    ?: CatalogItemInput(),
                saveLabel = "Guardar categoría",
                allowScope = creating,
                onDismiss = { editorOpen = false },
                save = { input, uuid ->
                    if (category == null) repository.createFromEditor(input, uuid)
                    else {
                        val row = checkNotNull(repository.category(category.uuid))
                        val edited = row.value.edited(name = input.name, iconKey = input.iconKey, iconColor = input.iconColor)
                        repository.updateCategory(edited, category.revision).value.uuid
                    }
                },
                onSaved = { editorOpen = false; runChange() },
            )
        } else LaunchedEffect(editorId) {
            editorOpen = false
            error = "La categoría ya no está disponible."
        }
    }

    val archiveCategory = snapshot?.categories?.firstOrNull { it.uuid == archiveId }
    if (archiveCategory != null) {
        val restore = archiveCategory.archived
        AlertDialog(
            onDismissRequest = { archiveId = null },
            title = { Text(if (restore) "¿Restaurar categoría?" else "¿Archivar categoría?") },
            text = { Text(if (restore) "«${archiveCategory.name}» volverá a estar disponible." else
                "«${archiveCategory.name}» dejará de aparecer al registrar movimientos nuevos. Los movimientos existentes conservarán su clasificación.") },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    archiveId = null
                    runChange {
                        val row = checkNotNull(repository.category(archiveCategory.uuid))
                        repository.updateCategory(row.value.edited(archived = !restore), archiveCategory.revision)
                    }
                }) { Text(if (restore) "Restaurar" else "Archivar") }
            },
            dismissButton = { TextButton(onClick = { archiveId = null }) { Text("Cancelar") } },
        )
    }
}
