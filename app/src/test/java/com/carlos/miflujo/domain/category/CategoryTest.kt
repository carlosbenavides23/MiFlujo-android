package com.carlos.miflujo.domain.category

import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import org.junit.Assert.*
import org.junit.Test

class CategoryTest {
    private val fixed = LegacyCategoryCatalog.categories.single { it.id == 2L }
    private val water = LegacyCategoryCatalog.subcategories.single { it.id == 1L }

    @Test fun `all legacy enums map to unique stable seeded identities`() {
        assertEquals(setOf(1L, 2L, 3L, 4L), MovementCategory.entries.map(LegacyCategoryCatalog::categoryId).toSet())
        assertEquals(setOf(1L, 2L, 3L), MovementSubcategory.entries.map(LegacyCategoryCatalog::subcategoryId).toSet())
        assertEquals(7, (LegacyCategoryCatalog.categories.map { it.uuid } + LegacyCategoryCatalog.subcategories.map { it.uuid }).toSet().size)
        MovementSubcategory.entries.forEach {
            val selection = LegacyCategoryCatalog.selection(MovementCategory.FIXED_COST, it)
            val sub = LegacyCategoryCatalog.subcategories.single { s -> s.id == selection.subcategoryId }
            assertTrue(selection.isAssignable(MovementType.EXPENSE, fixed, sub))
        }
    }
    @Test fun `optional selection and optional child are valid`() {
        assertTrue(CategorySelection().isAssignable(MovementType.INCOME, null, null))
        assertTrue(CategorySelection(2).isAssignable(MovementType.EXPENSE, fixed, null))
        assertTrue(CategorySelection(2, 1).isAssignable(MovementType.EXPENSE, fixed, water))
    }
    @Test fun `wrong parent type missing and archived references cannot be newly assigned`() {
        val selection = CategorySelection(2, 1)
        assertFalse(selection.isAssignable(MovementType.INCOME, fixed, water))
        assertFalse(selection.isAssignable(MovementType.EXPENSE, fixed, water.copy(categoryId = 3)))
        assertFalse(selection.isAssignable(MovementType.EXPENSE, fixed.copy(archived = true), water))
        assertFalse(selection.isAssignable(MovementType.EXPENSE, fixed, water.copy(archived = true)))
        assertFalse(selection.isAssignable(MovementType.EXPENSE, null, null))
    }
    @Test(expected = IllegalArgumentException::class) fun `child without parent is rejected`() { CategorySelection(subcategoryId = 1) }
    @Test fun `uncategorized expenses remain in their own report bucket`() {
        assertEquals(CategoryReportBucket.UNCATEGORIZED, categoryReportBucket(MovementType.EXPENSE, null))
        assertEquals(CategoryReportBucket.INCOME, categoryReportBucket(MovementType.INCOME, fixed.copy(scope = CategoryScope.BOTH)))
        assertEquals(CategoryReportBucket.FIXED_COST, categoryReportBucket(MovementType.EXPENSE, fixed.copy(name = "Casa", archived = true)))
    }
    @Test fun `renaming and archiving preserve report identity`() {
        assertEquals(ReportGroup.FIXED_COST, fixed.copy(name = "Casa", archived = true, iconKey = "unknown-future-key").reportGroup)
        assertTrue(fixed.copy(scope = CategoryScope.BOTH).scope.supports(MovementType.INCOME))
        assertTrue(fixed.copy(scope = CategoryScope.BOTH).scope.supports(MovementType.EXPENSE))
    }
}
