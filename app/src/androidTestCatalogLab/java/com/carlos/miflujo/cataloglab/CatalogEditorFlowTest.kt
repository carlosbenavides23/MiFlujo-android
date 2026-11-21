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
        compose.onNodeWithText("Guardar categoría").performClick()
        waitFor("Trabajo")
        waitFor("Guardar").performClick()
        waitFor("US$ 123.45")
        val snapshot = runBlocking { model.session.snapshot() }
        val category = snapshot.categories.single { it.name == "Trabajo" }
        val movement = snapshot.movements.single()
        assertEquals("directions_car", category.iconKey)
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
            val remote = restored.toRemoteDto()
            assertEquals(category.iconKey, RemoteCatalogFields.decodeCategory(category.uuid, RemoteCatalogFields.encode(remote)).iconKey)
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
        compose.onNodeWithText("Guardar subcategoría").performClick()
        waitFor("Servicios")
        val old = runBlocking { model.session.snapshot() }
        val category = old.categories.single { it.name == "Trabajo" }
        val child = old.subcategories.single { it.name == "Servicios" }
        assertEquals(category.uuid, child.categoryUuid)

        compose.onAllNodesWithText("Editar")[0].performClick()
        field("Nombre").performTextReplacement("Trabajo remoto")
        chooseIcon("automovil", "Automóvil")
        compose.onNodeWithText("Guardar categoría").performClick()
        waitFor("Trabajo remoto")
        compose.onAllNodesWithText("Editar")[1].performClick()
        field("Nombre").performTextReplacement("Conexión")
        chooseIcon("internet", "Internet")
        compose.onNodeWithText("Guardar subcategoría").performClick()
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
        assertEquals(category.copy(name = "Trabajo remoto", iconKey = "directions_car", revision = current.categories.single { it.uuid == category.uuid }.revision),
            current.categories.single { it.uuid == category.uuid })
        val updated = current.subcategories.single { it.uuid == child.uuid }
        assertEquals("Conexión", updated.name)
        assertEquals("internet", updated.iconKey)
        assertEquals(category.uuid, updated.categoryUuid)
        assertFalse(updated.archived)
    }

    @Test
    fun selectsChildAndKeepsArchivedClassificationDuringHistoricalEdit() {
        val (cat, child) = runBlocking {
            val repo = model.session.categories
            val cat = repo.createCategory(Category(
                name = "Gastos varios", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER,
                iconKey = "home",
            ))
            val child = repo.createSubcategory(Subcategory(categoryId = cat.value.id, name = "Gasolina", iconKey = "directions_car"))
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
        compose.onNodeWithText("Guardar categoría").performClick()
    }

    private fun chooseIcon(query: String, label: String) {
        waitFor("Elegir icono").performClick()
        field("Buscar iconos").performTextInput(query)
        waitFor(label).performClick()
    }
}
