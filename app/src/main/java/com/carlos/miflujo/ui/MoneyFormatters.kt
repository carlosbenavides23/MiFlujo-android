package com.carlos.miflujo.ui

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementType
import kotlin.math.abs

internal fun Movement.formatSignedMoney(): String {
    val sign = when (type) {
        MovementType.INCOME -> "+"
        MovementType.EXPENSE -> "-"
    }
    return "$sign ${currency.symbol()} ${amountMinor.formatMoneyMinor()}"
}

internal fun Long.formatMoneyMinor(): String {
    val absoluteAmount = abs(this)
    val whole = absoluteAmount / 100L
    val cents = absoluteAmount % 100L
    return "${whole.formatThousands()}.${cents.toString().padStart(2, '0')}"
}

internal fun Long.formatSignedMoney(currencySymbol: String): String {
    val sign = if (this < 0L) "-" else "+"
    return "$sign $currencySymbol ${formatMoneyMinor()}"
}

internal fun Currency.symbol(): String {
    return when (this) {
        Currency.CORDOBA -> "C$"
        Currency.DOLLAR -> "US$"
    }
}

private fun Long.formatThousands(): String {
    val digits = toString()
    return buildString {
        digits.forEachIndexed { index, digit ->
            if (index > 0 && (digits.length - index) % 3 == 0) {
                append(',')
            }
            append(digit)
        }
    }
}
