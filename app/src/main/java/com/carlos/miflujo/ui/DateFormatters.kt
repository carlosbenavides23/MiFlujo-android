package com.carlos.miflujo.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val visibleDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

internal fun LocalDate.formatVisibleDate(): String {
    return format(visibleDateFormatter)
}
