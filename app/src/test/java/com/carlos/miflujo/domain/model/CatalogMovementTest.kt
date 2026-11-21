package com.carlos.miflujo.domain.model

import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogMovementTest {
    @Test
    fun portableSelectionsKeepNullAndKnownUuidsDistinct() {
        val category = LegacyCategoryCatalog.categories[1].uuid
        val child = LegacyCategoryCatalog.subcategories[0].uuid
        assertEquals(CatalogMovementSelection(null, null), CatalogMovementSelection())
        assertEquals(null, CatalogMovementSelection(category).subcategoryUuid)
        assertEquals(child, CatalogMovementSelection(category, child).subcategoryUuid)
        assertThrows(IllegalArgumentException::class.java) { CatalogMovementSelection(subcategoryUuid = child) }
        assertThrows(IllegalArgumentException::class.java) { CatalogMovementSelection("2", child) }
    }

    @Test
    fun amountMustBePositiveAndKeepsExactLongValues() {
        val draft = CatalogMovementDraft(MovementType.EXPENSE, Long.MAX_VALUE, Currency.CORDOBA, LocalDate.ofEpochDay(-1))
        assertEquals(Long.MAX_VALUE, draft.amountMinor)
        assertEquals(-1L, draft.date.toEpochDay())
        assertThrows(IllegalArgumentException::class.java) { draft.copy(amountMinor = 0) }
    }
}
