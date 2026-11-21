package com.carlos.miflujo.ui.report.export

internal data class PdfRowFragment(val rowIndex: Int, val firstLine: Int, val endLine: Int, val y: Int) {
    val showsAmount get() = firstLine == 0
}

/** Whole text lines can move to the next page, even when one row exceeds a page. */
internal fun paginateFlexiblePdf(
    lineHeights: List<List<Int>>,
    availableHeight: Int,
    headings: Set<Int> = emptySet(),
): List<List<PdfRowFragment>> = buildList {
    require(availableHeight > 0)
    var page = mutableListOf<PdfRowFragment>()
    var y = 0
    fun nextPage() {
        if (page.isNotEmpty()) add(page)
        page = mutableListOf()
        y = 0
    }
    for ((rowIndex, lines) in lineHeights.withIndex()) {
        require(lines.isNotEmpty() && lines.all { it in 1..availableHeight })
        val nextFirstLine = lineHeights.getOrNull(rowIndex + 1)?.firstOrNull() ?: 0
        if (rowIndex in headings && lines.sum() + nextFirstLine in 1..availableHeight &&
            y + lines.sum() + nextFirstLine > availableHeight
        ) nextPage()

        var first = 0
        while (first < lines.size) {
            if (y + lines[first] > availableHeight) nextPage()
            val top = y
            var end = first
            while (end < lines.size && y + lines[end] <= availableHeight) {
                y += lines[end]
                end++
            }
            page.add(PdfRowFragment(rowIndex, first, end, top))
            first = end
        }
    }
    nextPage()
}
