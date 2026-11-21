package com.carlos.miflujo.domain.report

import com.carlos.miflujo.domain.category.CategoryReportBucket
import com.carlos.miflujo.domain.category.categoryOrder
import com.carlos.miflujo.domain.category.categoryReportBucket
import com.carlos.miflujo.domain.category.isCanonicalCatalogUuid
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.YearMonth

class CalculateFlexibleMonthlyReport {
    operator fun invoke(
        movements: List<ReportMovement>,
        month: YearMonth,
        catalog: ReportCatalog,
    ): FlexibleMonthlyReport {
        val parents = catalog.categories.associateBy { it.uuid }
        val children = catalog.subcategories.associateBy { it.uuid }
        require(parents.size == catalog.categories.size && children.size == catalog.subcategories.size &&
            parents.keys.none { it in children }) { "Identidades de catálogo ambiguas" }

        fun classify(movement: ReportMovement): ReportClassification {
            val categoryId = movement.categoryUuid
            val childId = movement.subcategoryUuid
            if (categoryId == null) {
                return if (childId == null) ReportClassification.Uncategorized
                else ReportClassification.Unresolved(ReportReferenceIssue.SUBCATEGORY_WITHOUT_CATEGORY)
            }
            fun invalid(issue: ReportReferenceIssue) = ReportClassification.Unresolved(issue)
            if (!isCanonicalCatalogUuid(categoryId) || (childId != null && !isCanonicalCatalogUuid(childId))) {
                return invalid(ReportReferenceIssue.INVALID_UUID)
            }
            if (categoryId in children || childId in parents) return invalid(ReportReferenceIssue.WRONG_KIND)
            val category = parents[categoryId] ?: return invalid(ReportReferenceIssue.CATEGORY_UNAVAILABLE)
            if (!category.scope.supports(movement.type)) return invalid(ReportReferenceIssue.WRONG_SCOPE)
            if (childId == null) return ReportClassification.Assigned(category, null)
            val child = children[childId] ?: return invalid(ReportReferenceIssue.SUBCATEGORY_UNAVAILABLE)
            if (child.categoryUuid != categoryId) return invalid(ReportReferenceIssue.WRONG_PARENT)
            return ReportClassification.Assigned(category, child)
        }

        val selected = movements.filter { YearMonth.from(it.date) == month }
        require(selected.map { it.uuid }.toSet().size == selected.size) { "Movimientos duplicados en el período" }
        val byCurrency = selected.sortedByDescending { it.date }
            .map { ReportMovementLine(it, classify(it)) }
            .groupBy { it.movement.currency }

        fun summary(currency: Currency): FlexibleCurrencyReport {
            val lines = byCurrency[currency].orEmpty()
            val (income, expense) = lines.partition { it.movement.type == MovementType.INCOME }
            return FlexibleCurrencyReport(currency, breakdown(income), breakdown(expense), lines)
        }
        return FlexibleMonthlyReport(month, summary(Currency.CORDOBA), summary(Currency.DOLLAR))
    }

    private fun breakdown(lines: List<ReportMovementLine>): FlexibleBreakdown {
        val buckets = mutableMapOf<CategoryReportBucket, Long>()
        val categoryLines = mutableMapOf<String, MutableList<ReportMovementLine>>()
        var total = 0L
        var unresolved = 0L
        for (line in lines) {
            val amount = line.movement.amountMinor
            total = Math.addExact(total, amount)
            val bucket = when (val classification = line.classification) {
                ReportClassification.Uncategorized -> CategoryReportBucket.UNCATEGORIZED
                is ReportClassification.Assigned -> {
                    categoryLines.getOrPut(classification.category.uuid, ::mutableListOf).add(line)
                    categoryReportBucket(line.movement.type, classification.category)
                }
                is ReportClassification.Unresolved -> {
                    unresolved = Math.addExact(unresolved, amount)
                    continue
                }
            }
            buckets[bucket] = Math.addExact(buckets[bucket] ?: 0L, amount)
        }

        val categories = categoryLines.values.map { group ->
            val first = group.first()
            val category = (first.classification as ReportClassification.Assigned).category
            val children = group.groupBy { (it.classification as ReportClassification.Assigned).subcategory }
            CategoryTotal(
                category = category,
                bucket = categoryReportBucket(first.movement.type, category),
                amountMinor = group.sumAmounts(),
                withoutSubcategoryMinor = children[null].orEmpty().sumAmounts(),
                subcategories = children.mapNotNull { (child, entries) ->
                    child?.let { SubcategoryTotal(it, entries.sumAmounts()) }
                }.sortedWith(compareBy({ it.subcategory.sortOrder }, { it.subcategory.uuid })),
            )
        }.sortedWith(compareBy(categoryOrder) { it.category })
        return FlexibleBreakdown(total, buckets.toMap(), categories, unresolved)
    }

    private fun List<ReportMovementLine>.sumAmounts() =
        fold(0L) { sum, line -> Math.addExact(sum, line.movement.amountMinor) }
}
