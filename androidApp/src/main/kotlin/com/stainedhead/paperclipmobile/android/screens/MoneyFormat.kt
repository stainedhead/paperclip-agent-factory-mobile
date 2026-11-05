package com.stainedhead.paperclipmobile.android.screens

import com.stainedhead.paperclipmobile.presentation.MoneyUi
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val DEFAULT_DIGITS = 2
private const val TEN = 10L

/**
 * The number of decimals a currency has (cents for USD, none for JPY); an unknown code reads as two so nothing is
 * silently rounded to whole units.
 */
fun fractionDigits(currency: String): Int =
    runCatching { Currency.getInstance(currency).defaultFractionDigits }
        .getOrNull()
        ?.takeIf { it >= 0 }
        ?: DEFAULT_DIGITS

/**
 * Money as the person reads it. The amounts are minor units in a currency; the decimals are the currency's own. A whole
 * amount drops its zero cents ("$150"), any other keeps them ("$150.50").
 */
fun formatMoney(
    minorUnits: Long,
    currency: String,
    locale: Locale = Locale.getDefault(),
): String {
    val digits = fractionDigits(currency)
    var scale = 1L
    repeat(digits) { scale *= TEN }
    val whole = minorUnits % scale == 0L
    val format = NumberFormat.getCurrencyInstance(locale)
    val known = runCatching { format.currency = Currency.getInstance(currency) }.isSuccess
    if (!known) return "$minorUnits $currency"
    format.minimumFractionDigits = if (whole) 0 else digits
    format.maximumFractionDigits = if (whole) 0 else digits
    return format.format(BigDecimal.valueOf(minorUnits, digits))
}

fun MoneyUi.text(locale: Locale = Locale.getDefault()): String = formatMoney(minorUnits, currency, locale)

/**
 * What shared code needs to read a typed amount: the locale's decimal separator and the currency's fraction digits.
 * Reading, validating and refusing the text is shared code's (`MoneyInput`); nothing is parsed here.
 */
data class MoneyInputFormat(
    val decimalSeparator: String,
    val fractionDigits: Int,
) {
    companion object {
        fun of(
            currency: String,
            locale: Locale = Locale.getDefault(),
        ) = MoneyInputFormat(
            DecimalFormatSymbols.getInstance(locale).decimalSeparator.toString(),
            fractionDigits(currency),
        )
    }
}
