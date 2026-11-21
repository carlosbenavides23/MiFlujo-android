package com.carlos.miflujo.domain.category

import com.carlos.miflujo.domain.model.MovementType

/** Resolution only: totals must still sum amountMinor as Long, separately for each currency. */
enum class CategoryReportBucket { INCOME, FIXED_COST, MAINTENANCE, OTHER, UNCATEGORIZED }

fun categoryReportBucket(type: MovementType, category: Category?): CategoryReportBucket = when {
    type == MovementType.INCOME -> CategoryReportBucket.INCOME
    category == null -> CategoryReportBucket.UNCATEGORIZED
    else -> when (category.reportGroup) {
        ReportGroup.FIXED_COST -> CategoryReportBucket.FIXED_COST
        ReportGroup.MAINTENANCE -> CategoryReportBucket.MAINTENANCE
        ReportGroup.OTHER -> CategoryReportBucket.OTHER
        ReportGroup.INCOME -> error("Income-only reporting group cannot classify an expense")
    }
}
