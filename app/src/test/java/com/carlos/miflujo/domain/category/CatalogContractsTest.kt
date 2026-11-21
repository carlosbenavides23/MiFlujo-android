package com.carlos.miflujo.domain.category

import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class CatalogContractsTest {
    private val fixed = LegacyCategoryCatalog.categories[1]
    private val water = LegacyCategoryCatalog.subcategories[0]
    private val expense = MovementType.EXPENSE
    private val selection = CategorySelection(fixed.id, water.id)
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected structural/identity rejection") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `legacy identities are frozen and reproducible from the original enum keys`() {
        val expected = listOf(
            "e2e2de81-2c70-37cb-adda-392be081ad53", "299690db-dcc7-380d-850f-535c74bd8827",
            "9a93cc1f-a2ca-3969-b4d8-3ead840589ec", "ee7fe63c-f73b-381f-9260-f2cdee3c51c5",
            "919bb4b6-da9e-3857-b442-d682d5d80c2d", "4307fcb5-e7e5-3e01-afd3-3cbefc523e13",
            "f1b7ab2a-33aa-3912-969a-a45c9d4481f1",
        )
        val actual = LegacyCategoryCatalog.categories.map { it.uuid } + LegacyCategoryCatalog.subcategories.map { it.uuid }
        assertEquals(expected, actual)
        val keys = MovementCategory.entries.map { "category/${it.name}" } +
            MovementSubcategory.entries.map { "subcategory/${it.name}" }
        assertEquals(expected, keys.map { UUID.nameUUIDFromBytes("com.carlos.miflujo/catalog/v1/$it".toByteArray(Charsets.UTF_8)).toString() })
        actual.forEach { assertTrue(isCanonicalCatalogUuid(it)); assertEquals(3, UUID.fromString(it).version()) }
    }
    @Test fun `user creation generates independent v4 identities even with equal names`() {
        val a = Category(name = "Casa", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER)
        val b = Category(name = "Casa", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER)
        val child = Subcategory(categoryId = fixed.id, name = "Casa")
        assertNotEquals(a.uuid, b.uuid)
        listOf(a.uuid, b.uuid, child.uuid).forEach { assertTrue(isCanonicalCatalogUuid(it)); assertEquals(4, UUID.fromString(it).version()) }
    }
    @Test fun `noncanonical portable identities are rejected at model boundaries`() {
        listOf("", "miflujo:category:other", "1-1-1-1-1", fixed.uuid.uppercase(), "00000000-0000-0000-0000-000000000000").forEach {
            assertFalse(isCanonicalCatalogUuid(it))
            rejects { fixed.copy(uuid = it) }
            rejects { water.copy(uuid = it) }
        }
    }
    @Test fun `metadata edits preserve all structural properties`() {
        fixed.edited(name = "Casa", iconKey = "future-icon", archived = true, sortOrder = 20).requireSameStructureAs(fixed)
        water.edited(name = "Agua potable", archived = true).requireSameStructureAs(water)
        rejects { fixed.copy(uuid = newCatalogUuid()).requireSameStructureAs(fixed) }
        rejects { fixed.copy(id = 50).requireSameStructureAs(fixed) }
        rejects { fixed.copy(scope = CategoryScope.BOTH).requireSameStructureAs(fixed) }
        rejects { fixed.copy(reportGroup = ReportGroup.OTHER).requireSameStructureAs(fixed) }
        rejects { water.copy(uuid = newCatalogUuid()).requireSameStructureAs(water) }
        rejects { water.copy(categoryId = 4).requireSameStructureAs(water) }
    }
    @Test fun `archived relationship can be retained but never newly assigned`() {
        val parent = fixed.edited(archived = true)
        val child = water.edited(archived = true)
        assertEquals(setOf(CategorySelectionError.ARCHIVED_CATEGORY, CategorySelectionError.ARCHIVED_SUBCATEGORY), selection.validate(expense, parent, child))
        assertTrue(selection.validate(expense, parent, child, selection, expense).isEmpty())
        assertFalse(selection.validate(expense, parent, child, CategorySelection(2), expense).isEmpty())
        assertFalse(selection.validate(MovementType.INCOME, parent, child, selection, expense).isEmpty())
        assertTrue(CategorySelection().validate(expense, null, null, selection, expense).isEmpty())
    }
    @Test fun `unknown references remain integrity errors even for historical edits`() {
        assertTrue(selection.validate(expense, null, water, selection, expense).contains(CategorySelectionError.UNKNOWN_CATEGORY))
        assertTrue(selection.validate(expense, fixed, null, selection, expense).contains(CategorySelectionError.UNKNOWN_SUBCATEGORY))
        assertTrue(selection.validate(expense, fixed.copy(id = 4), water).contains(CategorySelectionError.UNKNOWN_CATEGORY))
        assertTrue(selection.validate(expense, fixed, water.copy(categoryId = 4)).contains(CategorySelectionError.WRONG_PARENT))
        assertTrue(CategorySelection().validate(expense, fixed, null).contains(CategorySelectionError.UNEXPECTED_REFERENCE))
    }
    @Test fun `portable order does not depend on names or local ids`() {
        val a = fixed.edited(sortOrder = 0)
        val b = LegacyCategoryCatalog.categories[3].edited(sortOrder = 0)
        assertEquals(listOf(a.uuid, b.uuid), listOf(b, a).sortedWith(categoryOrder).map { it.uuid })
        assertEquals(listOf(a.uuid, b.uuid), listOf(b.copy(id = 1, name = "AAA"), a.copy(id = 99, name = "ZZZ")).sortedWith(categoryOrder).map { it.uuid })
        assertEquals(listOf(water.uuid, LegacyCategoryCatalog.subcategories[2].uuid), listOf(LegacyCategoryCatalog.subcategories[2].copy(sortOrder = 0), water).sortedWith(subcategoryOrder).map { it.uuid })
    }
    @Test fun `revision order converges independently of arrival order and physical clock`() {
        val a = CatalogRevision(4, fixed.uuid)
        val b = CatalogRevision(4, water.uuid)
        assertEquals(b, maxOf(a, b))
        assertEquals(b, maxOf(b, a))
        assertTrue(a.nextFor(fixed.uuid) > b)
        rejects { CatalogRevision(-1, fixed.uuid) }
        rejects { CatalogRevision(0, "device-name") }
    }
    @Test(expected = IllegalStateException::class)
    fun `revision overflow fails instead of wrapping`() { CatalogRevision(Long.MAX_VALUE, fixed.uuid).nextFor(water.uuid) }
}
