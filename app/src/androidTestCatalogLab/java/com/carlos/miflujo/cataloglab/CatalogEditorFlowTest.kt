package com.carlos.miflujo.cataloglab

import android.app.Application
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.data.cloud.catalog.RemoteCatalogFields
import com.carlos.miflujo.data.cloud.catalog.toRemoteDto
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CatalogIconColor
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.Subcategory
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.util.UUID
import com.carlos.miflujo.ui.backup.CatalogBackupJson
import com.carlos.miflujo.ui.theme.MiFlujoTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogEditorFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: CatalogMovementDatabase
    private lateinit var app: Application
    private val databaseName = "catalog-editor-test-${UUID.randomUUID()}.db"
    private val currentModel = mutableStateOf<CatalogLabModel?>(null)
    private val model get() = checkNotNull(currentModel.value)
    private val restoration = StateRestorationTester(compose)

    @Before
    fun setup() {
        app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        check(app.packageName == "com.carlos.miflujo.cataloglab.uitest")
        openDatabase()
        restoration.setContent { MiFlujoTheme { key(model) { CatalogLabScreen(model) } } }
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(app, CatalogMovementDatabase::class.java, databaseName)
            .addCallback(CatalogMovementDatabase.initializer).build()
        currentModel.value = CatalogLabModel(app, db)
    }

    @After
    fun close() {
        db.close()
        app.deleteDatabase(databaseName)
    }

    @Test
    fun createsCategoryFromMovementAndPreservesDraft() {
        waitFor("Nuevo").performClick()
        compose.onNodeWithContentDescription("Monto").performTextInput("123.45")
        field("Detalle").performTextInput("Pago de prueba")
        compose.onNodeWithContentDescription("Córdobas, cambiar a dólares").performClick()
        compose.onNodeWithContentDescription("Categoría: Elegir categoría").performClick()
        compose.onNodeWithText("Crear nueva categoría").performClick()
        field("Nombre").performTextInput("Trabajo")
        restoration.emulateSavedInstanceStateRestore()
        chooseIcon("automovil", "Automóvil")
        colorSection("Predeterminado", expanded = false).assertExists()
        compose.onNodeWithText("Usar color predeterminado").assertDoesNotExist()
        compose.onNodeWithContentDescription("Violeta").assertDoesNotExist()
        selectColor("Violeta")
        colorSection("Violeta", expanded = true).performScrollTo().performClick()
        colorSection("Violeta", expanded = false).assertExists()
        compose.onNodeWithContentDescription("Violeta").assertDoesNotExist()
        colorSection("Violeta", expanded = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Violeta").performScrollTo().assertIsSelected()
        saveForm("Guardar categoría")
        waitForCategory("Trabajo").assertExists()
        assertSingleLevel()
        waitFor("Guardar").performClick()
        waitFor("US$ 123.45")
        val snapshot = runBlocking { model.session.snapshot() }
        val category = snapshot.categories.single { it.name == "Trabajo" }
        val movement = snapshot.movements.single()
        assertEquals("directions_car", category.iconKey)
        assertEquals(CatalogIconColor.VIOLET, category.iconColor)
        assertEquals(ReportGroup.OTHER, category.reportGroup)
        assertEquals(category.uuid, movement.categoryUuid)
        assertNull(movement.subcategoryUuid)
        assertEquals(12345L, movement.amountMinor)
        assertEquals(Currency.DOLLAR, movement.currency)
        assertEquals("Pago de prueba", movement.detail)
        assertEquals(LocalDate.now(), movement.date)
        runBlocking {
            val backup = CatalogBackupJson.parse(model.session.exportJson())
            model.session.restore(backup)
            val restored = checkNotNull(model.session.categories.category(category.uuid))
            assertEquals(category.iconKey, restored.value.iconKey)
            assertEquals(category.iconColor, restored.value.iconColor)
            val remote = restored.toRemoteDto()
            val decoded = RemoteCatalogFields.decodeCategory(category.uuid, RemoteCatalogFields.encode(remote))
            assertEquals(category.iconKey, decoded.iconKey)
            assertEquals(category.iconColor, decoded.iconColor)
            assertEquals(category.uuid, model.session.snapshot().movements.single().categoryUuid)
        }

        waitForEnabled("Nuevo").performClick()
        compose.onNodeWithContentDescription("Monto").performTextInput("2")
        compose.onNodeWithContentDescription("Categoría: Elegir categoría").performClick()
        waitFor("Trabajo").performClick()
        assertSingleLevel()
        saveForm("Guardar")
        waitFor("C$ 2.00")
        val next = runBlocking { model.session.snapshot() }.movements.single { it.uuid != movement.uuid }
        assertEquals(category.uuid, next.categoryUuid)
        assertNull(next.subcategoryUuid)
    }

    @Test
    fun managesCategoryIncludingArchiveAndRestore() {
        waitFor("Configuración").performClick()
        compose.onNodeWithText("Categorías").performClick()
        waitFor("Nueva categoría").performClick()
        saveCategoryWithIcon("Trabajo", "hogar", "Casa")
        waitFor("Trabajo").performClick()
        assertSingleLevel()
        val category = runBlocking { model.session.snapshot() }.categories.single { it.name == "Trabajo" }
        assertEquals(CatalogIconColor.MINT, category.iconColor)

        waitFor("Editar").performClick()
        colorSection("Menta", expanded = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Menta").performScrollTo().assertIsSelected()
        selectColor("Azul")
        field("Nombre").performScrollTo().performTextReplacement("Trabajo remoto")
        chooseIcon("automovil", "Automóvil")
        compose.onNodeWithContentDescription("Azul").performScrollTo().assertIsSelected()
        saveForm("Guardar categoría")
        waitFor("Trabajo remoto")
        waitFor("Editar").performClick()
        colorSection("Azul", expanded = false).performScrollTo().performClick()
        compose.onNodeWithText("Usar color predeterminado").performScrollTo().performClick().assertIsSelected()
        colorSection("Predeterminado", expanded = true).assertExists()
        saveForm("Guardar categoría")

        waitForEnabled("Archivar").performClick()
        compose.onAllNodesWithText("Archivar").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        waitForEnabled("Archivadas").performClick()
        waitFor("Trabajo remoto (archivada)")
        compose.onNodeWithText("Restaurar").performClick()
        compose.onAllNodesWithText("Restaurar").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        waitForEnabled("Activas").performClick()
        waitFor("Trabajo remoto")
        val current = runBlocking { model.session.snapshot() }
        val updated = current.categories.single { it.uuid == category.uuid }
        assertEquals(category.copy(name = "Trabajo remoto", iconKey = "directions_car",
            iconColor = null, revision = updated.revision), updated)
    }

    @Test
    fun keepsExistingScopeAndReportGroupAndCreatesCategoriesForBothTypes() {
        val existing = runBlocking {
            model.session.categories.createCategory(Category(
                name = "Reparaciones", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.MAINTENANCE,
            ))
        }
        compose.onNodeWithText("Actualizar").performClick()
        waitFor("Configuración").performClick()
        compose.onNodeWithText("Categorías").performClick()
        waitFor("Reparaciones").performClick()
        waitFor("Editar").performClick()
        compose.onNodeWithText("Grupo del reporte", substring = true).assertDoesNotExist()
        field("Nombre").performTextReplacement("Reparaciones del hogar")
        saveForm("Guardar categoría")
        waitFor("Reparaciones del hogar")

        waitFor("Nueva categoría").performClick()
        field("Nombre").performTextInput("Bonos")
        compose.onNodeWithText("Tipo de movimiento", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Grupo del reporte", substring = true).assertDoesNotExist()
        saveForm("Guardar categoría")
        waitFor("Bonos")

        val snapshot = runBlocking { model.session.snapshot() }
        val historical = snapshot.categories.single { it.uuid == existing.value.uuid }
        assertEquals(CategoryScope.EXPENSE, historical.scope)
        assertEquals(ReportGroup.MAINTENANCE, historical.reportGroup)
        val created = snapshot.categories.single { it.name == "Bonos" }
        assertEquals(CategoryScope.BOTH, created.scope)
        assertEquals(ReportGroup.OTHER, created.reportGroup)
    }

    @Test
    fun editsHistoricalMovementAndReopensItsCategoryOnlyHistoryAndReport() {
        val before = runBlocking {
            val repo = model.session.categories
            val category = repo.createCategory(Category(
                name = "Gastos varios", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER,
                iconKey = "home", iconColor = CatalogIconColor.ORANGE,
            ))
            val child = repo.createSubcategory(Subcategory(
                categoryId = category.value.id, name = "Gasolina",
                iconKey = "directions_car", iconColor = CatalogIconColor.ORANGE,
            ))
            model.session.movements.create(CatalogMovementDraft(
                MovementType.EXPENSE, 512, Currency.CORDOBA, LocalDate.now(),
                CatalogMovementSelection(category.value.uuid, child.value.uuid),
            ))
            repo.updateSubcategory(child.value.edited(archived = true), child.revision)
            repo.updateCategory(category.value.edited(archived = true), category.revision)
            model.session.snapshot()
        }
        compose.onNodeWithText("Actualizar").performClick()
        waitFor("Editar").performClick()
        waitForCategory("Gastos varios (archivada)").assertExists()
        assertSingleLevel()
        field("Detalle").performTextInput("Recibo histórico")
        saveForm("Guardar")
        waitFor("Recibo histórico", substring = true)

        compose.runOnIdle {
            db.close()
            openDatabase()
        }
        waitFor("C$ 5.12")
        compose.onNodeWithText("Gastos varios", substring = true).assertExists()
        compose.onNodeWithText("Recibo histórico", substring = true).assertExists()
        assertSingleLevel()
        val reopened = runBlocking { CatalogBackupJson.parse(model.session.exportJson()) }
        assertEquals(before.categories, reopened.categories)
        assertEquals(before.subcategories, reopened.subcategories)
        val movement = reopened.movements.single()
        assertEquals(before.movements.single().copy(detail = "Recibo histórico", updatedAt = movement.updatedAt), movement)

        compose.onNodeWithText("Reporte mensual").performClick()
        compose.onNodeWithText("Egresos C$ 5.12").assertExists()
        compose.onNodeWithText("Gastos varios (archivada)").assertExists()
        assertSingleLevel()
    }

    private fun assertSingleLevel() {
        compose.onNode(hasText("subcategoría", substring = true, ignoreCase = true)).assertDoesNotExist()
        compose.onNodeWithText("Gasolina", substring = true).assertDoesNotExist()
    }

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun waitForCategory(label: String): SemanticsNodeInteraction {
        val description = "Categoría: $label"
        compose.waitUntil(8_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithContentDescription(description)
    }

    private fun waitFor(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(text, substring = substring)
    }

    private fun waitForEnabled(text: String): SemanticsNodeInteraction {
        val matcher = hasText(text) and isEnabled()
        compose.waitUntil(8_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNode(matcher)
    }

    private fun saveCategoryWithIcon(name: String, query: String, label: String) {
        field("Nombre").performTextInput(name)
        chooseIcon(query, label)
        selectColor("Menta")
        saveForm("Guardar categoría")
    }

    private fun selectColor(label: String) {
        val collapsed = hasText("Color del icono") and hasStateDescription("Contraído")
        if (compose.onAllNodes(collapsed).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(collapsed).performScrollTo().performClick()
        }
        compose.onNodeWithContentDescription(label).performScrollTo().performClick().assertIsSelected()
    }

    private fun colorSection(summary: String, expanded: Boolean) = compose.onNode(
        hasText("Color del icono") and hasText(summary) and hasStateDescription(if (expanded) "Expandido" else "Contraído"),
    )

    private fun saveForm(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun chooseIcon(query: String, label: String) {
        waitFor("Elegir icono").performScrollTo().performClick()
        field("Buscar iconos").performTextInput(query)
        waitFor(label).performClick()
    }
}
