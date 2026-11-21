package com.carlos.miflujo.ui.report.export

import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.report.FlexibleMonthlyReport
import com.carlos.miflujo.ui.formatSignedVisibleMoney
import com.carlos.miflujo.ui.formatVisibleMoney
import com.carlos.miflujo.ui.report.experimental.presentationRows
import com.carlos.miflujo.ui.report.experimental.reportSymbol
import com.carlos.miflujo.ui.report.experimental.visibleLabel

internal data class FlexiblePdfRow(
    val label: String,
    val amount: String? = null,
    val indent: Int = 0,
    val heading: Boolean = false,
)

internal fun FlexibleMonthlyReport.pdfRows(): List<FlexiblePdfRow> = buildList {
    for (summary in listOf(cordoba, dollar)) {
        val symbol = summary.currency.reportSymbol()
        add(FlexiblePdfRow("Resumen en $symbol", heading = true))
        add(FlexiblePdfRow("Ingresos", summary.totalIncomeMinor.formatVisibleMoney(symbol)))
        add(FlexiblePdfRow("Egresos", summary.totalExpenseMinor.formatVisibleMoney(symbol)))
        add(FlexiblePdfRow("Flujo neto", summary.netCashFlowMinor.formatSignedVisibleMoney(symbol)))
        for ((title, breakdown) in listOf("Detalle de ingresos" to summary.income, "Detalle de egresos" to summary.expense)) {
            add(FlexiblePdfRow("$title en $symbol", heading = true))
            val rows = breakdown.presentationRows()
            if (rows.isEmpty()) add(FlexiblePdfRow("Sin movimientos"))
            rows.forEach { row ->
                add(FlexiblePdfRow(row.visibleLabel, row.amountMinor.formatVisibleMoney(symbol), row.indent))
            }
        }
        add(FlexiblePdfRow("Movimientos en $symbol", heading = true))
        if (summary.movements.isEmpty()) add(FlexiblePdfRow("Sin movimientos"))
        summary.movements.forEach { (movement, classification) ->
            val income = movement.type == MovementType.INCOME
            val label = buildString {
                append(movement.date)
                append(if (income) " · Ingreso\n" else " · Egreso\n")
                append(classification.visibleLabel())
                movement.detail?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
            }
            val signed = if (income) movement.amountMinor else -movement.amountMinor
            add(FlexiblePdfRow(label, signed.formatSignedVisibleMoney(symbol)))
        }
    }
}
