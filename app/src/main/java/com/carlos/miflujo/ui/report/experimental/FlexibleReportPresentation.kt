package com.carlos.miflujo.ui.report.experimental

import com.carlos.miflujo.domain.category.CategoryReportBucket
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.report.FlexibleBreakdown
import com.carlos.miflujo.domain.report.ReportClassification
import com.carlos.miflujo.domain.report.ReportReferenceIssue

internal enum class BreakdownRowKind { GROUP, CATEGORY, SUBCATEGORY, DIRECT, STATUS }

internal data class FlexibleBreakdownRow(
    val key: String,
    val label: String,
    val amountMinor: Long,
    val kind: BreakdownRowKind,
    val iconKey: String? = null,
    val archived: Boolean = false,
) {
    val visibleLabel get() = if (archived) "$label (archivada)" else label
    val indent get() = when (kind) {
        BreakdownRowKind.CATEGORY -> 1
        BreakdownRowKind.SUBCATEGORY, BreakdownRowKind.DIRECT -> 2
        BreakdownRowKind.GROUP, BreakdownRowKind.STATUS -> 0
    }
}

/** Parent rows are subtotals. Only rows at the group level reconcile with the financial total. */
internal fun FlexibleBreakdown.presentationRows(): List<FlexibleBreakdownRow> = buildList {
    val byBucket = categories.groupBy { it.bucket }
    for (bucket in CategoryReportBucket.entries) {
        val groupedAmount = groupTotals[bucket] ?: continue
        val kind = if (bucket == CategoryReportBucket.UNCATEGORIZED) BreakdownRowKind.STATUS else BreakdownRowKind.GROUP
        add(FlexibleBreakdownRow(bucket.name, bucket.visibleLabel(), groupedAmount, kind))
        for (total in byBucket[bucket].orEmpty()) {
            val category = total.category
            add(FlexibleBreakdownRow(category.uuid, category.name, total.amountMinor,
                BreakdownRowKind.CATEGORY, category.iconKey, category.archived))
            for ((child, amountMinor) in total.subcategories) {
                add(FlexibleBreakdownRow(child.uuid, child.name, amountMinor,
                    BreakdownRowKind.SUBCATEGORY, child.iconKey, child.archived))
            }
            if (total.subcategories.isNotEmpty() && total.withoutSubcategoryMinor > 0L) {
                add(FlexibleBreakdownRow("${category.uuid}:direct", "Sin subcategoría",
                    total.withoutSubcategoryMinor, BreakdownRowKind.DIRECT))
            }
        }
    }
    if (unresolvedMinor > 0L) {
        add(FlexibleBreakdownRow("unresolved", "Referencias sin resolver", unresolvedMinor, BreakdownRowKind.STATUS))
    }
}

private fun CategoryReportBucket.visibleLabel() = when (this) {
    CategoryReportBucket.INCOME -> "Ingresos generales"
    CategoryReportBucket.FIXED_COST -> "Costos fijos"
    CategoryReportBucket.MAINTENANCE -> "Mantenimiento"
    CategoryReportBucket.OTHER -> "Otros"
    CategoryReportBucket.UNCATEGORIZED -> "Sin categoría"
}

internal fun ReportClassification.visibleLabel(): String = when (this) {
    ReportClassification.Uncategorized -> "Sin categoría"
    is ReportClassification.Assigned -> buildString {
        append(category.name)
        if (category.archived) append(" (archivada)")
        subcategory?.let { child ->
            append(" / ")
            append(child.name)
            if (child.archived) append(" (archivada)")
        }
    }
    is ReportClassification.Unresolved -> when (issue) {
        ReportReferenceIssue.CATEGORY_UNAVAILABLE -> "Categoría no disponible"
        ReportReferenceIssue.SUBCATEGORY_UNAVAILABLE -> "Subcategoría no disponible"
        ReportReferenceIssue.INVALID_UUID, ReportReferenceIssue.SUBCATEGORY_WITHOUT_CATEGORY,
        ReportReferenceIssue.WRONG_PARENT, ReportReferenceIssue.WRONG_SCOPE, ReportReferenceIssue.WRONG_KIND ->
            "Referencia de categoría inválida"
    }
}

internal fun Currency.reportSymbol() = when (this) {
    Currency.CORDOBA -> "C$"
    Currency.DOLLAR -> "US$"
}
