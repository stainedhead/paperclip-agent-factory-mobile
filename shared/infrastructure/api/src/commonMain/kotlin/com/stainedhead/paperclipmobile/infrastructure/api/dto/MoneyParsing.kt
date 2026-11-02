package com.stainedhead.paperclipmobile.infrastructure.api.dto

private const val MAX_WHOLE_DIGITS = 15
private const val MAX_EXPONENT_DIGITS = 3

private fun Char.isAsciiDigit() = this in '0'..'9'

/**
 * Whole cents from the literal of a server number, rounded up, with no floating point (ADR-016, ADR-054). The server
 * reads `numeric(24,7)` spend as a JS number, so the text is a plain decimal such as `12`, `12.5` or `9999.0000001`, or
 * an exponent literal such as `5e-7` or `1.5e+3`: `JSON.stringify` prints a number below 1e-6 that way (pinned dist,
 * `db/schema/budget_incidents.js`, `db/schema/agents.js`, `server/services/costs.js`). Anything else (a sign on the
 * number, a second dot, no digits, more than 15 whole digits) is null: the caller decides whether that is a missing
 * value or a malformed reply.
 */
internal fun ceilToCents(literal: String): Long? {
    val exponentAt = literal.indexOfFirst { it == 'e' || it == 'E' }
    val mantissa = if (exponentAt < 0) literal else literal.substring(0, exponentAt)
    val exponent = if (exponentAt < 0) 0 else exponentOf(literal.substring(exponentAt + 1)) ?: return null
    val whole = mantissa.substringBefore('.')
    val hasFraction = '.' in mantissa
    val fraction = mantissa.substringAfter('.', "")
    val valid =
        whole.isNotEmpty() && whole.all { it.isAsciiDigit() } &&
            (!hasFraction || (fraction.isNotEmpty() && fraction.all { it.isAsciiDigit() }))
    return if (valid) shiftedCeil(whole + fraction, whole.length + exponent) else null
}

/** The value of an exponent with an optional sign; null when it is empty or not a small integer. */
private fun exponentOf(text: String): Int? {
    val negative = text.startsWith('-')
    val digits = text.removePrefix("-").removePrefix("+")
    val valid = digits.isNotEmpty() && digits.length <= MAX_EXPONENT_DIGITS && digits.all { it.isAsciiDigit() }
    return if (valid) digits.toInt().let { if (negative) -it else it } else null
}

/** [digits] with the decimal point at [point] (which may lie outside the digits), rounded up to a whole number. */
private fun shiftedCeil(
    digits: String,
    point: Int,
): Long? {
    val integerPart = if (point <= 0) "" else digits.take(point).padEnd(point, '0')
    val remainder = if (point < 0) digits else digits.drop(point)
    val significant = integerPart.trimStart('0')
    if (significant.length > MAX_WHOLE_DIGITS) return null
    val base = if (significant.isEmpty()) 0L else significant.toLong()
    return if (remainder.any { it != '0' }) base + 1 else base
}
