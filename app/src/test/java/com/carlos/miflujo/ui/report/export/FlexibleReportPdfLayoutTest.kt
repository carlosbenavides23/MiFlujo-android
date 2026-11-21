package com.carlos.miflujo.ui.report.export

import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.newCatalogUuid
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.report.CalculateFlexibleMonthlyReport
import com.carlos.miflujo.domain.report.ReportCatalog
import com.carlos.miflujo.domain.report.ReportMovement
import com.carlos.miflujo.domain.report.ReportSubcategory
import com.carlos.miflujo.ui.report.experimental.presentationRows
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexibleReportPdfLayoutTest {
    private val month = YearMonth.of(2024, 2)

    @Test
    fun dynamicRowsPreserveNamesArchiveLabelsStructureAndExactAmounts() {
        val name = "Servicios con un nombre personalizado ".repeat(3).trim()
        val category = Category(name = name, scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER,
            iconKey = "home", archived = true)
        val duplicateName = category.copy(uuid = newCatalogUuid(), archived = false, sortOrder = 1)
        val child = ReportSubcategory(newCatalogUuid(), category.uuid, "Mantenimiento preventivo", "maintenance", true, 0)
        val movements = listOf(
            movement(45, category.uuid, child.uuid), movement(20, category.uuid),
            movement(15, duplicateName.uuid), movement(10), movement(5, newCatalogUuid()),
            movement(Long.MAX_VALUE, currency = Currency.DOLLAR),
        )
        val report = CalculateFlexibleMonthlyReport()(movements, month,
            ReportCatalog(listOf(duplicateName, category), listOf(child)))

        val rows = report.cordoba.expense.presentationRows()
        assertEquals(listOf("OTHER", category.uuid, child.uuid, "${category.uuid}:direct",
            duplicateName.uuid, "UNCATEGORIZED", "unresolved"), rows.map { it.key })
        assertEquals(listOf(0, 1, 2, 2, 1, 0, 0), rows.map { it.indent })
        assertEquals("home", rows[1].iconKey)
        assertEquals(95L, rows.filter { it.indent == 0 }.sumOf { it.amountMinor })
        val pdf = report.pdfRows()
        assertTrue(pdf.any { it.label == "$name (archivada)" && it.amount == "C$ 0.65" })
        assertTrue(pdf.any { it.label == name && it.amount == "C$ 0.15" })
        assertTrue(pdf.any { it.label == "Mantenimiento preventivo (archivada)" && it.indent == 2 })
        assertTrue(pdf.any { it.label == "Sin categoría" && it.amount == "C$ 0.10" })
        assertTrue(pdf.any { it.label == "Referencias sin resolver" && it.amount == "C$ 0.05" })
        assertTrue(pdf.any { "Categoría no disponible" in it.label && it.amount == "- C$ 0.05" })
        assertTrue(pdf.any { it.label == "Flujo neto" && it.amount == "- US$ 92,233,720,368,547,758.07" })
    }

    @Test
    fun splitsAnOversizedRowOnLineBoundariesWithAmountOnlyOnItsFirstPage() {
        val heights = listOf(listOf(8), List(13) { 10 } + 16, listOf(12))
        val pages = paginateFlexiblePdf(heights, availableHeight = 40)
        assertTrue(pages.size > 2)
        val fragments = pages.flatten()
        assertEquals(heights.flatMapIndexed { index, lines -> lines.indices.map { index to it } },
            fragments.flatMap { fragment -> (fragment.firstLine until fragment.endLine).map { fragment.rowIndex to it } })
        assertEquals(1, fragments.count { it.rowIndex == 1 && it.showsAmount })
        for (page in pages) {
            assertTrue(page.isNotEmpty())
            var nextY = 0
            for (fragment in page) {
                assertEquals(nextY, fragment.y)
                nextY += heights[fragment.rowIndex].subList(fragment.firstLine, fragment.endLine).sum()
                assertTrue(nextY <= 40)
            }
        }
    }

    @Test
    fun keepsHeadingWithFirstFollowingLineAndDoesNotAddEmptyLastPage() {
        val pages = paginateFlexiblePdf(listOf(listOf(25), listOf(10), listOf(11, 19)), 40, setOf(1))
        assertEquals(listOf(listOf(0), listOf(1, 2)), pages.map { page -> page.map { it.rowIndex } })
        assertEquals(listOf(0, 10), pages.last().map { it.y })
        assertTrue(paginateFlexiblePdf(emptyList(), 40).isEmpty())
    }

    @Test
    fun placesExactFitOnCurrentPageWithoutTakingTheFirstLineOfTheNextPage() {
        val pages = paginateFlexiblePdf(listOf(listOf(6), listOf(4, 10)), availableHeight = 10)
        assertEquals(listOf(
            listOf(PdfRowFragment(0, 0, 1, 0), PdfRowFragment(1, 0, 1, 6)),
            listOf(PdfRowFragment(1, 1, 2, 0)),
        ), pages)
    }

    @Test
    fun finishesVeryLongRowAndPartitionsEveryLineExactlyOnce() {
        val lineCount = 20_000
        val pages = paginateFlexiblePdf(listOf(List(lineCount) { 2 }), availableHeight = 101)
        assertEquals(400, pages.size)
        assertEquals((0 until lineCount).toList(), pages.flatMap { page ->
            val fragment = page.single()
            assertEquals(0, fragment.y)
            (fragment.firstLine until fragment.endLine).toList()
        })
        assertEquals(1, pages.count { it.single().showsAmount })
    }

    @Test
    fun passesTheEntireDetailAndAmountToThePdfMovementRow() {
        val detail = (1..240).joinToString("\n") { "Detalle $it" }
        val movement = movement(Long.MAX_VALUE).copy(detail = detail)
        val report = CalculateFlexibleMonthlyReport()(listOf(movement), month, ReportCatalog(emptyList(), emptyList()))
        val row = report.pdfRows().single { it.label.startsWith("${movement.date} · Egreso\n") }
        assertEquals("${movement.date} · Egreso\nSin categoría\n$detail", row.label)
        assertEquals("- C$ 92,233,720,368,547,758.07", row.amount)
    }

    @Test
    fun emptyReportStillHasBothSummariesAndNoCategoryRows() {
        val empty = CalculateFlexibleMonthlyReport()(emptyList(), month, ReportCatalog(emptyList(), emptyList()))
        val rows = empty.pdfRows()
        assertEquals(listOf("C$ 0.00", "US$ 0.00"), rows.filter { it.label == "Egresos" }.map { it.amount })
        assertEquals(6, rows.count { it.label == "Sin movimientos" })
        assertTrue(rows.none { it.indent > 0 || it.label == "Sin categoría" })
    }

    private fun movement(amount: Long, category: String? = null, child: String? = null, currency: Currency = Currency.CORDOBA) =
        ReportMovement(newCatalogUuid(), MovementType.EXPENSE, amount, currency, month.atDay(1), category, child)
}
