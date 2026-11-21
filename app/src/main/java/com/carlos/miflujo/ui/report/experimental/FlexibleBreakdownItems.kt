package com.carlos.miflujo.ui.report.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.report.FlexibleBreakdown
import com.carlos.miflujo.ui.formatVisibleMoney

/** Ready for the existing report list once the catalog read model is connected. */
internal fun LazyListScope.flexibleBreakdownItems(
    sectionKey: String,
    breakdown: FlexibleBreakdown,
    currency: Currency,
) {
    val rows = breakdown.presentationRows()
    if (rows.isEmpty()) {
        item(key = "$sectionKey:empty") { Text("Sin movimientos") }
        return
    }
    items(rows, key = { "$sectionKey:${it.key}" }) { row ->
        val style = if (row.indent == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = (row.indent * 12).dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.visibleLabel, modifier = Modifier.weight(1f), style = style)
            Text(
                row.amountMinor.formatVisibleMoney(currency.reportSymbol()),
                modifier = Modifier.weight(0.85f),
                textAlign = TextAlign.End,
                style = style,
            )
        }
    }
}
