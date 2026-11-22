package com.carlos.miflujo.cataloglab

import android.app.Application
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
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
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
    private lateinit var model: CatalogLabModel
    private val restoration = StateRestorationTester(compose)

    @Before
    fun setup() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        check(app.packageName == "com.carlos.miflujo.cataloglab.uitest")
        db = Room.inMemoryDatabaseBuilder(app, CatalogMovementDatabase::class.java)
            .addCallback(CatalogMovementDatabase.initializer).build()
        model = CatalogLabModel(app, db)
        restoration.setContent { MiFlujoTheme { CatalogLabScreen(model) } }
    }

    @After fun close() { db.close() }

    @Test
    fun createsCategoryFromMovementAndPreservesDraft() {
        waitFor("Nuevo").performClick()
        compose.onNodeWithContentDescription("Monto").performTextInput("123.45")
        field("Detalle").performTextInput("Pago de prueba")
        compose.onNodeWithContentDescription("Córdobas, cambiar a dólares").performClick()
        compose.onNodeWithText("Sin categoría").performClick()
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
        waitFor("Trabajo")
        waitFor("Guardar").performClick()
        waitFor("US$ 123.45")
        val snapshot = runBlocking { model.session.snapshot() }
        val category = snapshot.categories.single { it.name == "Trabajo" }
        val movement = snapshot.movements.single()
        assertEquals("directions_car", category.iconKey)
        assertEquals(CatalogIconColor.VIOLET, category.iconColor)
        assertEquals(ReportGroup.OTHER, category.reportGroup)
        assertEquals(category.uuid, movement.categoryUuid)
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
    }

    @Test
    fun managesCategoryAndSubcategoryIncludingArchiveAndRestore() {
        waitFor("Configuración").performClick()
        compose.onNodeWithText("Categorías y subcategorías").performClick()
        waitFor("Nueva categoría").performClick()
        saveCategoryWithIcon("Trabajo", "hogar", "Casa")
        waitFor("Trabajo").performClick()
        waitFor("Nueva subcategoría").performClick()
        field("Nombre").performTextInput("Servicios")
        selectColor("Menta")
        saveForm("Guardar subcategoría")
        waitFor("Servicios")
        val old = runBlocking { model.session.snapshot() }
        val category = old.categories.single { it.name == "Trabajo" }
        val child = old.subcategories.single { it.name == "Servicios" }
        assertEquals(category.uuid, child.categoryUuid)
        assertEquals(CatalogIconColor.MINT, category.iconColor)
        assertEquals(category.iconColor, child.iconColor)

        compose.onAllNodesWithText("Editar")[0].performClick()
        colorSection("Menta", expanded = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Menta").performScrollTo().assertIsSelected()
        selectColor("Azul")
        field("Nombre").performScrollTo().performTextReplacement("Trabajo remoto")
        chooseIcon("automovil", "Automóvil")
        compose.onNodeWithContentDescription("Azul").performScrollTo().assertIsSelected()
        saveForm("Guardar categoría")
        waitFor("Trabajo remoto")
        compose.onAllNodesWithText("Editar")[1].performClick()
        colorSection("Menta", expanded = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Menta").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Usar color predeterminado").performScrollTo().performClick().assertIsSelected()
        colorSection("Predeterminado", expanded = true).assertExists()
        field("Nombre").performScrollTo().performTextReplacement("Conexión")
        chooseIcon("internet", "Internet")
        saveForm("Guardar subcategoría")
        waitFor("Conexión")

        compose.onAllNodesWithText("Archivar")[1].performClick()
        compose.onAllNodesWithText("Archivar").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        waitForEnabled("Archivadas").performClick()
        waitFor("Conexión")
        compose.onNodeWithText("Restaurar").performClick()
        compose.onAllNodesWithText("Restaurar").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        waitForEnabled("Activas").performClick()
        waitFor("Conexión")

        compose.onAllNodesWithText("Archivar")[0].performClick()
        compose.onAllNodesWithText("Archivar").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        waitForEnabled("Archivadas").performClick()
        waitFor("Trabajo remoto (archivada)")
        compose.onNodeWithText("Restaurar").performClick()
        compose.onAllNodesWithText("Restaurar").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        waitForEnabled("Activas").performClick()
        waitFor("Trabajo remoto")
        val current = runBlocking { model.session.snapshot() }
        assertEquals(category.copy(name = "Trabajo remoto", iconKey = "directions_car", iconColor = CatalogIconColor.BLUE, revision = current.categories.single { it.uuid == category.uuid }.revision),
            current.categories.single { it.uuid == category.uuid })
        val updated = current.subcategories.single { it.uuid == child.uuid }
        assertEquals("Conexión", updated.name)
        assertEquals("internet", updated.iconKey)
        assertNull(updated.iconColor)
        assertEquals(category.uuid, updated.categoryUuid)
        assertFalse(updated.archived)
    }

    @Test
    fun keepsExistingReportGroupAndDefaultsNewIncomeCategory() {
        val existing = runBlocking {
            model.session.categories.createCategory(Category(
                name = "Reparaciones", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.MAINTENANCE,
            ))
        }
        compose.onNodeWithText("Actualizar").performClick()
        waitFor("Configuración").performClick()
        compose.onNodeWithText("Categorías y subcategorías").performClick()
        waitFor("Reparaciones").performClick()
        waitFor("Editar").performClick()
        compose.onNodeWithText("Grupo del reporte", substring = true).assertDoesNotExist()
        field("Nombre").performTextReplacement("Reparaciones del hogar")
        saveForm("Guardar categoría")
        waitFor("Reparaciones del hogar")

        waitFor("Nueva categoría").performClick()
        field("Nombre").performTextInput("Bonos")
        compose.onNodeWithText("Tipo de movimiento · Egresos").performScrollTo().performClick()
        compose.onNodeWithText("Ingresos").performClick()
        compose.onNodeWithText("Grupo del reporte", substring = true).assertDoesNotExist()
        saveForm("Guardar categoría")
        waitFor("Bonos")

        val snapshot = runBlocking { model.session.snapshot() }
        assertEquals(ReportGroup.MAINTENANCE, snapshot.categories.single { it.uuid == existing.value.uuid }.reportGroup)
        val created = snapshot.categories.single { it.name == "Bonos" }
        assertEquals(CategoryScope.INCOME, created.scope)
        assertEquals(ReportGroup.INCOME, created.reportGroup)
    }

    @Test
    fun selectsChildAndKeepsArchivedClassificationDuringHistoricalEdit() {
        val (cat, child) = runBlocking {
            val repo = model.session.categories
            val cat = repo.createCategory(Category(
                name = "Gastos varios", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER,
                iconKey = "home", iconColor = CatalogIconColor.ORANGE,
            ))
            val child = repo.createSubcategory(Subcategory(categoryId = cat.value.id, name = "Gasolina", iconKey = "directions_car", iconColor = CatalogIconColor.ORANGE))
            repo.createCategory(Category(
                name = cat.value.name, scope = CategoryScope.INCOME, reportGroup = ReportGroup.INCOME,
            ))
            cat to child
        }
        compose.onNodeWithText("Actualizar").performClick()
        waitFor("Nuevo").performClick()
        compose.onNodeWithContentDescription("Monto").performTextInput("5.12")
        compose.onNodeWithText("Sin categoría").performClick()
        compose.onNodeWithText("Gastos varios").performClick()
        compose.onNodeWithText("Sin subcategoría").performClick()
        compose.onNodeWithText("Gasolina").performClick()
        compose.onNodeWithText("Guardar").performClick()
        waitFor("C$ 5.12")
        runBlocking {
            val repo = model.session.categories
            repo.updateSubcategory(child.value.edited(archived = true), child.revision)
            repo.updateCategory(cat.value.edited(archived = true), cat.revision)
        }
        compose.onNodeWithText("Actualizar").performClick()
        waitFor("Editar").performClick()
        waitFor("Gastos varios (archivada)")
        waitFor("Gasolina")
        field("Detalle").performTextInput("Recibo histórico")
        compose.onNodeWithText("Guardar").performClick()
        waitFor("C$ 5.12")
        val snapshot = runBlocking { model.session.snapshot() }
        val movement = snapshot.movements.single()
        assertEquals(cat.value.uuid, movement.categoryUuid)
        assertEquals(child.value.uuid, movement.subcategoryUuid)
        assertEquals("Recibo histórico", movement.detail)
        assertEquals(CatalogIconColor.ORANGE, snapshot.categories.single { it.uuid == cat.value.uuid }.iconColor)
        assertEquals(CatalogIconColor.ORANGE, snapshot.subcategories.single { it.uuid == child.value.uuid }.iconColor)
        val report = model.session.report(snapshot, java.time.YearMonth.from(movement.date))
        assertEquals(512L, report.cordoba.totalExpenseMinor)
        assertEquals(0L, report.dollar.totalExpenseMinor)
    }

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun waitFor(text: String): SemanticsNodeInteraction {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNodeWithText(text)
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
