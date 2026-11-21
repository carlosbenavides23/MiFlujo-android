package com.carlos.miflujo.data.cloud.catalogmovement

import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.google.firebase.Timestamp
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CatalogMovementFieldsTest {
    private val uid = "10000000-0000-4000-8000-000000000001"
    private val category = LegacyCategoryCatalog.categories[1]
    private val child = LegacyCategoryCatalog.subcategories.first()
    private val record = RemoteCatalogMovement(
        MovementContent(uid, CatalogMovementDraft(
            MovementType.EXPENSE, Long.MAX_VALUE, Currency.CORDOBA, LocalDate.of(2024, 2, 29),
            CatalogMovementSelection(category.uuid, child.uuid), "Agua 🌊",
        ), -1L, 987L, 987L), 7,
    )

    @Test fun `strict v2 round trip preserves portable identities, cents and tombstone`() {
        val fields = CatalogMovementFields.encode(record)
        assertEquals(Long.MAX_VALUE, fields["amountMinor"])
        assertFalse("id" in fields || "categoryId" in fields || "syncStatus" in fields)
        assertEquals(record, CatalogMovementFields.decode(uid, fields))
        assertEquals(Timestamp(-1, 999_000_000), fields["createdAt"])
    }

    @Test fun `wire rejects incompatible shape, coercions, child without parent and sub-millisecond timestamps`() {
        val fields = CatalogMovementFields.encode(record)
        for (wrong in listOf(
            fields - "categoryUuid", fields + ("categoryId" to 4), fields + ("schemaVersion" to 1L),
            fields + ("amountMinor" to 12.0), fields + ("categoryUuid" to null), fields + ("revision" to 0L),
            fields + ("createdAt" to Timestamp(0, 2)),
        )) assertThrows(IllegalArgumentException::class.java) { CatalogMovementFields.decode(uid, wrong) }
    }

    @Test fun `backfill preserves existing classification and UUID with initial server revision`() {
        val legacy = CatalogMovementFields.encode(record) - setOf("categoryUuid", "subcategoryUuid", "revision") +
            mapOf("schemaVersion" to 1L, "category" to "FIXED_COST", "subcategory" to "WATER")
        assertEquals(record.copy(revision = 1), CatalogMovementFields.upgradeLegacy(uid, legacy))
        assertThrows(IllegalArgumentException::class.java) {
            CatalogMovementFields.upgradeLegacy(uid, legacy + ("subcategory" to "UNKNOWN"))
        }
    }
}
