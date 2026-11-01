package com.stainedhead.paperclipmobile.domain

/**
 * An amount of money as whole minor units (cents) plus an ISO currency code. No floating point anywhere (ADR-016).
 * Formatting is a presentation concern.
 */
data class Money(
    val minorUnits: Long,
    val currency: String,
) : Comparable<Money> {
    init {
        require(minorUnits >= 0) { "Money must not be negative" }
        require(currency.length == CURRENCY_LENGTH && currency.all { it in 'A'..'Z' }) {
            "Currency must be three capital letters"
        }
    }

    override fun compareTo(other: Money): Int {
        require(currency == other.currency) { "Cannot compare $currency with ${other.currency}" }
        return minorUnits.compareTo(other.minorUnits)
    }

    companion object {
        private const val CURRENCY_LENGTH = 3
        const val USD = "USD"

        fun usdCents(cents: Long): Money = Money(cents, USD)
    }
}
