package com.carlos.miflujo.catalogpreview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.cataloglab.CatalogManagement
import com.carlos.miflujo.cataloglab.visibleName
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.model.CatalogMovement
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.usecase.CalculateMonthlyCashFlowReportUseCase
import com.carlos.miflujo.ui.MainDestination
import com.carlos.miflujo.ui.MiFlujoScaffold
import com.carlos.miflujo.ui.home.CloudSyncHomeIndicatorState
import com.carlos.miflujo.ui.home.HomeScreen
import com.carlos.miflujo.ui.home.HomeUiState
import com.carlos.miflujo.ui.movement.MovementFilter
import com.carlos.miflujo.ui.movement.MovementUiState
import com.carlos.miflujo.ui.movement.MovementsScreen
import com.carlos.miflujo.ui.report.ReportScreen
import com.carlos.miflujo.ui.report.ReportUiState
import com.carlos.miflujo.ui.settings.CatalogSettingsScreen
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CatalogPreviewApp(model: CatalogPreviewModel) {
    val result by model.snapshot.collectAsState()
    val data = result?.getOrNull()
    val snapshot = data?.catalog
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var destination by rememberSaveable { mutableStateOf(MainDestination.Home) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCatalog by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<CatalogMovement?>(null) }
    var movementMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var reportMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var filter by rememberSaveable { mutableStateOf(MovementFilter.All) }

    fun closeSettings() {
        if (showCatalog) showCatalog = false else showSettings = false
    }
    BackHandler(enabled = showSettings) { closeSettings() }

    LaunchedEffect(editUuid) {
        val uuid = editUuid
        editing = if (uuid == null) null else try {
            checkNotNull(model.find(uuid))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            editUuid = null
            scope.launch { snackbar.showSnackbar("El movimiento ya no está disponible.") }
            null
        }
    }

    val categories = remember(snapshot) { snapshot?.categories.orEmpty().associateBy { it.uuid } }
    val movements = remember(snapshot, categories) {
        snapshot?.movements.orEmpty()
            .sortedWith(compareByDescending<BackupMovement> { it.date }.thenByDescending { it.createdAt })
            .map { it.forMainScreens(categories[it.categoryUuid]?.reportGroup) }
    }
    val labels = remember(snapshot, categories) {
        snapshot?.movements.orEmpty().associate { it.uuid to (categories[it.categoryUuid]?.visibleName() ?: "Sin categoría") }
    }
    val uncategorizedMovementUuids = remember(snapshot) {
        snapshot?.movements.orEmpty().filter { it.categoryUuid == null }.mapTo(mutableSetOf()) { it.uuid }
    }
    val currentMonth = YearMonth.now()
    val calculate = remember { CalculateMonthlyCashFlowReportUseCase() }

    MiFlujoScaffold(
        selectedDestination = destination,
        onSelectDestination = { destination = it },
        showSettings = showSettings,
        onOpenSettings = { showSettings = true },
        onCloseSettings = ::closeSettings,
        onAdd = snapshot?.let { { showAdd = true } },
        title = "MiFlujo · Vista de catálogo",
        settingsTitle = if (showCatalog) "Categorías" else "MiFlujo · Vista de catálogo",
        snackbarHost = { SnackbarHost(snackbar) },
        settings = { modifier ->
            if (showCatalog) {
                CatalogManagement(
                    snapshot, model.categories, modifier.fillMaxSize(),
                    onBack = { showCatalog = false },
                    refresh = model::refresh,
                    loadError = "No se pudo leer el catálogo.".takeIf { result?.isFailure == true },
                    showHeader = false,
                )
            } else CatalogSettingsScreen({ showCatalog = true }, modifier)
        },
    ) { selected ->
        if (snapshot == null) {
            Text(
                if (result?.isFailure == true) "No se pudieron cargar los movimientos." else "Cargando movimientos…",
                Modifier.padding(20.dp),
            )
        } else when (selected) {
            MainDestination.Home -> HomeScreen(
                HomeUiState(
                    currentMonth,
                    calculate(movements, currentMonth, uncategorizedMovementUuids),
                    movements.filter { YearMonth.from(it.date) == currentMonth }.take(3),
                ),
                CloudSyncHomeIndicatorState.Hidden, categoryLabels = labels,
            )
            MainDestination.Movements -> MovementsScreen(
                MovementUiState(movementMonth, filter, movements.filter {
                    YearMonth.from(it.date) == movementMonth && when (filter) {
                        MovementFilter.All -> true
                        MovementFilter.Income -> it.type == MovementType.INCOME
                        MovementFilter.Expense -> it.type == MovementType.EXPENSE
                    }
                }),
                onPreviousMonth = { movementMonth = movementMonth.minusMonths(1) },
                onNextMonth = { movementMonth = movementMonth.plusMonths(1) },
                onFilterSelected = { filter = it },
                onEditMovement = null,
                onDeleteMovement = { movement, onDeleted ->
                    scope.launch {
                        try {
                            model.delete(movement.uuid)
                            onDeleted()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            snackbar.showSnackbar("No se pudo eliminar el movimiento.")
                        }
                    }
                },
                categoryLabels = labels,
                onOpenEditor = { editUuid = it.uuid },
            )
            MainDestination.Report -> ReportScreen(
                ReportUiState(reportMonth, calculate(movements, reportMonth, uncategorizedMovementUuids),
                    movements.filter { YearMonth.from(it.date) == reportMonth }),
                onPreviousMonth = { reportMonth = reportMonth.minusMonths(1) },
                onNextMonth = { reportMonth = reportMonth.plusMonths(1) },
                onShareReport = null,
                categoryLabels = labels,
            )
        }
    }

    val original = editing?.takeIf { it.uuid == editUuid }
    if (data != null && (showAdd || original != null)) key(original?.uuid) {
        CatalogPreviewMovementDialog(
            data.categoriesByUsage, original, model,
            onClose = { showAdd = false; editUuid = null },
        )
    }
}

// The shared screens still expect enum report buckets. All writes resolve the
// original UUID in the candidate repository, never through this presentation value.
private fun BackupMovement.forMainScreens(group: ReportGroup?): Movement = Movement(
    uuid = uuid, type = type, amountMinor = amountMinor, currency = currency, date = date,
    category = when {
        type == MovementType.INCOME -> MovementCategory.GENERAL_INCOME
        group == ReportGroup.FIXED_COST -> MovementCategory.FIXED_COST
        group == ReportGroup.MAINTENANCE -> MovementCategory.MAINTENANCE
        else -> MovementCategory.OTHER
    },
    detail = detail, createdAt = createdAt, updatedAt = updatedAt,
)
