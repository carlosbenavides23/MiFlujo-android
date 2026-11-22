package com.carlos.miflujo.catalogpreview

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.data.cataloglab.CatalogLabSession
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.domain.category.CatalogIconColor
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.Subcategory
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.google.firebase.FirebaseApp
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogPreviewFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var context: Context
    private var activity: ActivityScenario<CatalogPreviewActivity>? = null

    @Before
    fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.carlos.miflujo.catalogpreview.uitest")
        context.deleteDatabase(DATABASE)
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.INTERNET))
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertTrue(FirebaseApp.getApps(context).isEmpty())
    }

    @After
    fun close() {
        activity?.close()
        if (context.packageName == "com.carlos.miflujo.catalogpreview.uitest") context.deleteDatabase(DATABASE)
    }

    @Test
    fun savesCapsuleQueueAndEditsAfterRelaunchWithContextualCategory() {
        launch()
        waitFor("+ Agregar").performClick()
        compose.onNodeWithContentDescription("Monto").performTextInput("12.50")
        compose.onNodeWithContentDescription("Detalle (opcional)").performTextInput("Viaje de prueba")
        createFromCapsule("Transporte local") {
            waitFor("Tipo de movimiento · Egresos").performScrollTo().performClick()
            waitFor("Ingresos y egresos").performClick()
            chooseIconAndColor()
        }
        assertSingleLevel()

        waitFor("Agregar otro").performClick()
        compose.onNodeWithContentDescription("Egreso, cambiar a ingreso").performClick()
        compose.onNodeWithContentDescription("Monto").performTextInput("8")
        compose.onNodeWithContentDescription("Detalle (opcional)").performTextInput("Reembolso de prueba")
        categoryControl("Categoría").performTouchInput { click() }
        waitFor(hasText("Transporte local") and hasClickAction()).performClick()
        waitFor("Confirmar").performClick()
        waitFor("Viaje de prueba")
        waitFor("Reembolso de prueba")

        relaunch()
        openMovement("Viaje de prueba")
        waitFor("Editar").performClick()
        categoryControl("Transporte local").assertExists()
        createFromCapsule("Transporte editado")
        assertSingleLevel()
        waitFor("Guardar cambios").performClick()
        waitFor("Transporte editado")

        activity?.close()
        activity = null
        withSession { session ->
            val data = session.snapshot()
            val category = data.categories.single { it.name == "Transporte local" }
            val replacement = data.categories.single { it.name == "Transporte editado" }
            assertEquals(CategoryScope.BOTH, category.scope)
            assertEquals("directions_car", category.iconKey)
            assertEquals(CatalogIconColor.VIOLET, category.iconColor)
            assertEquals(2, data.movements.size)
            val edited = data.movements.single { it.type == MovementType.EXPENSE }
            assertEquals(replacement.uuid, edited.categoryUuid)
            assertEquals(1250L, edited.amountMinor)
            assertEquals("Viaje de prueba", edited.detail)
            val income = data.movements.single { it.type == MovementType.INCOME }
            assertEquals(800L, income.amountMinor)
            assertEquals(category.uuid, income.categoryUuid)
            assertTrue(data.movements.all { it.subcategoryUuid == null })
        }
    }

    @Test
    fun managesCategoriesAndKeepsArchivedHistoricalSelection() {
        val historical = withSession { session ->
            val parent = session.categories.createCategory(Category(
                name = "Servicios de prueba", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.FIXED_COST,
            ))
            val child = session.categories.createSubcategory(Subcategory(
                categoryId = parent.value.id, name = "Servicio histórico oculto",
            ))
            val movement = session.movements.create(CatalogMovementDraft(
                MovementType.EXPENSE, 975L, Currency.CORDOBA, LocalDate.now(),
                CatalogMovementSelection(parent.value.uuid, child.value.uuid), "Cuenta anterior",
            ))
            session.categories.updateSubcategory(child.value.edited(archived = true), child.revision)
            movement
        }
        launch()
        openManagement()
        waitFor("Nueva categoría").performClick()
        field("Nombre").performTextInput("Categoría temporal")
        chooseIconAndColor()
        saveCategory()
        scrollToCategory("Categoría temporal").performClick()
        waitFor("Editar").performClick()
        field("Nombre").performTextReplacement("Categoría revisada")
        waitFor("Color del icono").performScrollTo().performClick()
        waitFor("Usar color predeterminado").performScrollTo().performClick().assertIsSelected()
        saveCategory()
        scrollToCategory("Categoría revisada").assertExists()
        assertSingleLevel()

        scrollToCategory("Servicios de prueba").performClick()
        waitFor("Archivar").performClick()
        waitFor("¿Archivar categoría?")
        dialogButton("Archivar").performClick()
        waitFor(hasText("Archivadas") and isEnabled()).performClick()
        scrollToCategory("Servicios de prueba (archivada)").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()

        waitFor("+ Agregar").performClick()
        categoryControl("Categoría").performTouchInput { click() }
        waitFor("Crear nueva categoría")
        compose.onNodeWithText("Servicios de prueba").assertDoesNotExist()
        compose.onNodeWithText("Servicios de prueba (archivada)").assertDoesNotExist()
        categoryControl("Categoría").performTouchInput { click() }
        waitFor("Salir").performClick()

        openMovement("Cuenta anterior")
        waitFor("Editar").performClick()
        categoryControl("Servicios de prueba (archivada)").assertExists()
        assertSingleLevel()
        compose.onNodeWithContentDescription("Detalle (opcional)").performTextReplacement("Cuenta editada")
        waitFor("Guardar cambios").performClick()
        waitFor("Cuenta editada")

        openManagement()
        waitFor(hasText("Archivadas") and isEnabled()).performClick()
        scrollToCategory("Servicios de prueba (archivada)").performClick()
        waitFor("Restaurar").performClick()
        dialogButton("Restaurar").performClick()
        waitFor(hasText("Activas") and isEnabled()).performClick()
        scrollToCategory("Servicios de prueba").assertExists()
        activity?.close()
        activity = null
        withSession { session ->
            val movement = checkNotNull(session.movements.find(historical.uuid))
            assertEquals(historical.draft.selection, movement.draft.selection)
            assertEquals(975L, movement.draft.amountMinor)
            assertEquals("Cuenta editada", movement.draft.detail)
            val data = session.snapshot()
            assertFalse(data.categories.single { it.uuid == movement.draft.selection.categoryUuid }.archived)
            val edited = data.categories.single { it.name == "Categoría revisada" }
            assertEquals("directions_car", edited.iconKey)
            assertNull(edited.iconColor)
        }
    }

    private fun launch() {
        activity = ActivityScenario.launch(CatalogPreviewActivity::class.java)
        waitFor("+ Agregar")
    }

    private fun relaunch() {
        activity?.close()
        launch()
    }

    private fun <T> withSession(block: suspend (CatalogLabSession) -> T): T {
        val db = Room.databaseBuilder(context, CatalogMovementDatabase::class.java, DATABASE)
            .addCallback(CatalogMovementDatabase.initializer).build()
        try {
            return runBlocking { block(CatalogLabSession.local(db)) }
        } finally {
            db.close()
        }
    }

    private fun openMovement(detail: String) {
        waitFor(hasText("Movimientos") and hasClickAction()).performClick()
        waitFor(detail).performClick()
        waitFor("Detalle del movimiento")
    }

    private fun openManagement() {
        compose.onNodeWithContentDescription("Ajustes").performClick()
        waitFor("Categorías").performClick()
        waitFor("Activas")
    }

    private fun scrollToCategory(label: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("catalog-management").performScrollToNode(hasText(label))
        return waitFor(label)
    }

    private fun categoryControl(label: String) = waitFor(hasContentDescription("Categoría: $label"))
    private fun createFromCapsule(name: String, customize: () -> Unit = {}) {
        waitFor(hasContentDescription("Categoría:", substring = true)).performTouchInput { click() }
        waitFor("Crear nueva categoría").performClick()
        field("Nombre").performTextInput(name)
        customize()
        saveCategory()
        categoryControl(name).assertExists()
    }

    private fun chooseIconAndColor() {
        waitFor("Elegir icono").performScrollTo().performClick()
        field("Buscar iconos").performTextInput("automovil")
        waitFor("Automóvil").performClick()
        waitFor("Color del icono").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Violeta").performScrollTo().performClick().assertIsSelected()
    }

    private fun saveCategory() {
        waitFor("Guardar categoría").performScrollTo().performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Guardar categoría").fetchSemanticsNodes().isEmpty() }
    }

    private fun assertSingleLevel() {
        compose.onNode(hasText("subcategoría", substring = true, ignoreCase = true)).assertDoesNotExist()
        compose.onNode(hasContentDescription("subcategoría", substring = true, ignoreCase = true)).assertDoesNotExist()
        compose.onNodeWithText("Servicio histórico oculto").assertDoesNotExist()
        compose.onNodeWithText("Grupo del reporte", substring = true).assertDoesNotExist()
    }

    private fun dialogButton(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun field(label: String) = waitFor(hasText(label) and hasSetTextAction())
    private fun waitFor(text: String) = waitFor(hasText(text))
    private fun waitFor(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(8_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        return compose.onNode(matcher)
    }

    private companion object {
        const val DATABASE = "catalog-preview.db"
    }
}
