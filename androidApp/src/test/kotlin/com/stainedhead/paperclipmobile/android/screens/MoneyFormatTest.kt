package com.stainedhead.paperclipmobile.android.screens

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

/** Money as the person reads and types it: minor units in a currency, with the currency's own decimals. */
class MoneyFormatTest {
    @Test
    fun `a whole amount drops its zero cents and any other keeps them`() {
        formatMoney(15_000, "USD", Locale.US) shouldBe "$150"
        formatMoney(15_050, "USD", Locale.US) shouldBe "$150.50"
        formatMoney(0, "USD", Locale.US) shouldBe "$0"
    }

    @Test
    fun `a currency without minor units is read as it is`() {
        fractionDigits("JPY") shouldBe 0
        formatMoney(500, "JPY", Locale.JAPAN).filter { it.isDigit() } shouldBe "500"
    }

    @Test
    fun `the digits of a currency come from the currency, and an unknown code reads as two`() {
        fractionDigits("USD") shouldBe 2
        fractionDigits("KWD") shouldBe 3
        fractionDigits("NOT-A-CODE") shouldBe 2
    }

    @Test
    fun `what shared code needs to read a typed amount is the locales separator and the currencys digits`() {
        MoneyInputFormat.of("USD", Locale.US) shouldBe MoneyInputFormat(".", 2)
        MoneyInputFormat.of("USD", Locale.GERMANY) shouldBe MoneyInputFormat(",", 2)
        MoneyInputFormat.of("JPY", Locale.US) shouldBe MoneyInputFormat(".", 0)
    }
}
