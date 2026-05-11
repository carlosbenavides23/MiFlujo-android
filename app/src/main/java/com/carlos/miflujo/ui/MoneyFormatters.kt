package com.carlos.miflujo.ui

internal fun Long.formatVisibleMoneyAmount(): String {
    val absoluteAmount = kotlin.math.abs(this)
    val whole = absoluteAmount / 100L
    val cents = absoluteAmount % 100L

    return "${whole.toString().withThousandsSeparators()}.${cents.toString().padStart(2, '0')}"
}

internal fun Long.formatVisibleMoney(currencySymbol: String): String {
    return "$currencySymbol ${formatVisibleMoneyAmount()}"
}

internal fun Long.formatSignedVisibleMoney(currencySymbol: String): String {
    val sign = if (this < 0L) "-" else "+"
    return "$sign ${formatVisibleMoney(currencySymbol)}"
}

private fun String.withThousandsSeparators(): String {
    return reversed()
        .chunked(3)
        .joinToString(separator = ",")
        .reversed()
}
