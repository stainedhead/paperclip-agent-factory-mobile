package com.stainedhead.paperclipmobile.infrastructure.api.dto

private const val MAX_WHOLE_DIGITS = 15

private fun Char.isAsciiDigit() = this in '0'..'9'

/**
 * Whole cents from the literal of a server number, rounded up, with no floating point (ADR-016, ADR-054). The server
 * reads `numeric(24,7)` spend as a JS number, so the text is a plain decimal such as `12`, `12.5` or `9999.0000001`.
 * Anything else (a sign, an exponent, a second dot, no digits, more than 15 whole digits) is null: the caller decides
 * whether that is a missing value or a malformed reply.
 */
internal fun ceilToCents(literal: String): Long? {
    val whole = literal.substringBefore('.')
    val hasFraction = '.' in literal
    val fraction = literal.substringAfter('.', "")
    val valid =
        whole.isNotEmpty() && whole.length <= MAX_WHOLE_DIGITS && whole.all { it.isAsciiDigit() } &&
            (!hasFraction || (fraction.isNotEmpty() && fraction.all { it.isAsciiDigit() }))
    if (!valid) return null
    val base = whole.toLong()
    return if (fraction.any { it != '0' }) base + 1 else base
}
