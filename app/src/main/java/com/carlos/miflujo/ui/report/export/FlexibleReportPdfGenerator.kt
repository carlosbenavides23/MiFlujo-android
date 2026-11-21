package com.carlos.miflujo.ui.report.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.carlos.miflujo.domain.report.FlexibleMonthlyReport
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** Prepared PDF route for the flexible report. The caller owns the output stream. */
class FlexibleReportPdfGenerator {
    fun generate(report: FlexibleMonthlyReport, output: OutputStream) {
        val rows = report.pdfRows()
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f }
        val heading = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = TextPaint(heading).apply { textSize = 18f }
        val right = PageWidth - Margin
        val amountWidth = ceil(rows.maxOf { row -> body.measureText(row.amount.orEmpty()) }).toInt()

        val layouts = rows.map { row ->
            val indent = row.indent * Indent
            val reserved = if (row.amount == null) 0 else amountWidth + Gap
            val width = right - Margin - indent - reserved
            require(width > 0) { "El importe no cabe en la página" }
            StaticLayout.Builder.obtain(row.label, 0, row.label.length, if (row.heading) heading else body, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        }
        val measured = layouts.map { layout ->
            List(layout.lineCount) { index ->
                val gap = if (index == layout.lineCount - 1) RowGap else 0
                layout.getLineTop(index + 1) - layout.getLineTop(index) + gap
            }
        }
        val pages = paginateFlexiblePdf(measured, BodyBottom - BodyTop, rows.indices.filter { rows[it].heading }.toSet())
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, fragments ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PageWidth, PageHeight, index + 1).create())
                try {
                    val canvas = page.canvas
                    canvas.drawText("MiFlujo - Reporte mensual", Margin.toFloat(), 42f, title)
                    canvas.drawText(report.month.format(MonthLabel), Margin.toFloat(), 62f, body)
                    for (fragment in fragments) {
                        val row = rows[fragment.rowIndex]
                        val source = layouts[fragment.rowIndex]
                        val layout = if (fragment.firstLine == 0 && fragment.endLine == source.lineCount) {
                            source
                        } else {
                            PdfFragmentLayout(source, fragment.firstLine, fragment.endLine)
                        }
                        val top = (BodyTop + fragment.y).toFloat()
                        val x = (Margin + row.indent * Indent).toFloat()
                        val save = canvas.save()
                        canvas.clipRect(x, top, x + layout.width, top + layout.height)
                        canvas.translate(x, top)
                        layout.draw(canvas)
                        canvas.restoreToCount(save)
                        if (fragment.showsAmount) row.amount?.let { amount ->
                            canvas.drawText(amount, right - body.measureText(amount), top + layout.getLineBaseline(0), body)
                        }
                    }
                    val footer = "Página ${index + 1} de ${pages.size}"
                    canvas.drawText(footer, right - body.measureText(footer), (PageHeight - 22).toFloat(), body)
                } finally {
                    document.finishPage(page)
                }
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    // Layout.draw can visit the line at the bottom edge of its clip. Expose only the
    // assigned lines so a neighboring page's text never reaches the PDF renderer.
    private class PdfFragmentLayout(
        private val source: StaticLayout,
        private val firstLine: Int,
        private val endLine: Int,
    ) : Layout(
        source.text.subSequence(source.getLineStart(firstLine), source.getLineVisibleEnd(endLine - 1)),
        source.paint, source.width, source.alignment, 1f, 0f,
    ) {
        private val start = source.getLineStart(firstLine)
        private val top = source.getLineTop(firstLine)

        override fun getLineCount() = endLine - firstLine
        override fun getLineTop(line: Int) = source.getLineTop(firstLine + line) - top
        override fun getLineStart(line: Int) =
            if (line == lineCount) text.length else source.getLineStart(firstLine + line) - start
        override fun getLineDescent(line: Int) = source.getLineDescent(firstLine + line)
        override fun getParagraphDirection(line: Int) = source.getParagraphDirection(firstLine + line)
        override fun getLineContainsTab(line: Int) = source.getLineContainsTab(firstLine + line)
        override fun getLineDirections(line: Int) = source.getLineDirections(firstLine + line)
        override fun getTopPadding() = 0
        override fun getBottomPadding() = 0
        override fun getEllipsisStart(line: Int) = 0
        override fun getEllipsisCount(line: Int) = 0
    }

    private companion object {
        const val PageWidth = 595
        const val PageHeight = 842
        const val Margin = 44
        const val BodyTop = 82
        const val BodyBottom = 798
        const val Gap = 14
        const val Indent = 12
        const val RowGap = 6
        val MonthLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.forLanguageTag("es-NI"))
    }
}
