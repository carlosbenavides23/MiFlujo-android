package com.carlos.miflujo.catalogpreview

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlos.miflujo.cataloglab.CatalogIcon
import com.carlos.miflujo.ui.movement.CapsuleMenuControl
import com.carlos.miflujo.cataloglab.CatalogCategoryPicker
import com.carlos.miflujo.domain.backup.backup
import com.carlos.miflujo.ui.theme.MiFlujoTheme
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
            chooseIconAndColor()
        }
        assertSingleLevel()

        waitFor("Agregar otro").performClick()
        waitFor(hasContentDescription("Editar movimiento")).performClick()
        categoryControl("Transporte local").assertIsDisplayed()
        waitFor("Guardar").performClick()
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
            assertEquals(ReportGroup.OTHER, category.reportGroup)
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
    fun savesMixedQueueReportsUncategorizedAmountsAndAssignsCategoryLater() {
        val category = withSession { session ->
            session.categories.createCategory(Category(
                name = "Compras de prueba", scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER,
            )).value
        }
        launch()
        waitFor("+ Agregar").performClick()
        val amount = compose.onNodeWithContentDescription("Monto")
        val detail = compose.onNodeWithContentDescription("Detalle (opcional)")
        val categoryOption = hasText(category.name) and hasClickAction() and
            hasAnyAncestor(hasTestTag("category-picker-list"))

        compose.onNodeWithContentDescription("Egreso, cambiar a ingreso").performClick()
        amount.performTextInput("100")
        detail.performTextInput("Ingreso libre")
        waitFor("Agregar otro").performClick()

        compose.onNodeWithContentDescription("Ingreso, cambiar a egreso").performClick()
        amount.performTextInput("25")
        detail.performTextInput("Egreso libre")
        waitFor("Agregar otro").performClick()
        assertTrue(withSession { it.snapshot().movements.isEmpty() })

        amount.performTextInput("10")
        detail.performTextInput("Egreso clasificado")
        categoryControl("Categoría").performTouchInput { click() }
        waitFor(categoryOption).performClick()
        waitFor("Confirmar").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("Registro con cápsulas").fetchSemanticsNodes().isEmpty()
        }

        val original = withSession { session ->
            val saved = session.snapshot()
            val income = saved.movements.single { it.detail == "Ingreso libre" }
            val expense = saved.movements.single { it.detail == "Egreso libre" }
            assertEquals(3, saved.movements.size)
            assertEquals(MovementType.INCOME, income.type)
            assertNull(income.categoryUuid)
            assertEquals(MovementType.EXPENSE, expense.type)
            assertNull(expense.categoryUuid)
            assertEquals(category.uuid, saved.movements.single { it.detail == "Egreso clasificado" }.categoryUuid)
            assertFalse(saved.categories.any { it.name == "Sin categoría" })
            expense
        }

        waitFor(hasText("Reporte") and hasClickAction()).performClick()
        waitFor("+ C\$ 100.00")
        waitFor("- C\$ 35.00")
        waitFor("+ C\$ 65.00")
        waitFor(hasText("Sin categoría") and hasAnySibling(hasText("C\$ 25.00")))
            .performScrollTo().assertIsDisplayed()
        waitFor(hasText("Otros") and hasAnySibling(hasText("C\$ 10.00")))
        waitFor(hasText("· Sin categoría", substring = true) and hasText("Egreso", substring = true))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("null", ignoreCase = true).assertDoesNotExist()

        openMovement("Egreso libre")
        dialogButton("Sin categoría").assertIsDisplayed()
        waitFor("Editar").performClick()
        categoryControl("Categoría").assertExists()
        detail.performTextReplacement("Egreso libre editado")
        waitFor("Guardar cambios").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("Editar movimiento").fetchSemanticsNodes().isEmpty()
        }
        waitFor("Egreso libre editado")
        withSession { session ->
            val edited = checkNotNull(session.movements.find(original.uuid))
            assertEquals("Egreso libre editado", edited.draft.detail)
            assertNull(edited.draft.selection.categoryUuid)
        }

        openMovement("Egreso libre editado")
        waitFor("Editar").performClick()
        categoryControl("Categoría").performTouchInput { click() }
        waitFor(categoryOption).performClick()
        waitFor("Guardar cambios").performClick()
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("Editar movimiento").fetchSemanticsNodes().isEmpty()
        }
        withSession { session ->
            val edited = checkNotNull(session.movements.find(original.uuid))
            assertEquals(category.uuid, edited.draft.selection.categoryUuid)
            assertEquals(2500L, edited.draft.amountMinor)
        }
    }

    @Test
    fun dialogGrowsWithPendingCardsAndOnlyTheQueueScrollsAtTheLimit() {
        launch()
        waitFor("+ Agregar").performClick()
        compose.onNodeWithText("Los movimientos pendientes aparecerán aquí.").assertDoesNotExist()
        val title = compose.onNodeWithText("Registro con cápsulas")
        val amount = compose.onNodeWithContentDescription("Monto")
        val actions = compose.onNodeWithText("Agregar otro")
        val queue = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(isDialog())
        )
        fun scrollExtent() = queue.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
        fun addPending(number: Int) {
            amount.performTextInput(number.toString())
            actions.performClick()
        }
        fun cardTop(number: Int) = waitFor("− C\$ $number").fetchSemanticsNode().boundsInRoot.top
        fun contentHeight() = actions.fetchSemanticsNode().boundsInRoot.bottom -
            title.fetchSemanticsNode().boundsInRoot.top

        queue.assertDoesNotExist()
        val emptyHeight = contentHeight()
        addPending(101)
        waitFor("− C\$ 101").assertIsDisplayed()
        val oneCardHeight = contentHeight()
        assertTrue(oneCardHeight > emptyHeight)
        assertEquals(0f, scrollExtent(), 0f)

        addPending(102)
        assertTrue(cardTop(101) < cardTop(102))
        assertTrue(cardTop(102) < amount.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(contentHeight() > oneCardHeight)
        assertEquals(0f, scrollExtent(), 0f)

        var newest = 102
        for (number in 103..124) {
            addPending(number)
            newest = number
            if (scrollExtent() > 0f) break
        }
        assertTrue(scrollExtent() > 0f)
        val maximumHeight = contentHeight()
        val capsuleTop = amount.fetchSemanticsNode().boundsInRoot.top
        val actionsTop = actions.fetchSemanticsNode().boundsInRoot.top
        addPending(newest + 1)
        val newestCard = compose.onAllNodesWithText("− C\$ ${newest + 1}")
        compose.waitUntil(8_000) {
            newestCard.fetchSemanticsNodes().any { it.boundsInRoot.height > 0f }
        }
        assertEquals(maximumHeight, contentHeight(), 1f)
        queue.performScrollToIndex(0)
        waitFor("− C\$ 101").assertIsDisplayed()
        assertEquals(capsuleTop, amount.fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(actionsTop, actions.fetchSemanticsNode().boundsInRoot.top, 1f)
        amount.performTouchInput { click() }
        amount.assertIsDisplayed()
        actions.assertIsDisplayed()
    }

    @Test
    fun restoresSuspendedDraftAfterRecreationCancelAndSave() {
        withSession { session ->
            for (name in listOf("Servicios de prueba", "Materiales de prueba")) {
                session.categories.createCategory(Category(
                    name = name, scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER,
                ))
            }
        }
        launch()
        waitFor("+ Agregar").performClick()
        val amount = compose.onNodeWithContentDescription("Monto")
        val detail = compose.onNodeWithContentDescription("Detalle (opcional)")
        amount.performTextInput("800")
        categoryControl("Categoría").performTouchInput { click() }
        waitFor(hasText("Servicios de prueba") and hasClickAction()).performClick()
        waitFor("Agregar otro").performClick()

        compose.onNodeWithContentDescription("Egreso, cambiar a ingreso").performClick()
        compose.onNodeWithContentDescription("Córdobas, cambiar a dólares").performClick()
        amount.performTextInput("250.")
        detail.performTextInput("Compra de materiales")
        categoryControl("Categoría").performTouchInput { click() }
        waitFor(hasText("Materiales de prueba") and hasClickAction()).performClick()
        waitFor(hasContentDescription("Editar movimiento")).performClick()
        amount.assertTextEquals("800").performTextReplacement("900")
        categoryControl("Servicios de prueba").assertExists()

        checkNotNull(activity).recreate()
        waitFor("Editando movimiento")
        amount.assertTextEquals("900")
        waitFor("Salir").performClick()
        waitFor("¿Descartar cambios?")
        waitFor(hasText("borrador del movimiento nuevo", substring = true))
        waitFor("Continuar").performClick()
        amount.assertTextEquals("900")
        waitFor("Cancelar").performClick()
        amount.assertTextEquals("250.")
        detail.assertTextEquals("Compra de materiales")
        categoryControl("Materiales de prueba").assertExists()
        waitFor("− C\$ 800")

        waitFor(hasContentDescription("Editar movimiento")).performClick()
        amount.performTextReplacement("950")
        waitFor("Guardar").performClick()
        amount.assertTextEquals("250.")
        detail.assertTextEquals("Compra de materiales")
        categoryControl("Materiales de prueba").assertExists()
        compose.onNodeWithContentDescription("Ingreso, cambiar a egreso").assertExists()
        compose.onNodeWithContentDescription("Dólares, cambiar a córdobas").assertExists()
        waitFor("− C\$ 950")
        assertTrue(withSession { it.snapshot().movements.isEmpty() })

        waitFor(hasContentDescription("Editar movimiento")).performClick()
        waitFor("Salir").performClick()
        waitFor("Descartar y salir").performClick()
        waitFor("+ Agregar")
        compose.onNodeWithText("Registro con cápsulas").assertDoesNotExist()
        assertTrue(withSession { it.snapshot().movements.isEmpty() })
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
            val historicalCategory = data.categories.single { it.uuid == movement.draft.selection.categoryUuid }
            assertFalse(historicalCategory.archived)
            assertEquals(CategoryScope.EXPENSE, historicalCategory.scope)
            assertEquals(ReportGroup.FIXED_COST, historicalCategory.reportGroup)
            val edited = data.categories.single { it.name == "Categoría revisada" }
            assertEquals("directions_car", edited.iconKey)
            assertNull(edited.iconColor)
        }
    }

    @Test
    fun opensRankedCatalogScrollsAndCreatesWithoutLosingDraft() {
        withSession { session ->
            val categories = (0 until 80).map { index ->
                session.categories.createCategory(Category(
                    name = "Comercio ${index.toString().padStart(2, '0')}",
                    scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER,
                )).value
            }
            repeat(2) {
                session.movements.create(CatalogMovementDraft(
                    MovementType.EXPENSE, 100L, Currency.CORDOBA, LocalDate.now(),
                    CatalogMovementSelection(categories.last().uuid),
                ))
            }
        }

        launch()
        waitFor("+ Agregar").performClick()
        compose.onNodeWithContentDescription("Monto").performTextInput("12.50")
        compose.onNodeWithContentDescription("Detalle (opcional)").performTextInput("Borrador de prueba")
        categoryControl("Categoría").performTouchInput { click() }
        waitFor("Comercio 79").assertIsDisplayed()
        waitFor("Crear nueva categoría").assertIsDisplayed()

        val list = compose.onNodeWithTag("category-picker-list")
        list.performScrollToNode(hasText("Comercio 76"))
        waitFor("Comercio 76").assertIsDisplayed().performClick()
        categoryControl("Comercio 76").assertIsDisplayed()

        categoryControl("Comercio 76").performTouchInput { click() }
        list.performScrollToNode(hasText("Comercio 76"))
        waitFor("Comercio 76").assertIsSelected()
        waitFor("Registro con cápsulas").performTouchInput { click() }
        compose.onNodeWithText("Crear nueva categoría").assertDoesNotExist()
        categoryControl("Comercio 76").assertIsDisplayed()

        createFromCapsule("Comercio nuevo")
        waitFor("Confirmar").performClick()
        waitFor("Borrador de prueba")
        activity?.close()
        activity = null
        withSession { session ->
            val snapshot = session.snapshot()
            val category = snapshot.categories.single { it.name == "Comercio nuevo" }
            val saved = snapshot.movements.single { it.detail == "Borrador de prueba" }
            assertEquals(1250L, saved.amountMinor)
            assertEquals(category.uuid, saved.categoryUuid)
            assertEquals(LocalDate.now(), saved.date)
            assertEquals(Currency.CORDOBA, saved.currency)
        }
    }

    @Test
    fun showsFullAccessibleCategoryNameInCompactControlWithLargeTextInBothThemes() {
        val label = "Transporte y mantenimiento para todos los desplazamientos"
        val category = withSession { session ->
            val stored = session.categories.createCategory(Category(
                name = label, scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER,
                iconColor = CatalogIconColor.VIOLET,
            ))
            stored.value.backup(stored.revision)
        }
        launch()
        for (dark in listOf(false, true)) {
            activity?.onActivity {
                it.setContent {
                    DeviceConfigurationOverride(
                        DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 480.dp)) then
                            DeviceConfigurationOverride.FontScale(1.8f),
                    ) {
                        MiFlujoTheme(darkTheme = dark) {
                            var picking by remember { mutableStateOf(false) }
                            Box(Modifier.width(148.dp)) {
                                CapsuleMenuControl(
                                    label = "Categoría",
                                    text = category.name,
                                    onClick = { picking = !picking },
                                    announceLabelOnly = true,
                                ) { CatalogIcon(category.iconKey, it, color = category.iconColor) }
                                if (picking) CatalogCategoryPicker(
                                    listOf(category), MovementType.EXPENSE, category.uuid,
                                    allowUncategorized = false, onDismiss = { picking = false },
                                    onSelect = { picking = false }, onCreate = { picking = false },
                                )
                            }
                        }
                    }
                }
            }
            categoryControl(label).assertIsDisplayed().performClick()
            waitFor("Crear nueva categoría").assertIsDisplayed()
            waitFor(label).assertIsDisplayed().assertIsSelected()
            waitFor(label).performClick()
            categoryControl(label).assertIsDisplayed()
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
        compose.onNodeWithText("Tipo de movimiento", substring = true).assertDoesNotExist()
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
