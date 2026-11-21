package com.carlos.miflujo.domain.report

import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryReportBucket
import com.carlos.miflujo.domain.category.isCanonicalCatalogUuid
import com.carlos.miflujo.domain.category.requireValidCatalogMetadata
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.time.YearMonth

/** Read model for visible movements. Classification accepts unresolved UUID references. */
data class ReportMovement(
    val uuid: String,
    val type: MovementType,
    val amountMinor: Long,
    val currency: Currency,
    val date: LocalDate,
    val categoryUuid: String?,
    val subcategoryUuid: String? = null,
    val detail: String? = null,
) {
    init { require(amountMinor > 0) }
}

data class ReportSubcategory(
    val uuid: String,
    val categoryUuid: String,
    val name: String,
    val iconKey: String,
    val archived: Boolean,
    val sortOrder: Int,
) {
    init {
        requireValidCatalogMetadata(uuid, name, iconKey)
        require(isCanonicalCatalogUuid(categoryUuid) && categoryUuid != uuid)
    }
}

data class ReportCatalog(val categories: List<Category>, val subcategories: List<ReportSubcategory>)

enum class ReportReferenceIssue {
    INVALID_UUID, SUBCATEGORY_WITHOUT_CATEGORY, CATEGORY_UNAVAILABLE, SUBCATEGORY_UNAVAILABLE,
    WRONG_PARENT, WRONG_SCOPE, WRONG_KIND,
}

sealed interface ReportClassification {
    data object Uncategorized : ReportClassification
    data class Assigned(val category: Category, val subcategory: ReportSubcategory?) : ReportClassification
    data class Unresolved(val issue: ReportReferenceIssue) : ReportClassification
}

data class ReportMovementLine(val movement: ReportMovement, val classification: ReportClassification)
data class SubcategoryTotal(val subcategory: ReportSubcategory, val amountMinor: Long)

/** amountMinor includes both direct assignments and all the nested subtotals. */
data class CategoryTotal(
    val category: Category,
    val bucket: CategoryReportBucket,
    val amountMinor: Long,
    val withoutSubcategoryMinor: Long,
    val subcategories: List<SubcategoryTotal>,
)

data class FlexibleBreakdown(
    val amountMinor: Long,
    val groupTotals: Map<CategoryReportBucket, Long>,
    val categories: List<CategoryTotal>,
    val unresolvedMinor: Long,
)

data class FlexibleCurrencyReport(
    val currency: Currency,
    val income: FlexibleBreakdown,
    val expense: FlexibleBreakdown,
    val movements: List<ReportMovementLine>,
) {
    val totalIncomeMinor get() = income.amountMinor
    val totalExpenseMinor get() = expense.amountMinor
    val netCashFlowMinor get() = Math.subtractExact(totalIncomeMinor, totalExpenseMinor)
}

data class FlexibleMonthlyReport(
    val month: YearMonth,
    val cordoba: FlexibleCurrencyReport,
    val dollar: FlexibleCurrencyReport,
)
