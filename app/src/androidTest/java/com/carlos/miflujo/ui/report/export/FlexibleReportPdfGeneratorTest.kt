package com.carlos.miflujo.ui.report.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File
import java.nio.file.Files
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlexibleReportPdfGeneratorTest {
    @Test
    @SdkSuppress(minSdkVersion = 35)
    fun exportsSeveralPagesWithFullAmountsAndLongContent() {
        val month = YearMonth.of(2024, 2)
        val category = Category(name = "Servicios con nombre extenso para confirmar el ajuste de línea NOMBRECOMPLETO",
            scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER, archived = true)
        val child = ReportSubcategory(newCatalogUuid(), category.uuid, "Servicio domiciliario", "home", false, 0)
        val detailLines = listOf("INICIODETALLE") + (1..240).map { number ->
            "LINEA${number.toString().padStart(3, '0')} $detailText"
        } + "FINDETALLE"
        val movements = listOf(
            ReportMovement(newCatalogUuid(), MovementType.EXPENSE, Long.MAX_VALUE,
                Currency.CORDOBA, month.atEndOfMonth(), category.uuid, child.uuid, detailLines.joinToString("\n")),
            ReportMovement(newCatalogUuid(), MovementType.INCOME, 101, Currency.DOLLAR, month.atDay(1), null),
        )
        val report = CalculateFlexibleMonthlyReport()(movements, month, ReportCatalog(listOf(category), listOf(child)))
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File.createTempFile("flexible-report-", ".pdf", cacheDir)
        try {
            file.outputStream().use { FlexibleReportPdfGenerator().generate(report, it) }

            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdf = try {
                PdfRenderer(descriptor)
            } catch (exception: Exception) {
                descriptor.close()
                throw exception
            }
            pdf.use {
                Log.i(tag, "PDF generado con ${pdf.pageCount} páginas")
                assertTrue("Se esperaban al menos tres páginas y se generaron ${pdf.pageCount}", pdf.pageCount >= 3)
                val pages = (0 until pdf.pageCount).map { index ->
                    pdf.openPage(index).use { page ->
                        val raster = verifyPageRaster(page)
                        logTextProbe(page)
                        raster
                    }
                }
                val densePages = pages.indices.filter { pages[it].leftTextBands >= 38 }
                assertTrue("El detalle debe ocupar varias páginas con renglones visibles",
                    densePages.size >= 4 && densePages.zipWithNext().all { (a, b) -> b == a + 1 })
                val longAmountPages = pages.indices.filter { pages[it].longAmounts > 0 }
                assertEquals("Los seis importes largos deben caber en la primera página", 6, pages.first().longAmounts)
                assertEquals(listOf(0), longAmountPages)
                assertTrue("Los importes en dólares deben verse completos en la última página",
                    pages.last().shortAmounts >= 4)
            }
        } finally {
            try {
                if (InstrumentationRegistry.getArguments().getString("flexiblePdfInspect") == "true") {
                    logPdfForInspection(file)
                }
            } finally {
                Files.deleteIfExists(file.toPath())
            }
        }
    }

    // Set a debugger breakpoint on this line to copy the closed PDF before cleanup.
    private fun logPdfForInspection(file: File) {
        Log.i(tag, "PDF sintético para inspección ${file.absolutePath}")
    }

    private fun logTextProbe(page: PdfRenderer.Page) {
        try {
            val blocks = page.textContents.map { it.text }
            val markers = PdfTextComparison(blocks).markers
            Log.i(tag, "Pág. ${page.index + 1}, ${blocks.sumOf { it.length }} caracteres, " +
                "${markers.size} marcas por getTextContents")
        } catch (exception: Exception) {
            Log.i(tag, "getTextContents falló en pág. ${page.index + 1} (${exception.javaClass.simpleName})")
        }
    }

    private data class PageRaster(val leftTextBands: Int, val longAmounts: Int, val shortAmounts: Int)

    private fun verifyPageRaster(page: PdfRenderer.Page): PageRaster {
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val pixels = IntArray(page.width * page.height)
            bitmap.getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
            val leftRows = BooleanArray(page.height)
            val amountLeft = IntArray(page.height) { page.width }
            val amountRight = IntArray(page.height) { -1 }
            for ((index, pixel) in pixels.withIndex()) {
                if (!isInk(pixel)) continue
                val x = index % page.width
                val y = index / page.width
                if (x < 42 || x > page.width - 42 || y < 18 || y > page.height - 16 ||
                    y in 67..79 || y in 801..807
                ) {
                    throw AssertionError("Texto fuera del margen en página ${page.index + 1}, x=$x, y=$y")
                }
                if (y !in 82..798) continue
                if (x in 42..95) leftRows[y] = true
                if (x >= 390) {
                    amountLeft[y] = minOf(amountLeft[y], x)
                    amountRight[y] = maxOf(amountRight[y], x)
                }
            }
            val leftBands = inkBands { leftRows[it] }.count { it.count() >= 5 }
            assertTrue("La página ${page.index + 1} debe tener contenido en el cuerpo", leftBands > 0)
            val amountWidths = inkBands { amountRight[it] >= 0 }.mapNotNull { band ->
                val right = band.maxOf { amountRight[it] }
                if (band.count() < 5 || right !in 545..552) return@mapNotNull null
                // The last two digits share a baseline. The decimal dot has ink only near it.
                val decimalDot = (right - 15..right - 11).any { x ->
                    (band.last - 1..band.last).any { y -> isInk(pixels[y * page.width + x]) } &&
                        (band.first until band.last - 1).none { y -> isInk(pixels[y * page.width + x]) }
                }
                (right - band.minOf { amountLeft[it] } + 1) to decimalDot
            }
            return PageRaster(
                leftBands,
                amountWidths.count { (width, dot) -> width in 145..165 && dot },
                amountWidths.count { (width) -> width in 36..55 },
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun inkBands(hasInk: (Int) -> Boolean): List<IntRange> = buildList {
        var start = 82
        for (line in 82..799) {
            if (line < 799 && hasInk(line)) continue
            if (line > start) add(start until line)
            start = line + 1
        }
    }

    private fun isInk(pixel: Int) = Color.alpha(pixel) != 0 && Color.red(pixel) < 128

    private companion object {
        const val tag = "FlexibleReportPdfTest"
        const val detailText = "Servicio con descripción detallada"
    }
}
