package com.stainedhead.paperclipmobile.android.designsystem

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The mocks' compact ages: each age is cut down to its largest whole unit, never rounded up. */
class RelativeAgeTest {
    @Test
    fun `ages are cut to the largest whole unit like the mocks`() {
        val expected =
            listOf(
                12L to Age(AgeUnit.Seconds, 12),
                59L to Age(AgeUnit.Seconds, 59),
                60L to Age(AgeUnit.Minutes, 1),
                480L to Age(AgeUnit.Minutes, 8),
                3_540L to Age(AgeUnit.Minutes, 59),
                3_600L to Age(AgeUnit.Hours, 1),
                82_800L to Age(AgeUnit.Hours, 23),
                86_400L to Age(AgeUnit.Days, 1),
                259_200L to Age(AgeUnit.Days, 3),
            )

        for ((seconds, age) in expected) RelativeAge.of(seconds) shouldBe age
    }

    @Test
    fun `a negative age reads as zero seconds so a clock skew never shows a future time`() {
        RelativeAge.of(-5) shouldBe Age(AgeUnit.Seconds, 0)
    }

    @Test
    fun `reduced motion turns every animation into an instant change`() {
        Motion.durationMillis(reduceMotion = true) shouldBe 0
        (Motion.durationMillis(reduceMotion = false) > 0) shouldBe true
    }
}
