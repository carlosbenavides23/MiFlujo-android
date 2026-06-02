package com.carlos.miflujo.ui.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.CurrencySummary
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.ui.formatSignedVisibleMoney
import com.carlos.miflujo.ui.formatVisibleDate
import com.carlos.miflujo.ui.formatVisibleMoney
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.YearMonth

fun shareMonthlyReportPdf(
    context: Context,
    uiState: ReportUiState,
) {
    try {
        val pdfFile = createMonthlyReportPdf(
            context = context,
            uiState = uiState,
        )
        val pdfUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, "MiFlujo - Reporte mensual")
            clipData = ClipData.newUri(
                context.contentResolver,
                pdfFile.name,
                pdfUri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Compartir reporte").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooser)
    } catch (_: IOException) {
        Toast.makeText(
            context,
            "No se pudo generar el reporte.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun createMonthlyReportPdf(
    context: Context,
    uiState: ReportUiState,
): File {
    val outputDirectory = File(context.cacheDir, SHARED_REPORTS_DIRECTORY).apply {
        mkdirs()
    }
    val pdfFile = File(outputDirectory, uiState.selectedMonth.pdfFileName())
    val pdfDocument = PdfDocument()

    try {
        MonthlyReportPdfWriter(pdfDocument).drawReport(uiState)
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
    } finally {
        pdfDocument.close()
    }

    return pdfFile
}

private class MonthlyReportPdfWriter(
    private val pdfDocument: PdfDocument,
) {
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 34, 45)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 94, 108)
        textSize = 9f
    }
    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 34, 45)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(36, 45, 56)
        textSize = 8.2f
    }
    private val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(25, 34, 45)
        textSize = 8.2f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(184, 193, 204)
        strokeWidth = 0.8f
        style = Paint.Style.STROKE
    }
    private val headerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(232, 238, 246)
        style = Paint.Style.FILL
    }
    private val rowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(250, 252, 255)
        style = Paint.Style.FILL
    }
    private val whiteFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = PageMargin

    fun drawReport(uiState: ReportUiState) {
        startPage()
        drawTitle(uiState.selectedMonth)
        drawSummary(uiState)
        drawExpenseDetail(uiState)
        drawMonthlyActivity(uiState.movements)
        drawMovementsTable(uiState.movements)
        finishPage()
    }

    private fun drawTitle(selectedMonth: YearMonth) {
        canvas.drawText("MiFlujo - Reporte mensual", PageMargin, y, titlePaint)
        y += 18f
        canvas.drawText(selectedMonth.toSpanishMonthLabel(), PageMargin, y, monthPaint)
        y += 14f
    }

    private fun drawSummary(uiState: ReportUiState) {
        drawSectionTitle("Resumen por moneda")
        drawTable(
            headers = listOf("Moneda", "Ingresos", "Egresos", "Flujo neto"),
            rows = listOf(
                uiState.report.cordoba.summaryRow("C$"),
                uiState.report.dollar.summaryRow("US$"),
            ),
            weights = floatArrayOf(1f, 1.4f, 1.4f, 1.4f),
            rightAlignedColumns = setOf(1, 2, 3),
        )
    }

    private fun drawExpenseDetail(uiState: ReportUiState) {
        drawSectionTitle("Detalle de egresos")
        drawTable(
            headers = listOf("Moneda", "Costos fijos", "Mantenimiento", "Otros"),
            rows = listOf(
                uiState.report.cordoba.expenseRow("C$"),
                uiState.report.dollar.expenseRow("US$"),
            ),
            weights = floatArrayOf(1f, 1.4f, 1.4f, 1.4f),
            rightAlignedColumns = setOf(1, 2, 3),
        )
    }

    private fun drawMonthlyActivity(movements: List<Movement>) {
        drawSectionTitle("Actividad del mes")
        val incomeCount = movements.count { it.type == MovementType.INCOME }
        val expenseCount = movements.count { it.type == MovementType.EXPENSE }
        drawTable(
            headers = listOf("Total movimientos", "Ingresos", "Egresos"),
            rows = listOf(
                listOf(
                    movements.size.toString(),
                    incomeCount.toString(),
                    expenseCount.toString(),
                ),
            ),
            weights = floatArrayOf(1f, 1f, 1f),
            rightAlignedColumns = setOf(0, 1, 2),
        )
    }

    private fun drawMovementsTable(movements: List<Movement>) {
        drawSectionTitle("Movimientos del mes")
        val rows = if (movements.isEmpty()) {
            listOf(listOf("-", "-", "-", "Sin movimientos registrados.", "-", "-"))
        } else {
            movements.map { it.tableRow() }
        }

        drawTable(
            headers = listOf("Fecha", "Tipo", "Categoría", "Detalle", "Moneda", "Monto"),
            rows = rows,
            weights = floatArrayOf(0.78f, 0.78f, 1.45f, 3.15f, 0.72f, 1.48f),
            rightAlignedColumns = setOf(5),
        )
    }

    private fun drawSectionTitle(text: String) {
        y += SectionTopSpacing
        ensureSpace(SectionTitleHeight + RowHeight)
        canvas.drawText(text, PageMargin, y, sectionPaint)
        y += SectionTitleHeight
    }

    private fun drawTable(
        headers: List<String>,
        rows: List<List<String>>,
        weights: FloatArray,
        rightAlignedColumns: Set<Int> = emptySet(),
    ) {
        val widths = columnWidths(weights)
        ensureSpace(RowHeight * 2)
        drawRow(
            values = headers,
            widths = widths,
            yTop = y,
            fillHeader = true,
            rowIndex = -1,
            rightAlignedColumns = emptySet(),
        )
        y += RowHeight

        rows.forEachIndexed { rowIndex, row ->
            ensureSpace(RowHeight * 2)
            drawRow(
                values = row,
                widths = widths,
                yTop = y,
                fillHeader = false,
                rowIndex = rowIndex,
                rightAlignedColumns = rightAlignedColumns,
            )
            y += RowHeight
        }
        y += TableBottomSpacing
    }

    private fun drawRow(
        values: List<String>,
        widths: FloatArray,
        yTop: Float,
        fillHeader: Boolean,
        rowIndex: Int,
        rightAlignedColumns: Set<Int>,
    ) {
        var x = PageMargin
        val paint = if (fillHeader) tableHeaderPaint else bodyPaint
        val rowPaint = when {
            fillHeader -> headerFillPaint
            rowIndex % 2 == 0 -> rowFillPaint
            else -> whiteFillPaint
        }

        canvas.drawRect(
            PageMargin,
            yTop,
            PageWidth - PageMargin,
            yTop + RowHeight,
            rowPaint,
        )

        widths.forEachIndexed { index, width ->
            canvas.drawRect(x, yTop, x + width, yTop + RowHeight, gridPaint)

            val rawText = values.getOrNull(index).orEmpty()
            val text = rawText.ellipsizeToWidth(paint, width - CellPadding * 2)
            val textY = yTop + RowTextBaseline
            val textX = if (index in rightAlignedColumns) {
                x + width - CellPadding - paint.measureText(text)
            } else {
                x + CellPadding
            }
            canvas.drawText(text, textX, textY, paint)
            x += width
        }
    }

    private fun columnWidths(weights: FloatArray): FloatArray {
        val totalWeight = weights.sum()
        return weights.map { weight ->
            (PageWidth - PageMargin * 2) * (weight / totalWeight)
        }.toFloatArray()
    }

    private fun ensureSpace(requiredHeight: Float) {
        if (y + requiredHeight <= PageHeight - PageMargin) return

        finishPage()
        startPage()
    }

    private fun startPage() {
        pageNumber += 1
        page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(PageWidth.toInt(), PageHeight.toInt(), pageNumber).create(),
        )
        canvas = page.canvas
        y = PageMargin
    }

    private fun finishPage() {
        canvas.drawText("Página $pageNumber", PageWidth - PageMargin - 48f, PageHeight - 18f, bodyPaint)
        pdfDocument.finishPage(page)
    }
}

private fun CurrencySummary.summaryRow(currencySymbol: String): List<String> {
    return listOf(
        currencySymbol,
        totalIncomeMinor.formatVisibleMoney(currencySymbol),
        totalExpenseMinor.formatVisibleMoney(currencySymbol),
        netCashFlowMinor.formatSignedVisibleMoney(currencySymbol),
    )
}

private fun CurrencySummary.expenseRow(currencySymbol: String): List<String> {
    return listOf(
        currencySymbol,
        expenseBreakdown.fixedCostMinor.formatVisibleMoney(currencySymbol),
        expenseBreakdown.maintenanceMinor.formatVisibleMoney(currencySymbol),
        expenseBreakdown.otherMinor.formatVisibleMoney(currencySymbol),
    )
}

private fun Movement.tableRow(): List<String> {
    return listOf(
        date.formatVisibleDate(),
        type.label(),
        categoryLabel(),
        detail.orEmpty().ifBlank { "Sin detalle" },
        currency.symbol(),
        signedAmountMinor().formatSignedVisibleMoney(currency.symbol()),
    )
}

private fun Movement.signedAmountMinor(): Long {
    return when (type) {
        MovementType.INCOME -> amountMinor
        MovementType.EXPENSE -> -amountMinor
    }
}

private fun MovementType.label(): String {
    return when (this) {
        MovementType.INCOME -> "Ingreso"
        MovementType.EXPENSE -> "Egreso"
    }
}

private fun Movement.categoryLabel(): String {
    return when (category) {
        MovementCategory.GENERAL_INCOME -> "Ingreso"
        MovementCategory.FIXED_COST -> "Costo fijo${subcategory?.let { " / ${it.label()}" }.orEmpty()}"
        MovementCategory.MAINTENANCE -> "Mantenimiento"
        MovementCategory.OTHER -> "Otros"
    }
}

private fun MovementSubcategory.label(): String {
    return when (this) {
        MovementSubcategory.WATER -> "Agua"
        MovementSubcategory.ELECTRICITY -> "Luz"
        MovementSubcategory.INTERNET -> "Internet"
    }
}

private fun Currency.symbol(): String {
    return when (this) {
        Currency.CORDOBA -> "C$"
        Currency.DOLLAR -> "US$"
    }
}

private fun YearMonth.pdfFileName(): String {
    return "MiFlujo-reporte-$year-${monthValue.toString().padStart(2, '0')}.pdf"
}

private fun YearMonth.toSpanishMonthLabel(): String {
    val month = when (monthValue) {
        1 -> "Enero"
        2 -> "Febrero"
        3 -> "Marzo"
        4 -> "Abril"
        5 -> "Mayo"
        6 -> "Junio"
        7 -> "Julio"
        8 -> "Agosto"
        9 -> "Septiembre"
        10 -> "Octubre"
        11 -> "Noviembre"
        else -> "Diciembre"
    }
    return "$month $year"
}

private fun String.ellipsizeToWidth(
    paint: Paint,
    maxWidth: Float,
): String {
    if (paint.measureText(this) <= maxWidth) return this

    val ellipsis = "..."
    var text = this
    while (text.isNotEmpty() && paint.measureText(text + ellipsis) > maxWidth) {
        text = text.dropLast(1)
    }
    return if (text.isEmpty()) ellipsis else text + ellipsis
}

private const val SHARED_REPORTS_DIRECTORY = "shared_reports"
private const val PageWidth = 842f
private const val PageHeight = 595f
private const val PageMargin = 28f
private const val RowHeight = 17f
private const val RowTextBaseline = 11.5f
private const val CellPadding = 5f
private const val SectionTopSpacing = 7f
private const val SectionTitleHeight = 15f
private const val TableBottomSpacing = 5f
