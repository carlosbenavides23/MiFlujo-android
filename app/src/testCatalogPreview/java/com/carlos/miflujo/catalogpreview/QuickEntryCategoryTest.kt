package com.carlos.miflujo.catalogpreview

import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.MovementType.EXPENSE
import com.carlos.miflujo.domain.model.MovementType.INCOME
import com.carlos.miflujo.ui.movement.QuickEntryDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickEntryCategoryTest {
    @Test
    fun `a restored category waits for its metadata and survives a compatible type change`() {
        val waiting = original.changeCatalogType(EXPENSE, original, null, false)
        assertEquals(INCOME, waiting.type)
        assertEquals(EXPENSE, waiting.pendingCatalogType)
        assertEquals(selection, waiting.selectionForSave(selection))

        val resolved = waiting.changeCatalogType(EXPENSE, original, catalog(CategoryScope.BOTH), true)
        assertEquals(EXPENSE, resolved.type)
        assertNull(resolved.pendingCatalogType)
        assertEquals(selection, resolved.selectionForSave(selection))
    }

    @Test
    fun `an available empty or incompatible catalog clears the category after waiting`() {
        val waiting = original.changeCatalogType(EXPENSE, null, null, false)
        assertEquals(categoryUuid, waiting.catalogCategoryUuid)
        for (available in listOf(emptyMap(), catalog(CategoryScope.INCOME))) {
            val resolved = waiting.changeCatalogType(EXPENSE, null, available, false)
            assertEquals(EXPENSE, resolved.type)
            assertNull(resolved.pendingCatalogType)
            assertNull(resolved.catalogCategoryUuid)
        }
    }

    @Test
    fun `explicit removal while waiting is never undone by the arriving catalog`() {
        val waiting = original.changeCatalogType(EXPENSE, original, null, false)
        val removed = waiting.selectCatalogCategory(null, original)
        val available = catalog(CategoryScope.BOTH)
        val resolved = removed.changeCatalogType(EXPENSE, original, available, true)
        val returned = resolved.changeCatalogType(INCOME, original, available, true)
        assertEquals(EXPENSE, resolved.type)
        assertEquals(CatalogMovementSelection(), returned.selectionForSave(selection))
    }

    @Test
    fun `only a null reference means uncategorized`() {
        assertEquals("Sin categoría", catalogCategoryLabel(null, null))
        assertEquals("Categoría seleccionada", catalogCategoryLabel(categoryUuid, null))
        assertEquals("Categoría seleccionada", catalogCategoryLabel(categoryUuid, emptyMap()))
        assertEquals("Transporte", catalogCategoryLabel(categoryUuid, catalog(CategoryScope.BOTH)))
    }

    @Test
    fun `both scope survives switching in either direction`() {
        val expense = original.changeCatalogType(EXPENSE, null, catalog(CategoryScope.BOTH), false)
        val income = expense.changeCatalogType(INCOME, null, catalog(CategoryScope.BOTH), false)
        assertEquals(original.catalogCategoryUuid, expense.catalogCategoryUuid)
        assertEquals(original, income)
    }

    @Test
    fun `a category just created in the editor keeps its both scope until the snapshot arrives`() {
        val created = original.copy(catalogCategoryCreatedInEditor = true)
        val switched = created.changeCatalogType(EXPENSE, null, emptyMap(), false)
        assertEquals(categoryUuid, switched.catalogCategoryUuid)
        val archived = catalog(CategoryScope.BOTH, archived = true)
        assertNull(switched.changeCatalogType(INCOME, null, archived, false).catalogCategoryUuid)
    }

    @Test
    fun `an incompatible category is cleared and historical assignment returns with its type`() {
        val categories = catalog(CategoryScope.INCOME)
        assertNull(original.changeCatalogType(EXPENSE, null, categories, true).catalogCategoryUuid)

        val switched = original.changeCatalogType(EXPENSE, original, categories, true)
        assertEquals(CatalogMovementSelection(), switched.selectionForSave(selection))
        val restored = switched.changeCatalogType(INCOME, original, categories, true)
        assertEquals(selection, restored.selectionForSave(selection))
    }

    @Test
    fun `an archived historical category and child survive the original type`() {
        val categories = catalog(CategoryScope.BOTH, archived = true)
        assertEquals(selection, original.selectionForSave(selection))
        val switched = original.changeCatalogType(EXPENSE, original, categories, false)
        assertNull(switched.catalogCategoryUuid)
        val restored = switched.changeCatalogType(INCOME, original, categories, false)
        assertEquals(selection, restored.selectionForSave(selection))
    }

    @Test
    fun `a compatible parent keeps only a child that Room allows on the new type`() {
        val categories = catalog(CategoryScope.BOTH)
        val active = original.changeCatalogType(EXPENSE, original, categories, true)
        assertEquals(selection, active.selectionForSave(selection))

        val archived = original.changeCatalogType(EXPENSE, original, categories, false)
        assertEquals(CatalogMovementSelection(categoryUuid), archived.selectionForSave(selection))
        val restored = archived.changeCatalogType(INCOME, original, categories, false)
        assertEquals(selection, restored.selectionForSave(selection))
    }

    @Test
    fun `explicit null clears the relationship for both movement types`() {
        for (type in listOf(INCOME, EXPENSE)) {
            val draft = original.copy(type = type)
            val removed = draft.selectCatalogCategory(null, draft)
            assertNull(removed.catalogCategoryUuid)
            assertEquals(CatalogMovementSelection(), removed.selectionForSave(selection))
        }
    }

    @Test
    fun `explicit null during temporary incompatibility prevents historical restoration`() {
        val categories = catalog(CategoryScope.BOTH, archived = true)
        val switched = original.changeCatalogType(EXPENSE, original, categories, false)
        val removed = switched.selectCatalogCategory(null, original)
        val returned = removed.changeCatalogType(INCOME, original, categories, false)
        assertEquals(CatalogMovementSelection(), returned.selectionForSave(selection))
    }

    @Test
    fun `replacing a historical category clears its child even if selected again`() {
        val replacementUuid = "00000000-0000-4000-8000-000000000003"
        assertEquals(selection, original.selectCatalogCategory(categoryUuid, original).selectionForSave(selection))
        val replaced = original.selectCatalogCategory(replacementUuid, original)
        assertEquals(CatalogMovementSelection(replacementUuid), replaced.selectionForSave(selection))
        val reselected = replaced.selectCatalogCategory(categoryUuid, original)
        assertEquals(CatalogMovementSelection(categoryUuid), reselected.selectionForSave(selection))
    }

    private fun catalog(scope: CategoryScope, archived: Boolean = false) = mapOf(
        categoryUuid to BackupCategory(
            categoryUuid, "Transporte", scope, ReportGroup.OTHER, "category", archived, 0,
            CatalogRevision(0, categoryUuid),
        ),
    )

    private val categoryUuid = "00000000-0000-4000-8000-000000000001"
    private val selection = CatalogMovementSelection(categoryUuid, "00000000-0000-4000-8000-000000000002")
    private val original = QuickEntryDraft(type = INCOME, catalogCategoryUuid = categoryUuid)
}
