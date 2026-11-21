package com.carlos.miflujo.domain.report

import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.model.Movement
import java.time.YearMonth

/** Legacy financial storage remains the source of its enum values. */
fun CalculateFlexibleMonthlyReport.fromLegacy(movements: List<Movement>, month: YearMonth): FlexibleMonthlyReport {
    val parents = LegacyCategoryCatalog.categories.associateBy { it.id }
    val children = LegacyCategoryCatalog.subcategories.associateBy { it.id }
    val catalog = ReportCatalog(parents.values.toList(), children.values.map { child ->
        ReportSubcategory(child.uuid, parents.getValue(child.categoryId).uuid, child.name,
            child.iconKey, child.archived, child.sortOrder)
    })
    val entries = movements.filter { it.deletedAt == null }.map {
        ReportMovement(it.uuid, it.type, it.amountMinor, it.currency, it.date,
            parents.getValue(LegacyCategoryCatalog.categoryId(it.category)).uuid,
            it.subcategory?.let { child -> children.getValue(LegacyCategoryCatalog.subcategoryId(child)).uuid },
            it.detail)
    }
    return invoke(entries, month, catalog)
}

/** Adapter for an experimental snapshot already in memory. Does not read or restore a backup. */
fun CalculateFlexibleMonthlyReport.fromCatalogSnapshot(snapshot: CatalogBackup, month: YearMonth): FlexibleMonthlyReport {
    val catalog = ReportCatalog(snapshot.categories.map { it.domain() }, snapshot.subcategories.map {
        ReportSubcategory(it.uuid, it.categoryUuid, it.name, it.iconKey, it.archived, it.sortOrder)
    })
    val entries = snapshot.movements.map {
        ReportMovement(it.uuid, it.type, it.amountMinor, it.currency, it.date, it.categoryUuid,
            it.subcategoryUuid, it.detail)
    }
    return invoke(entries, month, catalog)
}
