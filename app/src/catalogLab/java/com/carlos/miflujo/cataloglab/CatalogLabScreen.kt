package com.carlos.miflujo.cataloglab

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.report.FlexibleMonthlyReport
import com.carlos.miflujo.ui.backup.BackupDocument
import com.carlos.miflujo.ui.backup.BackupExporter
import com.carlos.miflujo.ui.formatSignedVisibleMoney
import com.carlos.miflujo.ui.formatVisibleMoney
import com.carlos.miflujo.ui.report.experimental.flexibleBreakdownItems
import com.carlos.miflujo.ui.report.experimental.reportSymbol
import com.carlos.miflujo.ui.report.export.FlexibleReportPdfGenerator
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CatalogLabScreen(model: CatalogLabModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = model.session
    var snapshot by remember { mutableStateOf<CatalogBackup?>(null) }
    var canRestore by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var formOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var editingUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOwner by rememberSaveable { mutableStateOf<String?>(null) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var manageCatalog by rememberSaveable { mutableStateOf(false) }
    var restore by remember { mutableStateOf<CatalogBackup?>(null) }
    var deleting by remember { mutableStateOf<BackupMovement?>(null) }
    var export by remember { mutableStateOf<BackupDocument?>(null) }
    var pdf by remember { mutableStateOf<FlexibleMonthlyReport?>(null) }
    var reportTab by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    fun perform(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                message = error.localizedMessage ?: "La operación falló."
            } finally {
                busy = false
            }
        }
    }

    suspend fun refresh() {
        val currentSession = model.session
        snapshot = currentSession.snapshot()
        canRestore = currentSession.canRestore()
    }

    LaunchedEffect(Unit) {
        if (settingsOwner != session.accountUid) {
            settings = false
            manageCatalog = false
        }
        if (formOpen && (formOwner != session.accountUid || editingUuid != model.editingMovement?.uuid)) formOpen = false
        perform { refresh() }
    }
    val pickJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform {
            val input = withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            restore = session.prepareRestore(input)
        }
    }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val document = export
        export = null
        if (uri != null && document != null) perform {
            BackupExporter.saveBackup(context, uri, document)
            message = "Respaldo guardado."
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val report = pdf
        pdf = null
        if (uri != null && report != null) perform {
            withContext(Dispatchers.IO) {
                checkNotNull(context.contentResolver.openOutputStream(uri)).use { FlexibleReportPdfGenerator().generate(report, it) }
            }
            message = "PDF guardado."
        }
    }

    val data = snapshot
    val reportResult = remember(data, month, reportTab) {
        if (reportTab && data != null) runCatching { session.report(data, month) } else null
    }
    val report = reportResult?.getOrNull()
    val controlsEnabled = !busy && export == null && pdf == null
    Scaffold { padding ->
        when {
            manageCatalog -> CatalogManagement(
                data, session.categories, Modifier.fillMaxSize().padding(padding),
                onBack = { manageCatalog = false },
                refresh = { refresh() },
                loadError = message.takeIf { data == null },
            )
            settings -> {
                BackHandler { settings = false }
                Column(Modifier.padding(padding).padding(16.dp)) {
                    TextButton(onClick = { settings = false }) { Text("Volver") }
                    Text("Configuración", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = { manageCatalog = true }) { Text("Categorías y subcategorías") }
                }
            }
            else -> LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("MiFlujo · Catálogo de pruebas", style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = controlsEnabled, onClick = { settingsOwner = session.accountUid; settings = true }) { Text("Configuración") }
                Text(if (session.accountUid == null) "Base local · Datos ficticios" else "Cuenta ficticia · Firebase Emulator")
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it) }
            }
            item {
                if (session.accountUid == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for ((label, host) in listOf("Emulador por USB" to "127.0.0.1", "Emulador en AVD" to "10.0.2.2")) {
                            OutlinedButton(enabled = controlsEnabled, onClick = { perform {
                                model.connect(host)
                                snapshot = null
                                canRestore = false
                                refresh()
                            } }) { Text(label) }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(enabled = controlsEnabled, onClick = { perform {
                            val result = session.sync()
                            refresh()
                            message = if (result.successful) "Sincronización completada."
                            else result.issues.joinToString("\n") { issue ->
                                listOfNotNull(issue.kind.name, issue.uuid).joinToString(" · ")
                            }
                        } }) { Text("Sincronizar") }
                        OutlinedButton(enabled = controlsEnabled, onClick = { perform {
                            snapshot = null
                            canRestore = false
                            model.disconnect()
                            refresh()
                        } }) { Text("Cerrar sesión de prueba") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(enabled = controlsEnabled, onClick = { perform {
                        val document = BackupDocument("miflujo-catalogo-v4.json", session.exportJson())
                        export = document
                        saveJson.launch(document.fileName)
                    } }) { Text("Exportar JSON") }
                    TextButton(enabled = controlsEnabled && canRestore, onClick = {
                        pickJson.launch(arrayOf("application/json", "text/plain"))
                    }) { Text("Restaurar JSON") }
                    TextButton(enabled = controlsEnabled, onClick = { perform { refresh() } }) { Text("Actualizar") }
                }
                if (!canRestore && data != null) Text("Restauración disponible en la base local sin historial de sincronización.")
                TabRow(selectedTabIndex = if (reportTab) 1 else 0) {
                    Tab(!reportTab, onClick = { reportTab = false }, text = { Text("Movimientos") })
                    Tab(reportTab, onClick = { reportTab = true }, text = { Text("Reporte mensual") })
                }
            }

            if (!reportTab && data != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(enabled = controlsEnabled, onClick = {
                            model.editingMovement = null
                            editingUuid = null
                            formOwner = session.accountUid
                            formOpen = true
                        }) { Text("Nuevo") }
                        TextButton(enabled = controlsEnabled, onClick = { perform {
                            session.addExampleCatalog()
                            refresh()
                        } }) { Text("Agregar categoría de prueba") }
                    }
                    if (data.movements.isEmpty()) Text("Sin movimientos.")
                }
                items(data.movements.sortedByDescending { it.date }, key = { it.uuid }) { row ->
                    val category = data.categories.firstOrNull { it.uuid == row.categoryUuid }
                    val child = data.subcategories.firstOrNull { it.uuid == row.subcategoryUuid }
                    ListItem(
                        headlineContent = { Text(row.amountMinor.formatVisibleMoney(row.currency.reportSymbol())) },
                        supportingContent = { Text(listOfNotNull(
                            row.date.toString(), if (row.type == MovementType.INCOME) "Ingreso" else "Egreso",
                            category?.name ?: "Sin categoría", child?.name, row.detail,
                        ).joinToString(" · ")) },
                        leadingContent = if (category != null) ({
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CatalogIcon(category.iconKey, color = category.iconColor)
                                if (child != null) CatalogIcon(child.iconKey, color = child.iconColor)
                            }
                        }) else null,
                        trailingContent = {
                            Column {
                                TextButton(enabled = controlsEnabled, onClick = { perform {
                                    model.editingMovement = checkNotNull(session.movements.find(row.uuid))
                                    editingUuid = row.uuid
                                    formOwner = session.accountUid
                                    formOpen = true
                                } }) { Text("Editar") }
                                if (session.accountUid != null) TextButton(
                                    enabled = controlsEnabled, onClick = { deleting = row },
                                ) { Text("Eliminar") }
                            }
                        },
                    )
                }
            }
            if (reportTab) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { month = month.minusMonths(1) }) { Text("Anterior") }
                        Text(month.toString(), Modifier.padding(top = 12.dp))
                        TextButton(onClick = { month = month.plusMonths(1) }) { Text("Siguiente") }
                    }
                    if (report != null) OutlinedButton(enabled = controlsEnabled, onClick = {
                        pdf = report
                        savePdf.launch("miflujo-${report.month}.pdf")
                    }) { Text("Exportar PDF") }
                    if (reportResult?.isFailure == true) Text("No se pudo calcular el total con precisión.")
                }
                if (report != null) for (summary in listOf(report.cordoba, report.dollar)) {
                    val symbol = summary.currency.reportSymbol()
                    item { Text("$symbol · Saldo ${summary.netCashFlowMinor.formatSignedVisibleMoney(symbol)}") }
                    item { Text("Ingresos ${summary.totalIncomeMinor.formatVisibleMoney(symbol)}") }
                    flexibleBreakdownItems("$symbol-income", summary.income, summary.currency)
                    item { Text("Egresos ${summary.totalExpenseMinor.formatVisibleMoney(symbol)}") }
                    flexibleBreakdownItems("$symbol-expense", summary.expense, summary.currency)
                }
            }
        }
        }
    }

    if (formOpen && data != null && formOwner == session.accountUid && editingUuid == model.editingMovement?.uuid) {
        CatalogLabMovementForm(
            data, model.editingMovement, busy, onDismiss = { formOpen = false }, saveError = message,
            repository = session.categories, onCatalogChanged = { perform { refresh() } },
        ) { draft ->
            perform {
                val previous = model.editingMovement
                if (previous == null) session.movements.create(draft) else session.movements.edit(previous, draft)
                formOpen = false
                refresh()
            }
        }
    }

    restore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) restore = null },
            title = { Text("¿Reemplazar los datos locales?") },
            text = { Text("El respaldo contiene ${backup.movements.size} movimientos. Se reemplazarán los movimientos y el catálogo local.") },
            confirmButton = { Button(enabled = !busy, onClick = { perform {
                restore = null
                session.restore(backup)
                refresh()
                message = "Respaldo restaurado."
            } }) { Text("Restaurar") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { restore = null }) { Text("Cancelar") } },
        )
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("¿Eliminar el movimiento?") },
            text = { Text("La eliminación quedará pendiente de sincronización.") },
            confirmButton = { Button(enabled = !busy, onClick = { perform {
                deleting = null
                session.movements.tombstone(checkNotNull(session.movements.find(row.uuid)))
                refresh()
            } }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}
