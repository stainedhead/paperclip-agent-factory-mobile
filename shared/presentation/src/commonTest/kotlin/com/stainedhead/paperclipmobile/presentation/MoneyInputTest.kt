package com.stainedhead.paperclipmobile.presentation

import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** A4: what the person typed becomes minor units in shared code, whatever the phone's locale. */
class MoneyInputTest {
    private fun parse(
        text: String,
        decimal: String = ".",
        digits: Int = 2,
    ) = MoneyInput.parse(text, decimal, digits)

    @Test
    fun `a point decimal reads as cents`() {
        parse("250.50").minorUnits shouldBe 25_050L
        parse("250.50").issue shouldBe null
    }

    @Test
    fun `a comma decimal reads as cents when the locale uses a comma`() {
        parse("250,50", decimal = ",").minorUnits shouldBe 25_050L
    }

    @Test
    fun `a point in a comma locale is not silently dropped`() {
        val parsed = parse("250.50", decimal = ",")
        parsed.minorUnits shouldBe null
        parsed.issue shouldBe InvalidKey.AmountNotNumeric
    }

    @Test
    fun `a comma in a point locale is not silently dropped`() {
        val parsed = parse("250,50")
        parsed.minorUnits shouldBe null
        parsed.issue shouldBe InvalidKey.AmountNotNumeric
    }

    @Test
    fun `grouping is understood in both conventions`() {
        parse("1,200.50").minorUnits shouldBe 120_050L
        parse("1.200,50", decimal = ",").minorUnits shouldBe 120_050L
        parse("1,200").minorUnits shouldBe 120_000L
        parse("1.200", decimal = ",").minorUnits shouldBe 120_000L
    }

    @Test
    fun `whole amounts and a leading currency sign read`() {
        parse("250").minorUnits shouldBe 25_000L
        parse("\$1,200").minorUnits shouldBe 120_000L
        parse(" 250. ").minorUnits shouldBe 25_000L
        parse(".5").minorUnits shouldBe 50L
    }

    @Test
    fun `misplaced grouping is refused`() {
        parse("12,34.00").issue shouldBe InvalidKey.AmountNotNumeric
        parse("1,2,3").issue shouldBe InvalidKey.AmountNotNumeric
        parse("1.2.3").issue shouldBe InvalidKey.AmountNotNumeric
        parse("1.2.3", decimal = ",").issue shouldBe InvalidKey.AmountNotNumeric
    }

    @Test
    fun `letters and lone separators are not a number`() {
        parse("abc").issue shouldBe InvalidKey.AmountNotNumeric
        parse("12a").issue shouldBe InvalidKey.AmountNotNumeric
        parse(".").issue shouldBe InvalidKey.AmountNotNumeric
        parse("--5").issue shouldBe InvalidKey.AmountNotNumeric
    }

    @Test
    fun `an empty field is not an error yet`() {
        parse("").minorUnits shouldBe null
        parse("").issue shouldBe null
        parse("   ").issue shouldBe null
    }

    @Test
    fun `negative and zero are not positive`() {
        parse("-5").issue shouldBe InvalidKey.AmountNotPositive
        parse("0").issue shouldBe InvalidKey.AmountNotPositive
        parse("0.00").issue shouldBe InvalidKey.AmountNotPositive
        parse("-5").minorUnits shouldBe null
    }

    @Test
    fun `more decimals than the currency has are refused not rounded`() {
        parse("1.234").issue shouldBe InvalidKey.AmountTooManyDecimals
        parse("1.5", digits = 0).issue shouldBe InvalidKey.AmountTooManyDecimals
        parse("1500", digits = 0).minorUnits shouldBe 1_500L
        parse("1.234", digits = 3).minorUnits shouldBe 1_234L
    }

    @Test
    fun `an amount too large for money is refused without overflow`() {
        parse("99999999999999999999").issue shouldBe InvalidKey.AmountTooLarge
        parse("1000000000000").issue shouldBe InvalidKey.AmountTooLarge
        parse("999999999999").minorUnits shouldBe 99_999_999_999_900L
        parse("000000000250").minorUnits shouldBe 25_000L
    }

    @Test
    fun `R7 native digits of the phone's numeral set read like Latin digits`() {
        parse("\u0662\u0665\u0660\u066B\u0665\u0660", decimal = "\u066B").minorUnits shouldBe 25_050L
        parse("\u06F1\u06F2\u06F3").minorUnits shouldBe 12_300L
    }

    @Test
    fun `R7 whitespace inside the amount and a currency sign in the middle are refused`() {
        parse("1 2 3").issue shouldBe InvalidKey.AmountNotNumeric
        parse("1\u00A0200").issue shouldBe InvalidKey.AmountNotNumeric
        parse("1$5").issue shouldBe InvalidKey.AmountNotNumeric
        parse("  250.50  ").minorUnits shouldBe 25_050L
        parse("250.50 \$").minorUnits shouldBe 25_050L
        parse("\$ 250").minorUnits shouldBe 25_000L
    }

    @Test
    fun `R7 one comma then three digits is grouping where the locale's decimal mark is a point`() {
        // The rule: the mark that is not the locale's decimal mark groups thousands. "1,500" is 1500.00 in a point
        // locale and refused (three decimals) in a comma locale; the Raise button shows the parsed amount.
        parse("1,500").minorUnits shouldBe 150_000L
        parse("1,500", decimal = ",").issue shouldBe InvalidKey.AmountTooManyDecimals
    }
}
