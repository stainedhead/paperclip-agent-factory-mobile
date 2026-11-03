package com.stainedhead.paperclipmobile.presentation

/** What typed money text came to: minor units, or why it is not an amount. Both are null while the field is empty. */
data class ParsedMoney(
    val minorUnits: Long?,
    val issue: InvalidKey?,
)

private const val GROUP_SIZE = 3
private const val MAX_WHOLE_DIGITS = 12
private const val RADIX = 10L
private const val ARABIC_GROUP = '\u066C'

/**
 * Strict, locale-independent reading of an amount a person typed. The app passes only the locale's decimal separator
 * and the currency's fraction digits; the other of "." and "," is the grouping mark. Nothing is guessed: a mark in the
 * wrong place, whitespace or a currency sign inside the number, a second decimal mark or too many decimals is refused,
 * never dropped (a dropped mark is a 100x error). "1,500" is 1500.00 where the decimal mark is a point (the comma
 * groups) and refused where it is a comma; the Raise button shows the parsed amount before anything is confirmed.
 */
object MoneyInput {
    fun parse(
        text: String,
        decimalSeparator: String,
        fractionDigits: Int,
    ): ParsedMoney {
        val cleaned = latinDigits(withoutEdgeSigns(text.trim()))
        val decimal = decimalSeparator.singleOrNull() ?: '.'
        return when {
            cleaned.isEmpty() -> ParsedMoney(null, null)
            cleaned.startsWith("-") -> negative(cleaned.drop(1), decimal, fractionDigits)
            else -> read(cleaned, decimal, fractionDigits)
        }
    }

    /** One currency sign at either end (and the space beside it) is fine; elsewhere it is not part of a number. */
    private fun withoutEdgeSigns(text: String): String {
        val noLeading = if (text.firstOrNull()?.category == CharCategory.CURRENCY_SYMBOL) text.drop(1).trim() else text
        return if (noLeading.lastOrNull()?.category == CharCategory.CURRENCY_SYMBOL) {
            noLeading.dropLast(1).trim()
        } else {
            noLeading
        }
    }

    /** Digits of any numeral set (Arabic-Indic, Persian, ...) become Latin digits; the Arabic thousands mark a comma. */
    private fun latinDigits(text: String): String =
        text
            .map { ch -> ch.digitToIntOrNull()?.let { ('0' + it) } ?: if (ch == ARABIC_GROUP) ',' else ch }
            .joinToString("")

    private fun negative(
        rest: String,
        decimal: Char,
        fractionDigits: Int,
    ): ParsedMoney =
        if (read(rest, decimal, fractionDigits).issue == InvalidKey.AmountNotNumeric || rest.isEmpty()) {
            ParsedMoney(null, InvalidKey.AmountNotNumeric)
        } else {
            ParsedMoney(null, InvalidKey.AmountNotPositive)
        }

    private fun read(
        text: String,
        decimal: Char,
        fractionDigits: Int,
    ): ParsedMoney {
        val group = if (decimal == ',') '.' else ','
        val parts = text.split(decimal)
        val whole = parts[0]
        val fraction = parts.getOrElse(1) { "" }
        val invalid =
            parts.size > 2 ||
                (whole.isEmpty() && fraction.isEmpty()) ||
                !wellFormedWhole(whole, group) ||
                !fraction.all { it in '0'..'9' }
        return when {
            invalid -> ParsedMoney(null, InvalidKey.AmountNotNumeric)
            fraction.length > fractionDigits -> ParsedMoney(null, InvalidKey.AmountTooManyDecimals)
            else -> amount(whole.filter { it != group }.trimStart('0'), fraction, fractionDigits)
        }
    }

    /** Digits only, or digits grouped as 1 to 3 then runs of exactly 3 (the only grouping that cannot be misread). */
    private fun wellFormedWhole(
        whole: String,
        group: Char,
    ): Boolean {
        val groups = whole.split(group)
        val first = groups.first()
        val rest = groups.drop(1)
        return groups.all { part -> part.all { it in '0'..'9' } } &&
            if (rest.isEmpty()) true else first.length in 1..GROUP_SIZE && rest.all { it.length == GROUP_SIZE }
    }

    private fun amount(
        whole: String,
        fraction: String,
        fractionDigits: Int,
    ): ParsedMoney {
        if (whole.length > MAX_WHOLE_DIGITS) return ParsedMoney(null, InvalidKey.AmountTooLarge)
        var scale = 1L
        repeat(fractionDigits) { scale *= RADIX }
        val padded = fraction.padEnd(fractionDigits, '0')
        val minor = (whole.ifEmpty { "0" }.toLong() * scale) + (padded.ifEmpty { "0" }.toLong())
        return if (minor <= 0L) ParsedMoney(null, InvalidKey.AmountNotPositive) else ParsedMoney(minor, null)
    }
}
