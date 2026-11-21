package com.carlos.miflujo.cataloglab

import androidx.compose.ui.graphics.Color
import com.carlos.miflujo.domain.category.CatalogIconColor
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CatalogEditorStateTest {
    @Test
    fun tenSwatchesReachThreeToOneOnLightAndDarkSurfaces() {
        assertEquals(CatalogIconColor.entries, iconSwatches.keys.toList())
        val backgrounds = listOf(Color.White, Color(0xFFF0EDF2), Color(0xFF121316), Color(0xFF292A30))
        for ((key, swatch) in iconSwatches) {
            for (background in backgrounds) {
                val tint = catalogTint(swatch.reference, background)
                assertTrue(key.key, iconContrast(tint, background) >= 3f)
                if (iconContrast(swatch.reference, background) >= 3f) assertEquals(swatch.reference, tint)
            }
        }
    }

    @Test
    fun searchesSpanishAliasesAndFallsBackForUnknownKeys() {
        assertEquals(50, catalogIcons.map { it.key }.distinct().size)
        assertEquals(9, catalogIcons.map { it.group }.distinct().size)
        assertEquals("directions_car", searchCatalogIcons("AUTOMOVIL", IconGroup.TRANSPORT).single().key)
        assertEquals("home", searchCatalogIcons("hogar alquiler", IconGroup.HOME).single().key)
        assertTrue(searchCatalogIcons("automovil", IconGroup.HOME).isEmpty())
        assertEquals(DEFAULT_CATALOG_ICON, resolveCatalogIcon("icono-importado-desconocido").key)
        assertEquals("payments", resolveCatalogIcon("income").key)
    }

    @Test
    fun contextualSelectionChangesOnlyClassificationAndClearsOldChild() {
        val old = "180312b9-407e-417d-8e13-911bef27eca0"
        val new = "540a53f2-caed-4c3d-963e-072f5a26046e"
        val child = "fffa35d3-c9fd-41d6-ba9f-e455d3a8145a"
        val draft = CatalogMovementDraft(
            MovementType.EXPENSE, 12345L, Currency.DOLLAR, LocalDate.of(2026, 11, 21),
            CatalogMovementSelection(old, child), "Recibo",
        )
        val entry = CatalogMovementEntry.from(draft)
        assertEquals("123.45", entry.amount)
        assertEquals("2026-11-21", entry.date)
        assertEquals("Recibo", entry.detail)
        assertEquals(entry, entry.selectCategory(old))
        assertEquals(entry.copy(categoryUuid = new, subcategoryUuid = null), entry.selectCategory(new))
    }

    @Test
    fun validatesNameWithoutInterpretingItsMeaning() {
        assertNull(catalogNameError("Trabajo"))
        assertNull(catalogNameError("Trabajo 🐾"))
        assertNotNull(catalogNameError("   "))
        assertNull(catalogNameError("🐾".repeat(120)))
        assertNotNull(catalogNameError("🐾".repeat(121)))
    }
}
