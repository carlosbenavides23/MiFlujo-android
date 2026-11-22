package com.carlos.miflujo.catalogpreview

import com.carlos.miflujo.cataloglab.categoriesByRecentUsage
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.backup.backup
import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogCategoryOrderTest {
    private val now = LocalDateTime.of(2026, 11, 21, 10, 0)
    private val revision = CatalogRevision(0, "10000000-0000-4000-8000-000000000000")

    @Test
    fun ranksByCountThenOriginalRegistrationTime() {
        val mostUsed = category("Tienda")
        val lastUsed = category("Papelería")
        val edited = category("Mercado")
        val unused = category("Alquiler")
        val catalog = CatalogBackup(
            now, listOf(unused, lastUsed, edited, mostUsed).map { it.backup(revision) }, emptyList(),
            listOf(
                movement(mostUsed, 90), movement(mostUsed, 30),
                movement(edited, 25).copy(updatedAt = now),
                movement(lastUsed, 7),
                movement(unused, 91).copy(updatedAt = now),
                movement(unused, -1),
                movement(null, 1),
            ),
        )

        assertEquals(listOf(mostUsed, lastUsed, edited, unused).map { it.uuid },
            catalog.categoriesByRecentUsage(now).map { it.uuid })
    }

    @Test
    fun keepsUnusedAndExactTiesStableRegardlessOfSnapshotOrder() {
        val seed = category("Zeta", sortOrder = 0)
        val alpha = category("Alfa", sortOrder = 1)
        val duplicate = alpha.copy(uuid = "20000000-0000-4000-8000-000000000000")
        val first = alpha.copy(uuid = "10000000-0000-4000-8000-000000000000")
        val catalog = CatalogBackup(now,
            listOf(duplicate, seed, first).map { it.backup(revision) }, emptyList(), emptyList())
        val expected = listOf(seed, first, duplicate).map { it.uuid }

        assertEquals(expected, catalog.categoriesByRecentUsage(now).map { it.uuid })
        assertEquals(expected, catalog.copy(categories = catalog.categories.reversed())
            .categoriesByRecentUsage(now).map { it.uuid })
    }

    private fun category(name: String, sortOrder: Int = 0) = Category(
        name = name, scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER, sortOrder = sortOrder,
    )

    private fun movement(category: Category?, daysAgo: Long): BackupMovement {
        val created = now.minusDays(daysAgo)
        return BackupMovement(
            uuid = UUID.randomUUID().toString(),
            type = MovementType.EXPENSE,
            amountMinor = 100,
            currency = Currency.CORDOBA,
            date = now.toLocalDate().minusYears(1),
            categoryUuid = category?.uuid,
            subcategoryUuid = null,
            detail = null,
            createdAt = created,
            updatedAt = created,
        )
    }
}
