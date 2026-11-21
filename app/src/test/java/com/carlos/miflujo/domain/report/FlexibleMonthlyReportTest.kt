package com.carlos.miflujo.domain.report

import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.BackupSubcategory
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.backup.backup
import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryReportBucket
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.newCatalogUuid
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.usecase.CalculateMonthlyCashFlowReportUseCase
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FlexibleMonthlyReportTest {
    private val calculate = CalculateFlexibleMonthlyReport()
    private val month = YearMonth.of(2024, 2)
    private val day = month.atEndOfMonth()
    private val emptyCatalog = ReportCatalog(emptyList(), emptyList())

    @Test
    fun legacyAdapterPreservesBothCurrenciesAndEveryLegacySubtotal() {
        val categories = listOf(
            MovementCategory.GENERAL_INCOME to null,
            MovementCategory.FIXED_COST to MovementSubcategory.WATER,
            MovementCategory.FIXED_COST to MovementSubcategory.ELECTRICITY,
            MovementCategory.FIXED_COST to MovementSubcategory.INTERNET,
            MovementCategory.MAINTENANCE to null,
            MovementCategory.OTHER to null,
        )
        val movements = Currency.entries.flatMapIndexed { currencyIndex, currency ->
            categories.mapIndexed { index, (category, child) ->
                Movement(
                    type = if (index == 0) MovementType.INCOME else MovementType.EXPENSE,
                    amountMinor = 900_000_000_000_001L + currencyIndex * 100 + index,
                    currency = currency,
                    date = if (index == 0) month.atDay(1) else day,
                    category = category,
                    subcategory = child,
                    detail = "Detalle $index",
                    createdAt = day.atStartOfDay(),
                    updatedAt = day.atStartOfDay(),
                )
            }
        }
        val outside = movements.first().copy(uuid = newCatalogUuid(), date = day.plusDays(1))
        val deleted = movements.first().copy(uuid = newCatalogUuid(), deletedAt = day.atStartOfDay())
        val existing = CalculateMonthlyCashFlowReportUseCase()(movements + outside, month)
        val flexible = calculate.fromLegacy(movements + outside + deleted, month)

        assertEquals(month, flexible.month)
        for ((old, new) in listOf(existing.cordoba to flexible.cordoba, existing.dollar to flexible.dollar)) {
            assertEquals(old.currency, new.currency)
            assertEquals(old.totalIncomeMinor, new.totalIncomeMinor)
            assertEquals(old.totalExpenseMinor, new.totalExpenseMinor)
            assertEquals(old.netCashFlowMinor, new.netCashFlowMinor)
            assertEquals(old.totalIncomeMinor, new.income.groupTotals.getValue(CategoryReportBucket.INCOME))
            val previous = old.expenseBreakdown
            assertEquals(previous.fixedCostMinor, new.expense.groupTotals.getValue(CategoryReportBucket.FIXED_COST))
            assertEquals(previous.maintenanceMinor, new.expense.groupTotals.getValue(CategoryReportBucket.MAINTENANCE))
            assertEquals(previous.otherMinor, new.expense.groupTotals.getValue(CategoryReportBucket.OTHER))
            val children = new.expense.categories.single { it.bucket == CategoryReportBucket.FIXED_COST }.subcategories
            for ((seed, total) in listOf(
                MovementSubcategory.WATER to previous.waterMinor,
                MovementSubcategory.ELECTRICITY to previous.electricityMinor,
                MovementSubcategory.INTERNET to previous.internetMinor,
            )) {
                val uuid = LegacyCategoryCatalog.subcategories.single { it.id == LegacyCategoryCatalog.subcategoryId(seed) }.uuid
                assertEquals(total, children.single { it.subcategory.uuid == uuid }.amountMinor)
            }
            val expected = movements.filter { it.currency == old.currency }.associateBy { it.uuid }
            assertEquals(expected.keys, new.movements.map { it.movement.uuid }.toSet())
            for ((movement) in new.movements) {
                val original = expected.getValue(movement.uuid)
                assertEquals(original.date, movement.date)
                assertEquals(original.detail, movement.detail)
                assertEquals(original.amountMinor, movement.amountMinor)
            }
        }
    }

    @Test
    fun portableSnapshotKeepsArchivedAssignmentsDistinctFromUncategorizedAndDirectAmounts() {
        val category = expenseCategory().edited(name = "Nombre actual del servicio", archived = true)
        val child = ReportSubcategory(newCatalogUuid(), category.uuid, "Contrato antiguo", "bill", true, 0)
        val revision = CatalogRevision(1, newCatalogUuid())
        val entries = listOf(
            movement(401, category.uuid, child.uuid),
            movement(202, category.uuid),
            movement(103),
            movement(Long.MAX_VALUE, type = MovementType.INCOME, currency = Currency.DOLLAR),
        )
        val time = day.atStartOfDay()
        val snapshot = CatalogBackup(
            time,
            listOf(category.backup(revision)),
            listOf(BackupSubcategory(child.uuid, category.uuid, child.name, child.iconKey, child.archived, child.sortOrder, revision)),
            entries.map {
                BackupMovement(it.uuid, it.type, it.amountMinor, it.currency, it.date, it.categoryUuid,
                    it.subcategoryUuid, it.detail, time, time)
            },
        ).validatedCopy()

        val report = calculate.fromCatalogSnapshot(snapshot, month)
        val expense = report.cordoba.expense
        val subtotal = expense.categories.single()
        assertEquals(category, subtotal.category)
        assertEquals(child, subtotal.subcategories.single().subcategory)
        assertEquals(401L, subtotal.subcategories.single().amountMinor)
        assertEquals(202L, subtotal.withoutSubcategoryMinor)
        assertEquals(603L, subtotal.amountMinor)
        assertEquals(706L, expense.amountMinor)
        assertEquals(expense.amountMinor, expense.groupTotals.values.sum())
        assertEquals(103L, expense.groupTotals.getValue(CategoryReportBucket.UNCATEGORIZED))
        assertEquals(-706L, report.cordoba.netCashFlowMinor)
        assertEquals(Long.MAX_VALUE, report.dollar.income.groupTotals.getValue(CategoryReportBucket.UNCATEGORIZED))
        assertEquals(Long.MAX_VALUE, report.dollar.netCashFlowMinor)
        assertEquals(ReportClassification.Assigned(category, child), report.cordoba.movements[0].classification)
        assertEquals(ReportClassification.Assigned(category, null), report.cordoba.movements[1].classification)
        assertEquals(ReportClassification.Uncategorized, report.cordoba.movements[2].classification)
    }

    @Test
    fun resolvesByUuidRatherThanNameOrLocalIdAndOrdersParentsAndChildrenPortably() {
        val first = expenseCategory(uuid = "00000000-0000-4000-8000-000000000001", order = 2).copy(id = 90)
        val second = expenseCategory(uuid = "00000000-0000-4000-8000-000000000002", order = 2).copy(id = 1)
        val before = expenseCategory(order = -1)
        val unused = expenseCategory(order = -100)
        val childA = ReportSubcategory("10000000-0000-4000-8000-000000000001", first.uuid, "Servicio", "bill", false, 5)
        val childB = childA.copy(uuid = "10000000-0000-4000-8000-000000000002")
        val childFirst = childA.copy(uuid = newCatalogUuid(), sortOrder = 0)
        val entries = listOf(
            movement(6, second.uuid), movement(8, first.uuid, childB.uuid), movement(2, before.uuid),
            movement(3, first.uuid, childA.uuid), movement(1, first.uuid, childFirst.uuid),
        )
        val expense = calculate(entries, month, ReportCatalog(
            listOf(unused, second, first, before), listOf(childB, childA, childFirst),
        )).cordoba.expense
        assertEquals(listOf(before.uuid, first.uuid, second.uuid), expense.categories.map { it.category.uuid })
        assertEquals(listOf(2L, 12L, 6L), expense.categories.map { it.amountMinor })
        assertEquals(listOf(childFirst, childA, childB), expense.categories[1].subcategories.map { it.subcategory })
    }

    @Test
    fun aCategoryForBothTypesUsesTheMovementTypeAndItsStableExpenseGroup() {
        val category = Category(name = "Mantenimiento", scope = CategoryScope.BOTH, reportGroup = ReportGroup.FIXED_COST)
        val entries = listOf(movement(130, category.uuid), movement(300, category.uuid, type = MovementType.INCOME))
        val report = calculate(entries, month, ReportCatalog(listOf(category), emptyList())).cordoba

        assertEquals(mapOf(CategoryReportBucket.FIXED_COST to 130L), report.expense.groupTotals)
        assertEquals(mapOf(CategoryReportBucket.INCOME to 300L), report.income.groupTotals)
        assertEquals(170L, report.netCashFlowMinor)
    }

    @Test
    fun invalidReferencesStayVisibleAndRetainTheirAmountWithoutInventingAssignments() {
        val category = expenseCategory()
        val other = expenseCategory()
        val income = Category(name = "Salario", scope = CategoryScope.INCOME, reportGroup = ReportGroup.INCOME)
        val child = ReportSubcategory(newCatalogUuid(), other.uuid, "Ajena", "bill", false, 0)
        val invalid = listOf(
            movement(1, "invalido") to ReportReferenceIssue.INVALID_UUID,
            movement(2, child = child.uuid) to ReportReferenceIssue.SUBCATEGORY_WITHOUT_CATEGORY,
            movement(3, newCatalogUuid()) to ReportReferenceIssue.CATEGORY_UNAVAILABLE,
            movement(4, category.uuid, newCatalogUuid()) to ReportReferenceIssue.SUBCATEGORY_UNAVAILABLE,
            movement(5, category.uuid, child.uuid) to ReportReferenceIssue.WRONG_PARENT,
            movement(6, income.uuid) to ReportReferenceIssue.WRONG_SCOPE,
            movement(7, child.uuid) to ReportReferenceIssue.WRONG_KIND,
        )
        val report = calculate(invalid.map { it.first } + movement(93), month,
            ReportCatalog(listOf(category, other, income), listOf(child))).cordoba

        assertEquals(invalid.map { ReportClassification.Unresolved(it.second) },
            report.movements.dropLast(1).map { it.classification })
        assertEquals(28L, report.expense.unresolvedMinor)
        assertEquals(93L, report.expense.groupTotals.getValue(CategoryReportBucket.UNCATEGORIZED))
        assertEquals(121L, report.totalExpenseMinor)
        assertEquals(emptyList<CategoryTotal>(), report.expense.categories)
    }

    @Test
    fun emptyMonthRemainsZeroAndUnrepresentableOrDuplicatedTotalsFailExplicitly() {
        val entry = movement(Long.MAX_VALUE)
        val report = calculate(listOf(entry), month.plusMonths(1), emptyCatalog)
        for (currency in listOf(report.cordoba, report.dollar)) {
            assertEquals(0L, currency.totalIncomeMinor)
            assertEquals(0L, currency.totalExpenseMinor)
            assertEquals(0L, currency.netCashFlowMinor)
            assertEquals(emptyList<ReportMovementLine>(), currency.movements)
        }
        assertThrows(IllegalArgumentException::class.java) { calculate(listOf(entry, entry), month, emptyCatalog) }
        assertThrows(ArithmeticException::class.java) { calculate(listOf(entry, movement(1)), month, emptyCatalog) }
        val category = expenseCategory()
        assertThrows(IllegalArgumentException::class.java) {
            calculate(emptyList(), month, ReportCatalog(listOf(category, category), emptyList()))
        }
    }

    private fun expenseCategory(uuid: String = newCatalogUuid(), order: Int = 0) =
        Category(uuid = uuid, name = "Servicios", scope = CategoryScope.EXPENSE,
            reportGroup = ReportGroup.FIXED_COST, sortOrder = order)

    private fun movement(
        amount: Long,
        category: String? = null,
        child: String? = null,
        type: MovementType = MovementType.EXPENSE,
        currency: Currency = Currency.CORDOBA,
    ) = ReportMovement(newCatalogUuid(), type, amount, currency, day, category, child, "Detalle")
}
