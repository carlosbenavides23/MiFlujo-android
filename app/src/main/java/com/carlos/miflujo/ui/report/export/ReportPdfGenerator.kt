package com.carlos.miflujo.ui.report.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.CurrencySummary
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.ui.formatSignedVisibleMoney
import com.carlos.miflujo.ui.formatVisibleDate
import com.carlos.miflujo.ui.formatVisibleMoney
import com.carlos.miflujo.ui.report.ReportUiState
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import kotlin.math.max

class ReportPdfGenerator {
    private val document = PdfDocument()
    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = TopMargin

    fun generate(
        uiState: ReportUiState,
        outputFile: File,
    ) {
        outputFile.parentFile?.mkdirs()
        try {
            startPage()
            drawHeader(uiState.selectedMonth)
            drawSummarySection(uiState)
            drawExpenseDetailSection(uiState)
            drawActivitySection(uiState.movements)
            drawMovementsSection(uiState.movements)
            finishPage()

            FileOutputStream(outputFile).use { outputStream ->
                document.writeTo(outputStream)
            }
        } finally {
            document.close()
        }
    }

    private fun drawHeader(selectedMonth: YearMonth) {
        canvas.drawText("MiFlujo - Reporte mensual", LeftMargin, y, titlePaint)
        y += 18f
        canvas.drawText(selectedMonth.toSpanishMonthLabel(), LeftMargin, y, monthPaint)
        y += 14f
        canvas.drawLine(LeftMargin, y, PageWidth - RightMargin, y, borderPaint)
        y += 8f
    }

    private fun drawSummarySection(uiState: ReportUiState) {
        drawSectionTitle("1. Resumen por moneda")
        drawTable(
            headers = listOf("Moneda", "Ingresos", "Egresos", "Flujo neto"),
            rows = listOf(
                uiState.report.cordoba.summaryRow("C$"),
                uiState.report.dollar.summaryRow("US$"),
            ),
            weights = floatArrayOf(1f, 1.45f, 1.45f, 1.45f),
        )
    }

    private fun drawExpenseDetailSection(uiState: ReportUiState) {
        drawSectionTitle("2. Detalle de egresos")
        drawTable(
            headers = listOf("Moneda", "Costos fijos", "Mantenimiento", "Otros"),
            rows = listOf(
                uiState.report.cordoba.expenseRow("C$"),
                uiState.report.dollar.expenseRow("US$"),
            ),
            weights = floatArrayOf(1f, 1.45f, 1.45f, 1.45f),
        )
    }

    private fun drawActivitySection(movements: List<Movement>) {
        drawSectionTitle("3. Actividad del mes")
        drawTable(
            headers = listOf("Total movimientos", "Ingresos", "Egresos"),
            rows = listOf(
                listOf(
                    PdfCell(movements.size.toString(), TextAlignment.Right),
                    PdfCell(movements.count { it.type == MovementType.INCOME }.toString(), TextAlignment.Right),
                    PdfCell(movements.count { it.type == MovementType.EXPENSE }.toString(), TextAlignment.Right),
                ),
            ),
            weights = floatArrayOf(1f, 1f, 1f),
        )
    }

    private fun drawMovementsSection(movements: List<Movement>) {
        drawSectionTitle("4. Movimientos del mes")
        val headers = listOf("Fecha", "Tipo", "Categoría", "Detalle", "Moneda", "Monto")
        val widths = columnWidths(floatArrayOf(12f, 11f, 19f, 25f, 10f, 23f))
        drawTableHeader(headers, widths)

        if (movements.isEmpty()) {
            val row = listOf(
                PdfCell(
                    text = "Sin movimientos registrados para este mes.",
                    alignment = TextAlignment.Left,
                    colspan = headers.size,
                ),
            )
            drawRows(
                rows = listOf(row),
                widths = widths,
                continuationTitle = "4. Movimientos del mes (continuación)",
                headers = headers,
            )
        } else {
            drawRows(
                rows = movements.map { it.movementRow() },
                widths = widths,
                continuationTitle = "4. Movimientos del mes (continuación)",
                headers = headers,
            )
        }
    }

    private fun drawSectionTitle(title: String) {
        ensureSpace(SectionTopGap + SectionTitleHeight + GapAfterSectionTitle)
        y += SectionTopGap
        canvas.drawText(title, LeftMargin, y, sectionPaint)
        y += GapAfterSectionTitle
    }

    private fun drawTable(
        headers: List<String>,
        rows: List<List<PdfCell>>,
        weights: FloatArray,
    ) {
        val widths = columnWidths(weights)
        drawTableHeader(headers, widths)
        drawRows(
            rows = rows,
            widths = widths,
        )
    }

    private fun drawRows(
        rows: List<List<PdfCell>>,
        widths: FloatArray,
        continuationTitle: String? = null,
        headers: List<String>? = null,
    ) {
        rows.forEach { row ->
            val rowHeight = measureRowHeight(row, widths)
            if (y + rowHeight > contentBottom()) {
                finishPage()
                startPage()
                if (continuationTitle != null && headers != null) {
                    drawSectionTitle(continuationTitle)
                    drawTableHeader(headers, widths)
                }
            }
            drawTableRow(row, widths, rowHeight)
        }
        y += TableBottomGap
    }

    private fun drawTableHeader(
        headers: List<String>,
        widths: FloatArray,
    ) {
        ensureSpace(HeaderRowHeight + BodyRowMinHeight)
        var x = LeftMargin
        headers.forEachIndexed { index, header ->
            drawCell(
                rect = RectF(x, y, x + widths[index], y + HeaderRowHeight),
                cell = PdfCell(header, TextAlignment.Left),
                paint = tableHeaderPaint,
                backgroundColor = HeaderBackgroundColor,
                textColor = TextColor,
            )
            x += widths[index]
        }
        y += HeaderRowHeight
    }

    private fun drawTableRow(
        row: List<PdfCell>,
        widths: FloatArray,
        rowHeight: Float,
    ) {
        var x = LeftMargin
        var columnIndex = 0
        row.forEach { cell ->
            val width = (0 until cell.colspan).sumOf { offset ->
                widths.getOrNull(columnIndex + offset)?.toDouble() ?: 0.0
            }.toFloat()
            drawCell(
                rect = RectF(x, y, x + width, y + rowHeight),
                cell = cell,
                paint = tableBodyPaint,
                backgroundColor = Color.WHITE,
                textColor = cell.textColor,
            )
            x += width
            columnIndex += cell.colspan
        }
        y += rowHeight
    }

    private fun drawCell(
        rect: RectF,
        cell: PdfCell,
        paint: Paint,
        backgroundColor: Int,
        textColor: Int,
    ) {
        fillPaint.color = backgroundColor
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, borderPaint)

        val lines = wrapText(
            text = cell.text,
            paint = paint,
            maxWidth = rect.width() - CellHorizontalPadding * 2,
        )
        paint.color = textColor
        drawWrappedText(
            lines = lines,
            rect = rect,
            alignment = cell.alignment,
            paint = paint,
        )
    }

    private fun drawWrappedText(
        lines: List<String>,
        rect: RectF,
        alignment: TextAlignment,
        paint: Paint,
    ) {
        val lineHeight = paint.lineHeight()
        var baseline = rect.top + CellVerticalPadding - paint.fontMetrics.ascent
        lines.forEach { line ->
            val x = when (alignment) {
                TextAlignment.Left -> rect.left + CellHorizontalPadding
                TextAlignment.Right -> rect.right - CellHorizontalPadding - paint.measureText(line)
                TextAlignment.Center -> rect.left + (rect.width() - paint.measureText(line)) / 2f
            }
            canvas.drawText(line, x, baseline, paint)
            baseline += lineHeight
        }
    }

    private fun measureRowHeight(
        row: List<PdfCell>,
        widths: FloatArray,
    ): Float {
        var columnIndex = 0
        var maxHeight = BodyRowMinHeight
        row.forEach { cell ->
            val width = (0 until cell.colspan).sumOf { offset ->
                widths.getOrNull(columnIndex + offset)?.toDouble() ?: 0.0
            }.toFloat()
            val textHeight = measureWrappedTextHeight(
                text = cell.text,
                maxWidth = width - CellHorizontalPadding * 2,
                paint = tableBodyPaint,
            )
            maxHeight = max(maxHeight, textHeight + CellVerticalPadding * 2)
            columnIndex += cell.colspan
        }
        return maxHeight
    }

    private fun measureWrappedTextHeight(
        text: String,
        maxWidth: Float,
        paint: Paint,
    ): Float {
        return wrapText(text, paint, maxWidth).size * paint.lineHeight()
    }

    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Float,
    ): List<String> {
        if (text.isBlank()) return listOf("")

        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var currentLine = ""
            paragraph.trim().split(WhitespaceRegex).filter { it.isNotBlank() }.forEach { word ->
                if (paint.measureText(word) > maxWidth) {
                    if (currentLine.isNotBlank()) {
                        lines.add(currentLine)
                        currentLine = ""
                    }
                    lines.addAll(breakLongWord(word, paint, maxWidth))
                } else {
                    val candidate = if (currentLine.isBlank()) word else "$currentLine $word"
                    if (paint.measureText(candidate) <= maxWidth) {
                        currentLine = candidate
                    } else {
                        lines.add(currentLine)
                        currentLine = word
                    }
                }
            }
            if (currentLine.isNotBlank()) {
                lines.add(currentLine)
            }
        }

        return lines.ifEmpty { listOf("") }
    }

    private fun breakLongWord(
        word: String,
        paint: Paint,
        maxWidth: Float,
    ): List<String> {
        val parts = mutableListOf<String>()
        var remaining = word
        while (remaining.isNotEmpty()) {
            var count = remaining.length
            while (count > 1 && paint.measureText(remaining.take(count)) > maxWidth) {
                count -= 1
            }
            parts.add(remaining.take(count))
            remaining = remaining.drop(count)
        }
        return parts
    }

    private fun ensureSpace(requiredHeight: Float) {
        if (y + requiredHeight <= contentBottom()) return
        finishPage()
        startPage()
    }

    private fun startPage() {
        pageNumber += 1
        page = document.startPage(
            PdfDocument.PageInfo.Builder(PageWidth.toInt(), PageHeight.toInt(), pageNumber).create(),
        )
        canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        y = TopMargin
    }

    private fun finishPage() {
        drawFooter()
        document.finishPage(page)
    }

    private fun drawFooter() {
        val text = "Página $pageNumber"
        canvas.drawText(
            text,
            PageWidth - RightMargin - footerPaint.measureText(text),
            PageHeight - 18f,
            footerPaint,
        )
    }

    private fun columnWidths(weights: FloatArray): FloatArray {
        val totalWeight = weights.sum()
        return weights.map { weight ->
            contentWidth() * (weight / totalWeight)
        }.toFloatArray()
    }

    private fun contentWidth(): Float = PageWidth - LeftMargin - RightMargin

    private fun contentBottom(): Float = PageHeight - BottomMargin

    private fun Paint.lineHeight(): Float {
        val metrics = fontMetrics
        return metrics.descent - metrics.ascent
    }

    private fun CurrencySummary.summaryRow(currencySymbol: String): List<PdfCell> {
        return listOf(
            PdfCell(currencySymbol, TextAlignment.Left),
            PdfCell("+ ${totalIncomeMinor.formatVisibleMoney(currencySymbol)}", TextAlignment.Right, IncomeColor),
            PdfCell("- ${totalExpenseMinor.formatVisibleMoney(currencySymbol)}", TextAlignment.Right, ExpenseColor),
            PdfCell(
                text = netCashFlowMinor.formatSignedVisibleMoney(currencySymbol),
                alignment = TextAlignment.Right,
                textColor = netCashFlowMinor.moneyColor(),
            ),
        )
    }

    private fun CurrencySummary.expenseRow(currencySymbol: String): List<PdfCell> {
        return listOf(
            PdfCell(currencySymbol, TextAlignment.Left),
            PdfCell(expenseBreakdown.fixedCostMinor.formatVisibleMoney(currencySymbol), TextAlignment.Right),
            PdfCell(expenseBreakdown.maintenanceMinor.formatVisibleMoney(currencySymbol), TextAlignment.Right),
            PdfCell(expenseBreakdown.otherMinor.formatVisibleMoney(currencySymbol), TextAlignment.Right),
        )
    }

    private fun Movement.movementRow(): List<PdfCell> {
        val signedAmount = signedAmountMinor()
        return listOf(
            PdfCell(date.formatVisibleDate(), TextAlignment.Left),
            PdfCell(type.label(), TextAlignment.Left),
            PdfCell(categoryLabel(), TextAlignment.Left),
            PdfCell(detail.orEmpty().ifBlank { "Sin detalle" }, TextAlignment.Left),
            PdfCell(currency.symbol(), TextAlignment.Left),
            PdfCell(
                text = signedAmount.formatSignedVisibleMoney(currency.symbol()),
                alignment = TextAlignment.Right,
                textColor = signedAmount.moneyColor(),
            ),
        )
    }

    private fun Long.moneyColor(): Int {
        return when {
            this > 0L -> IncomeColor
            this < 0L -> ExpenseColor
            else -> TextColor
        }
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

    private data class PdfCell(
        val text: String,
        val alignment: TextAlignment,
        val textColor: Int = TextColor,
        val colspan: Int = 1,
    )

    private enum class TextAlignment {
        Left,
        Center,
        Right,
    }
}

private const val PageWidth = 595f
private const val PageHeight = 842f
private const val LeftMargin = 48f
private const val RightMargin = 48f
private const val TopMargin = 44f
private const val BottomMargin = 44f
private const val SectionTopGap = 20f
private const val GapAfterSectionTitle = 8f
private const val SectionTitleHeight = 14f
private const val HeaderRowHeight = 26f
private const val BodyRowMinHeight = 24f
private const val CellHorizontalPadding = 4f
private const val CellVerticalPadding = 4f
private const val TableBottomGap = 4f

private val WhitespaceRegex = Regex("\\s+")
private val TextColor = Color.rgb(17, 24, 39)
private val SecondaryTextColor = Color.rgb(71, 85, 105)
private val BorderColor = Color.rgb(184, 195, 209)
private val HeaderBackgroundColor = Color.rgb(238, 243, 248)
private val IncomeColor = Color.rgb(21, 128, 61)
private val ExpenseColor = Color.rgb(185, 28, 28)

private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = TextColor
    textSize = 22f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = SecondaryTextColor
    textSize = 11f
}
private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = TextColor
    textSize = 12f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val tableHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = TextColor
    textSize = 9f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
}
private val tableBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = TextColor
    textSize = 9f
}
private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = SecondaryTextColor
    textSize = 9f
}
private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = BorderColor
    strokeWidth = 0.7f
    style = Paint.Style.STROKE
}
private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}
