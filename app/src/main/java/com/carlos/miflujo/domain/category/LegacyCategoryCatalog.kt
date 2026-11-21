package com.carlos.miflujo.domain.category

import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory

/** Explicit IDs only for the seeded catalog. Custom IDs must never cross a device boundary. */
object LegacyCategoryCatalog {
    val seedRevision = CatalogRevision(0, "00000000-0000-4000-8000-000000000000")

    val categories = listOf(
        Category(1, "e2e2de81-2c70-37cb-adda-392be081ad53", "Ingreso general", CategoryScope.INCOME, ReportGroup.INCOME, "income", sortOrder = 0),
        Category(2, "299690db-dcc7-380d-850f-535c74bd8827", "Costos fijos", CategoryScope.EXPENSE, ReportGroup.FIXED_COST, "home", sortOrder = 1),
        Category(3, "9a93cc1f-a2ca-3969-b4d8-3ead840589ec", "Mantenimiento", CategoryScope.EXPENSE, ReportGroup.MAINTENANCE, "maintenance", sortOrder = 2),
        Category(4, "ee7fe63c-f73b-381f-9260-f2cdee3c51c5", "Otros", CategoryScope.EXPENSE, ReportGroup.OTHER, "category", sortOrder = 3),
    )
    val subcategories = listOf(
        Subcategory(1, "919bb4b6-da9e-3857-b442-d682d5d80c2d", 2, "Agua", "water", sortOrder = 0),
        Subcategory(2, "4307fcb5-e7e5-3e01-afd3-3cbefc523e13", 2, "Luz", "electricity", sortOrder = 1),
        Subcategory(3, "f1b7ab2a-33aa-3912-969a-a45c9d4481f1", 2, "Internet", "internet", sortOrder = 2),
    )
    fun categoryId(value: MovementCategory): Long = when (value) {
        MovementCategory.GENERAL_INCOME -> 1
        MovementCategory.FIXED_COST -> 2
        MovementCategory.MAINTENANCE -> 3
        MovementCategory.OTHER -> 4
    }
    fun subcategoryId(value: MovementSubcategory): Long = when (value) {
        MovementSubcategory.WATER -> 1
        MovementSubcategory.ELECTRICITY -> 2
        MovementSubcategory.INTERNET -> 3
    }
    fun selection(category: MovementCategory, subcategory: MovementSubcategory?) =
        CategorySelection(categoryId(category), subcategory?.let(::subcategoryId)).also {
            require(subcategory == null || category == MovementCategory.FIXED_COST)
        }
}
